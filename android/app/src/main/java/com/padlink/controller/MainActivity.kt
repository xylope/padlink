package com.padlink.controller

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.padlink.controller.data.Presets
import com.padlink.controller.data.Profile
import com.padlink.controller.data.Profiles
import com.padlink.controller.data.Style
import com.padlink.controller.net.Link
import com.padlink.controller.ui.B_DANGER
import com.padlink.controller.ui.B_GHOST
import com.padlink.controller.ui.B_PRIMARY
import com.padlink.controller.ui.C
import com.padlink.controller.ui.Font
import com.padlink.controller.ui.SheetItem
import com.padlink.controller.ui.WRAP
import com.padlink.controller.ui.add
import com.padlink.controller.ui.badge
import com.padlink.controller.ui.btn
import com.padlink.controller.ui.card
import com.padlink.controller.ui.dialog
import com.padlink.controller.ui.dot
import com.padlink.controller.ui.dp
import com.padlink.controller.ui.dpi
import com.padlink.controller.ui.fitSystemBars
import com.padlink.controller.ui.gradient
import com.padlink.controller.ui.hbox
import com.padlink.controller.ui.label
import com.padlink.controller.ui.overline
import com.padlink.controller.ui.ripple
import com.padlink.controller.ui.rounded
import com.padlink.controller.ui.segmented
import com.padlink.controller.ui.setDotColor
import com.padlink.controller.ui.sheet
import com.padlink.controller.ui.styleBadge
import com.padlink.controller.ui.toast
import com.padlink.controller.ui.vbox

class MainActivity : Activity(), Link.Listener {

    private lateinit var statusDot: View
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var stopBtn: TextView
    private lateinit var tabsBox: LinearLayout
    private lateinit var profileBox: LinearLayout
    private lateinit var stepsBox: LinearLayout
    /** Onglet de profils affiché : 0 = par défaut, 1 = mes profils. */
    private var tab = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = vbox().apply { setPadding(dpi(20), dpi(12), dpi(20), dpi(28)) }

        // ---- En-tête
        col.add(hbox().apply {
            addView(android.widget.ImageView(context).apply {
                setImageResource(R.drawable.ic_launcher_foreground)
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                background = gradient(dp(14))
                clipToOutline = true
            }, LinearLayout.LayoutParams(dpi(46), dpi(46)))
            add(vbox().apply {
                add(label("PadLink", 24f, bold = true))
                add(label("Manette Bluetooth pour PC", 13.5f, C.MUTED))
            }, 0, WRAP, 1f).also { (it.layoutParams as LinearLayout.LayoutParams).marginStart = dpi(14) }
        }, top = 8)

        // ---- État de la manette
        val status = card(20)
        statusDot = dot(C.MUTED, 10)
        statusTitle = label("", 18f, bold = true)
        status.add(hbox().apply {
            addView(statusDot)
            add(statusTitle, 0, WRAP, 1f).also { (it.layoutParams as LinearLayout.LayoutParams).marginStart = dpi(10) }
        })
        statusDetail = label("", 13.5f, C.MUTED)
        status.add(statusDetail, top = 4)
        status.add(btn("Lancer la manette", B_PRIMARY) { startActivity(Intent(this, ControllerActivity::class.java)) }.apply {
            textSize = 16.5f
            setPadding(dpi(16), dpi(17), dpi(16), dpi(17))
        }, top = 18)
        stopBtn = btn("Éteindre la manette", B_DANGER) { Link.stop() }
        status.add(stopBtn, top = 10)
        col.add(status, top = 22)

