package com.padlink.controller.net

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
import android.bluetooth.BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelUuid
import java.util.UUID

/**
 * Le téléphone se présente en Bluetooth Low Energy comme une manette Xbox sans fil (HID over GATT).
 * Windows la reconnaît avec son pilote intégré (xinputhid) : rien à installer sur le PC.
 *
 * Sécurité : toutes les caractéristiques HID exigent un lien chiffré, donc un appairage que l'utilisateur
 * valide sur le téléphone (code identique sur les deux écrans). Un seul PC est servi à la fois.
 */
@SuppressLint("MissingPermission") // permissions vérifiées par l'appelant (Link.startBluetooth)
class BleGamepad(private val ctx: Context, private val cb: Callback) {

    interface Callback {
        /** [host] : nom du PC connecté, ou null. */
        fun onBleState(connected: Boolean, text: String, host: String? = null)
        /** Vibrations demandées par le PC, 0..255. */
        fun onBleRumble(strong: Int, weak: Int)
    }

    private val manager = ctx.getSystemService(BluetoothManager::class.java)
    private val thread = HandlerThread("padlink-ble").apply { start() }
    private val h = Handler(thread.looper)
    private var server: BluetoothGattServer? = null
    private val pending = ArrayDeque<BluetoothGattService>()
    private lateinit var input: BluetoothGattCharacteristic
    private lateinit var battery: BluetoothGattCharacteristic
    private var advertising = false
    @Volatile private var stopped = false

    // État de la liaison (accédé sous verrou : rappels Bluetooth + fil de l'interface)
    private var host: BluetoothDevice? = null
    private var notifying = false
    private var inFlight = false
    private var inFlightSince = 0L
    private var latest = XboxReport.encode(InputState.NEUTRAL)
    private var lastSent: ByteArray? = null

    /** Démarre ; renvoie un message d'erreur si impossible. */
    fun start(): String? {
        val adapter = manager?.adapter ?: return "Ce téléphone n'a pas de Bluetooth."
        if (!adapter.isEnabled) return "Active le Bluetooth du téléphone."
        if (!adapter.isMultipleAdvertisementSupported || adapter.bluetoothLeAdvertiser == null) {
            return "Ce téléphone ne peut pas se présenter comme une manette Bluetooth (mode périphérique BLE non pris en charge)."
        }
        val s = manager.openGattServer(ctx, gattCb) ?: return "Bluetooth indisponible."
        server = s
        pending.addAll(listOf(hidService(), deviceInfoService(), batteryService()))
        addNextService()
        cb.onBleState(false, "Préparation du Bluetooth…")
        return null
    }

    fun stop() {
        stopped = true
        h.post {
            stopAdvertising()
            synchronized(this) { host?.let { server?.cancelConnection(it) } }
            runCatching { server?.close() }
            server = null
            thread.quitSafely()
        }
    }

    /** Nouvel état de la manette : envoyé au PC dès que le lien est libre (le plus récent gagne). */
    fun update(s: InputState) {
        val r = XboxReport.encode(s)
        synchronized(this) {
            latest = r
            if (!inFlight) h.post { pump() }
        }
    }

    // ------------------------------------------------------------------ envoi

    private fun pump() {
        val dev: BluetoothDevice
        val value: ByteArray
        synchronized(this) {
            val now = System.currentTimeMillis()
            if (inFlight && now - inFlightSince > 250) inFlight = false  // accusé perdu
            if (inFlight || !notifying) return
            dev = host ?: return
            value = latest
            if (lastSent?.contentEquals(value) == true) return
            inFlight = true
            inFlightSince = now
        }
        val ok = notify(dev, input, value)
        synchronized(this) {
            if (ok) lastSent = value
            else { inFlight = false; h.postDelayed({ pump() }, 4) }  // pile occupée : on réessaie
        }
    }

