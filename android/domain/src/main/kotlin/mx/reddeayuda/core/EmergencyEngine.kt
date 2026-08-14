package mx.reddeayuda.core

import mx.reddeayuda.protocol.AckKind
import mx.reddeayuda.protocol.AckPayload
import mx.reddeayuda.protocol.DeviceRole
import mx.reddeayuda.protocol.DeviceState
import mx.reddeayuda.protocol.EmergencyPacket
import mx.reddeayuda.protocol.EventType
import mx.reddeayuda.protocol.GeoFix
import mx.reddeayuda.protocol.OriginStatus
import mx.reddeayuda.protocol.PacketType
import mx.reddeayuda.protocol.ProtocolConstants
import mx.reddeayuda.protocol.RescueAction
import mx.reddeayuda.protocol.RescuePingPayload
import mx.reddeayuda.protocol.StateEvent

class StoreForwardEngine(
    private val localDeviceId: ByteArray,
    private val clock: Clock,
    private val repository: PacketRepository,
    private val cache: MessageCache,
    private val ttlPolicy: TtlPolicy = TtlPolicy(),
    private val queueCapacity: Int = ProtocolConstants.QUEUE_CAPACITY
) {
    fun receiveForStore(packet: EmergencyPacket): Boolean {
        val now = clock.nowEpochSeconds()
        if (!ttlPolicy.shouldForward(packet.ttl, packet.timestamp, now) && packet.ttl == 0) {
            return false
        }
        if (ttlPolicy.isExpired(packet.timestamp, now)) return false
        if (packet.ttl == 0) return false
        evictIfNeeded(packet)
        repository.insert(packet, now)
        return true
    }

    fun packetsToSend(): List<EmergencyPacket> {
        val now = clock.nowEpochSeconds()
        val queued = repository.getAll().sortedWith(
            compareBy<QueuedPacket> { Priority.of(it.packet, localDeviceId) }
                .thenBy { it.firstSeenAt }
        )
        val out = mutableListOf<EmergencyPacket>()
        for (item in queued) {
            val p = item.packet
            if (ttlPolicy.isExpired(p.timestamp, now) || p.ttl <= 0) {
                repository.delete(p.messageId)
                continue
            }
            out.add(
                p.copy(
                    ttl = (p.ttl - 1).coerceAtLeast(0),
                    hopCount = (p.hopCount + 1).coerceAtMost(255)
                )
            )
            item.lastSentAt = now
            item.sendCount += 1
        }
        return out
    }

    fun pendingCount(): Int = repository.size()

    fun hasSos(): Boolean = repository.getAll().any { it.packet.type == PacketType.SOS }

    private fun evictIfNeeded(incoming: EmergencyPacket) {
        if (repository.size() < queueCapacity) return
        val worst = repository.getAll()
            .sortedWith(
                compareByDescending<QueuedPacket> { Priority.of(it.packet, localDeviceId) }
                    .thenBy { it.firstSeenAt }
            )
            .lastOrNull() ?: return
        val incomingPri = Priority.of(incoming, localDeviceId)
        val worstPri = Priority.of(worst.packet, localDeviceId)
        if (incomingPri <= worstPri) {
            repository.delete(worst.packet.messageId)
        }
    }
}

