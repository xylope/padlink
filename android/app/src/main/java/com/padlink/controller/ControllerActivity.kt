package com.padlink.controller

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputFilter
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import com.padlink.controller.data.Act
import com.padlink.controller.data.Control
import com.padlink.controller.data.Kind
import com.padlink.controller.data.Presets
import com.padlink.controller.data.Profile
import com.padlink.controller.data.Profiles
import com.padlink.controller.data.Style
import com.padlink.controller.net.InputState
import com.padlink.controller.net.Link
import com.padlink.controller.ui.B_DANGER
import com.padlink.controller.ui.B_PRIMARY
import com.padlink.controller.ui.C
import com.padlink.controller.ui.Font
import com.padlink.controller.ui.SheetItem
import com.padlink.controller.ui.dot
import com.padlink.controller.ui.setDotColor
import com.padlink.controller.ui.sheet
import com.padlink.controller.ui.styleBadge
import com.padlink.controller.ui.GamepadView
import com.padlink.controller.ui.WRAP
import com.padlink.controller.ui.add
import com.padlink.controller.ui.btn
import com.padlink.controller.ui.dialog
import com.padlink.controller.ui.dp
import com.padlink.controller.ui.dpi
import com.padlink.controller.ui.hbox
import com.padlink.controller.ui.label
import com.padlink.controller.ui.rounded
import com.padlink.controller.ui.toast
import com.padlink.controller.ui.vbox
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

class ControllerActivity : Activity(), Link.Listener, SensorEventListener {

    private lateinit var pad: GamepadView
    private lateinit var chip: LinearLayout
    private lateinit var dot: View
    private lateinit var status: TextView
    private lateinit var profileBtn: TextView
    private lateinit var editBar: View
    private lateinit var editInfo: TextView
    /** Profil enregistré (utilisé pour jouer). */
    private lateinit var profile: Profile
    /** Brouillon pendant l'édition : rien n'est enregistré tant qu'on ne valide pas. */
    private var draft: Profile? = null
    private var draftOrigin = ""
    private var barAtTop = true
    private val editButtons = HashMap<String, TextView>()
    private val handler = Handler(Looper.getMainLooper())
    private var vibrator: Vibrator? = null
    private var sensors: SensorManager? = null

    private var aimX = 0f
    private var aimY = 0f
    private var steer = 0f
    private var turboPending = false
    private var rumbling = false

    private val turboTick = Runnable { turboPending = false; pushState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        vibrator = getSystemService(Vibrator::class.java)
        sensors = getSystemService(SensorManager::class.java)
        profile = Profiles.selected()

        val root = FrameLayout(this)
        pad = GamepadView(this).apply {
            setProfile(this@ControllerActivity.profile)
            onInputChanged = { pushState() }
            onPress = { tick() }
            onSelectionChanged = { updateEditBar(it) }
            onEdited = { updateEditBar(selected); autoPlaceBar(selected) }
        }
        root.addView(pad, FrameLayout.LayoutParams(-1, -1))

        // Pastille : état de la connexion · profil (changement rapide) · menu.
        dot = dot(C.MUTED, 8)
        status = label("", 12.5f, C.TEXT2).apply { maxLines = 1; maxWidth = dpi(260) }
        profileBtn = label("", 13f, C.TEXT).apply {
            typeface = Font.medium
            maxLines = 1
            maxWidth = dpi(200)
            setPadding(dpi(12), dpi(6), dpi(12), dpi(6))
            background = rounded(C.SURFACE2, dp(12))
            setOnClickListener { chooseProfile() }
        }
        val gear = label("☰", 17f, C.TEXT).apply {
            gravity = Gravity.CENTER
            background = rounded(C.SURFACE2, dp(12))
            setOnClickListener { showMenu() }
        }
        chip = hbox().apply {
            background = rounded(0xE612151C.toInt(), dp(20), dpi(1), C.BORDER)
            setPadding(dpi(14), dpi(5), dpi(5), dpi(5))
            addView(dot)
            addView(status, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dpi(8); marginEnd = dpi(10) })
            addView(profileBtn)
            addView(gear, LinearLayout.LayoutParams(dpi(34), dpi(30)).apply { marginStart = dpi(5) })
        }
        root.addView(chip, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        pad.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ -> chip.translationY = (bottom - top) * 0.17f }