    @Suppress("DEPRECATION")
    private fun notify(dev: BluetoothDevice, c: BluetoothGattCharacteristic, value: ByteArray): Boolean {
        val s = server ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= 33) s.notifyCharacteristicChanged(dev, c, false, value) == BluetoothGatt.GATT_SUCCESS
            else { c.value = value; s.notifyCharacteristicChanged(dev, c, false) }
        }.getOrDefault(false)
    }

    // ------------------------------------------------------------------ services GATT

    private fun addNextService() {
        val next = pending.removeFirstOrNull()
        if (next == null) { startAdvertising(); return }
        if (server?.addService(next) != true) cb.onBleState(false, "Erreur Bluetooth (service GATT refusé).")
    }

    private fun hidService() = BluetoothGattService(HID, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
        addCharacteristic(chr(PROTOCOL_MODE, P_READ or P_WRITE_NR, PERMISSION_READ_ENCRYPTED or PERMISSION_WRITE_ENCRYPTED))
        addCharacteristic(chr(HID_INFO, P_READ, PERMISSION_READ_ENCRYPTED))
        addCharacteristic(chr(REPORT_MAP, P_READ, PERMISSION_READ_ENCRYPTED))
        addCharacteristic(chr(CONTROL_POINT, P_WRITE_NR, PERMISSION_WRITE_ENCRYPTED))
        input = chr(REPORT, P_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY, PERMISSION_READ_ENCRYPTED).apply {
            addDescriptor(BluetoothGattDescriptor(CCCD, BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED))
            addDescriptor(BluetoothGattDescriptor(REPORT_REF, BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED))
        }
        addCharacteristic(input)
        addCharacteristic(chr(REPORT, P_READ or P_WRITE or P_WRITE_NR, PERMISSION_READ_ENCRYPTED or PERMISSION_WRITE_ENCRYPTED).apply {
            addDescriptor(BluetoothGattDescriptor(REPORT_REF, BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED))
        })
    }

    private fun deviceInfoService() = BluetoothGattService(DIS, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
        addCharacteristic(chr(MANUFACTURER, P_READ, BluetoothGattCharacteristic.PERMISSION_READ))
        addCharacteristic(chr(PNP_ID, P_READ, BluetoothGattCharacteristic.PERMISSION_READ))
    }

    private fun batteryService() = BluetoothGattService(BATTERY, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
        battery = chr(BATTERY_LEVEL, P_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ).apply {
            addDescriptor(BluetoothGattDescriptor(CCCD, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
        }
        addCharacteristic(battery)
    }

    private fun chr(uuid: UUID, props: Int, perms: Int) = BluetoothGattCharacteristic(uuid, props, perms)

    /** Valeur lue par le PC. */
    private fun readValue(c: BluetoothGattCharacteristic): ByteArray? = when (c.uuid) {
        PROTOCOL_MODE -> byteArrayOf(1)  // mode « report »
        HID_INFO -> byteArrayOf(0x11, 0x01, 0x00, 0x02)  // HID 1.11, pays 0, connectable à distance
        REPORT_MAP -> XboxReport.DESCRIPTOR
        REPORT -> if (c === input) synchronized(this) { latest } else ByteArray(8)
        MANUFACTURER -> "Microsoft".toByteArray()
        PNP_ID -> byteArrayOf(0x02, 0x5E, 0x04, 0x13, 0x0B, 0x01, 0x05)  // source USB, VID 045E, PID 0B13, v5.01
        BATTERY_LEVEL -> byteArrayOf(batteryLevel().toByte())
        else -> null
    }

    private fun batteryLevel(): Int =
        ctx.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 } ?: 100

    // ------------------------------------------------------------------ rappels Bluetooth

    private val gattCb = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            h.post {
                if (status != BluetoothGatt.GATT_SUCCESS) cb.onBleState(false, "Erreur Bluetooth (service $status).")
                else addNextService()
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            h.post { if (!stopped) onConnection(device, newState == BluetoothProfile.STATE_CONNECTED) }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, c: BluetoothGattCharacteristic) {
            val s = server ?: return
            if (!isHost(device)) { s.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null); return }
            val v = readValue(c)
            when {
                v == null -> s.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                offset > v.size -> s.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                else -> s.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, v.copyOfRange(offset, v.size))
            }
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, d: BluetoothGattDescriptor) {
            val s = server ?: return
            val v = when (d.uuid) {
                REPORT_REF -> if (d.characteristic === input) byteArrayOf(1, 1) else byteArrayOf(3, 2)  // id 1 entrée / id 3 sortie
                CCCD -> byteArrayOf(if (synchronized(this@BleGamepad) { notifying }) 1 else 0, 0)
                else -> null
            }
            if (v == null || offset > v.size || !isHost(device)) s.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
            else s.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, v.copyOfRange(offset, v.size))
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, d: BluetoothGattDescriptor,
                                              prepared: Boolean, needResponse: Boolean, offset: Int, value: ByteArray?) {
            val ok = isHost(device) && d.uuid == CCCD
            if (ok && d.characteristic === input) {
                synchronized(this@BleGamepad) { notifying = value != null && value.isNotEmpty() && (value[0].toInt() and 1) != 0; lastSent = null }
                h.post { cb.onBleState(true, "Connecté à ${hostName()}", hostName()); pump() }
            }
            if (needResponse) server?.sendResponse(device, requestId,
                if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, c: BluetoothGattCharacteristic,
                                                  prepared: Boolean, needResponse: Boolean, offset: Int, value: ByteArray?) {
            val ok = isHost(device)
            if (ok && c.uuid == REPORT && c !== input && value != null) XboxReport.rumble(value)?.let { (st, wk) -> cb.onBleRumble(st, wk) }
            if (needResponse) server?.sendResponse(device, requestId,
                if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
        }

        /** Écritures longues : rien n'est mis en attente, on se contente de répondre (sinon le PC attend indéfiniment). */
        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            synchronized(this@BleGamepad) { inFlight = false }
            h.post { pump() }
        }
    }

    private fun isHost(d: BluetoothDevice) = synchronized(this) { host?.address == d.address }

    private fun hostName(): String = runCatching { synchronized(this) { host }?.name }.getOrNull()?.take(40) ?: "PC"

    private fun onConnection(device: BluetoothDevice, connected: Boolean) {
        if (connected) {
            val accepted = synchronized(this) {
                if (host != null && host?.address != device.address) false
                else {
                    host = device
                    // PC déjà appairé : il a gardé l'abonnement aux notifications de la fois précédente.
                    notifying = device.bondState == BluetoothDevice.BOND_BONDED
                    inFlight = false
                    lastSent = null
                    true
                }
            }
            if (!accepted) { server?.cancelConnection(device); return }  // un seul PC à la fois
            stopAdvertising()
            // Un appareil non appairé ne doit pas monopoliser la manette : sans appairage accepté, il est éjecté.
            if (device.bondState != BluetoothDevice.BOND_BONDED) h.postDelayed({
                val squatter = synchronized(this) { host?.address == device.address } && device.bondState != BluetoothDevice.BOND_BONDED
                if (squatter && !stopped) server?.cancelConnection(device)
            }, PAIRING_TIMEOUT_MS)
            cb.onBleState(notifying(), if (notifying()) "Connecté à ${hostName()}"
                else "Appairage avec ${hostName()}… valide le code", hostName())
            pump()
        } else {
            val wasHost = synchronized(this) {
                if (host?.address != device.address) false else { host = null; notifying = false; true }
            }
            if (wasHost) {
                cb.onBleRumble(0, 0)
                startAdvertising()
            }
        }
    }

    private fun notifying() = synchronized(this) { notifying }

    // ------------------------------------------------------------------ annonce Bluetooth

    private val advCb = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            h.post {
                if (synchronized(this@BleGamepad) { host } == null) cb.onBleState(false,
                    "En attente du PC")
            }
        }

        override fun onStartFailure(errorCode: Int) {
            h.post {
                advertising = false
                if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE && withName) { withName = false; startAdvertising() }
                else if (errorCode != ADVERTISE_FAILED_ALREADY_STARTED) cb.onBleState(false, "Annonce Bluetooth impossible (erreur $errorCode).")
            }
        }
    }
    private var withName = true

    private fun adapterName() = runCatching { manager?.adapter?.name }.getOrNull() ?: "ton téléphone"

    private fun startAdvertising() {
        if (stopped || advertising) return
        val adv = manager?.adapter?.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder().addServiceUuid(ParcelUuid(HID)).setIncludeDeviceName(false).build()
        val scan = AdvertiseData.Builder().setIncludeDeviceName(withName).build()
        advertising = true
        runCatching { adv.startAdvertising(settings, data, scan, advCb) }.onFailure {
            advertising = false
            cb.onBleState(false, "Annonce Bluetooth impossible : ${it.message}")
        }
    }

    private fun stopAdvertising() {
        if (!advertising) return
        advertising = false
        runCatching { manager?.adapter?.bluetoothLeAdvertiser?.stopAdvertising(advCb) }
    }

    companion object {
        private fun u16(v: Int): UUID = UUID.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(v))
        private val HID = u16(0x1812)
        private val DIS = u16(0x180A)
        private val BATTERY = u16(0x180F)
        private val PROTOCOL_MODE = u16(0x2A4E)
        private val HID_INFO = u16(0x2A4A)
        private val REPORT_MAP = u16(0x2A4B)
        private val CONTROL_POINT = u16(0x2A4C)
        private val REPORT = u16(0x2A4D)
        private val MANUFACTURER = u16(0x2A29)
        private val PNP_ID = u16(0x2A50)
        private val BATTERY_LEVEL = u16(0x2A19)
        private val CCCD = u16(0x2902)
        private val REPORT_REF = u16(0x2908)
        private const val P_READ = BluetoothGattCharacteristic.PROPERTY_READ
        private const val P_WRITE = BluetoothGattCharacteristic.PROPERTY_WRITE
        private const val P_WRITE_NR = BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
        private const val PAIRING_TIMEOUT_MS = 45_000L
    }
}