        // ---- Première connexion
        val how = card(18)
        val chevron = label("▾", 16f, C.MUTED)
        how.add(hbox().apply {
            add(vbox().apply {
                add(label("Première connexion", 16f).apply { typeface = Font.medium })
                add(label("Aucun logiciel à installer sur le PC", 13f, C.MUTED), top = 2)
            }, 0, WRAP, 1f)
            addView(chevron)
            setOnClickListener {
                stepsBox.visibility = if (stepsBox.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                chevron.text = if (stepsBox.visibility == View.VISIBLE) "▴" else "▾"
            }
        })
        stepsBox = vbox()
        listOf(
            "Touche « Lancer la manette » et autorise « Appareils à proximité ».",
            "Sur le PC : Paramètres › Bluetooth et appareils › Ajouter un appareil › Bluetooth.",
            "Choisis ton téléphone, vérifie que le code est identique et accepte des deux côtés.",
            "C'est fait : le PC voit une manette Xbox. Les fois suivantes, il se reconnecte tout seul.",
        ).forEachIndexed { i, s ->
            stepsBox.add(hbox().apply {
                gravity = Gravity.TOP
                addView(badge("${i + 1}", C.ACCENT, C.ACCENT_SOFT, 26, 12.5f))
                add(label(s, 14f, C.TEXT2), 0, WRAP, 1f).also { (it.layoutParams as LinearLayout.LayoutParams).marginStart = dpi(12) }
            }, top = 14)
        }
        stepsBox.visibility = View.GONE
        how.add(stepsBox)
        col.add(how, top = 14)

        // ---- Profils
        col.add(hbox().apply {
            add(overline("Profils de manette"), 0, WRAP, 1f)
            addView(btn("Importer", B_GHOST) { importProfile() }.apply { textSize = 13.5f; setPadding(dpi(10), dpi(6), dpi(10), dpi(6)) })
        }, top = 28)
        tabsBox = vbox()
        col.add(tabsBox, top = 8)
        profileBox = vbox()
        col.add(profileBox, top = 12)
        col.add(btn("＋  Nouveau profil") { newProfile() }, top = 10)

        // ---- Pied de page
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
        col.add(label("Appairage Bluetooth chiffré · aucune connexion Internet · v$version", 12f, C.MUTED).apply {
            gravity = Gravity.CENTER
        }, top = 28)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(C.BG)
            isVerticalScrollBarEnabled = false
            addView(col)
            fitSystemBars()
        })
    }

    override fun onResume() {
        super.onResume()
        Link.addListener(this)
        renderProfiles()
    }

    override fun onPause() {
        super.onPause()
        Link.removeListener(this)
    }

    override fun onLinkState(state: Link.State, text: String) {
        val (title, color) = when {
            state == Link.State.CONNECTED -> "Connectée" to C.OK
            state == Link.State.CONNECTING -> "En attente du PC" to C.WARN
            Link.active -> "Manette allumée" to C.WARN
            else -> "Manette éteinte" to C.MUTED
        }
        statusTitle.text = title
        statusDot.setDotColor(color)
        statusDetail.text = when {
            state == Link.State.DISCONNECTED && !Link.active && text == "Manette éteinte" -> "Lance la manette pour que ton PC la détecte en Bluetooth."
            state == Link.State.CONNECTING && text == "En attente du PC" -> "Sur le PC : Ajouter un appareil › Bluetooth › « ${phoneName()} »"
            else -> text
        }
        stopBtn.visibility = if (Link.active) View.VISIBLE else View.GONE
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun phoneName(): String = runCatching {
        getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.name
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "ton téléphone"

    // ------------------------------------------------------------------ profils

    private fun renderProfiles() {
        val sel = Profiles.selected()
        if (tab < 0) tab = if (sel.builtin) 0 else 1
        val defaults = Profiles.defaults()
        val mine = Profiles.custom()
        tabsBox.removeAllViews()
        tabsBox.add(segmented(listOf("Par défaut · ${defaults.size}", "Mes profils · ${mine.size}"), tab) { tab = it; renderProfiles() })

        profileBox.removeAllViews()
        val list = if (tab == 0) defaults else mine
        if (list.isEmpty()) {
            profileBox.add(card(20).apply {
                add(label("Aucun profil personnel", 15.5f).apply { typeface = Font.medium; gravity = Gravity.CENTER })
                add(label("Crée un profil, duplique un profil par défaut, ou modifie une disposition depuis la manette puis enregistre-la.",
                    13.5f, C.MUTED).apply { gravity = Gravity.CENTER }, top = 6)
            })
            return
        }
        list.forEachIndexed { i, p -> profileBox.add(profileRow(p, p.id == sel.id), top = if (i == 0) 0 else 8) }
    }

    private fun profileRow(p: Profile, on: Boolean): View {
        val r = dp(18)
        return hbox().apply {
            setPadding(dpi(14), dpi(12), dpi(8), dpi(12))
            background = ripple(rounded(if (on) 0x1F5B7CFF else C.SURFACE, r, dpi(1), if (on) C.ACCENT else C.BORDER), r)
            isClickable = true
            setOnClickListener { Profiles.select(p.id); renderProfiles() }
            addView(styleBadge(p.style == Style.PS4))
            add(vbox().apply {
                add(label(p.name, 15.5f).apply { typeface = Font.medium; maxLines = 1 })
                add(label(Presets.all.firstOrNull { it.first == p.preset }?.second ?: "", 12.5f, C.MUTED).apply { maxLines = 1 }, top = 1)
            }, 0, WRAP, 1f).also { (it.layoutParams as LinearLayout.LayoutParams).marginStart = dpi(14) }
            if (on) addView(label("Actif", 12f, C.ACCENT).apply {
                typeface = Font.medium
                background = rounded(C.ACCENT_SOFT, dp(8))
                setPadding(dpi(8), dpi(3), dpi(8), dpi(3))
            })
            addView(label("⋯", 22f, C.TEXT2).apply {
                gravity = Gravity.CENTER
                background = ripple(rounded(0x00000000, dp(12)), dp(12))
                setOnClickListener { profileMenu(p) }
            }, LinearLayout.LayoutParams(dpi(44), dpi(44)))
        }
    }

    private fun profileMenu(p: Profile) {
        val items = mutableListOf(
            SheetItem("Utiliser ce profil", "✓") { Profiles.select(p.id); renderProfiles() },
            SheetItem("Réglages", "⚙", "Joysticks, vibrations, gyroscope, turbo") {
                Profiles.select(p.id)
                startActivity(Intent(this, SettingsActivity::class.java))
            },
            SheetItem("Dupliquer", "⧉", "Crée une copie dans Mes profils") { duplicateProfile(p) },
        )
        if (!p.builtin) items += SheetItem("Renommer", "✎") { renameProfile(p) }
        items += SheetItem("Exporter", "↗", "Partager ce profil en texte") { exportProfile(p) }
        items += if (p.builtin) SheetItem("Restaurer l'original", "↺", "Disposition et réglages d'origine", danger = true) { restoreProfile(p) }
        else SheetItem("Supprimer", "✕", danger = true) { deleteProfile(p) }
        sheet(p.name, (if (p.builtin) "Profil par défaut · " else "Mon profil · ") + p.style.title, items)
    }

    private fun newProfile() {
        if (Profiles.isFull()) { toast("Nombre maximum de profils atteint"); return }
        sheet("Nouveau profil", "Apparence de la manette sur le téléphone", Style.entries.map { style ->
            SheetItem("Manette ${style.title}", badge = styleBadge(style == Style.PS4, 34)) {
                sheet("Disposition de départ", "Tu pourras tout déplacer et redimensionner ensuite", Presets.all.map { (key, title) ->
                    SheetItem(title) {
                        val name = Profiles.uniqueName("${Presets.shortName(key)} (${style.title})")
                        Profiles.add(Presets.create(key, name, style))
                        tab = 1
                        renderProfiles()
                        toast("« $name » créé · personnalise-le depuis la manette")
                    }
                })
            }
        })
    }

    private fun duplicateProfile(p: Profile) {
        if (Profiles.isFull()) { toast("Nombre maximum de profils atteint"); return }
        val copy = Profiles.duplicate(p)
        copy.name = Profiles.uniqueName(copy.name)
        Profiles.add(copy)
        tab = 1
        renderProfiles()
    }

    private fun renameProfile(p: Profile) {
        val input = EditText(this).apply {
            setText(p.name)
            setSelection(text.length)
            filters = arrayOf(InputFilter.LengthFilter(30))
            setSingleLine()
        }
        dialog().setTitle("Renommer le profil").setView(input)
            .setPositiveButton("Enregistrer") { _, _ ->
                val n = Profile.cleanName(input.text.toString())
                when {
                    n.isEmpty() -> toast("Le nom ne peut pas être vide")
                    Profiles.nameTaken(n, p.id) -> toast("Un profil porte déjà ce nom")
                    else -> { p.name = n; Profiles.save(); renderProfiles() }
                }
            }.setNegativeButton("Annuler", null).show()
    }

    private fun restoreProfile(p: Profile) {
        dialog().setTitle("Restaurer « ${p.name} » ?")
            .setMessage("La disposition et les réglages d'origine de ce profil par défaut seront remis.")
            .setPositiveButton("Restaurer") { _, _ -> Profiles.restore(p); renderProfiles(); toast("Profil restauré") }
            .setNegativeButton("Annuler", null).show()
    }

    private fun deleteProfile(p: Profile) {
        dialog().setTitle("Supprimer « ${p.name} » ?")
            .setMessage("Cette action est définitive.")
            .setPositiveButton("Supprimer") { _, _ -> Profiles.delete(p); renderProfiles() }
            .setNegativeButton("Annuler", null).show()
    }

    private fun exportProfile(p: Profile) {
        val text = Profiles.export(p)
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Profil PadLink", text))
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Partager le profil"))
    }

    private fun importProfile() {
        val input = EditText(this).apply {
            hint = "Colle ici le texte du profil"
            minLines = 4
            maxLines = 8
        }
        dialog().setTitle("Importer un profil").setView(input)
            .setPositiveButton("Importer") { _, _ ->
                try {
                    val p = Profiles.import(input.text.toString())
                    toast("Profil « ${p.name} » importé")
                    tab = 1
                    renderProfiles()
                } catch (e: Exception) {
                    toast("Profil invalide : ${e.message}")
                }
            }.setNegativeButton("Annuler", null).show()
    }
}
