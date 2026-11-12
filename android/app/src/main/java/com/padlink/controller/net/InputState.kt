package com.padlink.controller.net

/** État complet de la manette, au format XInput (boutons, gâchettes 0..255, axes -32768..32767, Y vers le haut). */
data class InputState(
    val buttons: Int = 0, val lt: Int = 0, val rt: Int = 0,
    val lx: Int = 0, val ly: Int = 0, val rx: Int = 0, val ry: Int = 0,
) {
    companion object { val NEUTRAL = InputState() }
}
