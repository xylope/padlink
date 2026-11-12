package com.padlink.controller.ui

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/** Palette : sombre, sobre, un seul accent (dégradé bleu → violet). */
object C {
    const val BG = 0xFF0A0C11.toInt()
    const val SURFACE = 0xFF12151C.toInt()
    const val SURFACE2 = 0xFF1A1E27.toInt()
    const val SURFACE3 = 0xFF242A35.toInt()
    const val BORDER = 0xFF232836.toInt()
    const val TEXT = 0xFFF2F4F8.toInt()
    const val TEXT2 = 0xFFB6BDCB.toInt()
    const val MUTED = 0xFF7C8598.toInt()
    const val ACCENT = 0xFF5B7CFF.toInt()
    const val ACCENT2 = 0xFF8B5CF6.toInt()
    const val ACCENT_SOFT = 0x2E5B7CFF
    const val OK = 0xFF2FD47C.toInt()
    const val WARN = 0xFFF5B547.toInt()
    const val ERR = 0xFFFF5C6C.toInt()
    const val XBOX = 0xFF34C759.toInt()
    const val PS = 0xFF4F8BFF.toInt()
}

object Font {
    val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val bold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
}

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Styles de bouton. */
const val B_SECONDARY = 0
const val B_PRIMARY = 1
const val B_DANGER = 2
const val B_GHOST = 3

fun Context.dp(v: Number): Float = v.toFloat() * resources.displayMetrics.density
fun Context.dpi(v: Number): Int = dp(v).roundToInt()

fun rounded(color: Int, radius: Float, strokeWidth: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
    if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
}

fun gradient(radius: Float, vararg colors: Int = intArrayOf(C.ACCENT, C.ACCENT2)) =
    GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = radius }

fun ripple(content: android.graphics.drawable.Drawable, radius: Float) =
    RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), content, rounded(0xFFFFFFFF.toInt(), radius))

fun Context.label(text: CharSequence, size: Float = 15f, color: Int = C.TEXT, bold: Boolean = false) = TextView(this).apply {
    this.text = text
    textSize = size
    setTextColor(color)
    typeface = if (bold) Font.bold else Font.regular
    setLineSpacing(0f, 1.15f)
}

/** Petit titre de section en capitales espacées. */
fun Context.overline(text: String, color: Int = C.MUTED) = label(text.uppercase(), 11.5f, color).apply {
    typeface = Font.medium
    letterSpacing = 0.09f
}

fun Context.btn(text: String, style: Int = B_SECONDARY, onClick: () -> Unit) = TextView(this).apply {
    this.text = text
    gravity = Gravity.CENTER
    textSize = 15f
    typeface = Font.medium
    setTextColor(when (style) { B_DANGER -> C.ERR; B_GHOST -> C.ACCENT; else -> C.TEXT })
    setPadding(dpi(16), dpi(12), dpi(16), dpi(12))
    val r = dp(14)
    background = when (style) {
        B_PRIMARY -> ripple(gradient(r), r)
        B_GHOST -> ripple(rounded(0x00000000, r), r)
        else -> ripple(rounded(C.SURFACE2, r, dpi(1), C.BORDER), r)
    }
    isClickable = true
    isFocusable = true
    setOnClickListener { onClick() }
}

fun Context.vbox(pad: Int = 0) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(pad, pad, pad, pad)
}

fun Context.hbox() = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

fun Context.card(pad: Int = 18) = vbox(dpi(pad)).apply { background = rounded(C.SURFACE, dp(20), dpi(1), C.BORDER) }

fun LinearLayout.add(v: View, w: Int = MATCH, h: Int = WRAP, weight: Float = 0f, top: Int = 0, end: Int = 0): View {
    addView(v, LinearLayout.LayoutParams(w, h, weight).apply {
        topMargin = context.dpi(top)
        marginEnd = context.dpi(end)
    })
    return v
}

/** Rangée de boutons qui se partagent la largeur. */
fun Context.buttonRow(vararg buttons: View) = hbox().apply {
    buttons.forEachIndexed { i, b -> add(b, 0, WRAP, 1f, end = if (i < buttons.size - 1) 10 else 0) }
}

/** Pastille ronde de couleur (état). */
fun Context.dot(color: Int, size: Int = 10) = View(this).apply {
    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    layoutParams = LinearLayout.LayoutParams(dpi(size), dpi(size))
}

fun View.setDotColor(color: Int) { (background as? GradientDrawable)?.setColor(color) }

/** Carré arrondi avec un symbole au centre (icône sans bibliothèque). */
fun Context.badge(glyph: String, fg: Int, bg: Int, size: Int = 40, textSize: Float = 14f) = label(glyph, textSize, fg, bold = true).apply {
    gravity = Gravity.CENTER
    background = rounded(bg, dp(size * 0.3f))
    layoutParams = LinearLayout.LayoutParams(dpi(size), dpi(size))
}

/** Badge du type de manette. */
fun Context.styleBadge(ps: Boolean, size: Int = 40) =
    if (ps) badge("PS", C.PS, 0x264F8BFF, size, 13f) else badge("XB", C.XBOX, 0x2634C759, size, 13f)

/** Sélecteur à onglets (type iOS / Material 3). */
fun Context.segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout {
    val box = hbox().apply {
        background = rounded(C.SURFACE2, dp(14), dpi(1), C.BORDER)
        setPadding(dpi(4), dpi(4), dpi(4), dpi(4))
    }
    options.forEachIndexed { i, o ->
        val on = i == selected
        box.add(label(o, 14f, if (on) C.TEXT else C.MUTED).apply {
            typeface = Font.medium
            gravity = Gravity.CENTER
            setPadding(0, dpi(9), 0, dpi(9))
            background = if (on) rounded(C.SURFACE3, dp(10)) else null
            setOnClickListener { if (!on) onSelect(i) }
        }, 0, WRAP, 1f)
    }
    return box
}

