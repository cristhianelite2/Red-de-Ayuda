package mx.reddeayuda.protocol

import mx.reddeayuda.core.BatteryPolicy
import mx.reddeayuda.core.EmergencyEngine
import mx.reddeayuda.core.EmergencyStateMachine
import mx.reddeayuda.core.FixedClock
import mx.reddeayuda.core.InMemoryPacketRepository
import mx.reddeayuda.core.MessageCache
import mx.reddeayuda.core.ReceiveResult
import mx.reddeayuda.core.TtlPolicy
import mx.reddeayuda.protocol.DeviceRole
import mx.reddeayuda.protocol.DeviceState
import mx.reddeayuda.protocol.StateEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyPacketCodecTest {

    @Test
    fun vector1SosRoundtrip() {
        val hex = loadHex("v1-sos.hex")
        val bytes = Hex.decode(hex)
        val packet = EmergencyPacketCodec.decode(bytes)
        assertEquals(1, packet.version)
        assertEquals(PacketType.SOS, packet.type)
        assertEquals(0x01, packet.flags)
        assertEquals(20, packet.ttl)
        assertEquals(0, packet.hopCount)
        assertEquals(EventType.USER_INITIATED, packet.eventType)
        assertEquals(OriginStatus.NEED_HELP, packet.status)
        assertEquals(73, packet.battery)
        assertEquals(1_700_000_000L, packet.timestamp)
        assertEquals(19_432_608, packet.latitudeMicrodegrees)
        assertEquals(-99_133_100, packet.longitudeMicrodegrees)
        assertEquals(15, packet.accuracyMeters)
        assertEquals(0, packet.payload.size)
        assertEquals(hex, Hex.encode(EmergencyPacketCodec.encode(packet)))
    }

    @Test
    fun vector2Ack() {
        val packet = EmergencyPacketCodec.decode(Hex.decode(loadHex("v1-ack.hex")))
        assertEquals(PacketType.ACK, packet.type)
        assertEquals(10, packet.ttl)
        assertEquals(1, packet.hopCount)
        val ack = AckPayload.parse(packet.payload)!!
        assertEquals(AckKind.MESSAGE_DELIVERED, ack.kind)
        assertEquals("000102030405060708090a0b0c0d0e0f", Hex.encode(ack.refMessageId))
        assertEquals(loadHex("v1-ack.hex"), Hex.encode(EmergencyPacketCodec.encode(packet)))
    }

    @Test
    fun vector3RescuePing() {
        val packet = EmergencyPacketCodec.decode(Hex.decode(loadHex("v1-rescue-ping.hex")))
        assertEquals(PacketType.RESCUE_PING, packet.type)
        val ping = RescuePingPayload.parse(packet.payload)!!
        assertEquals(RescueAction.SOUND, ping.action)
        assertEquals("aabbccddeeff0011", Hex.encode(ping.targetDeviceId))
        assertEquals(loadHex("v1-rescue-ping.hex"), Hex.encode(EmergencyPacketCodec.encode(packet)))
    }

    @Test
    fun rejectsWrongVersion() {
        val bytes = Hex.decode(loadHex("v1-sos.hex"))
        bytes[0] = 2
        try {
            EmergencyPacketCodec.decode(bytes)
            throw AssertionError("expected failure")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("version"))
        }
    }

    @Test
    fun rejectsTruncated() {
        try {
            EmergencyPacketCodec.decode(ByteArray(20))
            throw AssertionError("expected failure")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("short"))
        }
    }

    private fun loadHex(name: String): String {
        val stream = javaClass.getResourceAsStream("/vectors/$name")
            ?: error("missing classpath /vectors/$name")
        return stream.bufferedReader().readText().replace(Regex("\\s"), "")
    }
}

class TtlAndDedupTest {
    @Test
    fun ttlExpiredAndFuture() {
        val p = TtlPolicy()
        assertTrue(p.isExpired(0, 90_000))
        assertTrue(p.isExpired(10_000, 1_000))
        assertFalse(p.isExpired(1_000, 1_100))
        assertFalse(p.shouldForward(0, 1_000, 1_100))
        assertTrue(p.shouldForward(1, 1_000, 1_100))
    }

    @Test
    fun lruDedup() {
        val cache = MessageCache(capacity = 2)
        val a = ByteArray(16) { 1 }
        val b = ByteArray(16) { 2 }
        val c = ByteArray(16) { 3 }
        cache.add(a, 1)
        cache.add(b, 2)
        assertTrue(cache.contains(a))
        cache.add(c, 3)
        assertFalse(cache.contains(a))
        assertTrue(cache.contains(b))
        assertTrue(cache.contains(c))
    }
}

