package com.vn.qrscanner.features.scanner

import com.google.zxing.BarcodeFormat

/**
 * Checks that a decoded 1D barcode is complete and fits its format (length, allowed
 * characters, check digit). Formats without a rule here, QR codes included, pass.
 */
object BarcodeValidator {

    private val CODE_39_CHARS = Regex("^[0-9A-Z \\-.$/+%]+$")
    private val CODABAR_CHARS = Regex("^[0-9\\-$:/.+]+$")
    private val DIGITS = Regex("^[0-9]+$")

    fun isValid(text: String, format: BarcodeFormat?): Boolean = when (format) {
        BarcodeFormat.EAN_13 -> text.length == 13 && hasValidCheckDigit(text)
        BarcodeFormat.EAN_8 -> text.length == 8 && hasValidCheckDigit(text)
        BarcodeFormat.UPC_A -> text.length == 12 && hasValidCheckDigit(text)
        BarcodeFormat.UPC_E -> isValidUpcE(text)
        BarcodeFormat.RSS_14 -> text.length == 14 && hasValidCheckDigit(text)
        BarcodeFormat.ITF -> isValidItf(text)
        BarcodeFormat.CODE_39 -> CODE_39_CHARS.matches(text)
        BarcodeFormat.CODABAR -> CODABAR_CHARS.matches(text)
        else -> true
    }

    private fun isValidItf(text: String): Boolean {
        if (!DIGITS.matches(text) || text.length % 2 != 0) return false
        // ITF-14 carries a GTIN check digit.
        return text.length != 14 || hasValidCheckDigit(text)
    }

    /** UPC-E is 8 digits (number system 0/1, 6 data digits, check digit); check it as UPC-A. */
    private fun isValidUpcE(text: String): Boolean {
        if (text.length != 8 || !DIGITS.matches(text) || text[0] !in "01") return false
        val last = text[6]
        val upcA = buildString {
            append(text[0])
            when (last) {
                '0', '1', '2' -> append(text, 1, 3).append(last).append("0000").append(text, 3, 6)
                '3' -> append(text, 1, 4).append("00000").append(text, 4, 6)
                '4' -> append(text, 1, 5).append("00000").append(text[5])
                else -> append(text, 1, 6).append("0000").append(last)
            }
            append(text[7])
        }
        return hasValidCheckDigit(upcA)
    }

    /** GS1 check digit: from the right, data digits are weighted 3, 1, 3, 1... */
    internal fun hasValidCheckDigit(digits: String): Boolean {
        if (digits.length < 2 || !DIGITS.matches(digits)) return false
        val body = digits.dropLast(1)
        val sum = body.reversed().mapIndexed { i, c ->
            (c - '0') * if (i % 2 == 0) 3 else 1
        }.sum()
        return (10 - sum % 10) % 10 == digits.last() - '0'
    }
}
