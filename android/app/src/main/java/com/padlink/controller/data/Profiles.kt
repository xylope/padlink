package com.padlink.controller.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Type de manette : apparence sur le téléphone ET manette virtuelle créée sur le PC. */
enum class Style(val wire: String, val title: String) {
    XBOX("x360", "Xbox"),
    PS4("ds4", "PlayStation 4");

    companion object { fun of(s: String?) = entries.firstOrNull { it.name == s } }
}

/**
 * Sortie de la manette (masques XInput transmis au PC). LT/RT sont des gâchettes analogiques.
 * TOUCHPAD (0x0800, bit libre en XInput) n'a d'effet qu'en mode PS4.
 */
enum class Act(val mask: Int, private val xbox: String, private val ps: String, private val desc: String) {
    A(0x1000, "A", "✕", "bas"), B(0x2000, "B", "○", "droite"), X(0x4000, "X", "□", "gauche"), Y(0x8000, "Y", "△", "haut"),
    LB(0x0100, "LB", "L1", "épaule gauche"), RB(0x0200, "RB", "R1", "épaule droite"),
    LT(0, "LT", "L2", "gâchette gauche"), RT(0, "RT", "R2", "gâchette droite"),
    BACK(0x0020, "Back", "Share", "petit bouton gauche"), START(0x0010, "Start", "Options", "petit bouton droit"),
    GUIDE(0x0400, "Xbox", "PS", "bouton central"),
    L3(0x0040, "L3", "L3", "clic joystick gauche"), R3(0x0080, "R3", "R3", "clic joystick droit"),
    UP(0x0001, "↑", "↑", "croix haut"), DOWN(0x0002, "↓", "↓", "croix bas"),
    LEFT(0x0004, "←", "←", "croix gauche"), RIGHT(0x0008, "→", "→", "croix droite"),
    TOUCHPAD(0x0800, "Pavé", "Pavé", "pavé tactile");

    fun label(style: Style) = if (style == Style.PS4) ps else xbox

    fun title(style: Style): String {
        val name = label(style)
        return if (this == TOUCHPAD && style == Style.XBOX) "Pavé tactile (PS4 uniquement)" else "$name ($desc)"
    }

    companion object { fun of(s: String?) = entries.firstOrNull { it.name == s } }
}

enum class Kind {
    STICK_L, STICK_R, DPAD, BUTTON;
    companion object { fun of(s: String?) = entries.firstOrNull { it.name == s } }
}

class Control(
    var id: String,
    var kind: Kind,
    var act: Act = Act.A,
    var x: Float,
    var y: Float,
    var size: Float,
    var label: String = "",
    var turbo: Boolean = false,
    var toggle: Boolean = false,
    var visible: Boolean = true,
) {
    val isExtra get() = id.startsWith("extra")

    fun displayName(style: Style): String = when (kind) {
        Kind.STICK_L -> "Joystick gauche"
        Kind.STICK_R -> "Joystick droit"
        Kind.DPAD -> "Croix directionnelle"
        Kind.BUTTON -> "Bouton " + label.ifEmpty { act.label(style) } + if (isExtra) " (ajouté)" else ""
    }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("kind", kind.name).put("act", act.name)
        .put("x", x.toDouble()).put("y", y.toDouble()).put("size", size.toDouble()).put("label", label)
        .put("turbo", turbo).put("toggle", toggle).put("visible", visible)

    companion object {
        const val MIN_SIZE = 0.06f
        const val MAX_SIZE = 0.7f

        fun fromJson(o: JSONObject): Control? {
            val kind = Kind.of(o.optString("kind")) ?: return null
            return Control(
                id = o.optString("id").filter { it.isLetterOrDigit() || it == '_' }.take(24).ifEmpty { return null },
                kind = kind,
                act = Act.of(o.optString("act")) ?: Act.A,
                x = o.optDouble("x", 0.5).toFloat().safe(0.5f).coerceIn(0f, 1f),
                y = o.optDouble("y", 0.5).toFloat().safe(0.5f).coerceIn(0f, 1f),
                size = o.optDouble("size", 0.15).toFloat().safe(0.15f).coerceIn(MIN_SIZE, MAX_SIZE),
                label = o.optString("label").filter { !it.isISOControl() }.take(6),
                turbo = o.optBoolean("turbo"),
                toggle = o.optBoolean("toggle"),
                visible = o.optBoolean("visible", true),
            )
        }
    }
}

