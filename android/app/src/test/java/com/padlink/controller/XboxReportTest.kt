package com.padlink.controller

import com.padlink.controller.net.InputState
import com.padlink.controller.net.XboxReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class XboxReportTest {

    /** Décode la description HID : taille en bits des rapports d'entrée (id 1) et de sortie (id 3). */
    private fun reportBits(): Pair<Int, Int> {
        val d = XboxReport.DESCRIPTOR
        var i = 0
        var size = 0
        var count = 0
        var id = 0
        var depth = 0
        var input = 0
        var output = 0
        while (i < d.size) {
            val prefix = d[i].toInt() and 0xFF
            val len = when (prefix and 3) { 3 -> 4; else -> prefix and 3 }
            var v = 0
            for (k in 0 until len) v = v or ((d[i + 1 + k].toInt() and 0xFF) shl (8 * k))
            when (prefix and 0xFC) {
                0x74 -> size = v
                0x94 -> count = v
                0x84 -> id = v
                0x80 -> { assertEquals(1, id); input += size * count }
                0x90 -> { assertEquals(3, id); output += size * count }
                0xA0 -> depth++
                0xC0 -> depth--
            }
            i += 1 + len
        }
        assertEquals("collections équilibrées", 0, depth)
        return input to output
    }

    @Test
    fun descriptorMatchesReports() {
        val (inBits, outBits) = reportBits()
        assertEquals(16 * 8, inBits)
        assertEquals(8 * 8, outBits)
        assertEquals(16, XboxReport.encode(InputState.NEUTRAL).size)
    }

    private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

    @Test
    fun encodesAxesTriggersHatAndButtons() {
        val n = XboxReport.encode(InputState.NEUTRAL)
        assertEquals(32768, u16(n, 0)); assertEquals(32767, u16(n, 2))
        assertEquals(0, n[12].toInt()); assertEquals(0, u16(n, 13))

        // Stick gauche en haut à droite, LT à fond, A + Xbox + R3, croix haut-droite
        val r = XboxReport.encode(InputState(buttons = 0x1000 or 0x0400 or 0x0080 or 0x0001 or 0x0008, lt = 255,
            lx = 32767, ly = 32767, rx = -32768, ry = -32768))
        assertEquals(65535, u16(r, 0)); assertEquals(0, u16(r, 2))      // HID : Y vers le bas
        assertEquals(0, u16(r, 4)); assertEquals(65535, u16(r, 6))
        assertEquals(1023, u16(r, 8)); assertEquals(0, u16(r, 10))
        assertEquals(2, r[12].toInt())                                    // nord-est
        assertEquals((1 shl 0) or (1 shl 12) or (1 shl 14), u16(r, 13))  // A, Xbox, R3
        assertEquals(0, r[15].toInt())
    }

    @Test
    fun parsesRumble() {
        val r = XboxReport.rumble(byteArrayOf(0x03, 0, 0, 100, 50, 0xFF.toByte(), 0, 0))
        assertNotNull(r)
        assertEquals(255 to 127, r)
        assertEquals(0 to 0, XboxReport.rumble(byteArrayOf(0x00, 0, 0, 100, 50, 0, 0, 0)))
    }
}
