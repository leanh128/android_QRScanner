package com.vn.qrscanner.features.scanner

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.zxing.BarcodeFormat
import com.vn.qrscanner.utils.QrCodeType
import com.vn.qrscanner.utils.QrCodeUtils

/**
 * A code as read from the camera or a picture, before cleaning: its [text], [format], a [preview]
 * crop, and where it was in the frame ([location], a bounding box in the upright camera frame;
 * null for pictures).
 */
data class ScannedItem(
    val text: String?,
    val preview: Bitmap? = null,
    val format: BarcodeFormat? = null,
    val location: Rect? = null
) {
    /** Pixel area of the preview; bigger means the code was seen closer/clearer. 0 if none. */
    val quality: Int get() = preview?.let { it.width * it.height } ?: 0
}

/**
 * Cleans what a scan session picked up, in two steps, so a camera that keeps running cannot
 * sneak in accidental or half-read codes:
 * 1. drop invalid codes (blank or garbled text, incomplete QR content, and barcodes whose
 *    content does not fit their format, see [BarcodeValidator]);
 * 2. remove duplicates among the rest, keeping the copy with the best preview.
 */
object ScanCleaner {

    private val SCHEME_ONLY = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:(//)?$")

    /** Returns the usable items in their original order, with trimmed text. */
    fun clean(items: List<ScannedItem>): List<ScannedItem> =
        removeDuplicates(removeInvalid(items))

    internal fun removeInvalid(items: List<ScannedItem>): List<ScannedItem> =
        items.mapNotNull { item ->
            val text = item.text?.trim()
            if (text != null && isValid(text, item.format)) item.copy(text = text) else null
        }

    /**
     * One item per text, at the position where it was first seen, but with the highest
     * [ScannedItem.quality] preview among its copies (the earliest wins a tie).
     */
    internal fun removeDuplicates(items: List<ScannedItem>): List<ScannedItem> {
        val best = LinkedHashMap<String?, ScannedItem>()
        for (item in items) {
            val current = best[item.text]
            if (current == null || item.quality > current.quality) best[item.text] = item
        }
        return best.values.toList()
    }

    internal fun isValid(text: String, format: BarcodeFormat? = null): Boolean {
        if (text.isEmpty()) return false
        // Garbled reads: control characters (other than line breaks/tabs) or the "unknown" glyph.
        if (text.any { (it.isISOControl() && it !in "\n\r\t") || it == '\uFFFD' }) return false
        if (!BarcodeValidator.isValid(text, format)) return false
        // "http://" alone and similar.
        if (SCHEME_ONLY.matches(text)) return false
        // Looks like Wi-Fi but cannot be turned into a network (e.g. no SSID).
        if (text.startsWith("WIFI:", ignoreCase = true) &&
            QrCodeUtils.detect(text) !is QrCodeType.Wifi
        ) {
            return false
        }
        return true
    }
}

/**
 * Gathers the codes seen during one scan session. Safe to call from the decoding thread
 * and the main thread.
 */
class ScanCollector(private val maxItems: Int = MAX_ITEMS) {

    private val items = ArrayList<ScannedItem>()

    val isEmpty: Boolean @Synchronized get() = items.isEmpty()

    /**
     * Whether a sighting of [text] with preview [quality] is worth keeping: it is a new code,
     * or a clearer view of one we have. Lets callers skip building previews that would lose.
     */
    @Synchronized
    fun wouldImprove(text: String?, quality: Int): Boolean {
        val existing = items.firstOrNull { it.text?.trim() == text?.trim() }
        return if (existing == null) items.size < maxItems else quality > existing.quality
    }

    /** Adds a new code, or replaces an earlier sighting of it that had a worse preview. */
    @Synchronized
    fun add(item: ScannedItem) {
        val index = items.indexOfFirst { it.text?.trim() == item.text?.trim() }
        when {
            index == -1 -> if (items.size < maxItems) items.add(item)
            item.quality > items[index].quality -> items[index] = item
        }
    }

    /** Returns the cleaned items and empties the collector. */
    @Synchronized
    fun drain(): List<ScannedItem> {
        val cleaned = ScanCleaner.clean(items)
        items.clear()
        return cleaned
    }

    @Synchronized
    fun clear() = items.clear()

    companion object {
        const val MAX_ITEMS = 20
    }
}