class EmergencyEngine(
    val localDeviceId: ByteArray,
    private val clock: Clock,
    private val repository: PacketRepository,
    val cache: MessageCache,
    val stateMachine: EmergencyStateMachine,
    private val ttlPolicy: TtlPolicy = TtlPolicy(),
    var listener: EngineListener = object : EngineListener {},
    var repeaterAck: Boolean = false
) {
    private val store = StoreForwardEngine(localDeviceId, clock, repository, cache, ttlPolicy)
    var lastAckKind: AckKind? = null
        private set
    private val visibleSos = LinkedHashMap<String, EmergencyPacket>()

    fun snapshot(): StateSnapshot = stateMachine.snapshot()

    fun visibleSosPackets(): List<EmergencyPacket> = visibleSos.values.toList()

    fun pendingCount(): Int = store.pendingCount()

    fun hasSosQueued(): Boolean = store.hasSos()

    fun createEmergency(
        fix: GeoFix,
        battery: Int,
        eventType: EventType = EventType.USER_INITIATED
    ): EmergencyPacket {
        stateMachine.onEvent(StateEvent.USER_NEED_HELP)
        listener.onStateChanged(snapshot())
        val packet = newPacket(
            type = PacketType.SOS,
            ttl = ProtocolConstants.DEFAULT_TTL,
            eventType = eventType,
            status = OriginStatus.NEED_HELP,
            battery = battery.coerceIn(0, 100),
            fix = fix,
            payload = ByteArray(0)
        )
        acceptLocal(packet)
        listener.onLog("SOS creado ${packet.shortId()}")
        return packet
    }

    fun createAck(kind: AckKind, ref: EmergencyPacket, battery: Int = ProtocolConstants.UNKNOWN_BATTERY): EmergencyPacket {
        val packet = newPacket(
            type = PacketType.ACK,
            ttl = ProtocolConstants.ACK_TTL,
            eventType = EventType.UNKNOWN,
            status = OriginStatus.UNKNOWN,
            battery = battery,
            fix = GeoFix(),
            payload = AckPayload(kind, ref.messageId).toBytes(),
            extraFlags = ProtocolConstants.FLAG_RESCUE_NODE
        )
        acceptLocal(packet)
        return packet
    }

    fun createRescuePing(targetDeviceId: ByteArray, action: RescueAction, battery: Int = ProtocolConstants.UNKNOWN_BATTERY): EmergencyPacket {
        val payload = RescuePingPayload(
            requestId = EmergencyPacket.randomMessageId(),
            targetDeviceId = targetDeviceId,
            action = action
        ).toBytes()
        val packet = newPacket(
            type = PacketType.RESCUE_PING,
            ttl = ProtocolConstants.PING_TTL,
            eventType = EventType.UNKNOWN,
            status = OriginStatus.UNKNOWN,
            battery = battery,
            fix = GeoFix(),
            payload = payload,
            extraFlags = ProtocolConstants.FLAG_RESCUE_NODE
        )
        acceptLocal(packet)
        val sos = visibleSos.values.find { it.originDeviceId.contentEquals(targetDeviceId) }
        if (sos != null) {
            createAck(AckKind.RESCUE_CONTACT, sos, battery)
        }
        return packet
    }

    fun createResponse(status: OriginStatus, text: ByteArray, fix: GeoFix, battery: Int): EmergencyPacket {
        val packet = newPacket(
            type = PacketType.RESPONSE,
            ttl = ProtocolConstants.DEFAULT_TTL,
            eventType = EventType.USER_INITIATED,
            status = status,
            battery = battery,
            fix = fix,
            payload = text.copyOf(minOf(text.size, 80))
        )
        acceptLocal(packet)
        return packet
    }

    fun enterRescueRole() {
        stateMachine.onEvent(StateEvent.ENTER_RESCUE_ROLE)
        listener.onStateChanged(snapshot())
    }

    fun enterRepeaterRole() {
        stateMachine.onEvent(StateEvent.LEAVE_RESCUE_ROLE)
        if (stateMachine.state == DeviceState.SOS || stateMachine.state == DeviceState.RESCUE_CONTACT) {
            stateMachine.onEvent(StateEvent.USER_RESOLVE)
        }
        listener.onStateChanged(snapshot())
    }

    fun imOk() {
        stateMachine.onEvent(StateEvent.USER_IM_OK)
        // Si aún quedara en SOS (versión antigua), forzar resolución.
        if (stateMachine.state == DeviceState.SOS || stateMachine.state == DeviceState.RESCUE_CONTACT) {
            stateMachine.onEvent(StateEvent.USER_RESOLVE)
        }
        dropOwnSosPackets()
        lastAckKind = null
        listener.onStateChanged(snapshot())
        listener.onLog("Estoy bien — SOS cancelado")
        listener.onQueueChanged(pendingCount())
    }

    private fun dropOwnSosPackets() {
        repository.getAll()
            .map { it.packet }
            .filter {
                it.type == PacketType.SOS && it.originDeviceId.contentEquals(localDeviceId)
            }
            .forEach { repository.delete(it.messageId) }
    }

    fun startSafetyCheck() {
        stateMachine.onEvent(StateEvent.START_SAFETY_CHECK)
        listener.onStateChanged(snapshot())
    }

    fun resolve() {
        stateMachine.onEvent(StateEvent.USER_RESOLVE)
        listener.onStateChanged(snapshot())
    }

    /**
     * Punto único de entrada de la mesh. Dedup + TTL + store + efectos locales.
     */
    fun receive(packet: EmergencyPacket): ReceiveResult {
        if (packet.version != ProtocolConstants.VERSION) {
            return ReceiveResult.DROP_VERSION
        }
        val now = clock.nowEpochSeconds()
        if (ttlPolicy.isExpired(packet.timestamp, now)) {
            return ReceiveResult.DROP_EXPIRED
        }
        if (cache.contains(packet.messageId)) {
            return ReceiveResult.DROP_DUPLICATE
        }
        cache.add(packet.messageId, now)

        var localEffect = false
        when (packet.type) {
            PacketType.RESCUE_PING, PacketType.RESCUE_PING_ALL -> {
                val ping = RescuePingPayload.parse(packet.payload)
                if (ping != null && ping.isFor(localDeviceId)) {
                    stateMachine.onEvent(StateEvent.PACKET_RESCUE_PING)
                    listener.onStateChanged(snapshot())
                    listener.onRescuePingForMe(ping.action, packet)
                    localEffect = true
                }
            }
            PacketType.ACK -> {
                val ack = AckPayload.parse(packet.payload)
                if (ack != null && repository.findByMessageId(ack.refMessageId)?.packet?.isOrigin(localDeviceId) == true) {
                    val previous = lastAckKind
                    if (previous == null || ack.kind.code > previous.code) {
                        lastAckKind = ack.kind
                    }
                    listener.onAckForMe(ack.kind, packet)
                    localEffect = true
                }
            }
            PacketType.SOS, PacketType.LOCATION_UPDATE -> {
                if (stateMachine.role == DeviceRole.RESCUER) {
                    visibleSos[packet.messageIdHex()] = packet
                    listener.onSosForRescuer(packet)
                    if (packet.type == PacketType.SOS) {
                        createAck(AckKind.MESSAGE_DELIVERED, packet)
                    }
                    localEffect = true
                }
            }
            else -> Unit
        }

        val stored = if (packet.ttl > 0 && ttlPolicy.shouldForward(packet.ttl, packet.timestamp, now)) {
            store.receiveForStore(packet)
        } else {
            false
        }

        if (repeaterAck && packet.type == PacketType.SOS && stateMachine.role != DeviceRole.RESCUER) {
            createAck(AckKind.MESSAGE_RECEIVED, packet)
        }

        listener.onQueueChanged(store.pendingCount())
        listener.onLog("recv ${packet.type} ${packet.shortId()} hop=${packet.hopCount} ttl=${packet.ttl} stored=$stored")
        return if (stored || localEffect) ReceiveResult.ACCEPTED else ReceiveResult.DROP_TTL
    }

    fun packetsToSend(): List<EmergencyPacket> = store.packetsToSend()

    fun flags(): Int {
        var f = ProtocolConstants.FLAG_BLE
        if (stateMachine.role == DeviceRole.RESCUER) f = f or ProtocolConstants.FLAG_RESCUE_NODE
        return f
    }

    private fun acceptLocal(packet: EmergencyPacket) {
        val now = clock.nowEpochSeconds()
        cache.add(packet.messageId, now)
        store.receiveForStore(packet)
        listener.onQueueChanged(store.pendingCount())
    }

    private fun newPacket(
        type: PacketType,
        ttl: Int,
        eventType: EventType,
        status: OriginStatus,
        battery: Int,
        fix: GeoFix,
        payload: ByteArray,
        extraFlags: Int = 0
    ): EmergencyPacket {
        return EmergencyPacket(
            type = type,
            flags = flags() or extraFlags,
            ttl = ttl,
            hopCount = 0,
            eventType = eventType,
            status = status,
            battery = battery,
            messageId = EmergencyPacket.randomMessageId(),
            originDeviceId = localDeviceId.copyOf(),
            timestamp = clock.nowEpochSeconds(),
            latitudeMicrodegrees = fix.latitudeMicrodegrees,
            longitudeMicrodegrees = fix.longitudeMicrodegrees,
            accuracyMeters = fix.accuracyMeters,
            payload = payload
        )
    }
}

enum class ReceiveResult {
    ACCEPTED,
    DROP_DUPLICATE,
    DROP_EXPIRED,
    DROP_TTL,
    DROP_VERSION
}