private fun Float.safe(def: Float) = if (isNaN() || isInfinite()) def else this

class Settings(
    var deadzone: Float = 0.10f,
    var curve: Float = 1.0f,
    var opacity: Float = 0.85f,
    var haptics: Boolean = true,
    var rumble: Boolean = true,
    var floating: Boolean = false,
    /** 0 = désactivé, 1 = visée (stick droit), 2 = direction par inclinaison (stick gauche). */
    var gyroMode: Int = 0,
    var gyroSens: Float = 1.0f,
    var gyroInvertY: Boolean = false,
    var turboHz: Int = 10,
    var showLabels: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().put("deadzone", deadzone.toDouble()).put("curve", curve.toDouble())
        .put("opacity", opacity.toDouble()).put("haptics", haptics).put("rumble", rumble).put("floating", floating)
        .put("gyroMode", gyroMode).put("gyroSens", gyroSens.toDouble()).put("gyroInvertY", gyroInvertY)
        .put("turboHz", turboHz).put("showLabels", showLabels)

    companion object {
        fun fromJson(o: JSONObject?): Settings {
            val d = Settings()
            if (o == null) return d
            return Settings(
                deadzone = o.optDouble("deadzone", d.deadzone.toDouble()).toFloat().safe(d.deadzone).coerceIn(0f, 0.3f),
                curve = o.optDouble("curve", 1.0).toFloat().safe(1f).coerceIn(0.5f, 2.5f),
                opacity = o.optDouble("opacity", d.opacity.toDouble()).toFloat().safe(d.opacity).coerceIn(0.2f, 1f),
                haptics = o.optBoolean("haptics", true),
                rumble = o.optBoolean("rumble", true),
                floating = o.optBoolean("floating", false),
                gyroMode = o.optInt("gyroMode", 0).coerceIn(0, 2),
                gyroSens = o.optDouble("gyroSens", 1.0).toFloat().safe(1f).coerceIn(0.2f, 4f),
                gyroInvertY = o.optBoolean("gyroInvertY", false),
                turboHz = o.optInt("turboHz", 10).coerceIn(4, 20),
                showLabels = o.optBoolean("showLabels", true),
            )
        }
    }
}

class Profile(
    var id: String,
    var name: String,
    var preset: String,
    var style: Style,
    val controls: MutableList<Control>,
    var settings: Settings,
) {
    fun toJson(): JSONObject = JSONObject().put("app", "padlink").put("id", id).put("name", name).put("preset", preset)
        .put("style", style.name)
        .put("controls", JSONArray().apply { controls.forEach { put(it.toJson()) } })
        .put("settings", settings.toJson())

    /** Copie indépendante (brouillon de l'éditeur). */
    fun copy(): Profile = fromJson(toJson())

    /** Profil fourni avec l'app (identifiant fixe « std… », jamais produit par newId qui est en hexadécimal). */
    val builtin get() = id.startsWith(Profiles.BUILTIN_PREFIX)

    fun subtitle(): String = style.title + " · " + (Presets.all.firstOrNull { it.first == preset }?.second ?: "")

    companion object {
        const val MAX_CONTROLS = 64

        fun fromJson(o: JSONObject): Profile {
            val preset = o.optString("preset").takeIf { k -> Presets.all.any { it.first == k } } ?: "standard"
            val style = Style.of(o.optString("style")) ?: Style.XBOX
            val arr = o.optJSONArray("controls")
            val controls = mutableListOf<Control>()
            val ids = HashSet<String>()
            if (arr != null) for (i in 0 until minOf(arr.length(), MAX_CONTROLS)) {
                val c = arr.optJSONObject(i)?.let(Control::fromJson) ?: continue
                if (ids.add(c.id)) controls.add(c)
            }
            if (controls.isEmpty()) controls.addAll(Presets.layout(preset, style))
            return Profile(
                id = o.optString("id").filter { it.isLetterOrDigit() }.take(16).ifEmpty { Profiles.newId() },
                name = cleanName(o.optString("name")).ifEmpty { "Profil" },
                preset = preset,
                style = style,
                controls = controls,
                settings = Settings.fromJson(o.optJSONObject("settings")),
            )
        }

        fun cleanName(s: String) = s.filter { !it.isISOControl() }.trim().take(30)
    }
}

