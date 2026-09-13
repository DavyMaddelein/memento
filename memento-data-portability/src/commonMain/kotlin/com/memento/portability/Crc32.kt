package com.memento.portability

/**
 * Pure-Kotlin CRC-32 (IEEE 802.3, reflected polynomial `0xEDB88320`) used by the ZIP codec.
 *
 * Implemented without any external library so it compiles unchanged on jvm, wasmJs and android.
 */
internal object Crc32 {

    private val table: IntArray = IntArray(256) { index ->
        var value = index
        repeat(8) {
            value = if (value and 1 != 0) {
                0xEDB88320.toInt() xor (value ushr 1)
            } else {
                value ushr 1
            }
        }
        value
    }

    fun compute(data: ByteArray): Int {
        var crc = -1
        for (byte in data) {
            crc = (crc ushr 8) xor table[(crc xor byte.toInt()) and 0xFF]
        }
        return crc xor -1
    }
}
