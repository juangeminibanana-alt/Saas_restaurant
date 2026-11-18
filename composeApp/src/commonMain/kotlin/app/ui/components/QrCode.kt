package app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * Código QR real (ISO/IEC 18004), generado en Kotlin común puro.
 * Siempre módulos oscuros sobre blanco, con zona de silencio de 4 módulos, para que cualquier cámara lo lea
 * sin importar el tema (claro u oscuro) de la pantalla.
 */
@Composable
fun QrCode(content: String, modifier: Modifier = Modifier, size: Dp = 168.dp) {
    val matrix = remember(content) {
        try {
            QrEncoder.encodeQr(content)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
    Canvas(modifier.size(size).semantics { contentDescription = "Código QR" }) {
        val side = this.size.minDimension
        val radius = 8.dp.toPx() // radio sm del sistema
        drawRoundRect(Color.White, size = Size(side, side), cornerRadius = CornerRadius(radius, radius))
        if (matrix == null) return@Canvas
        val n = matrix.size
        val quiet = 4
        val cell = side / (n + quiet * 2)
        val origin = cell * quiet
        val dark = Color(0xFF0B0B0C) // el negro de NOCHE; siempre sobre blanco para que cualquier cámara lo lea
        for (row in 0 until n) {
            val line = matrix[row]
            val top = floor(origin + row * cell)
            val bottom = floor(origin + (row + 1) * cell)
            var col = 0
            while (col < n) {
                if (!line[col]) { col++; continue }
                var end = col
                while (end + 1 < n && line[end + 1]) end++
                val left = floor(origin + col * cell)
                val right = floor(origin + (end + 1) * cell)
                drawRect(dark, topLeft = Offset(left, top), size = Size(right - left, bottom - top))
                col = end + 1
            }
        }
    }
}

/** Codificador QR: modo byte (UTF-8), corrección de errores nivel M, versión automática 1–40. */
private object QrEncoder {

    // Nivel M, indexado por versión (1..40). Índice 0 sin uso.
    private val eccCodewordsPerBlock = intArrayOf(
        -1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26,
        26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
    )
    private val numEccBlocks = intArrayOf(
        -1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16,
        17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49,
    )

    private const val PENALTY_N1 = 3
    private const val PENALTY_N2 = 3
    private const val PENALTY_N3 = 40
    private const val PENALTY_N4 = 10

    /** Devuelve la matriz [fila][columna]; true = módulo oscuro. Lanza IllegalArgumentException si el texto no cabe. */
    fun encodeQr(text: String): Array<BooleanArray> {
        val data = text.encodeToByteArray()

        // 1) Versión mínima en la que caben los datos.
        var version = 0
        for (v in 1..40) {
            val capacityBits = numDataCodewords(v) * 8
            val needed = 4 + charCountBits(v) + data.size * 8
            if (needed <= capacityBits) { version = v; break }
        }
        require(version != 0) { "El contenido es demasiado largo para un código QR" }

        // 2) Flujo de bits: modo byte + longitud + datos + terminador + relleno.
        val capacityBits = numDataCodewords(version) * 8
        val bits = BitBuffer()
        bits.append(0x4, 4)
        bits.append(data.size, charCountBits(version))
        for (b in data) bits.append(b.toInt() and 0xFF, 8)
        bits.append(0, minOf(4, capacityBits - bits.size))
        bits.append(0, (8 - bits.size % 8) % 8)
        var pad = 0xEC
        while (bits.size < capacityBits) {
            bits.append(pad, 8)
            pad = pad xor 0xEC xor 0x11
        }
        val dataCodewords = IntArray(bits.size / 8)
        for (i in 0 until bits.size) {
            if (bits[i]) dataCodewords[i ushr 3] = dataCodewords[i ushr 3] or (1 shl (7 - (i and 7)))
        }

        // 3) Matriz: patrones de función, datos + corrección de errores, mejor máscara.
        val size = version * 4 + 17
        val grid = Grid(version, size)
        grid.drawFunctionPatterns()
        grid.drawCodewords(addEccAndInterleave(dataCodewords, version))
        var bestMask = 0
        var minPenalty = Int.MAX_VALUE
        for (mask in 0..7) {
            grid.applyMask(mask)
            grid.drawFormatBits(mask)
            val penalty = grid.penaltyScore()
            if (penalty < minPenalty) { minPenalty = penalty; bestMask = mask }
            grid.applyMask(mask) // XOR de nuevo: deshace la máscara
        }
        grid.applyMask(bestMask)
        grid.drawFormatBits(bestMask)
        return grid.modules
    }

    private fun charCountBits(version: Int): Int = if (version <= 9) 8 else 16

    private fun numRawDataModules(version: Int): Int {
        var result = (16 * version + 128) * version + 64
        if (version >= 2) {
            val numAlign = version / 7 + 2
            result -= (25 * numAlign - 10) * numAlign - 55
            if (version >= 7) result -= 36
        }
        return result
    }

    private fun numDataCodewords(version: Int): Int =
        numRawDataModules(version) / 8 - eccCodewordsPerBlock[version] * numEccBlocks[version]

    private fun addEccAndInterleave(data: IntArray, version: Int): IntArray {
        val numBlocks = numEccBlocks[version]
        val blockEccLen = eccCodewordsPerBlock[version]
        val rawCodewords = numRawDataModules(version) / 8
        val numShortBlocks = numBlocks - rawCodewords % numBlocks
        val shortBlockLen = rawCodewords / numBlocks

        val divisor = reedSolomonDivisor(blockEccLen)
        val blocks = ArrayList<IntArray>(numBlocks)
        var k = 0
        for (i in 0 until numBlocks) {
            val datLen = shortBlockLen - blockEccLen + (if (i < numShortBlocks) 0 else 1)
            val dat = data.copyOfRange(k, k + datLen)
            k += datLen
            val ecc = reedSolomonRemainder(dat, divisor)
            // Bloque normalizado a longitud shortBlockLen + 1 (los cortos llevan un hueco que luego se salta).
            val block = IntArray(shortBlockLen + 1)
            dat.copyInto(block, 0)
            ecc.copyInto(block, shortBlockLen + 1 - blockEccLen)
            blocks.add(block)
        }

        val result = IntArray(rawCodewords)
        var idx = 0
        for (i in 0..shortBlockLen) {
            for (j in 0 until numBlocks) {
                if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) {
                    result[idx++] = blocks[j][i]
                }
            }
        }
        return result
    }

    // ── Reed–Solomon sobre GF(2^8) con polinomio 0x11D ──

    private fun gfMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z and 0xFF
    }

    private fun reedSolomonDivisor(degree: Int): IntArray {
        val result = IntArray(degree)
        result[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in 0 until degree) {
                result[j] = gfMultiply(result[j], root)
                if (j + 1 < degree) result[j] = result[j] xor result[j + 1]
            }
            root = gfMultiply(root, 0x02)
        }
        return result
    }

    private fun reedSolomonRemainder(data: IntArray, divisor: IntArray): IntArray {
        val result = IntArray(divisor.size)
        for (b in data) {
            val factor = b xor result[0]
            for (i in 0 until result.size - 1) result[i] = result[i + 1]
            result[result.size - 1] = 0
            for (i in divisor.indices) result[i] = result[i] xor gfMultiply(divisor[i], factor)
        }
        return result
    }

    private class BitBuffer {
        private var bits = BooleanArray(256)
        var size = 0
            private set

        operator fun get(index: Int): Boolean = bits[index]

        fun append(value: Int, length: Int) {
            if (length <= 0) return
            if (size + length > bits.size) bits = bits.copyOf(max(bits.size * 2, size + length))
            for (i in length - 1 downTo 0) {
                bits[size++] = ((value ushr i) and 1) != 0
            }
        }
    }

    private class Grid(val version: Int, val size: Int) {
        val modules = Array(size) { BooleanArray(size) }
        private val isFunction = Array(size) { BooleanArray(size) }

        private fun setFunction(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            isFunction[y][x] = true
        }

        fun drawFunctionPatterns() {
            // Patrones de sincronía.
            for (i in 0 until size) {
                setFunction(6, i, i % 2 == 0)
                setFunction(i, 6, i % 2 == 0)
            }
            // Tres localizadores (sobrescriben parte de la sincronía).
            drawFinder(3, 3)
            drawFinder(size - 4, 3)
            drawFinder(3, size - 4)
            // Patrones de alineación.
            val positions = alignmentPositions()
            val count = positions.size
            for (i in 0 until count) {
                for (j in 0 until count) {
                    val isCorner = (i == 0 && j == 0) || (i == 0 && j == count - 1) || (i == count - 1 && j == 0)
                    if (!isCorner) drawAlignment(positions[i], positions[j])
                }
            }
            // Reserva de formato (se redibuja con la máscara final) y versión.
            drawFormatBits(0)
            drawVersion()
        }

        fun drawFormatBits(mask: Int) {
            val data = (0 shl 3) or mask // nivel M = 0b00
            var rem = data
            for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
            val bits = ((data shl 10) or rem) xor 0x5412

            // Primera copia.
            for (i in 0..5) setFunction(8, i, bit(bits, i))
            setFunction(8, 7, bit(bits, 6))
            setFunction(8, 8, bit(bits, 7))
            setFunction(7, 8, bit(bits, 8))
            for (i in 9 until 15) setFunction(14 - i, 8, bit(bits, i))

            // Segunda copia.
            for (i in 0 until 8) setFunction(size - 1 - i, 8, bit(bits, i))
            for (i in 8 until 15) setFunction(8, size - 15 + i, bit(bits, i))
            setFunction(8, size - 8, true) // módulo siempre oscuro
        }

        private fun drawVersion() {
            if (version < 7) return
            var rem = version
            for (i in 0 until 12) rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
            val bits = (version shl 12) or rem
            for (i in 0 until 18) {
                val dark = bit(bits, i)
                val a = size - 11 + i % 3
                val b = i / 3
                setFunction(a, b, dark)
                setFunction(b, a, dark)
            }
        }

        private fun drawFinder(cx: Int, cy: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val dist = max(abs(dx), abs(dy))
                    val x = cx + dx
                    val y = cy + dy
                    if (x in 0 until size && y in 0 until size) setFunction(x, y, dist != 2 && dist != 4)
                }
            }
        }

        private fun drawAlignment(cx: Int, cy: Int) {
            for (dy in -2..2) {
                for (dx in -2..2) setFunction(cx + dx, cy + dy, max(abs(dx), abs(dy)) != 1)
            }
        }

        private fun alignmentPositions(): IntArray {
            if (version == 1) return IntArray(0)
            val numAlign = version / 7 + 2
            val step = if (version == 32) 26 else (version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2
            val result = IntArray(numAlign)
            result[0] = 6
            var pos = size - 7
            for (i in numAlign - 1 downTo 1) {
                result[i] = pos
                pos -= step
            }
            return result
        }

        fun drawCodewords(data: IntArray) {
            var i = 0
            val total = data.size * 8
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) {
                    for (j in 0 until 2) {
                        val x = right - j
                        val upward = ((right + 1) and 2) == 0
                        val y = if (upward) size - 1 - vert else vert
                        if (!isFunction[y][x] && i < total) {
                            modules[y][x] = ((data[i ushr 3] ushr (7 - (i and 7))) and 1) != 0
                            i++
                        }
                        // Los bits sobrantes (si los hay) quedan en claro, como marca el estándar.
                    }
                }
                right -= 2
            }
        }

        fun applyMask(mask: Int) {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    if (isFunction[y][x]) continue
                    val invert = when (mask) {
                        0 -> (x + y) % 2 == 0
                        1 -> y % 2 == 0
                        2 -> x % 3 == 0
                        3 -> (x + y) % 3 == 0
                        4 -> (x / 3 + y / 2) % 2 == 0
                        5 -> x * y % 2 + x * y % 3 == 0
                        6 -> (x * y % 2 + x * y % 3) % 2 == 0
                        else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                    }
                    if (invert) modules[y][x] = !modules[y][x]
                }
            }
        }

        fun penaltyScore(): Int {
            var result = 0

            // Regla 1: rachas de 5 o más módulos del mismo color (filas y columnas).
            for (y in 0 until size) {
                var runColor = false
                var run = 0
                for (x in 0 until size) {
                    if (x > 0 && modules[y][x] == runColor) {
                        run++
                        if (run == 5) result += PENALTY_N1 else if (run > 5) result++
                    } else {
                        runColor = modules[y][x]
                        run = 1
                    }
                }
            }
            for (x in 0 until size) {
                var runColor = false
                var run = 0
                for (y in 0 until size) {
                    if (y > 0 && modules[y][x] == runColor) {
                        run++
                        if (run == 5) result += PENALTY_N1 else if (run > 5) result++
                    } else {
                        runColor = modules[y][x]
                        run = 1
                    }
                }
            }

            // Regla 2: bloques de 2×2 del mismo color.
            for (y in 0 until size - 1) {
                for (x in 0 until size - 1) {
                    val c = modules[y][x]
                    if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) result += PENALTY_N2
                }
            }

            // Regla 3: secuencias parecidas a un localizador (1:1:3:1:1 con 4 claros a un lado).
            for (y in 0 until size) {
                for (x in 0..size - 11) {
                    if (finderLike(x, y, 1, 0)) result += PENALTY_N3
                }
            }
            for (x in 0 until size) {
                for (y in 0..size - 11) {
                    if (finderLike(x, y, 0, 1)) result += PENALTY_N3
                }
            }

            // Regla 4: proporción de módulos oscuros lejos del 50 %.
            var dark = 0
            for (row in modules) for (m in row) if (m) dark++
            val total = size * size
            val k = (abs(dark * 20 - total * 10) + total - 1) / total - 1
            result += max(k, 0) * PENALTY_N4
            return result
        }

        private fun finderLike(x: Int, y: Int, dx: Int, dy: Int): Boolean {
            var a = true
            var b = true
            for (i in 0 until 11) {
                val m = modules[y + dy * i][x + dx * i]
                if (m != PATTERN_A[i]) a = false
                if (m != PATTERN_B[i]) b = false
                if (!a && !b) return false
            }
            return true
        }

        private fun bit(value: Int, index: Int): Boolean = ((value ushr index) and 1) != 0

        private companion object {
            // 10111010000 y 00001011101
            val PATTERN_A = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
            val PATTERN_B = booleanArrayOf(false, false, false, false, true, false, true, true, true, false, true)
        }
    }
}
