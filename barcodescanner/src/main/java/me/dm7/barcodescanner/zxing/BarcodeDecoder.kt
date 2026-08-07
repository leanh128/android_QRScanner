package me.dm7.barcodescanner.zxing

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.ResultPoint
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.tasks.await
import androidx.core.graphics.scale

object BarcodeDecoder {

    private val mlKitScanner = BarcodeScanning.getClient()

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

    private const val MAX_DIMENSION = 1280

    suspend fun decode(data: ByteArray, width: Int, height: Int, framingRect: Rect?): Result? {
        // Try ML Kit first
        val mlKitResult = decodeWithMlKit(data, width, height)
        if (mlKitResult != null) return mlKitResult

        // Fallback to ZXing
        return decodeWithZxing(data, width, height, framingRect)
    }

    suspend fun decodeBitmap(bitmap: Bitmap): Result? {
        val scaledBitmap = scaleBitmapIfNeeded(bitmap)
        // Try ML Kit first
        val mlKitResult = decodeBitmapWithMlKit(scaledBitmap)
        if (mlKitResult != null) return mlKitResult

        // Fallback to ZXing
        return decodeBitmapWithZxing(scaledBitmap)
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = MAX_DIMENSION
            newHeight = (MAX_DIMENSION / ratio).toInt()
        } else {
            newHeight = MAX_DIMENSION
            newWidth = (MAX_DIMENSION * ratio).toInt()
        }

        return bitmap.scale(newWidth, newHeight)
    }

    private suspend fun decodeWithMlKit(data: ByteArray, width: Int, height: Int): Result? {
        return try {
            val image = InputImage.fromByteArray(
                data, width, height, 0, InputImage.IMAGE_FORMAT_NV21
            )
            val barcodes = mlKitScanner.process(image).await()
            if (barcodes.isNotEmpty()) {
                val barcode = barcodes[0]
                Result(barcode.rawValue, null, null, null) // Simplified Result for ML Kit
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun decodeBitmapWithMlKit(bitmap: Bitmap): Result? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val barcodes = mlKitScanner.process(image).await()
            if (barcodes.isNotEmpty()) {
                val barcode = barcodes[0]
                Result(barcode.rawValue, null, null, null)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeWithZxing(
        data: ByteArray,
        width: Int,
        height: Int,
        framingRect: Rect?
    ): Result? {
        val source = buildLuminanceSource(data, width, height, framingRect) ?: return null
        val reader = createMultiFormatReader()

        val result = decodeWithReader(reader, source)
        return result?.let { translateResultPoints(it, framingRect) }
    }

    private fun decodeBitmapWithZxing(bitmap: Bitmap): Result? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val reader = createMultiFormatReader()

        val result = decodeWithReader(reader, source)
        if (result != null) return result

        // Try rotating the bitmap if ZXing failed (it's less robust than ML Kit to rotation)
        val matrix = android.graphics.Matrix()
        matrix.postRotate(90f)
        val rotated90 = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        val result90 = decodeBitmapWithZxingInternal(rotated90)
        if (result90 != null) return result90

        matrix.postRotate(90f) // 180 total
        val rotated180 = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        val result180 = decodeBitmapWithZxingInternal(rotated180)
        if (result180 != null) return result180

        matrix.postRotate(90f) // 270 total
        val rotated270 = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        return decodeBitmapWithZxingInternal(rotated270)
    }

    private fun decodeBitmapWithZxingInternal(bitmap: Bitmap): Result? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val reader = createMultiFormatReader()
        return decodeWithReader(reader, source)
    }

    private fun decodeWithReader(
        reader: MultiFormatReader,
        source: com.google.zxing.LuminanceSource
    ): Result? {
        var result = tryDecode(reader, source)
        if (result == null) {
            result = tryDecode(reader, source.invert())
        }
        return result
    }

    private fun tryDecode(
        reader: MultiFormatReader,
        source: com.google.zxing.LuminanceSource
    ): Result? {
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            reader.decodeWithState(bitmap)
        } catch (e: Exception) {
            null
        } finally {
            reader.reset()
        }
    }

    private fun createMultiFormatReader(): MultiFormatReader {
        return MultiFormatReader().apply {
            val hints = HashMap<DecodeHintType, Any>()
            hints[DecodeHintType.POSSIBLE_FORMATS] = ALL_FORMATS
            hints[DecodeHintType.TRY_HARDER] = true
            setHints(hints)
        }
    }

    private fun translateResultPoints(result: Result, framingRect: Rect?): Result {
        val offsetLeft = framingRect?.left?.toFloat() ?: 0f
        val offsetTop = framingRect?.top?.toFloat() ?: 0f
        if (offsetLeft == 0f && offsetTop == 0f) return result

        val oldPoints = result.resultPoints ?: return result
        val newPoints =
            oldPoints.map { ResultPoint(it.x + offsetLeft, it.y + offsetTop) }.toTypedArray()

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
