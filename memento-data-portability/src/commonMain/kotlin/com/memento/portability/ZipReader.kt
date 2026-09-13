package com.memento.portability

/**
 * Minimal, dependency-free ZIP decoder.
 *
 * Only STORED (compression method `0`) entries are supported; encountering a deflate entry
 * (method `8`) or any other method throws an [UnsupportedOperationException] with the offending
 * entry name. The reader walks the Central Directory (located via the End Of Central Directory
 * record) so it tolerates data descriptors and arbitrary local metadata.
 */
object ZipReader {

    private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50
    private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50
    private const val EOCD_MIN_SIZE = 22
    private const val MAX_COMMENT = 0xFFFF
    private const val STORED = 0
    private const val DEFLATE = 8

    /**
     * Parses [bytes] and returns a map of entry name to uncompressed content.
     *
     * @throws UnsupportedOperationException if an entry uses an unsupported compression method.
     * @throws IllegalStateException if the archive is malformed or truncated.
     */
    fun read(bytes: ByteArray): Map<String, ByteArray> {
        val eocd = findEndOfCentralDirectory(bytes)
        val totalEntries = readU16(bytes, eocd + 10)
        val centralSize = readU32(bytes, eocd + 12)
        val centralOffset = readU32(bytes, eocd + 16)

        check(centralOffset >= 0 && centralSize >= 0 && centralOffset + centralSize <= bytes.size) {
            "Corrupt ZIP: central directory at $centralOffset size $centralSize is out of bounds"
        }

        val result = LinkedHashMap<String, ByteArray>(totalEntries)
        var cursor = centralOffset
        repeat(totalEntries) {
            check(readU32(bytes, cursor) == CENTRAL_DIRECTORY_SIGNATURE) {
                "Corrupt ZIP: bad central directory signature at $cursor"
            }

            val method = readU16(bytes, cursor + 10)
            val expectedCrc = readU32(bytes, cursor + 16)
            val compressedSize = readU32(bytes, cursor + 20)
            val nameLength = readU16(bytes, cursor + 28)
            val extraLength = readU16(bytes, cursor + 30)
            val commentLength = readU16(bytes, cursor + 32)
            val localOffset = readU32(bytes, cursor + 42)

            val name = bytes.decodeToString(cursor + 46, cursor + 46 + nameLength)

            when (method) {
                DEFLATE -> throw UnsupportedOperationException(
                    "ZIP entry '$name' uses deflate (method 8); only STORED (method 0) archives are supported"
                )
                STORED -> Unit
                else -> throw UnsupportedOperationException(
                    "ZIP entry '$name' uses unsupported compression method $method"
                )
            }

            check(localOffset >= 0 && localOffset + 30 <= bytes.size) {
                "Corrupt ZIP: local header offset $localOffset for '$name' is out of bounds"
            }
            val localNameLength = readU16(bytes, localOffset + 26)
            val localExtraLength = readU16(bytes, localOffset + 28)
            val dataStart = localOffset + 30 + localNameLength + localExtraLength
            check(dataStart + compressedSize <= bytes.size) {
                "Corrupt ZIP: data for '$name' extends past end of archive"
            }

            val content = bytes.copyOfRange(dataStart, dataStart + compressedSize)
            val actualCrc = Crc32.compute(content)
            check(actualCrc == expectedCrc) {
                "Corrupt ZIP: CRC mismatch for '$name' (expected $expectedCrc, got $actualCrc)"
            }

            result[name] = content
            cursor += 46 + nameLength + extraLength + commentLength
        }
        return result
    }

    private fun findEndOfCentralDirectory(bytes: ByteArray): Int {
        check(bytes.size >= EOCD_MIN_SIZE) { "Not a ZIP archive: file is too short (${bytes.size} bytes)" }
        val lowestPossible = (bytes.size - EOCD_MIN_SIZE - MAX_COMMENT).coerceAtLeast(0)
        var offset = bytes.size - EOCD_MIN_SIZE
        while (offset >= lowestPossible) {
            if (readU32(bytes, offset) == END_OF_CENTRAL_DIRECTORY_SIGNATURE) {
                return offset
            }
            offset--
        }
        error("Not a ZIP archive: end of central directory record not found")
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readU32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