/** Dispositions préenregistrées. x, y = centre en fraction de l'écran ; size = diamètre en fraction de la hauteur. */
object Presets {
    /** Nom court pour nommer automatiquement un profil. */
    fun shortName(key: String) = when (key) {
        "fps" -> "FPS gyro"
        "racing" -> "Course"
        "fight" -> "Combat"
        "retro" -> "Rétro"
        else -> "Standard"
    }

    val all = listOf(
        "standard" to "Standard",
        "fps" to "FPS – visée au gyroscope",
        "racing" to "Course – direction par inclinaison",
        "fight" to "Combat / Arcade",
        "retro" to "Rétro / Plateforme",
    )

    private fun b(act: Act, x: Float, y: Float, s: Float, label: String = "", visible: Boolean = true) =
        Control(act.name.lowercase(), Kind.BUTTON, act, x, y, s, label, visible = visible)

    private fun stick(k: Kind, x: Float, y: Float, s: Float, visible: Boolean = true) =
        Control(if (k == Kind.STICK_L) "lstick" else "rstick", k, Act.A, x, y, s, visible = visible)

    private fun dpad(x: Float, y: Float, s: Float, visible: Boolean = true) =
        Control("dpad", Kind.DPAD, Act.A, x, y, s, visible = visible)

    private fun menu() = listOf(
        b(Act.BACK, 0.40f, 0.08f, 0.09f), b(Act.GUIDE, 0.50f, 0.08f, 0.10f), b(Act.START, 0.60f, 0.08f, 0.09f),
    )

    fun layout(key: String, style: Style): MutableList<Control> {
        if (style == Style.PS4 && key == "standard") return ps4Standard()
        val list = xboxLayout(key)
        // Pavé tactile disponible (masqué) sur toutes les dispositions PS4.
        if (style == Style.PS4) list.add(b(Act.TOUCHPAD, 0.50f, 0.30f, 0.11f, visible = false))
        return list
    }

    /** Disposition DualShock 4 : croix en haut à gauche, joysticks côte à côte en bas. */
    private fun ps4Standard() = mutableListOf(
        dpad(0.14f, 0.50f, 0.34f),
        b(Act.Y, 0.87f, 0.30f, 0.15f), b(Act.A, 0.87f, 0.70f, 0.15f), b(Act.X, 0.79f, 0.50f, 0.15f), b(Act.B, 0.95f, 0.50f, 0.15f),
        stick(Kind.STICK_L, 0.33f, 0.74f, 0.34f), stick(Kind.STICK_R, 0.67f, 0.74f, 0.34f),
        b(Act.LT, 0.07f, 0.09f, 0.13f), b(Act.LB, 0.21f, 0.09f, 0.12f),
        b(Act.RB, 0.79f, 0.09f, 0.12f), b(Act.RT, 0.93f, 0.09f, 0.13f),
        b(Act.BACK, 0.33f, 0.09f, 0.09f), b(Act.TOUCHPAD, 0.50f, 0.08f, 0.11f), b(Act.START, 0.67f, 0.09f, 0.09f),
        b(Act.GUIDE, 0.50f, 0.92f, 0.09f),
        b(Act.L3, 0.05f, 0.90f, 0.10f), b(Act.R3, 0.95f, 0.90f, 0.10f),
    )

