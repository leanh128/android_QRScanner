package me.dm7.barcodescanner.zxing

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
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
import com.google.zxing.multi.GenericMultipleBarcodeReader
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
        val mlKitResult = decodeWithMlKit(data, width, height, framingRect)
        if (mlKitResult != null) return mlKitResult

        // Fallback to ZXing
        return decodeWithZxing(data, width, height, framingRect)
    }

    /**
     * Like [decode], but returns every code found inside [framingRect] of a camera frame (ML Kit
     * sees several at once).
     * The ZXing fallback can only find one.
     */
    suspend fun decodeAll(data: ByteArray, width: Int, height: Int, framingRect: Rect?): List<Result> {
        val mlKitResults = decodeAllWithMlKit(data, width, height, framingRect)
        if (mlKitResults.isNotEmpty()) return mlKitResults
        return decodeAllWithZxing(data, width, height, framingRect)
    }

    /**
     * Every code found anywhere in a picture (no scan-area filter). Result points are in [bitmap]'s own coordinates, so callers
     * can crop previews from it.
     */
    suspend fun decodeBitmapAll(bitmap: Bitmap): List<Result> {
        val scaled = scaleBitmapIfNeeded(bitmap)
        val factor = bitmap.width.toFloat() / scaled.width

        val mlKit = decodeBitmapAllWithMlKit(scaled)
        if (mlKit.isNotEmpty()) return mlKit.map { it.rescaled(factor) }

        val zxingMulti = decodeBitmapMultipleWithZxing(scaled)
        if (zxingMulti.isNotEmpty()) return zxingMulti.map { it.rescaled(factor) }

        // Rotated attempts report points in rotated coordinates, so drop them.
        return listOfNotNull(decodeBitmapWithZxing(scaled)).map {
            Result(it.text, it.rawBytes, null, it.barcodeFormat)
        }
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

    private suspend fun decodeWithMlKit(data: ByteArray, width: Int, height: Int, framingRect: Rect?): Result? {
        return try {
            val image = InputImage.fromByteArray(
                data, width, height, 0, InputImage.IMAGE_FORMAT_NV21
            )
            val barcodes = mlKitScanner.process(image).await()
            val filteredBarcode = barcodes.find { it.isInScanArea(framingRect) }

            filteredBarcode?.let {
                Result(it.rawValue, null, null, it.zxingFormat())
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Camera frames only count codes lying entirely inside the scan area (a code cut off by its
     * edge may be incomplete). Pictures from the gallery are not filtered: the whole image counts.
     */
    private fun Barcode.isInScanArea(scanArea: Rect?): Boolean {
        if (scanArea == null) return true
        val box = boundingBox ?: return false
        return scanArea.contains(box)
    }

    private fun Barcode.zxingFormat(): BarcodeFormat? = when (format) {
        Barcode.FORMAT_CODE_128 -> BarcodeFormat.CODE_128
        Barcode.FORMAT_CODE_39 -> BarcodeFormat.CODE_39
        Barcode.FORMAT_CODE_93 -> BarcodeFormat.CODE_93
        Barcode.FORMAT_CODABAR -> BarcodeFormat.CODABAR
        Barcode.FORMAT_EAN_13 -> BarcodeFormat.EAN_13
        Barcode.FORMAT_EAN_8 -> BarcodeFormat.EAN_8
        Barcode.FORMAT_ITF -> BarcodeFormat.ITF
        Barcode.FORMAT_UPC_A -> BarcodeFormat.UPC_A
        Barcode.FORMAT_UPC_E -> BarcodeFormat.UPC_E
        Barcode.FORMAT_QR_CODE -> BarcodeFormat.QR_CODE
        Barcode.FORMAT_PDF417 -> BarcodeFormat.PDF_417
        Barcode.FORMAT_AZTEC -> BarcodeFormat.AZTEC
        Barcode.FORMAT_DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
        else -> null
    }

    private suspend fun decodeAllWithMlKit(
        data: ByteArray,
        width: Int,
        height: Int,
        framingRect: Rect?
    ): List<Result> {
        return try {
            val image = InputImage.fromByteArray(
                data, width, height, 0, InputImage.IMAGE_FORMAT_NV21
            )
            mlKitScanner.process(image).await()
                .filter { it.isInScanArea(framingRect) }
                .map { barcode ->
                    // Corner points let callers crop a preview around this specific code.
                    val points = barcode.cornerPoints
                        ?.map { ResultPoint(it.x.toFloat(), it.y.toFloat()) }
                        ?.toTypedArray()
                    Result(barcode.rawValue, null, points, barcode.zxingFormat())
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun decodeBitmapAllWithMlKit(bitmap: Bitmap): List<Result> {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            mlKitScanner.process(image).await().map { barcode ->
                val points = barcode.cornerPoints
                    ?.map { ResultPoint(it.x.toFloat(), it.y.toFloat()) }
                    ?.toTypedArray()
                Result(barcode.rawValue, null, points, barcode.zxingFormat())
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun decodeBitmapMultipleWithZxing(bitmap: Bitmap): List<Result> {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        return decodeMultipleWithZxing(source).ifEmpty { decodeMultipleWithZxing(source.invert()) }
    }

    private fun decodeMultipleWithZxing(source: com.google.zxing.LuminanceSource): List<Result> {
        val hints = HashMap<DecodeHintType, Any>()
        hints[DecodeHintType.POSSIBLE_FORMATS] = ALL_FORMATS
        hints[DecodeHintType.TRY_HARDER] = true
        return try {
            val reader = GenericMultipleBarcodeReader(MultiFormatReader())
            reader.decodeMultiple(BinaryBitmap(HybridBinarizer(source)), hints).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun Result.rescaled(factor: Float): Result {
        val points = resultPoints
        if (factor == 1f || points == null) return this
        val scaled = points.map { ResultPoint(it.x * factor, it.y * factor) }.toTypedArray()
        return Result(text, rawBytes, scaled, barcodeFormat)
    }

    private suspend fun decodeBitmapWithMlKit(bitmap: Bitmap): Result? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val barcodes = mlKitScanner.process(image).await()
            if (barcodes.isNotEmpty()) {
                val barcode = barcodes[0]
                Result(barcode.rawValue, null, null, barcode.zxingFormat())
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeAllWithZxing(
        data: ByteArray,
        width: Int,
        height: Int,
        framingRect: Rect?
    ): List<Result> {
        val source = buildLuminanceSource(data, width, height, framingRect) ?: return emptyList()
        val results = decodeMultipleWithZxing(source).ifEmpty {
            decodeMultipleWithZxing(source.invert())
        }
        return results.map { translateResultPoints(it, framingRect) }
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