/** Rapport HID de la manette Xbox sans fil (Bluetooth LE, PID 0B13) : 16 octets, identifiant 1. */
object XboxReport {
    val DESCRIPTOR: ByteArray = intArrayOf(
        0x05, 0x01, 0x09, 0x05, 0xA1, 0x01,
        0x85, 0x01,
        // Joystick gauche X/Y, droit Z/Rz : 0..65535
        0x09, 0x01, 0xA1, 0x00, 0x09, 0x30, 0x09, 0x31, 0x15, 0x00, 0x27, 0xFF, 0xFF, 0x00, 0x00, 0x95, 0x02, 0x75, 0x10, 0x81, 0x02, 0xC0,
        0x09, 0x01, 0xA1, 0x00, 0x09, 0x32, 0x09, 0x35, 0x15, 0x00, 0x27, 0xFF, 0xFF, 0x00, 0x00, 0x95, 0x02, 0x75, 0x10, 0x81, 0x02, 0xC0,
        // Gâchettes : frein (LT) puis accélérateur (RT), 10 bits
        0x05, 0x02, 0x09, 0xC5, 0x15, 0x00, 0x26, 0xFF, 0x03, 0x95, 0x01, 0x75, 0x0A, 0x81, 0x02,
        0x15, 0x00, 0x25, 0x00, 0x75, 0x06, 0x95, 0x01, 0x81, 0x03,
        0x05, 0x02, 0x09, 0xC4, 0x15, 0x00, 0x26, 0xFF, 0x03, 0x95, 0x01, 0x75, 0x0A, 0x81, 0x02,
        0x15, 0x00, 0x25, 0x00, 0x75, 0x06, 0x95, 0x01, 0x81, 0x03,
        // Croix : chapeau 1..8 (0 = relâchée)
        0x05, 0x01, 0x09, 0x39, 0x15, 0x01, 0x25, 0x08, 0x35, 0x00, 0x46, 0x3B, 0x01, 0x66, 0x14, 0x00, 0x75, 0x04, 0x95, 0x01, 0x81, 0x42,
        0x75, 0x04, 0x95, 0x01, 0x15, 0x00, 0x25, 0x00, 0x35, 0x00, 0x45, 0x00, 0x65, 0x00, 0x81, 0x03,
        // 15 boutons
        0x05, 0x09, 0x19, 0x01, 0x29, 0x0F, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x0F, 0x81, 0x02,
        0x15, 0x00, 0x25, 0x00, 0x75, 0x01, 0x95, 0x01, 0x81, 0x03,
        // Bouton Partager
        0x05, 0x0C, 0x0A, 0xB2, 0x00, 0x15, 0x00, 0x25, 0x01, 0x95, 0x01, 0x75, 0x01, 0x81, 0x02,
        0x15, 0x00, 0x25, 0x00, 0x75, 0x07, 0x95, 0x01, 0x81, 0x03,
        // Sortie id 3 : vibrations
        0x05, 0x0F, 0x09, 0x21, 0x85, 0x03, 0xA1, 0x02,
        0x09, 0x97, 0x15, 0x00, 0x25, 0x01, 0x75, 0x04, 0x95, 0x01, 0x91, 0x02,
        0x15, 0x00, 0x25, 0x00, 0x75, 0x04, 0x95, 0x01, 0x91, 0x03,
        0x09, 0x70, 0x15, 0x00, 0x25, 0x64, 0x75, 0x08, 0x95, 0x04, 0x91, 0x02,
        0x09, 0x50, 0x66, 0x01, 0x10, 0x55, 0x0E, 0x15, 0x00, 0x26, 0xFF, 0x00, 0x75, 0x08, 0x95, 0x01, 0x91, 0x02,
        0x09, 0xA7, 0x15, 0x00, 0x26, 0xFF, 0x00, 0x75, 0x08, 0x95, 0x01, 0x91, 0x02,
        0x65, 0x00, 0x55, 0x00,
        0x09, 0x7C, 0x15, 0x00, 0x26, 0xFF, 0x00, 0x75, 0x08, 0x95, 0x01, 0x91, 0x02,
        0xC0,
        0xC0,
    ).let { a -> ByteArray(a.size) { a[it].toByte() } }