class StateMachineTest {
    @Test
    fun legalAndIllegal() {
        val sm = EmergencyStateMachine()
        assertTrue(sm.onEvent(StateEvent.USER_NEED_HELP))
        assertEquals(DeviceState.SOS, sm.state)
        assertTrue(sm.onEvent(StateEvent.USER_IM_OK))
        assertEquals(DeviceState.NORMAL, sm.state)
        assertTrue(sm.onEvent(StateEvent.USER_NEED_HELP))
        assertEquals(DeviceState.SOS, sm.state)
        assertTrue(sm.onEvent(StateEvent.PACKET_RESCUE_PING))
        assertEquals(DeviceState.RESCUE_CONTACT, sm.state)
        assertTrue(sm.onEvent(StateEvent.USER_RESOLVE))
        assertEquals(DeviceState.NORMAL, sm.state)
        assertTrue(sm.onEvent(StateEvent.DISASTER_ALERT))
        assertEquals(DeviceState.DISASTER, sm.state)
        assertTrue(sm.onEvent(StateEvent.DISASTER_CLEAR))
        assertEquals(DeviceState.NORMAL, sm.state)
        sm.onEvent(StateEvent.ENTER_RESCUE_ROLE)
        assertEquals(DeviceRole.RESCUER, sm.role)
    }
}

class EngineAckStoreForwardTest {
    private fun engine(id: Int, role: DeviceRole = DeviceRole.CIVILIAN, clock: FixedClock = FixedClock(1_700_000_000L)): EmergencyEngine {
        return EmergencyEngine(
            localDeviceId = ByteArray(8) { id.toByte() },
            clock = clock,
            repository = InMemoryPacketRepository(),
            cache = MessageCache(),
            stateMachine = EmergencyStateMachine(role)
        )
    }

    @Test
    fun storeForwardAndDedup() {
        val clock = FixedClock(1_700_000_000L)
        val a = engine(1, clock = clock)
        val b = engine(2, clock = clock)
        val sos = a.createEmergency(GeoFix(1, 2, 10), 80)
        assertEquals(ReceiveResult.ACCEPTED, b.receive(sos.copy(ttl = sos.ttl - 1, hopCount = sos.hopCount + 1)))
        assertEquals(ReceiveResult.DROP_DUPLICATE, b.receive(sos.copy(ttl = 10, hopCount = 5)))
        val fwd = b.packetsToSend().first { it.messageId.contentEquals(sos.messageId) }
        assertEquals(sos.ttl - 2, fwd.ttl)
        assertEquals(2, fwd.hopCount)
    }

    @Test
    fun ttlZeroNotStored() {
        val a = engine(1)
        val sos = a.createEmergency(GeoFix(), 50)
        val b = engine(2)
        assertEquals(ReceiveResult.DROP_TTL, b.receive(sos.copy(ttl = 0, hopCount = 20)))
        assertEquals(0, b.pendingCount())
    }

    @Test
    fun ackDeliveredOnlyForRescuerAuto() {
        val victim = engine(1)
        val rescue = engine(9, DeviceRole.RESCUER)
        val sos = victim.createEmergency(GeoFix(1, 1, 5), 40)
        rescue.receive(sos.copy(ttl = 19, hopCount = 1))
        val acks = rescue.packetsToSend().filter { it.type == PacketType.ACK }
        assertTrue(acks.isNotEmpty())
        val kind = AckPayload.parse(acks.first().payload)!!.kind
        assertEquals(AckKind.MESSAGE_DELIVERED, kind)
        victim.receive(acks.first())
        assertEquals(AckKind.MESSAGE_DELIVERED, victim.lastAckKind)
    }

    @Test
    fun batteryIntervals() {
        assertEquals(5L * 60_000, BatteryPolicy.originRetryMs(80))
        assertEquals(10L * 60_000, BatteryPolicy.originRetryMs(30))
        assertEquals(25L * 60_000, BatteryPolicy.originRetryMs(10))
        assertEquals(30L * 60_000, BatteryPolicy.originRetryMs(4))
        val normal = BatteryPolicy.dutyCycle(DeviceRole.CIVILIAN, DeviceState.NORMAL, 90)
        val rescue = BatteryPolicy.dutyCycle(DeviceRole.RESCUER, DeviceState.NORMAL, 90)
        assertTrue(rescue.scanOnMs >= normal.scanOnMs)
        assertTrue(normal.advertiseOffMs > 10_000)
    }

    @Test
    fun transportSelectionBleOnlyInMvp() {
        val caps = ProtocolConstants.FLAG_BLE
        assertEquals(ProtocolConstants.FLAG_BLE, caps and ProtocolConstants.FLAG_BLE)
        assertEquals(0, caps and ProtocolConstants.FLAG_WIFI)
    }
}
