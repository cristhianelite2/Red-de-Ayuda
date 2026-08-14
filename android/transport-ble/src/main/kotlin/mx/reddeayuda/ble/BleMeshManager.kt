package mx.reddeayuda.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import mx.reddeayuda.protocol.ProtocolConstants
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Transporte BLE: beacon + sesión GATT corta. El Core no vive aquí.
 */
class BleMeshManager(
    private val context: Context,
    private val localDeviceId: ByteArray,
    private val hooks: Hooks
) {
    interface Hooks {
        fun outgoingPackets(): List<ByteArray>
        fun onIncoming(bytes: ByteArray)
        fun metaBytes(): ByteArray
        fun beaconBytes(): ByteArray
        fun onLog(message: String)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val serviceUuid: UUID = UUID.fromString(ProtocolConstants.SERVICE_UUID)
    private val metaUuid: UUID = UUID.fromString(ProtocolConstants.META_UUID)
    private val inboxUuid: UUID = UUID.fromString(ProtocolConstants.INBOX_UUID)
    private val outboxUuid: UUID = UUID.fromString(ProtocolConstants.OUTBOX_UUID)
    private val seenUuid: UUID = UUID.fromString(ProtocolConstants.SEEN_UUID)
    private val cccdUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = manager.adapter

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var gattServer: BluetoothGattServer? = null
    private var advertisingSupported = false
    private var clientGatt: BluetoothGatt? = null
    private var connecting = false
    private val seenPeers = ConcurrentHashMap<String, Long>()
    private var running = false

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            hooks.onLog("Bluetooth apagado o inexistente")
            return
        }
        running = true
        advertisingSupported = bt.isMultipleAdvertisementSupported
        openServer()
        startAdvertising()
        startScanning()
        hooks.onLog(
            if (advertisingSupported) "BLE dual (advertise+scan)"
            else "BLE solo central (este teléfono no anuncia)"
        )
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        running = false
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: Exception) {
        }
        try {
            clientGatt?.disconnect()
            clientGatt?.close()
        } catch (_: Exception) {
        }
        clientGatt = null
        connecting = false
        try {
            gattServer?.close()
        } catch (_: Exception) {
        }
        gattServer = null
    }

    fun isAdvertisingSupported(): Boolean = advertisingSupported

    @SuppressLint("MissingPermission")
    private fun openServer() {
        gattServer = manager.openGattServer(context, serverCallback)
        val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val meta = BluetoothGattCharacteristic(
            metaUuid,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val inbox = BluetoothGattCharacteristic(
            inboxUuid,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val outbox = BluetoothGattCharacteristic(
            outboxUuid,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        outbox.addDescriptor(
            BluetoothGattDescriptor(cccdUuid, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
        )
        val seen = BluetoothGattCharacteristic(
            seenUuid,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(meta)
        service.addCharacteristic(inbox)
        service.addCharacteristic(outbox)
        service.addCharacteristic(seen)
        gattServer?.addService(service)
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        if (!advertisingSupported) return
        advertiser = adapter?.bluetoothLeAdvertiser
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(serviceUuid))
            .setIncludeDeviceName(false)
            .addManufacturerData(ProtocolConstants.MANUFACTURER_ID, hooks.beaconBytes())
            .build()
        try {
            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            hooks.onLog("Advertise con beacon falló, UUID solo: ${e.message}")
            val fallback = AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(serviceUuid))
                .setIncludeDeviceName(false)
                .build()
            advertiser?.startAdvertising(settings, fallback, advertiseCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        scanner = adapter?.bluetoothLeScanner
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner?.startScan(listOf(filter), settings, scanCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            hooks.onLog("Advertising OK")
        }

        override fun onStartFailure(errorCode: Int) {
            hooks.onLog("Advertising error $errorCode")
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!running) return
            val device = result.device ?: return
            val addr = device.address ?: return
            val now = System.currentTimeMillis()
            val last = seenPeers[addr] ?: 0L
            if (now - last < 4_000) return
            seenPeers[addr] = now
            if (!shouldInitiate(addr)) return
            connectAsCentral(device)
        }

        override fun onScanFailed(errorCode: Int) {
            hooks.onLog("Scan error $errorCode")
        }
    }

    private fun shouldInitiate(remoteAddress: String): Boolean {
        if (!advertisingSupported) return true
        val local = mx.reddeayuda.protocol.Hex.encode(localDeviceId)
        return local > remoteAddress.replace(":", "").lowercase()
    }

    @SuppressLint("MissingPermission")
    private fun connectAsCentral(device: BluetoothDevice) {
        if (connecting) return
        connecting = true
        hooks.onLog("Conectando a ${device.address}")
        clientGatt = device.connectGatt(context, false, clientCallback, BluetoothDevice.TRANSPORT_LE)
        handler.postDelayed({
            if (connecting) {
                hooks.onLog("Timeout GATT, cierro")
                closeClient()
            }
        }, 8_000)
    }

    @SuppressLint("MissingPermission")
    private fun closeClient() {
        try {
            clientGatt?.disconnect()
            clientGatt?.close()
        } catch (_: Exception) {
        }
        clientGatt = null
        connecting = false
    }

    private val clientCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                handler.post { gatt.requestMtu(517) }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                handler.post { closeClient() }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            hooks.onLog("MTU $mtu")
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(serviceUuid)
            if (service == null) {
                hooks.onLog("Sin servicio RDA")
                closeClient()
                return
            }
            val outbox = service.getCharacteristic(outboxUuid)
            val inbox = service.getCharacteristic(inboxUuid)
            if (outbox != null) {
                gatt.setCharacteristicNotification(outbox, true)
                val cccd = outbox.getDescriptor(cccdUuid)
                if (cccd != null) {
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(cccd)
                }
            }
            handler.postDelayed({
                writeOutgoing(gatt, inbox)
            }, 200)
            handler.postDelayed({ closeClient() }, 1_800)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == outboxUuid) {
                val value = characteristic.value ?: return
                hooks.onIncoming(value)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                hooks.onLog("write status $status")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeOutgoing(gatt: BluetoothGatt, inbox: BluetoothGattCharacteristic?) {
        if (inbox == null) return
        val packets = hooks.outgoingPackets()
        for (p in packets.take(8)) {
            inbox.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            inbox.value = p
            gatt.writeCharacteristic(inbox)
            try {
                Thread.sleep(80)
            } catch (_: InterruptedException) {
            }
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                hooks.onLog("Peer GATT ${device.address}")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = when (characteristic.uuid) {
                metaUuid -> hooks.metaBytes()
                outboxUuid -> hooks.outgoingPackets().firstOrNull() ?: ByteArray(0)
                else -> ByteArray(0)
            }
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (characteristic.uuid == inboxUuid && value != null) {
                hooks.onIncoming(value)
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
            if (descriptor.uuid == cccdUuid) {
                handler.post { notifyOutbox(device) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyOutbox(device: BluetoothDevice) {
        val server = gattServer ?: return
        val service = server.getService(serviceUuid) ?: return
        val outbox = service.getCharacteristic(outboxUuid) ?: return
        for (p in hooks.outgoingPackets().take(8)) {
            outbox.value = p
            server.notifyCharacteristicChanged(device, outbox, false)
            try {
                Thread.sleep(60)
            } catch (_: InterruptedException) {
            }
        }
    }
}