    private fun xboxLayout(key: String): MutableList<Control> = when (key) {
        "fps" -> mutableListOf(
            stick(Kind.STICK_L, 0.16f, 0.58f, 0.44f), stick(Kind.STICK_R, 0.64f, 0.74f, 0.36f),
            dpad(0.34f, 0.84f, 0.24f),
            b(Act.Y, 0.88f, 0.34f, 0.14f), b(Act.A, 0.88f, 0.70f, 0.15f), b(Act.X, 0.81f, 0.52f, 0.14f), b(Act.B, 0.95f, 0.52f, 0.14f),
            b(Act.LT, 0.08f, 0.12f, 0.17f, "VISER"), b(Act.LB, 0.24f, 0.10f, 0.12f),
            b(Act.RB, 0.76f, 0.10f, 0.12f), b(Act.RT, 0.92f, 0.12f, 0.17f, "TIR"),
            b(Act.L3, 0.05f, 0.92f, 0.11f, "SPRINT"), b(Act.R3, 0.50f, 0.90f, 0.11f),
        ).apply { addAll(menu()) }

        "racing" -> mutableListOf(
            stick(Kind.STICK_L, 0.14f, 0.56f, 0.38f), stick(Kind.STICK_R, 0.64f, 0.74f, 0.34f, visible = false),
            dpad(0.33f, 0.82f, 0.24f),
            b(Act.RT, 0.88f, 0.66f, 0.30f, "ACCÉL"), b(Act.LT, 0.67f, 0.72f, 0.24f, "FREIN"),
            b(Act.A, 0.93f, 0.30f, 0.13f), b(Act.B, 0.85f, 0.30f, 0.13f),
            b(Act.X, 0.77f, 0.30f, 0.13f), b(Act.Y, 0.69f, 0.30f, 0.13f),
            b(Act.LB, 0.22f, 0.10f, 0.12f), b(Act.RB, 0.78f, 0.10f, 0.12f),
            b(Act.L3, 0.05f, 0.90f, 0.10f, visible = false), b(Act.R3, 0.52f, 0.92f, 0.10f, visible = false),
        ).apply { addAll(menu()) }

        "fight" -> mutableListOf(
            stick(Kind.STICK_L, 0.18f, 0.58f, 0.42f, visible = false), stick(Kind.STICK_R, 0.64f, 0.74f, 0.34f, visible = false),
            dpad(0.18f, 0.60f, 0.46f),
            b(Act.X, 0.64f, 0.46f, 0.17f), b(Act.Y, 0.76f, 0.40f, 0.17f), b(Act.RB, 0.89f, 0.38f, 0.15f),
            b(Act.A, 0.64f, 0.78f, 0.17f), b(Act.B, 0.76f, 0.72f, 0.17f), b(Act.RT, 0.89f, 0.70f, 0.15f),
            b(Act.LB, 0.24f, 0.10f, 0.12f), b(Act.LT, 0.08f, 0.10f, 0.13f),
            b(Act.L3, 0.05f, 0.90f, 0.10f, visible = false), b(Act.R3, 0.52f, 0.92f, 0.10f, visible = false),
        ).apply { addAll(menu()) }

        "retro" -> mutableListOf(
            stick(Kind.STICK_L, 0.17f, 0.58f, 0.40f, visible = false), stick(Kind.STICK_R, 0.64f, 0.74f, 0.34f, visible = false),
            dpad(0.17f, 0.60f, 0.44f),
            b(Act.Y, 0.83f, 0.36f, 0.17f), b(Act.A, 0.83f, 0.80f, 0.17f), b(Act.X, 0.74f, 0.58f, 0.17f), b(Act.B, 0.92f, 0.58f, 0.17f),
            b(Act.LB, 0.10f, 0.10f, 0.13f), b(Act.RB, 0.90f, 0.10f, 0.13f),
            b(Act.LT, 0.26f, 0.10f, 0.11f, visible = false), b(Act.RT, 0.74f, 0.10f, 0.11f, visible = false),
            b(Act.BACK, 0.42f, 0.88f, 0.10f), b(Act.START, 0.58f, 0.88f, 0.10f), b(Act.GUIDE, 0.50f, 0.08f, 0.10f),
            b(Act.L3, 0.05f, 0.90f, 0.10f, visible = false), b(Act.R3, 0.52f, 0.92f, 0.10f, visible = false),
        )

        else -> mutableListOf(
            stick(Kind.STICK_L, 0.15f, 0.52f, 0.40f), stick(Kind.STICK_R, 0.64f, 0.76f, 0.34f),
            dpad(0.33f, 0.80f, 0.28f),
            b(Act.Y, 0.87f, 0.30f, 0.15f), b(Act.A, 0.87f, 0.70f, 0.15f), b(Act.X, 0.79f, 0.50f, 0.15f), b(Act.B, 0.95f, 0.50f, 0.15f),
            b(Act.LT, 0.07f, 0.09f, 0.13f), b(Act.LB, 0.21f, 0.09f, 0.12f),
            b(Act.RB, 0.79f, 0.09f, 0.12f), b(Act.RT, 0.93f, 0.09f, 0.13f),
            b(Act.L3, 0.05f, 0.90f, 0.10f), b(Act.R3, 0.50f, 0.90f, 0.10f),
        ).apply { addAll(menu()) }
    }

