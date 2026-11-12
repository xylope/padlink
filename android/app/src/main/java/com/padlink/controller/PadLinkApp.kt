package com.padlink.controller

import android.app.Application
import com.padlink.controller.data.Profiles
import java.security.KeyStore

class PadLinkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Profiles.init(this)
        forgetWifiData()
    }

    /** Version 1.x : efface les PC associés en Wi-Fi et leur clé de chiffrement, devenus inutiles. */
    private fun forgetWifiData() {
        val legacy = getSharedPreferences("padlink_secure", MODE_PRIVATE)
        if (legacy.all.isEmpty()) return
        legacy.edit().clear().apply()
        deleteSharedPreferences("padlink_secure")
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("padlink_vault_v1") }
    }
}
