package me.dm7.barcodescanner.core

/** Turns NV21 images (a Y plane followed by interleaved V/U pairs) by multiples of 90 degrees. */
internal object Nv21Rotation {

    private const val TILE = 64

    // The 90 and 270 degree turns read rows and write columns. Done tile by tile, both stay in
    // the cache; done in one pass, every write is a cache miss, which is very slow on big frames.
    fun by90(data: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val rotated = ByteArray(data.size)
        for (rowStart in 0 until height step TILE) {
            val rowEnd = minOf(rowStart + TILE, height)
            for (colStart in 0 until width step TILE) {
                val colEnd = minOf(colStart + TILE, width)
                for (j in rowStart until rowEnd) {
                    val target = height - 1 - j
                    for (i in colStart until colEnd) {
                        rotated[i * height + target] = data[j * width + i]
                    }
                }
            }
        }
        // Chroma: (V, U) pairs, one per 2x2 pixels.
        val halfWidth = width / 2
        val halfHeight = height / 2
        for (rowStart in 0 until halfHeight step TILE) {
            val rowEnd = minOf(rowStart + TILE, halfHeight)
            for (colStart in 0 until halfWidth step TILE) {
                val colEnd = minOf(colStart + TILE, halfWidth)
                for (j in rowStart until rowEnd) {
                    val target = halfHeight - 1 - j
                    for (i in colStart until colEnd) {
                        val from = ySize + j * width + i * 2
                        val to = ySize + (i * halfHeight + target) * 2
                        rotated[to] = data[from]
                        rotated[to + 1] = data[from + 1]
                    }
                }
            }
        }
        return rotated
    }

    fun by180(data: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val rotated = ByteArray(data.size)
        for (i in 0 until ySize) {
            rotated[ySize - i - 1] = data[i]
        }
        for (i in 0 until ySize / 2 step 2) {
            rotated[ySize + ySize / 2 - i - 2] = data[ySize + i]
            rotated[ySize + ySize / 2 - i - 1] = data[ySize + i + 1]
        }
        return rotated
    }

    fun by270(data: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val rotated = ByteArray(data.size)
        for (rowStart in 0 until height step TILE) {
            val rowEnd = minOf(rowStart + TILE, height)
            for (colStart in 0 until width step TILE) {
                val colEnd = minOf(colStart + TILE, width)
                for (j in rowStart until rowEnd) {
                    for (i in colStart until colEnd) {
                        rotated[(width - 1 - i) * height + j] = data[j * width + i]
                    }
                }
            }
        }
        val halfWidth = width / 2
        val halfHeight = height / 2
        for (rowStart in 0 until halfHeight step TILE) {
            val rowEnd = minOf(rowStart + TILE, halfHeight)
            for (colStart in 0 until halfWidth step TILE) {
                val colEnd = minOf(colStart + TILE, halfWidth)
                for (j in rowStart until rowEnd) {
                    for (i in colStart until colEnd) {
                        val from = ySize + j * width + i * 2
                        val to = ySize + ((halfWidth - 1 - i) * halfHeight + j) * 2
                        rotated[to] = data[from]
                        rotated[to + 1] = data[from + 1]
                    }
                }
            }
        }
        return rotated
    }
}
