package me.dm7.barcodescanner.core

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import kotlin.random.Random

/** The tiled rotation must give exactly what the plain one-pass loops did. */
class Nv21RotationTest {

    private fun image(width: Int, height: Int) =
        ByteArray(width * height * 3 / 2).also { Random(width * 31 + height).nextBytes(it) }

    private fun naive90(data: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val rotated = ByteArray(data.size)
        var k = 0
        for (i in 0 until width) for (j in height - 1 downTo 0) rotated[k++] = data[j * width + i]
        k = ySize
        for (i in 0 until width step 2) {
            for (j in height / 2 - 1 downTo 0) {
                rotated[k++] = data[ySize + j * width + i]
                rotated[k++] = data[ySize + j * width + i + 1]
            }
        }
        return rotated
    }

    private fun naive270(data: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val rotated = ByteArray(data.size)
        var k = 0
        for (i in width - 1 downTo 0) for (j in 0 until height) rotated[k++] = data[j * width + i]
        k = ySize
        for (i in width - 1 downTo 0 step 2) {
            for (j in 0 until height / 2) {
                rotated[k++] = data[ySize + j * width + i - 1]
                rotated[k++] = data[ySize + j * width + i]
            }
        }
        return rotated
    }

    // Sizes smaller than, equal to and not a multiple of the tile size.
    private val sizes = listOf(4 to 2, 16 to 8, 64 to 64, 130 to 70, 640 to 480, 202 to 134)

    @Test
    fun rotate90MatchesPlainLoops() {
        for ((w, h) in sizes) {
            val data = image(w, h)
            assertArrayEquals("${w}x$h", naive90(data, w, h), Nv21Rotation.by90(data, w, h))
        }
    }

    @Test
    fun rotate270MatchesPlainLoops() {
        for ((w, h) in sizes) {
            val data = image(w, h)
            assertArrayEquals("${w}x$h", naive270(data, w, h), Nv21Rotation.by270(data, w, h))
        }
    }
}