    /** Bits XInput -> numéro de bouton HID (0 = bouton 1). */
    private val BUTTONS = intArrayOf(
        0x1000, 0,   // A
        0x2000, 1,   // B
        0x4000, 3,   // X
        0x8000, 4,   // Y
        0x0100, 6,   // LB
        0x0200, 7,   // RB
        0x0020, 10,  // Back / View
        0x0010, 11,  // Start / Menu
        0x0400, 12,  // Xbox
        0x0040, 13,  // L3
        0x0080, 14,  // R3
    )

    fun encode(s: InputState): ByteArray {
        val b = ByteArray(16)
        fun u16(i: Int, v: Int) { b[i] = v.toByte(); b[i + 1] = (v shr 8).toByte() }
        fun axis(v: Int) = (v + 32768).coerceIn(0, 65535)
        u16(0, axis(s.lx)); u16(2, axis(-s.ly - 1))  // HID : Y vers le bas
        u16(4, axis(s.rx)); u16(6, axis(-s.ry - 1))
        u16(8, s.lt.coerceIn(0, 255) * 1023 / 255); u16(10, s.rt.coerceIn(0, 255) * 1023 / 255)
        val up = s.buttons and 0x0001 != 0; val down = s.buttons and 0x0002 != 0
        val left = s.buttons and 0x0004 != 0; val right = s.buttons and 0x0008 != 0
        b[12] = when {
            up && right -> 2; up && left -> 8; down && right -> 4; down && left -> 6
            up -> 1; right -> 3; down -> 5; left -> 7; else -> 0
        }.toByte()
        var btn = 0
        for (i in BUTTONS.indices step 2) if (s.buttons and BUTTONS[i] != 0) btn = btn or (1 shl BUTTONS[i + 1])
        u16(13, btn)
        b[15] = if (s.buttons and 0x0800 != 0) 1 else 0  // pavé tactile -> bouton Partager
        return b
    }

    /** Rapport de sortie : activation, gâchette G, gâchette D, moteur fort, moteur faible (0..100), durée… */
    fun rumble(v: ByteArray): Pair<Int, Int>? {
        if (v.size < 5) return null
        val en = v[0].toInt()
        val strong = if (en and 0x02 != 0) (v[3].toInt() and 0xFF).coerceAtMost(100) else 0
        val weak = if (en and 0x01 != 0) (v[4].toInt() and 0xFF).coerceAtMost(100) else 0
        return strong * 255 / 100 to weak * 255 / 100
    }
}