        editBar = buildEditBar()
        root.addView(editBar, FrameLayout.LayoutParams(-1, WRAP, Gravity.TOP))
        editInfo = label("", 13f, C.TEXT2).apply {
            gravity = Gravity.CENTER
            background = rounded(0xE612151C.toInt(), dp(14), dpi(1), C.BORDER)
            setPadding(dpi(16), dpi(8), dpi(16), dpi(8))
        }
        root.addView(editInfo, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dpi(10)
        })
        setContentView(root)
        showEditUi(false)

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { onBack() }
        }
    }

    @Deprecated("Android < 13")
    override fun onBackPressed() = onBack()

    private fun onBack() {
        if (draft != null) leaveEditor() else showMenu()
    }

    override fun onResume() {
        super.onResume()
        // Pendant l'édition on garde le brouillon (ex. retour après une boîte de dialogue système).
        if (draft == null) usePlayProfile(Profiles.selected())
        Link.addListener(this)
        registerSensors()
        startBluetooth()
        hideSystemUi()
    }

    // ------------------------------------------------------------------ Bluetooth

    private var askedBluetooth = false

    private fun bluetoothPermissions() =
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        else emptyArray()

    /** Demande les autorisations, l'activation du Bluetooth, puis se rend visible pour le PC. */
    private fun startBluetooth() {
        if (Link.ble != null) return
        val missing = bluetoothPermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            if (!askedBluetooth) { askedBluetooth = true; requestPermissions(missing.toTypedArray(), REQ_BT) }
            else status.text = "Autorise « Appareils à proximité » pour PadLink (paramètres Android)"
            return
        }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter != null && !adapter.isEnabled) {
            if (!askedBluetooth) {
                askedBluetooth = true
                runCatching { startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
            } else status.text = "Active le Bluetooth du téléphone"
            return
        }
        Link.start(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_BT) startBluetooth()
    }

    override fun onPause() {
        super.onPause()
        Link.removeListener(this)
        sensors?.unregisterListener(this)
        handler.removeCallbacks(turboTick)
        turboPending = false
        Link.publish(InputState.NEUTRAL)
        stopRumble()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi() else pad.resetTouches()
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 30) {
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    /** Active un profil pour jouer : affichage et capteurs. */
    private fun usePlayProfile(p: Profile) {
        profile = p
        Profiles.select(p.id)
        pad.setProfile(p)
        profileBtn.text = "${p.name}  ▾"
        registerSensors()
    }

    // ------------------------------------------------------------------ état -> PC

    private fun pushState() {
        if (!::profile.isInitialized) return
        val o = pad.collect(SystemClock.uptimeMillis())
        val s = profile.settings
        var lx = o.lx
        var rx = o.rx
        var ry = o.ry
        when (s.gyroMode) {
            1 -> { rx = (rx + aimX).coerceIn(-1f, 1f); ry = (ry + aimY).coerceIn(-1f, 1f) }
            2 -> if (!o.leftActive) lx = steer
        }
        Link.publish(InputState(o.buttons, o.lt, o.rt, i16(lx), i16(o.ly), i16(rx), i16(ry)))
        if (o.turboHeld && !turboPending) {
            turboPending = true
            handler.postDelayed(turboTick, max(20L, 500L / s.turboHz))
        }
    }

    private fun i16(v: Float) = (v.coerceIn(-1f, 1f) * 32767f).roundToInt()

    override fun onLinkState(state: Link.State, text: String) {
        status.text = text
        dot.setDotColor(when (state) {
            Link.State.CONNECTED -> C.OK
            Link.State.CONNECTING -> C.WARN
            Link.State.DISCONNECTED -> if (Link.active) C.WARN else C.ERR
        })
        if (state != Link.State.CONNECTED) stopRumble()
    }

    // ------------------------------------------------------------------ vibrations

    private fun tick() {
        if (!profile.settings.haptics || rumbling) return
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        else v.vibrate(VibrationEffect.createOneShot(12, 120))
    }

    override fun onRumble(large: Int, small: Int) {
        if (!profile.settings.rumble || draft != null) return
        val v = vibrator ?: return
        val amp = max(large, small)
        if (amp < 8) { stopRumble(); return }
        val a = if (v.hasAmplitudeControl()) amp.coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
        v.vibrate(VibrationEffect.createOneShot(3000, a))
        rumbling = true
    }

    private fun stopRumble() {
        if (rumbling) vibrator?.cancel()
        rumbling = false
    }

    // ------------------------------------------------------------------ gyroscope

    private fun registerSensors() {
        val sm = sensors ?: return
        sm.unregisterListener(this)
        aimX = 0f; aimY = 0f; steer = 0f
        if (!::profile.isInitialized) return
        when (profile.settings.gyroMode) {
            1 -> {
                val g = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
                if (g == null) toast("Pas de gyroscope sur ce téléphone") else sm.registerListener(this, g, SensorManager.SENSOR_DELAY_GAME)
            }
            2 -> {
                val g = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                if (g == null) toast("Pas d'accéléromètre sur ce téléphone") else sm.registerListener(this, g, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun rotation(): Int = if (Build.VERSION.SDK_INT >= 30) display?.rotation ?: Surface.ROTATION_90 else windowManager.defaultDisplay.rotation

    override fun onSensorChanged(e: SensorEvent) {
        if (draft != null) return
        val s = profile.settings
        val v = e.values
        // Axes de l'écran (paysage) : droite / haut, à partir des axes physiques du téléphone.
        val (right, up) = when (rotation()) {
            Surface.ROTATION_90 -> -v[1] to v[0]
            Surface.ROTATION_270 -> v[1] to -v[0]
            Surface.ROTATION_180 -> -v[0] to -v[1]
            else -> v[0] to v[1]
        }
        when (e.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                // Lacet (+ roulis) -> horizontal ; tangage -> vertical. Vitesse angulaire -> déflexion du stick.
                val yaw = deadband(-(up + v[2]), 0.03f)
                val pitch = deadband(right, 0.03f)
                val k = 0.35f * s.gyroSens
                aimX = aimX * 0.3f + (yaw * k).coerceIn(-1f, 1f) * 0.7f
                aimY = aimY * 0.3f + (pitch * k * if (s.gyroInvertY) -1f else 1f).coerceIn(-1f, 1f) * 0.7f
            }
            else -> {
                // Volant : angle d'inclinaison dans le plan de l'écran. 45° / sensibilité = braquage complet.
                val angle = atan2(-right, up)
                val maxAngle = (PI / 4).toFloat() / s.gyroSens
                val a = deadband(angle, 0.035f)
                steer = steer * 0.4f + (a / maxAngle).coerceIn(-1f, 1f) * 0.6f
            }
        }
        pushState()
    }

    private fun deadband(v: Float, d: Float) = if (abs(v) < d) 0f else v - d * sign(v)

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ------------------------------------------------------------------ menu & profils

    private fun showMenu() {
        pad.resetTouches()
        val on = Link.active
        sheet("PadLink", Link.text, listOf(
            SheetItem("Reprendre", "▶"),
            SheetItem("Modifier la disposition", "✥", "Déplacer, redimensionner, changer les touches") { startEditor() },
            SheetItem("Changer de profil", "◫", profile.name) { chooseProfile() },
            SheetItem("Nouveau profil", "＋") { newProfile() },
            SheetItem("Réglages du profil", "⚙", "Joysticks, vibrations, gyroscope, turbo") {
                startActivity(Intent(this, SettingsActivity::class.java))
            },
            if (on) SheetItem("Éteindre la manette", "⏻", "Le PC la voit se déconnecter") { Link.stop() }
            else SheetItem("Allumer la manette", "⏻", "Visible en Bluetooth pour le PC") { askedBluetooth = false; startBluetooth() },
            SheetItem("Quitter", "✕", "Retour à l'accueil (la manette reste allumée)", danger = true) { finish() },
        ))
    }

    private fun chooseProfile() {
        if (draft != null) { toast("Enregistre ou annule la disposition en cours d'abord"); return }
        pad.resetTouches()
        fun row(p: Profile) = SheetItem(p.name, detail = Presets.all.firstOrNull { it.first == p.preset }?.second ?: "",
            checked = p.id == profile.id, badge = styleBadge(p.style == Style.PS4, 34)) { usePlayProfile(p) }
        val items = mutableListOf<SheetItem>()
        // Profils personnels en premier : en paysage, la liste dépasse vite l'écran.
        val mine = Profiles.custom()
        if (mine.isNotEmpty()) {
            items += SheetItem("Mes profils", header = true)
            mine.forEach { items += row(it) }
        }
        items += SheetItem("Par défaut", header = true)
        Profiles.defaults().forEach { items += row(it) }
        items += SheetItem("Nouveau profil", "＋") { newProfile() }
        sheet("Profil", "Disposition utilisée pour jouer", items)
    }

    private fun newProfile() {
        if (Profiles.isFull()) { toast("Nombre maximum de profils atteint"); return }
        sheet("Nouveau profil", "Apparence de la manette", Style.entries.map { style ->
            SheetItem("Manette ${style.title}", badge = styleBadge(style == Style.PS4, 34)) {
                sheet("Disposition de départ", "Tu pourras tout déplacer ensuite", Presets.all.map { (key, title) ->
                    SheetItem(title) {
                        askName("Nom du nouveau profil", uniqueName("${Presets.shortName(key)} (${style.title})"), null) { name ->
                            val p = Presets.create(key, name, style)
                            Profiles.add(p)
                            usePlayProfile(p)
                            dialog().setTitle("Profil « $name » créé")
                                .setMessage("Veux-tu personnaliser la disposition maintenant ?")
                                .setPositiveButton("Personnaliser") { _, _ -> startEditor() }
                                .setNegativeButton("Plus tard", null).show()
                        }
                    }
                })
            }
        })
    }

    private fun uniqueName(base: String) = Profiles.uniqueName(base)

    /** Boîte de saisie d'un nom de profil, validé (non vide, unique). */
    private fun askName(title: String, initial: String, exceptId: String?, onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(initial)
            setSelection(text.length)
            filters = arrayOf(InputFilter.LengthFilter(30))
            setSingleLine()
        }
        dialog().setTitle(title).setView(input)
            .setPositiveButton("OK") { _, _ ->
                val name = Profile.cleanName(input.text.toString())
                when {
                    name.isEmpty() -> { toast("Le nom ne peut pas être vide"); askName(title, initial, exceptId, onOk) }
                    Profiles.nameTaken(name, exceptId) -> { toast("Un profil porte déjà ce nom"); askName(title, name, exceptId, onOk) }
                    else -> onOk(name)
                }
            }.setNegativeButton("Annuler", null).show()
    }

    // ------------------------------------------------------------------ éditeur

    private fun buildEditBar(): View {
        val row = hbox().apply { setPadding(dpi(10), dpi(8), dpi(10), dpi(8)) }
        fun add(key: String, text: String, style: Int = 0, f: () -> Unit) {
            val b = btn(text, style, f).apply { textSize = 13f; setPadding(dpi(14), dpi(9), dpi(14), dpi(9)) }
            editButtons[key] = b
            row.addView(b, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dpi(6) })
        }
        add("save", "Enregistrer", B_PRIMARY) { saveDraft() }
        add("cancel", "Annuler", B_DANGER) { leaveEditor() }
        add("move", "⇅") { placeEditBar(!barAtTop) }
        add("style", "Style") { switchStyle() }
        add("minus", "− Taille") { sel { it.size = (it.size * 0.9f).coerceAtLeast(Control.MIN_SIZE) } }
        add("plus", "+ Taille") { sel { it.size = (it.size * 1.1f).coerceAtMost(Control.MAX_SIZE) } }
        add("allminus", "Tout −") { scaleAll(0.92f) }
        add("allplus", "Tout +") { scaleAll(1.08f) }
        add("act", "Fonction") { chooseAct() }
        add("label", "Nom") { renameControl() }
        add("turbo", "Turbo") { sel { it.turbo = !it.turbo } }
        add("toggle", "Maintien") { sel { it.toggle = !it.toggle } }
        add("hide", "Masquer") { sel { it.visible = false }; pad.select(null) }
        add("delete", "Supprimer", B_DANGER) { deleteSelected() }
        add("add", "+ Bouton") { addButton() }
        add("show", "Afficher / masquer…") { chooseVisible() }
        add("reset", "Réinitialiser", B_DANGER) { resetLayout() }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(0xF20A0C11.toInt())
            addView(row)
        }
    }

    private fun startEditor() {
        val d = profile.copy()
        draft = d
        draftOrigin = d.toJson().toString()
        Link.publish(InputState.NEUTRAL)
        stopRumble()
        pad.setProfile(d)
        pad.editMode = true
        showEditUi(true)
    }

    private fun hasChanges() = draft?.let { it.toJson().toString() != draftOrigin } ?: false

    private fun leaveEditor() {
        if (!hasChanges()) { closeEditor(); return }
        dialog().setTitle("Modifications non enregistrées")
            .setMessage("Que veux-tu faire de la disposition modifiée ?")
            .setPositiveButton("Enregistrer…") { _, _ -> saveDraft() }
            .setNegativeButton("Abandonner") { _, _ -> closeEditor() }
            .setNeutralButton("Continuer", null).show()
    }

    private fun closeEditor() {
        draft = null
        pad.editMode = false
        showEditUi(false)
        usePlayProfile(Profiles.all().firstOrNull { it.id == profile.id } ?: Profiles.selected())
    }

    /** Enregistre le brouillon : remplace le profil actuel ou crée un nouveau profil, avec un nom. */
    private fun saveDraft() {
        val d = draft ?: return
        val builtin = profile.builtin
        val input = EditText(this).apply {
            setText(if (builtin) uniqueName("${d.name} perso") else d.name)
            setSelection(text.length)
            filters = arrayOf(InputFilter.LengthFilter(30))
            setSingleLine()
        }
        val b = dialog().setTitle("Enregistrer la disposition")
            .setMessage(if (builtin) "« ${profile.name} » est un profil par défaut : ta disposition sera enregistrée dans « Mes profils ». Donne-lui un nom. Une fois enregistrée, elle est figée pour jouer."
                else "Donne un nom à cette configuration. Une fois enregistrée, elle est figée pour jouer.")
            .setView(input)
        if (!builtin) b.setPositiveButton("Remplacer « ${profile.name} »") { _, _ ->
                val name = Profile.cleanName(input.text.toString())
                when {
                    name.isEmpty() -> { toast("Le nom ne peut pas être vide"); saveDraft() }
                    Profiles.nameTaken(name, profile.id) -> { toast("Un autre profil porte déjà ce nom"); saveDraft() }
                    else -> commit(d.apply { this.name = name; id = profile.id })
                }
            }
        b.setNeutralButton(if (builtin) "Enregistrer dans Mes profils" else "Nouveau profil") { _, _ ->
                val name = Profile.cleanName(input.text.toString())
                when {
                    Profiles.isFull() -> toast("Nombre maximum de profils atteint")
                    name.isEmpty() -> { toast("Le nom ne peut pas être vide"); saveDraft() }
                    Profiles.nameTaken(name) -> { toast("Un profil porte déjà ce nom : choisis-en un autre"); saveDraft() }
                    else -> commit(d.apply { this.name = name; id = Profiles.newId() }, asNew = true)
                }
            }
            .setNegativeButton("Annuler", null).show()
    }

    private fun commit(p: Profile, asNew: Boolean = false) {
        if (asNew) Profiles.add(p) else Profiles.replace(p)
        draft = null
        pad.editMode = false
        showEditUi(false)
        usePlayProfile(p)
        toast("« ${p.name} » enregistré ✔")
    }

    /** Barre d'outils en haut ou en bas, pour ne jamais cacher les éléments qu'on édite. */
    private fun placeEditBar(top: Boolean) {
        barAtTop = top
        (editBar.layoutParams as FrameLayout.LayoutParams).gravity = if (top) Gravity.TOP else Gravity.BOTTOM
        (editInfo.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = (if (top) Gravity.BOTTOM else Gravity.TOP) or Gravity.CENTER_HORIZONTAL
            topMargin = if (top) 0 else dpi(10)
            bottomMargin = if (top) dpi(10) else 0
        }
        editBar.requestLayout()
        editInfo.requestLayout()
    }

    private fun autoPlaceBar(c: Control?) {
        if (c == null) return
        if (c.y < 0.25f && barAtTop) placeEditBar(false)
        else if (c.y > 0.75f && !barAtTop) placeEditBar(true)
    }

    private fun showEditUi(on: Boolean) {
        editBar.visibility = if (on) View.VISIBLE else View.GONE
        editInfo.visibility = if (on) View.VISIBLE else View.GONE
        chip.visibility = if (on) View.GONE else View.VISIBLE
        updateEditBar(null)
    }

    private fun updateEditBar(c: Control?) {
        if (!::editInfo.isInitialized) return
        val d = draft
        val style = d?.style ?: profile.style
        val isButton = c?.kind == Kind.BUTTON
        fun enable(key: String, on: Boolean) = editButtons[key]?.let { it.isEnabled = on; it.alpha = if (on) 1f else 0.35f }
        listOf("minus", "plus", "hide", "delete").forEach { enable(it, c != null) }
        autoPlaceBar(c)
        listOf("act", "label", "turbo", "toggle").forEach { enable(it, isButton) }
        editButtons["style"]?.text = "Style : ${style.title}"
        editButtons["turbo"]?.text = if (c?.turbo == true) "Turbo : oui" else "Turbo"
        editButtons["toggle"]?.text = if (c?.toggle == true) "Maintien : oui" else "Maintien"
        editInfo.text = if (c == null) "Touche un élément pour le sélectionner · glisse pour déplacer · pince à 2 doigts pour la taille"
        else buildString {
            append(c.displayName(style))
            if (isButton) append(" → ").append(c.act.title(style))
            if (c.turbo) append(" · turbo")
            if (c.toggle) append(" · maintien")
        }
    }

    private fun sel(f: (Control) -> Unit) {
        val c = pad.selected ?: return
        f(c)
        pad.invalidate()
        updateEditBar(pad.selected)
    }

    private fun scaleAll(k: Float) {
        val d = draft ?: return
        d.controls.forEach { it.size = (it.size * k).coerceIn(Control.MIN_SIZE, Control.MAX_SIZE) }
        pad.invalidate()
    }

    private fun switchStyle() {
        val d = draft ?: return
        val next = if (d.style == Style.XBOX) Style.PS4 else Style.XBOX
        dialog().setTitle("Passer en manette ${next.title}")
            .setMessage("Les boutons s'afficheront en style ${next.title} (le PC voit toujours une manette Xbox). Garder ta disposition actuelle ou prendre la disposition ${next.title} d'origine ?")
            .setPositiveButton("Garder ma disposition") { _, _ ->
                d.style = next
                if (next == Style.PS4 && d.controls.none { it.act == Act.TOUCHPAD && it.kind == Kind.BUTTON }) {
                    d.controls.add(Control("touchpad", Kind.BUTTON, Act.TOUCHPAD, 0.5f, 0.3f, 0.11f, visible = false))
                }
                pad.select(null)
            }
            .setNeutralButton("Disposition ${next.title}") { _, _ ->
                d.style = next
                d.controls.clear()
                d.controls.addAll(Presets.layout(d.preset, next))
                pad.select(null)
            }
            .setNegativeButton("Annuler", null).show()
    }

    private fun chooseAct() {
        val c = pad.selected ?: return
        val style = draft?.style ?: return
        val acts = Act.entries
        dialog().setTitle("Ce bouton envoie…")
            .setSingleChoiceItems(acts.map { it.title(style) }.toTypedArray(), acts.indexOf(c.act)) { d, i ->
                sel { it.act = acts[i]; it.label = "" }
                d.dismiss()
            }.show()
    }

    private fun renameControl() {
        val c = pad.selected ?: return
        val style = draft?.style ?: return
        val input = EditText(this).apply {
            setText(c.label)
            hint = c.act.label(style)
            filters = arrayOf(InputFilter.LengthFilter(6))
            setSingleLine()
        }
        dialog().setTitle("Texte affiché (6 caractères max)").setView(input)
            .setPositiveButton("OK") { _, _ -> sel { it.label = input.text.toString().filter { ch -> !ch.isISOControl() }.trim() } }
            .setNegativeButton("Annuler", null).show()
    }

    private fun deleteSelected() {
        val d = draft ?: return
        val c = pad.selected ?: return
        if (c.isExtra) d.controls.remove(c) else c.visible = false
        pad.select(null)
        if (!c.isExtra) toast("Élément d'origine masqué (réaffichable via « Afficher / masquer »)")
    }

    private fun addButton() {
        val d = draft ?: return
        if (d.controls.size >= Profile.MAX_CONTROLS) { toast("Nombre maximum d'éléments atteint"); return }
        val c = Control("extra_" + Profiles.newId(), Kind.BUTTON, Act.A, 0.5f, 0.5f, 0.14f)
        d.controls.add(c)
        pad.select(c)
        chooseAct()
    }

    private fun chooseVisible() {
        val d = draft ?: return
        val list = d.controls
        val checked = BooleanArray(list.size) { list[it].visible }
        dialog().setTitle("Éléments affichés")
            .setMultiChoiceItems(list.map { it.displayName(d.style) }.toTypedArray(), checked) { _, i, b -> checked[i] = b }
            .setPositiveButton("OK") { _, _ ->
                list.forEachIndexed { i, c -> c.visible = checked[i] }
                pad.select(null)
            }.setNegativeButton("Annuler", null).show()
    }

    private fun resetLayout() {
        val d = draft ?: return
        dialog().setTitle("Réinitialiser la disposition ?")
            .setMessage("Les positions, tailles et fonctions reviennent au modèle d'origine (${d.style.title}). Rien n'est enregistré tant que tu ne valides pas.")
            .setPositiveButton("Réinitialiser") { _, _ ->
                d.controls.clear()
                d.controls.addAll(Presets.layout(d.preset, d.style))
                pad.select(null)
            }.setNegativeButton("Annuler", null).show()
    }

    private companion object {
        const val REQ_BT = 41
    }
}
