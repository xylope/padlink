package com.padlink.controller.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.MotionEvent
import android.view.View
import com.padlink.controller.data.Act
import com.padlink.controller.data.Control
import com.padlink.controller.data.Kind
import com.padlink.controller.data.Profile
import com.padlink.controller.data.Style
import android.graphics.Path
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Manette tactile multi-touch + éditeur de disposition. */
class GamepadView(context: Context) : View(context) {

    class Output {
        var buttons = 0
        var lt = 0
        var rt = 0
        var lx = 0f
        var ly = 0f
        var rx = 0f
        var ry = 0f
        var leftActive = false
        var rightActive = false
        var turboHeld = false
    }

    private class Ptr(var control: Control?, var x: Float, var y: Float, var ox: Float, var oy: Float, var dpad: Int = 0)

    private enum class Shape { CIRCLE, RECT, PILL, PAD }

    var onInputChanged: (() -> Unit)? = null
    var onSelectionChanged: ((Control?) -> Unit)? = null
    var onEdited: (() -> Unit)? = null
    var onPress: (() -> Unit)? = null

    lateinit var profile: Profile
        private set
    var selected: Control? = null
        private set
    var editMode = false
        set(value) {
            field = value
            resetTouches()
            select(null)
        }

    private val pointers = HashMap<Int, Ptr>()
    private val toggled = LinkedHashSet<Control>()
    private var dragId = -1
    private var dragDx = 0f
    private var dragDy = 0f
    private var pinchId = -1
    private var pinchDist = 0f
    private var pinchSize = 0f

    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f * density }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0xFFFFFFFF.toInt()
        pathEffect = DashPathEffect(floatArrayOf(10f * density, 6f * density), 0f)
    }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint()
    private val cross = android.graphics.Path()
    private val rect = RectF()
    private val path = Path()

    fun setProfile(p: Profile) {
        profile = p
        resetTouches()
        select(null)
    }

    fun resetTouches() {
        pointers.clear()
        toggled.clear()
        dragId = -1
        pinchId = -1
        invalidate()
        onInputChanged?.invoke()
    }

    fun select(c: Control?) {
        selected = c
        onSelectionChanged?.invoke(c)
        invalidate()
    }

    // ------------------------------------------------------------------ géométrie

    private fun shape(c: Control) = if (c.kind != Kind.BUTTON) Shape.CIRCLE else when (c.act) {
        Act.LB, Act.RB, Act.LT, Act.RT -> Shape.RECT
        Act.BACK, Act.START -> Shape.PILL
        Act.TOUCHPAD -> Shape.PAD
        else -> Shape.CIRCLE
    }

    private fun cx(c: Control) = c.x * width
    private fun cy(c: Control) = c.y * height
    private fun rad(c: Control) = c.size * height / 2f
    private fun halfW(c: Control) = rad(c) * when (shape(c)) { Shape.RECT -> 1.4f; Shape.PILL -> 1.25f; Shape.PAD -> 2.4f; Shape.CIRCLE -> 1f }
    private fun halfH(c: Control) = rad(c) * when (shape(c)) { Shape.RECT -> 0.75f; Shape.PILL -> 0.6f; Shape.PAD -> 0.85f; Shape.CIRCLE -> 1f }
    private fun isStick(c: Control) = c.kind == Kind.STICK_L || c.kind == Kind.STICK_R

    /** Distance normalisée au contrôle (≤ 1 = à l'intérieur). */
    private fun norm(c: Control, x: Float, y: Float): Float {
        val dx = (x - cx(c)) / halfW(c)
        val dy = (y - cy(c)) / halfH(c)
        return if (shape(c) == Shape.CIRCLE) hypot(dx, dy) else max(abs(dx), abs(dy))
    }

    private fun hit(x: Float, y: Float, buttonsOnly: Boolean, slack: Float): Control? {
        var best: Control? = null
        var bestScore = Float.MAX_VALUE
        for (c in profile.controls) {
            if (!c.visible || (buttonsOnly && c.kind != Kind.BUTTON)) continue
            val limit = if (isStick(c)) slack * 1.2f else slack
            val score = norm(c, x, y) / limit
            if (score <= 1f && score < bestScore) { best = c; bestScore = score }
        }
        return best
    }

    // ------------------------------------------------------------------ tactile

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!::profile.isInitialized) return false
        if (editMode) {
            editTouch(e)
            invalidate()
            return true
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                down(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> pointers.remove(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> pointers.clear()
        }
        invalidate()
        onInputChanged?.invoke()
        return true
    }

    private fun down(id: Int, x: Float, y: Float) {
        var c = hit(x, y, false, 1.15f)
        if (c != null && isStick(c) && pointers.values.any { it.control === c }) c = null
        val p = Ptr(c, x, y, x, y)
        if (c != null) {
            if (isStick(c) && !profile.settings.floating) { p.ox = cx(c); p.oy = cy(c) }
            if (c.kind == Kind.BUTTON && c.toggle && !toggled.remove(c)) toggled.add(c)
            if (c.kind == Kind.DPAD) p.dpad = dpadMask(p, c)
            onPress?.invoke()
        }
        pointers[id] = p
    }

    private fun move(id: Int, x: Float, y: Float) {
        val p = pointers[id] ?: return
        p.x = x
        p.y = y
        val c = p.control
        if (c == null || (c.kind == Kind.BUTTON && !c.toggle)) {
            // Glisser d'un bouton à l'autre (comme sur une vraie manette).
            val h = hit(x, y, true, 1.0f)?.takeIf { !it.toggle }
            if (h != null && h !== c) {
                p.control = h
                onPress?.invoke()
            } else if (h == null && c != null && norm(c, x, y) > 1.6f) {
                p.control = null
            }
        } else if (c.kind == Kind.DPAD) {
            val m = dpadMask(p, c)
            if (m != p.dpad) {
                if (m != 0) onPress?.invoke()
                p.dpad = m
            }
        }
    }

    private fun editTouch(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val c = hit(e.x, e.y, false, 1.0f)
                select(c)
                if (c != null) {
                    dragId = e.getPointerId(0)
                    dragDx = cx(c) - e.x
                    dragDy = cy(c) - e.y
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val c = selected
                if (c != null && dragId != -1 && pinchId == -1) {
                    pinchId = e.getPointerId(e.actionIndex)
                    pinchDist = pinchDistance(e)
                    pinchSize = c.size
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val c = selected ?: return
                if (pinchId != -1) {
                    val d = pinchDistance(e)
                    if (d > 0f && pinchDist > 0f) c.size = (pinchSize * d / pinchDist).coerceIn(Control.MIN_SIZE, Control.MAX_SIZE)
                } else if (dragId != -1) {
                    val i = e.findPointerIndex(dragId)
                    if (i >= 0) {
                        c.x = ((e.getX(i) + dragDx) / width).coerceIn(0.02f, 0.98f)
                        c.y = ((e.getY(i) + dragDy) / height).coerceIn(0.02f, 0.98f)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                if (id == pinchId) pinchId = -1
                if (id == dragId) { dragId = -1; pinchId = -1 }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (selected != null) onEdited?.invoke()
                dragId = -1
                pinchId = -1
            }
        }
    }

    private fun pinchDistance(e: MotionEvent): Float {
        val a = e.findPointerIndex(dragId)
        val b = e.findPointerIndex(pinchId)
        if (a < 0 || b < 0) return 0f
        return hypot(e.getX(a) - e.getX(b), e.getY(a) - e.getY(b))
    }

    // ------------------------------------------------------------------ sortie

    fun collect(now: Long): Output {
        val o = Output()
        if (!::profile.isInitialized || editMode) return o
        val half = 500L / profile.settings.turboHz.coerceAtLeast(1)
        val turboOn = (now / half) % 2L == 0L

        fun press(c: Control) {
            if (c.turbo) {
                o.turboHeld = true
                if (!turboOn) return
            }
            when (c.act) {
                Act.LT -> o.lt = 255
                Act.RT -> o.rt = 255
                else -> o.buttons = o.buttons or c.act.mask
            }
        }

        for (p in pointers.values) {
            val c = p.control ?: continue
            when (c.kind) {
                Kind.STICK_L -> { val v = stickVec(p, c); o.lx = v[0]; o.ly = v[1]; o.leftActive = true }
                Kind.STICK_R -> { val v = stickVec(p, c); o.rx = v[0]; o.ry = v[1]; o.rightActive = true }
                Kind.DPAD -> o.buttons = o.buttons or p.dpad
                Kind.BUTTON -> if (!c.toggle) press(c)
            }
        }
        toggled.forEach { if (it.visible) press(it) }
        return o
    }

    private fun stickVec(p: Ptr, c: Control): FloatArray {
        val travel = rad(c) * TRAVEL
        val dx = (p.x - p.ox) / travel
        val dy = -(p.y - p.oy) / travel
        val m = hypot(dx, dy)
        val s = profile.settings
        if (m == 0f || m <= s.deadzone) return floatArrayOf(0f, 0f)
        val scaled = ((min(m, 1f) - s.deadzone) / (1f - s.deadzone)).pow(s.curve)
        return floatArrayOf(dx / m * scaled, dy / m * scaled)
    }

    private fun dpadMask(p: Ptr, c: Control): Int {
        val dx = p.x - cx(c)
        val dy = p.y - cy(c)
        if (hypot(dx, dy) < rad(c) * 0.18f) return 0
        val ang = Math.toDegrees(atan2(-dy, dx).toDouble())
        var m = 0
        if (ang > 22.5 && ang < 157.5) m = m or Act.UP.mask
        if (ang < -22.5 && ang > -157.5) m = m or Act.DOWN.mask
        if (abs(ang) < 67.5) m = m or Act.RIGHT.mask
        if (abs(ang) > 112.5) m = m or Act.LEFT.mask
        return m
    }

    // ------------------------------------------------------------------ dessin

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Fond : léger halo au centre, bords plus sombres.
        bgPaint.shader = android.graphics.RadialGradient(w / 2f, h * 0.45f, max(w, h) * 0.75f,
            intArrayOf(0xFF151926.toInt(), C.BG), null, android.graphics.Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        if (!::profile.isInitialized) return
        if (editMode) {
            val step = width / 24f
            val r = 1.3f * density
            var x = step
            while (x < width) {
                var y = step
                while (y < height) { canvas.drawCircle(x, y, r, grid); y += step }
                x += step
            }
        }
        val a = (profile.settings.opacity * 255).roundToInt().coerceIn(40, 255)
        for (c in profile.controls) {
            if (!c.visible) continue
            when (c.kind) {
                Kind.STICK_L, Kind.STICK_R -> drawStick(canvas, c, pointers.values.firstOrNull { it.control === c }, a)
                Kind.DPAD -> drawDpad(canvas, c, pointers.values.fold(0) { acc, p -> if (p.control === c) acc or p.dpad else acc }, a)
                Kind.BUTTON -> drawButton(canvas, c, c in toggled || pointers.values.any { it.control === c }, a)
            }
            if (editMode && c === selected) {
                val m = 7f * density
                rect.set(cx(c) - halfW(c) - m, cy(c) - halfH(c) - m, cx(c) + halfW(c) + m, cy(c) + halfH(c) + m)
                fill.color = 0x1F5B7CFF
                canvas.drawRoundRect(rect, 12f * density, 12f * density, fill)
                dash.color = C.ACCENT
                canvas.drawRoundRect(rect, 12f * density, 12f * density, dash)
            }
        }
    }

    private fun showLabels() = profile.settings.showLabels || editMode

    private fun drawCenteredText(cv: Canvas, s: String, x: Float, y: Float) {
        cv.drawText(s, x, y - (text.descent() + text.ascent()) / 2f, text)
    }

    private fun drawButton(cv: Canvas, c: Control, pressed: Boolean, a: Int) {
        val color = actColor(c.act, profile.style)
        val x = cx(c)
        val y = cy(c)
        val hw = halfW(c)
        val hh = halfH(c)
        val face = color != NEUTRAL
        // Repos : verre sombre + contour fin ; appui : couleur pleine + halo.
        fill.color = if (pressed) withAlpha(color, a) else withAlpha(0xFF1A1F2B.toInt(), (a * 0.85f).toInt())
        stroke.color = withAlpha(color, if (pressed) a else (a * if (face) 0.75f else 0.4f).toInt())
        glow.color = withAlpha(color, (a * 0.22f).toInt())
        val g = 7f * density
        when (shape(c)) {
            Shape.CIRCLE -> {
                if (pressed) cv.drawCircle(x, y, hw + g, glow)
                cv.drawCircle(x, y, hw, fill)
                cv.drawCircle(x, y, hw, stroke)
            }
            else -> {
                val r = if (shape(c) == Shape.PILL) hh else hh * 0.45f
                if (pressed) { rect.set(x - hw - g, y - hh - g, x + hw + g, y + hh + g); cv.drawRoundRect(rect, r + g, r + g, glow) }
                rect.set(x - hw, y - hh, x + hw, y + hh)
                cv.drawRoundRect(rect, r, r, fill)
                cv.drawRoundRect(rect, r, r, stroke)
            }
        }
        if (showLabels()) {
            val ink = when {
                pressed -> withAlpha(C.BG, a)
                face -> withAlpha(color, a)
                else -> withAlpha(C.TEXT2, a)
            }
            if (profile.style == Style.PS4 && c.label.isEmpty() && c.act in PS_FACE) {
                drawPsSymbol(cv, c.act, x, y, hw * 0.42f, ink)
            } else {
                val label = c.label.ifEmpty { c.act.label(profile.style) }
                text.textSize = min(hh * (if (label.length <= 2) 0.85f else 0.7f), hw * 1.6f / max(1, label.length))
                text.typeface = Font.bold
                text.color = ink
                drawCenteredText(cv, label, x, y)
            }
        }
        if (c.turbo || c.toggle) {
            text.textSize = max(hh * 0.42f, 9f * density)
            val tag = (if (c.turbo) "T" else "") + (if (c.toggle) "M" else "")
            val bx = x + hw * 0.8f
            val by = y - hh * 0.8f
            fill.color = C.WARN
            cv.drawCircle(bx, by, text.textSize * (0.55f + 0.25f * (tag.length - 1)), fill)
            text.color = C.BG
            drawCenteredText(cv, tag, bx, by)
        }
    }

    /** Symboles PlayStation dessinés en vectoriel (✕ ○ □ △), indépendants des polices du téléphone. */
    private fun drawPsSymbol(cv: Canvas, act: Act, x: Float, y: Float, s: Float, color: Int) {
        val w = stroke.strokeWidth
        stroke.strokeWidth = max(w, s * 0.22f)
        stroke.color = color
        when (act) {
            Act.A -> {
                cv.drawLine(x - s, y - s, x + s, y + s, stroke)
                cv.drawLine(x - s, y + s, x + s, y - s, stroke)
            }
            Act.B -> cv.drawCircle(x, y, s, stroke)
            Act.X -> { rect.set(x - s * 0.9f, y - s * 0.9f, x + s * 0.9f, y + s * 0.9f); cv.drawRect(rect, stroke) }
            else -> {
                path.reset()
                path.moveTo(x, y - s * 1.05f)
                path.lineTo(x + s * 1.1f, y + s * 0.8f)
                path.lineTo(x - s * 1.1f, y + s * 0.8f)
                path.close()
                cv.drawPath(path, stroke)
            }
        }
        stroke.strokeWidth = w
    }

    private fun drawStick(cv: Canvas, c: Control, p: Ptr?, a: Int) {
        val r = rad(c)
        var bx = cx(c)
        var by = cy(c)
        var kx = bx
        var ky = by
        if (p != null) {
            bx = p.ox
            by = p.oy
            val travel = r * TRAVEL
            var dx = p.x - bx
            var dy = p.y - by
            val m = hypot(dx, dy)
            if (m > travel) { dx = dx / m * travel; dy = dy / m * travel }
            kx = bx + dx
            ky = by + dy
        }
        // Socle : disque sombre, anneau fin, repère intérieur.
        fill.color = withAlpha(0xFF141823.toInt(), (a * 0.8f).toInt())
        cv.drawCircle(bx, by, r, fill)
        stroke.color = withAlpha(if (p != null) C.ACCENT else 0xFF2C3342.toInt(), a)
        cv.drawCircle(bx, by, r, stroke)
        stroke.color = withAlpha(0xFF2C3342.toInt(), (a * 0.6f).toInt())
        cv.drawCircle(bx, by, r * TRAVEL * 0.55f, stroke)
        // Tête : relief (dégradé) et halo quand on la touche.
        val kr = r * 0.42f
        if (p != null) { glow.color = withAlpha(C.ACCENT, (a * 0.25f).toInt()); cv.drawCircle(kx, ky, kr + 8f * density, glow) }
        fill.shader = android.graphics.RadialGradient(kx - kr * 0.3f, ky - kr * 0.35f, kr * 1.3f,
            intArrayOf(withAlpha(if (p != null) 0xFF7B95FF.toInt() else 0xFF3B4357.toInt(), a),
                withAlpha(if (p != null) C.ACCENT else 0xFF232938.toInt(), a)), null, android.graphics.Shader.TileMode.CLAMP)
        cv.drawCircle(kx, ky, kr, fill)
        fill.shader = null
        if (showLabels()) {
            text.textSize = r * 0.24f
            text.typeface = Font.bold
            text.color = withAlpha(C.TEXT2, a)
            drawCenteredText(cv, if (c.kind == Kind.STICK_L) "L" else "R", kx, ky)
        }
    }

    private fun drawDpad(cv: Canvas, c: Control, mask: Int, a: Int) {
        val x = cx(c)
        val y = cy(c)
        val r = rad(c)
        val w = r * 0.34f
        val cr = w * 0.45f
        // Croix d'un seul bloc (verre sombre + contour), branche enfoncée en couleur d'accent.
        path.reset()
        rect.set(x - w, y - r, x + w, y + r); path.addRoundRect(rect, cr, cr, android.graphics.Path.Direction.CW)
        cross.reset()
        rect.set(x - r, y - w, x + r, y + w); cross.addRoundRect(rect, cr, cr, android.graphics.Path.Direction.CW)
        path.op(cross, android.graphics.Path.Op.UNION)
        fill.color = withAlpha(0xFF1A1F2B.toInt(), (a * 0.85f).toInt())
        cv.drawPath(path, fill)
        stroke.color = withAlpha(0xFF3A4254.toInt(), a)
        cv.drawPath(path, stroke)
        fun arm(l: Float, t: Float, rr: Float, b: Float, pressed: Boolean, dx: Float, dy: Float) {
            val ax = (l + rr) / 2f
            val ay = (t + b) / 2f
            if (pressed) {
                rect.set(l, t, rr, b)
                glow.color = withAlpha(C.ACCENT, (a * 0.3f).toInt())
                cv.drawRoundRect(rect, cr, cr, glow)
                fill.color = withAlpha(C.ACCENT, a)
                rect.inset(w * 0.12f, w * 0.12f)
                cv.drawRoundRect(rect, cr * 0.8f, cr * 0.8f, fill)
            }
            if (showLabels()) {
                // Chevron dessiné (indépendant des polices).
                val k = w * 0.32f
                stroke.color = withAlpha(if (pressed) C.BG else C.TEXT2, a)
                val sw = stroke.strokeWidth
                stroke.strokeWidth = max(sw, w * 0.13f)
                stroke.strokeCap = Paint.Cap.ROUND
                cv.drawLine(ax - k * dy - k * dx * 0.6f, ay - k * dx - k * dy * 0.6f, ax + k * dx * 0.6f, ay + k * dy * 0.6f, stroke)
                cv.drawLine(ax + k * dy - k * dx * 0.6f, ay + k * dx - k * dy * 0.6f, ax + k * dx * 0.6f, ay + k * dy * 0.6f, stroke)
                stroke.strokeWidth = sw
                stroke.strokeCap = Paint.Cap.BUTT
            }
        }
        arm(x - w, y - r, x + w, y - w, mask and Act.UP.mask != 0, 0f, -1f)
        arm(x - w, y + w, x + w, y + r, mask and Act.DOWN.mask != 0, 0f, 1f)
        arm(x - r, y - w, x - w, y + w, mask and Act.LEFT.mask != 0, -1f, 0f)
        arm(x + w, y - w, x + r, y + w, mask and Act.RIGHT.mask != 0, 1f, 0f)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Empêche le geste « retour » du système de voler les joysticks près des bords.
        if (Build.VERSION.SDK_INT >= 29) {
            val band = (200 * density).toInt()
            val edge = (40 * density).toInt()
            val w = right - left
            val h = bottom - top
            val t = (h / 2 - band / 2).coerceAtLeast(0)
            systemGestureExclusionRects = listOf(Rect(0, t, edge, t + band), Rect(w - edge, t, w, t + band))
        }
    }

    companion object {
        private const val TRAVEL = 0.75f

        fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

        private val PS_FACE = setOf(Act.A, Act.B, Act.X, Act.Y)
        private const val NEUTRAL = 0xFF8C96AA.toInt()

        fun actColor(act: Act, style: Style): Int = if (style == Style.PS4) when (act) {
            Act.A -> 0xFF7BA7E1.toInt()
            Act.B -> 0xFFE36A6A.toInt()
            Act.X -> 0xFFD88FC0.toInt()
            Act.Y -> 0xFF4FC5A2.toInt()
            Act.GUIDE -> C.ACCENT
            else -> NEUTRAL
        } else when (act) {
            Act.A -> 0xFF34C759.toInt()
            Act.B -> 0xFFFF5C6C.toInt()
            Act.X -> 0xFF4F8BFF.toInt()
            Act.Y -> 0xFFF5B547.toInt()
            Act.GUIDE -> C.ACCENT
            else -> NEUTRAL
        }
    }
}
