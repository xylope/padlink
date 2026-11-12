package com.padlink.controller

import android.app.Activity
import android.content.res.ColorStateList
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import com.padlink.controller.data.Profile
import com.padlink.controller.data.Profiles
import com.padlink.controller.data.Style
import com.padlink.controller.ui.C
import com.padlink.controller.ui.Font
import com.padlink.controller.ui.WRAP
import com.padlink.controller.ui.add
import com.padlink.controller.ui.card
import com.padlink.controller.ui.divider
import com.padlink.controller.ui.dp
import com.padlink.controller.ui.dpi
import com.padlink.controller.ui.fitSystemBars
import com.padlink.controller.ui.hbox
import com.padlink.controller.ui.label
import com.padlink.controller.ui.overline
import com.padlink.controller.ui.ripple
import com.padlink.controller.ui.rounded
import com.padlink.controller.ui.segmented
import com.padlink.controller.ui.styleBadge
import com.padlink.controller.ui.vbox
import kotlin.math.roundToInt

class SettingsActivity : Activity() {

    private lateinit var profile: Profile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profile = Profiles.selected()
        val s = profile.settings
        val col = vbox().apply { setPadding(dpi(20), dpi(8), dpi(20), dpi(28)) }

        // ---- Barre de titre
        col.add(hbox().apply {
            addView(label("←", 22f).apply {
                gravity = Gravity.CENTER
                background = ripple(rounded(C.SURFACE2, dp(14), dpi(1), C.BORDER), dp(14))
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dpi(44), dpi(44)))
            add(vbox().apply {
                add(label("Réglages", 22f, bold = true))
                add(label(profile.name, 13f, C.MUTED).apply { maxLines = 1 })
            }, 0, WRAP, 1f).also { (it.layoutParams as LinearLayout.LayoutParams).marginStart = dpi(14) }
            addView(styleBadge(profile.style == Style.PS4, 36))
        }, top = 8)

        col.section("Joysticks") {
            slider("Zone morte", 0, 30, (s.deadzone * 100).roundToInt(), { "$it %" }) { s.deadzone = it / 100f }
            slider("Courbe de réponse", 50, 250, (s.curve * 100).roundToInt(), { "×%.2f".format(it / 100f) }) { s.curve = it / 100f }
            note("Moins de 1 : plus réactif au centre · plus de 1 : plus précis au centre")
            toggle("Joysticks flottants", "Le centre se place sous ton doigt", s.floating) { s.floating = it }
        }

        col.section("Affichage") {
            slider("Opacité des contrôles", 20, 100, (s.opacity * 100).roundToInt(), { "$it %" }) { s.opacity = it / 100f }
            toggle("Libellés des boutons", "", s.showLabels) { s.showLabels = it }
        }

        col.section("Vibrations") {
            toggle("Retour haptique", "Petite vibration à chaque appui", s.haptics) { s.haptics = it }
            toggle("Vibrations du jeu", "Le PC fait vibrer le téléphone", s.rumble) { s.rumble = it }
        }

        col.section("Turbo") {
            slider("Vitesse du turbo", 4, 20, s.turboHz, { "$it / s" }) { s.turboHz = it }
            note("Active le turbo sur un bouton depuis « Modifier la disposition ».")
        }

        col.section("Gyroscope") {
            val sm = getSystemService(SensorManager::class.java)
            if (sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) == null) note("Aucun gyroscope détecté sur ce téléphone.")
            val modes = listOf("Désactivé", "Visée", "Volant")
            val desc = label("", 13f, C.MUTED)
            fun describe(m: Int) {
                desc.text = when (m) {
                    1 -> "Bouger le téléphone déplace le joystick droit (viser dans les FPS)."
                    2 -> "Incliner le téléphone comme un volant dirige le joystick gauche."
                    else -> "Le téléphone ne réagit pas aux mouvements."
                }
            }
            val holder = vbox()
            fun render() {
                holder.removeAllViews()
                holder.add(segmented(modes, s.gyroMode) { s.gyroMode = it.coerceIn(0, 2); describe(it); render() })
            }
            render()
            describe(s.gyroMode)
            add(holder, top = 12)
            add(desc, top = 8)
            slider("Sensibilité", 20, 400, (s.gyroSens * 100).roundToInt(), { "×%.2f".format(it / 100f) }) { s.gyroSens = it / 100f }
            toggle("Inverser l'axe vertical", "Pour la visée", s.gyroInvertY) { s.gyroInvertY = it }
        }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(C.BG)
            isVerticalScrollBarEnabled = false
            addView(col)
            fitSystemBars()
        })
    }

    override fun onPause() {
        super.onPause()
        Profiles.save()
    }

    private fun LinearLayout.section(title: String, build: LinearLayout.() -> Unit) {
        add(context.overline(title), top = 24)
        val c = context.card(18)
        c.build()
        add(c, top = 8)
    }

    private fun LinearLayout.note(text: String) = add(context.label(text, 12.5f, C.MUTED), top = 8)

    private fun LinearLayout.slider(title: String, min: Int, max: Int, value: Int, fmt: (Int) -> String, onChange: (Int) -> Unit) {
        if (childCount > 0) add(context.divider(), top = 14)
        val v = context.label(fmt(value), 13f, C.ACCENT).apply {
            typeface = Font.medium
            background = rounded(C.ACCENT_SOFT, context.dp(8))
            setPadding(context.dpi(8), context.dpi(3), context.dpi(8), context.dpi(3))
        }
        add(context.hbox().apply {
            add(context.label(title, 15f).apply { typeface = Font.medium }, 0, WRAP, 1f)
            addView(v)
        }, top = if (childCount > 0) 14 else 0)
        add(SeekBar(context).apply {
            this.max = max - min
            progress = value.coerceIn(min, max) - min
            progressTintList = ColorStateList.valueOf(C.ACCENT)
            thumbTintList = ColorStateList.valueOf(C.TEXT)
            progressBackgroundTintList = ColorStateList.valueOf(C.SURFACE3)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, p: Int, fromUser: Boolean) {
                    v.text = fmt(p + min)
                    onChange(p + min)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }, top = 6)
    }

    @Suppress("DEPRECATION")
    private fun LinearLayout.toggle(title: String, detail: String, value: Boolean, onChange: (Boolean) -> Unit) {
        if (childCount > 0) add(context.divider(), top = 14)
        val sw = Switch(context).apply {
            isChecked = value
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(C.TEXT, C.MUTED))
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(C.ACCENT, C.SURFACE3))
            setOnCheckedChangeListener { _, b -> onChange(b) }
        }
        add(context.hbox().apply {
            add(context.vbox().apply {
                add(context.label(title, 15f).apply { typeface = Font.medium })
                if (detail.isNotEmpty()) add(context.label(detail, 12.5f, C.MUTED), top = 2)
            }, 0, WRAP, 1f)
            addView(sw)
            setOnClickListener { sw.toggle() }
        }, top = if (childCount > 0) 14 else 0)
    }
}
