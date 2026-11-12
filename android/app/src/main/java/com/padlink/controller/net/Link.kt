package com.padlink.controller.net

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicReference

/** Manette Bluetooth active, partagée par tous les écrans. */
object Link {
    enum class State { DISCONNECTED, CONNECTING, CONNECTED }

    interface Listener {
        fun onLinkState(state: State, text: String) {}
        fun onRumble(large: Int, small: Int) {}
    }

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val input = AtomicReference(InputState.NEUTRAL)

    @Volatile var state = State.DISCONNECTED
        private set
    @Volatile var text = "Manette éteinte"
        private set
    /** Nom du PC connecté (null si aucun). */
    @Volatile var hostName: String? = null
        private set
    @Volatile var ble: BleGamepad? = null
        private set

    /** La manette Bluetooth est allumée (visible ou connectée). */
    val active get() = ble != null

    fun addListener(l: Listener) { listeners.add(l); l.onLinkState(state, text) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    fun publish(s: InputState) {
        if (input.getAndSet(s) != s) ble?.update(s)
    }

    /** Allume la manette Bluetooth (permissions déjà accordées). Renvoie un message d'erreur, ou null. */
    fun start(ctx: Context): String? {
        if (ble != null) return null
        lateinit var b: BleGamepad
        b = BleGamepad(ctx.applicationContext, object : BleGamepad.Callback {
            override fun onBleState(connected: Boolean, text: String, host: String?) {
                if (ble !== b) return
                hostName = host
                setState(if (connected) State.CONNECTED else State.CONNECTING, text)
            }
            override fun onBleRumble(strong: Int, weak: Int) {
                if (ble === b) main.post { listeners.forEach { it.onRumble(strong, weak) } }
            }
        })
        ble = b
        val err = b.start()
        if (err != null) {
            ble = null
            b.stop()
            setState(State.DISCONNECTED, err)
        }
        return err
    }

    /** Éteint la manette : le PC la voit se déconnecter, le téléphone n'est plus visible. */
    fun stop() {
        val b = ble ?: return
        ble = null
        hostName = null
        b.stop()
        setState(State.DISCONNECTED, "Manette éteinte")
    }

    private fun setState(st: State, txt: String) {
        state = st
        text = txt
        main.post { listeners.forEach { it.onLinkState(st, txt) } }
    }
}