    fun settings(key: String): Settings = when (key) {
        "fps" -> Settings(floating = true, gyroMode = 1, deadzone = 0.08f, curve = 1.3f)
        "racing" -> Settings(gyroMode = 2, gyroSens = 1.2f)
        "fight" -> Settings(opacity = 0.9f)
        "retro" -> Settings(opacity = 0.9f)
        else -> Settings()
    }

    fun create(key: String, name: String, style: Style) =
        Profile(Profiles.newId(), name, key, style, layout(key, style), settings(key))

    /** Profils par défaut : identifiant fixe, preset, style, nom. */
    val defaults = all.map { (k, _) -> Triple("std${k}x", k, Style.XBOX) } + Triple("stdstandardps", "standard", Style.PS4)

    fun defaultName(key: String, style: Style) = "${shortName(key)} (${if (style == Style.PS4) "PS4" else "Xbox"})"
}

object Profiles {
    /** Nombre maximum de profils personnels (les profils par défaut s'ajoutent). */
    const val MAX_PROFILES = 50
    const val BUILTIN_PREFIX = "std"
    private lateinit var prefs: SharedPreferences
    private val list = mutableListOf<Profile>()

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("padlink_profiles", Context.MODE_PRIVATE)
        runCatching {
            val arr = JSONArray(prefs.getString("profiles", "[]"))
            for (i in 0 until minOf(arr.length(), MAX_PROFILES + Presets.defaults.size)) list.add(Profile.fromJson(arr.getJSONObject(i)))
        }
        val firstRun = list.isEmpty()
        // Version 1.0 : noms par défaut trop longs, tronqués à 30 caractères -> nom court.
        var renamed = false
        list.forEach { p ->
            val long = Presets.all.firstOrNull { it.first == p.preset }?.second ?: return@forEach
            if (p.name.length >= 29 && p.name.startsWith(long)) {
                p.name = "${Presets.shortName(p.preset)} (Xbox)"
                renamed = true
            }
        }
        // Version 1.1 : les profils par défaut reçoivent un identifiant fixe pour être séparés des profils personnels.
        if (!firstRun && !prefs.getBoolean("defaults_v2", false)) {
            val sel = prefs.getString("selected", null)
            Presets.defaults.forEach { (id, key, style) ->
                val name = Presets.defaultName(key, style)
                val p = list.firstOrNull { !it.builtin && it.preset == key && it.style == style && it.name.equals(name, true) } ?: return@forEach
                if (sel == p.id) select(id)
                p.id = id
            }
            renamed = true
        }
        // Les profils par défaut sont toujours présents, en tête et dans l'ordre.
        Presets.defaults.forEach { (id, key, style) ->
            if (list.any { it.id == id }) return@forEach
            val name = Presets.defaultName(key, style)
            list.filter { it.name.equals(name, true) }.forEach { it.name = uniqueName(it.name, it.id + "!") }
            list.add(Presets.create(key, name, style).apply { this.id = id })
            renamed = true
        }
        val order = Presets.defaults.map { it.first }
        val sorted = list.sortedBy { p -> order.indexOf(p.id).let { if (it < 0) Int.MAX_VALUE else it } }
        if (sorted != list) { list.clear(); list.addAll(sorted); renamed = true }
        prefs.edit().putBoolean("defaults_v2", true).apply()
        if (renamed) persist()
    }

    /** Nom libre dérivé de [base] (suffixe 2, 3…). */
    fun uniqueName(base: String, exceptId: String? = null): String {
        val b = base.take(26)
        if (!nameTaken(b, exceptId)) return b
        var i = 2
        while (nameTaken("$b $i", exceptId)) i++
        return "$b $i"
    }

    private val rng = java.security.SecureRandom()

    /** Identifiant aléatoire en hexadécimal (jamais préfixé « std », réservé aux profils par défaut). */
    fun newId(): String = ByteArray(6).also(rng::nextBytes).joinToString("") { "%02x".format(it) }

    fun all(): List<Profile> = list
    fun defaults(): List<Profile> = list.filter { it.builtin }
    fun custom(): List<Profile> = list.filter { !it.builtin }

    fun selected(): Profile = list.firstOrNull { it.id == prefs.getString("selected", null) } ?: list.first()

    fun select(id: String) { prefs.edit().putString("selected", id).apply() }

    fun save() = persist()

    fun isFull() = custom().size >= MAX_PROFILES

    fun nameTaken(name: String, exceptId: String? = null) =
        list.any { it.id != exceptId && it.name.equals(name, ignoreCase = true) }

    fun add(p: Profile) {
        if (p.builtin) p.id = newId()
        if (isFull()) throw IllegalStateException("Nombre maximum de profils atteint ($MAX_PROFILES)")
        list.add(p)
        persist()
        select(p.id)
    }

    /** Remplace le profil de même identifiant (enregistrement d'un brouillon). */
    fun replace(p: Profile) {
        if (p.builtin) throw IllegalStateException("Un profil par défaut ne peut pas être remplacé")
        val i = list.indexOfFirst { it.id == p.id }
        if (i >= 0) list[i] = p else list.add(p)
        persist()
        select(p.id)
    }

    fun delete(p: Profile) {
        if (p.builtin || list.size <= 1) return
        list.removeAll { it.id == p.id }
        persist()
        if (prefs.getString("selected", null) == p.id) select(list.first().id)
    }

    /** Remet un profil par défaut dans son état d'origine (disposition et réglages). */
    fun restore(p: Profile) {
        if (!p.builtin) return
        p.controls.clear()
        p.controls.addAll(Presets.layout(p.preset, p.style))
        p.settings = Presets.settings(p.preset)
        p.name = Presets.defaultName(p.preset, p.style)
        persist()
    }

    fun duplicate(p: Profile): Profile = p.copy().apply {
        id = newId()
        name = (p.name + " (copie)").take(30)
    }

    fun export(p: Profile): String = p.toJson().toString(2)

    /** Import d'un profil partagé : tout est revalidé et borné (données non fiables). */
    fun import(text: String): Profile {
        if (text.length > 100_000) throw IllegalArgumentException("Texte trop long")
        val o = JSONObject(text.trim())
        if (o.optString("app") != "padlink") throw IllegalArgumentException("Ce n'est pas un profil PadLink")
        val p = Profile.fromJson(o)
        p.id = newId()
        p.name = uniqueName(p.name)
        add(p)
        return p
    }

    private fun persist() {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("profiles", arr.toString()).apply()
    }
}
