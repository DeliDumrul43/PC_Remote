package com.example.pcremote

/** Which keyboard layout the PC is set to. Must match the PC's input language. */
enum class PcLayout { US, TR_Q }

/**
 * HID sends key POSITIONS, not letters. Windows turns positions into characters
 * using the PC's keyboard layout, so we need one table per layout.
 */
object KeyMap {
    // Modifier bits (first byte of the keyboard report)
    const val MOD_CTRL = 0x01
    const val MOD_SHIFT = 0x02
    const val MOD_ALT = 0x04
    const val MOD_WIN = 0x08
    const val MOD_ALTGR = 0x40 // right Alt = AltGr on Turkish and most European layouts

    // Special keys
    const val ENTER = 0x28
    const val ESC = 0x29
    const val BACKSPACE = 0x2A
    const val TAB = 0x2B
    const val SPACE = 0x2C
    const val F4 = 0x3D
    const val F5 = 0x3E
    const val F11 = 0x44
    const val HOME = 0x4A
    const val DELETE = 0x4C
    const val END = 0x4D
    const val RIGHT = 0x4F
    const val LEFT = 0x50
    const val DOWN = 0x51
    const val UP = 0x52

    // Media keys (Consumer usage page)
    const val NEXT_TRACK = 0xB5
    const val PREV_TRACK = 0xB6
    const val PLAY_PAUSE = 0xCD
    const val MUTE = 0xE2
    const val VOL_UP = 0xE9
    const val VOL_DOWN = 0xEA

    /** Key code of a lowercase a–z key position (same on US and Turkish Q). */
    fun letter(c: Char): Int = 0x04 + (c - 'a')

    /**
     * Key strokes needed to type [c]: usually one (modifiers, keycode) pair.
     * Dead keys (like ^ on Turkish Q) need a Space afterwards to print the symbol itself.
     */
    fun strokes(c: Char, layout: PcLayout): List<Pair<Int, Int>> {
        val main = when (layout) {
            PcLayout.US -> us(c)
            PcLayout.TR_Q -> trq(c)
        } ?: return emptyList()
        return if (layout == PcLayout.TR_Q && c in TR_DEAD) listOf(main, 0 to SPACE) else listOf(main)
    }

    // ---------------------------------------------------------------- English (US)

    private const val US_PLAIN = "-=[]\\;'`,./"
    private const val US_SHIFTED = "_+{}|:\"~<>?"
    private val US_CODES = intArrayOf(0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38)
    private const val DIGIT_SHIFTED = ")!@#\$%^&*(" // index = the digit key it sits on

    private fun us(c: Char): Pair<Int, Int>? = when (c) {
        in 'a'..'z' -> 0 to letter(c)
        in 'A'..'Z' -> MOD_SHIFT to letter(c.lowercaseChar())
        in '1'..'9' -> 0 to (0x1E + (c - '1'))
        '0' -> 0 to 0x27
        ' ' -> 0 to SPACE
        '\n' -> 0 to ENTER
        '\t' -> 0 to TAB
        else -> {
            val p = US_PLAIN.indexOf(c)
            val s = US_SHIFTED.indexOf(c)
            val d = DIGIT_SHIFTED.indexOf(c)
            when {
                p >= 0 -> 0 to US_CODES[p]
                s >= 0 -> MOD_SHIFT to US_CODES[s]
                d >= 0 -> MOD_SHIFT to (if (d == 0) 0x27 else 0x1E + d - 1)
                else -> null
            }
        }
    }

    // ---------------------------------------------------------------- Türkçe Q
    // Key codes below are named by the US key in the same position.

    private val TR_PLAIN = mapOf(
        'ı' to 0x0C,  // US i
        'i' to 0x34,  // US '
        'ğ' to 0x2F,  // US [
        'ü' to 0x30,  // US ]
        'ş' to 0x33,  // US ;
        'ö' to 0x36,  // US ,
        'ç' to 0x37,  // US .
        '.' to 0x38,  // US /
        ',' to 0x31,  // US \
        '*' to 0x2D,  // US -
        '-' to 0x2E,  // US =
        '"' to 0x35,  // US `
        '<' to 0x64,  // extra key next to left Shift
    )

    private val TR_SHIFT = mapOf(
        'I' to 0x0C, 'İ' to 0x34, 'Ğ' to 0x2F, 'Ü' to 0x30, 'Ş' to 0x33, 'Ö' to 0x36, 'Ç' to 0x37,
        ':' to 0x38, ';' to 0x31, '?' to 0x2D, '_' to 0x2E, 'é' to 0x35, '>' to 0x64,
        '!' to 0x1E, '\'' to 0x1F, '^' to 0x20, '+' to 0x21, '%' to 0x22,
        '&' to 0x23, '/' to 0x24, '(' to 0x25, ')' to 0x26, '=' to 0x27,
    )

    private val TR_ALTGR = mapOf(
        '@' to 0x14, '€' to 0x08, '#' to 0x20, '$' to 0x21, '{' to 0x24, '[' to 0x25,
        ']' to 0x26, '}' to 0x27, '\\' to 0x2D, '|' to 0x2E, '~' to 0x30,
    )

    private val TR_DEAD = setOf('^', '~')

    private fun trq(c: Char): Pair<Int, Int>? {
        TR_PLAIN[c]?.let { return 0 to it }
        TR_SHIFT[c]?.let { return MOD_SHIFT to it }
        TR_ALTGR[c]?.let { return MOD_ALTGR to it }
        return when (c) {
            in 'a'..'z' -> 0 to letter(c)                         // 'i' was handled above
            in 'A'..'Z' -> MOD_SHIFT to letter(c.lowercaseChar())  // 'I' was handled above
            in '0'..'9', ' ', '\n', '\t' -> us(c)                  // same positions as US
            else -> null
        }
    }
}
