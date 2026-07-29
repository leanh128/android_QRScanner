package me.dm7.barcodescanner.zxing

import android.graphics.Rect
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.ensureActive

object BarcodeDecoder {

    val ALL_FORMATS = listOf(
        BarcodeFormat.AZTEC,
        BarcodeFormat.CODABAR,
        BarcodeFormat.CODE_39,
        BarcodeFormat.CODE_93,
        BarcodeFormat.CODE_128,
        BarcodeFormat.DATA_MATRIX,
        BarcodeFormat.EAN_8,
        BarcodeFormat.EAN_13,
        BarcodeFormat.ITF,
        BarcodeFormat.MAXICODE,
        BarcodeFormat.PDF_417,
        BarcodeFormat.QR_CODE,
        BarcodeFormat.RSS_14,
        BarcodeFormat.RSS_EXPANDED,
        BarcodeFormat.UPC_A,
        BarcodeFormat.UPC_E,
        BarcodeFormat.UPC_EAN_EXTENSION
    )

    suspend fun decode(data: ByteArray, width: Int, height: Int, framingRect: Rect?): Result? {
        val multiFormatReader = MultiFormatReader().apply {
            val hints = HashMap<DecodeHintType, Any>()
            val formats = ArrayList<BarcodeFormat>()
            formats.addAll(ALL_FORMATS)
            hints[DecodeHintType.POSSIBLE_FORMATS] = formats
            setHints(hints)
        }

        var rawResult: Result? = null
        val source = buildLuminanceSource(data, width, height, framingRect)

        if (source != null) {
            var bitmap = BinaryBitmap(HybridBinarizer(source))
            try {
                rawResult = multiFormatReader.decodeWithState(bitmap)
            } catch (re: ReaderException) {
                // continue
            } catch (t: Throwable) {
                // ignore internal zxing errors
            } finally {
                multiFormatReader.reset()
            }

            if (rawResult == null) {
                val invertedSource = source.invert()
                bitmap = BinaryBitmap(HybridBinarizer(invertedSource))
                try {
                    rawResult = multiFormatReader.decodeWithState(bitmap)
                } catch (e: NotFoundException) {
                    // continue
                } catch (t: Throwable) {
                    // ignore
                } finally {
                    multiFormatReader.reset()
                }
            }
        }
        return rawResult
    }

    private fun buildLuminanceSource(
        data: ByteArray,
        width: Int,
        height: Int,
        framingRect: Rect?
    ): PlanarYUVLuminanceSource? {
        if (framingRect == null) return null

        return try {
            PlanarYUVLuminanceSource(
                data, width, height, framingRect.left, framingRect.top,
                framingRect.width(), framingRect.height(), false
            )
        } catch (e: Exception) {
            null
        }
    }
}