fun Context.divider() = View(this).apply {
    setBackgroundColor(C.BORDER)
    layoutParams = LinearLayout.LayoutParams(MATCH, dpi(1))
}

fun Context.dialog(): AlertDialog.Builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

/** Élément de menu en feuille du bas. [header] : titre de groupe non cliquable. */
class SheetItem(
    val label: String,
    val icon: String = "",
    val detail: String = "",
    val danger: Boolean = false,
    val checked: Boolean = false,
    val header: Boolean = false,
    val badge: View? = null,
    val onClick: () -> Unit = {},
)

/** Menu qui monte du bas de l'écran (feuille), largeur limitée en paysage. */
fun Context.sheet(title: String, subtitle: String = "", items: List<SheetItem>): Dialog {
    // Thème non flottant : la fenêtre peut couvrir tout l'écran, barres système comprises.
    val d = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
    val col = vbox().apply { setPadding(dpi(8), dpi(10), dpi(8), dpi(16)) }
    col.add(View(this).apply { background = rounded(C.SURFACE3, dp(3)) }, dpi(40), dpi(5)).also {
        (it.layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER_HORIZONTAL
    }
    col.add(label(title, 18f, bold = true).apply { setPadding(dpi(16), dpi(14), dpi(16), 0) })
    if (subtitle.isNotEmpty()) col.add(label(subtitle, 13f, C.MUTED).apply { setPadding(dpi(16), dpi(2), dpi(16), 0) })
    col.add(View(this), MATCH, dpi(8))
    items.forEach { it ->
        if (it.header) {
            col.add(overline(it.label).apply { setPadding(dpi(16), dpi(14), dpi(16), dpi(6)) })
            return@forEach
        }
        val row = hbox().apply {
            setPadding(dpi(16), dpi(12), dpi(16), dpi(12))
            background = ripple(rounded(if (it.checked) C.ACCENT_SOFT else 0x00000000, dp(14)), dp(14))
            isClickable = true
            setOnClickListener { _ -> d.dismiss(); it.onClick() }
        }
        when {
            it.badge != null -> row.addView(it.badge, LinearLayout.LayoutParams(dpi(34), dpi(34)))
            it.icon.isNotEmpty() -> row.addView(label(it.icon, 18f, if (it.danger) C.ERR else C.TEXT2).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dpi(34), dpi(34)))
        }
        row.add(vbox().apply {
            add(label(it.label, 15.5f, if (it.danger) C.ERR else C.TEXT).apply { typeface = Font.medium })
            if (it.detail.isNotEmpty()) add(label(it.detail, 12.5f, C.MUTED), top = 1)
        }, 0, WRAP, 1f).also { v -> (v.layoutParams as LinearLayout.LayoutParams).marginStart = dpi(if (it.icon.isEmpty() && it.badge == null) 0 else 12) }
        if (it.checked) row.addView(label("✓", 17f, C.ACCENT, bold = true))
        col.add(row, top = 2)
    }
    val scroll = ScrollView(this).apply {
        addView(col)
        background = GradientDrawable().apply {
            setColor(C.SURFACE)
            val r = dp(26)
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
        isVerticalScrollBarEnabled = false
    }
    // Fenêtre plein écran transparente : fond assombri (toucher = fermer) et feuille collée au bas de l'écran,
    // derrière la barre de navigation ; son contenu reste au-dessus de celle-ci.
    val dm = resources.displayMetrics
    val root = FrameLayout(this).apply {
        setBackgroundColor(0x8C000000.toInt())
        setOnClickListener { d.dismiss() }
    }
    scroll.isClickable = true
    root.addView(scroll, FrameLayout.LayoutParams(minOf(dm.widthPixels, dpi(560)), WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
    val basePad = col.paddingBottom
    root.setOnApplyWindowInsetsListener { _, insets ->
        val (top, nav) = if (android.os.Build.VERSION.SDK_INT >= 30) {
            insets.getInsets(android.view.WindowInsets.Type.statusBars()).top to
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
        } else @Suppress("DEPRECATION") (insets.systemWindowInsetTop to insets.systemWindowInsetBottom)
        col.setPadding(col.paddingLeft, col.paddingTop, col.paddingRight, basePad + nav)
        // Jamais plus haut que l'écran moins une marge en haut : le contenu défile au-delà.
        (scroll.layoutParams as FrameLayout.LayoutParams).topMargin = top + dpi(48)
        scroll.requestLayout()
        insets
    }
    d.setContentView(root)
    d.window?.apply {
        setBackgroundDrawable(ColorDrawable(0))
        setLayout(MATCH, MATCH)
        clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        if (android.os.Build.VERSION.SDK_INT >= 30) setDecorFitsSystemWindows(false)
        statusBarColor = 0
        navigationBarColor = 0
        // Écran plein écran (manette) : le menu ne fait pas réapparaître les barres système.
        val parentFullscreen = (this@sheet as? android.app.Activity)?.window?.attributes?.flags
            ?.and(WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0
        if (parentFullscreen && android.os.Build.VERSION.SDK_INT >= 30) {
            decorView.post {
                insetsController?.hide(android.view.WindowInsets.Type.systemBars())
                insetsController?.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
    d.show()
    return d
}

/** Android 15+ affiche les apps bord à bord : on garde le contenu hors des barres système. */
fun View.fitSystemBars() {
    setOnApplyWindowInsetsListener { v, insets ->
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val b = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
        } else {
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
        }
        insets
    }
    if (this is ViewGroup) clipToPadding = false
    requestApplyInsets()
}

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
