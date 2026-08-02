package me.dm7.barcodescanner.zxing

import android.graphics.Rect
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.multi.GenericMultipleBarcodeReader
import com.google.zxing.Result
import com.google.zxing.ResultPoint
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

    suspend fun decode(data: ByteArray, width: Int, height: Int, framingRect: Rect?): List<Result> {
        val multiFormatReader = MultiFormatReader().apply {
            val hints = HashMap<DecodeHintType, Any>()
            val formats = ArrayList<BarcodeFormat>()
            formats.addAll(ALL_FORMATS)
            hints[DecodeHintType.POSSIBLE_FORMATS] = formats
            setHints(hints)
        }

        val multipleReader = GenericMultipleBarcodeReader(multiFormatReader)
        val results = mutableListOf<Result>()
        val source = buildLuminanceSource(data, width, height, framingRect)

        if (source != null) {
            var bitmap = BinaryBitmap(HybridBinarizer(source))
            try {
                val decodedResults = multipleReader.decodeMultiple(bitmap)
                if (decodedResults != null) {
                    results.addAll(decodedResults.map { translateResultPoints(it, framingRect) })
                }
            } catch (re: ReaderException) {
                // continue
            } catch (t: Throwable) {
                // ignore internal zxing errors
            } finally {
                multiFormatReader.reset()
            }

            if (results.isEmpty()) {
                val invertedSource = source.invert()
                bitmap = BinaryBitmap(HybridBinarizer(invertedSource))
                try {
                    val decodedResults = multipleReader.decodeMultiple(bitmap)
                    if (decodedResults != null) {
                        results.addAll(decodedResults.map { translateResultPoints(it, framingRect) })
                    }
                } catch (e: Exception) {
                    // continue
                } finally {
                    multiFormatReader.reset()
                }
            }
        }
        return results
    }

    private fun translateResultPoints(result: Result, framingRect: Rect?): Result {
        val offsetLeft = framingRect?.left?.toFloat() ?: 0f
        val offsetTop = framingRect?.top?.toFloat() ?: 0f
        if (offsetLeft == 0f && offsetTop == 0f) return result

        val oldPoints = result.resultPoints ?: return result
        val newPoints = oldPoints.map { ResultPoint(it.x + offsetLeft, it.y + offsetTop) }.toTypedArray()
        val newResult = Result(result.text, result.rawBytes, newPoints, result.barcodeFormat)
        newResult.putAllMetadata(result.resultMetadata)
        return newResult
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
