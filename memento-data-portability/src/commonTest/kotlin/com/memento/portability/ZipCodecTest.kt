package com.memento.portability

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ZipCodecTest {

    @Test
    fun crc32MatchesKnownIeeeVector() {
        assertEquals(0xCBF43926.toInt(), Crc32.compute("123456789".encodeToByteArray()))
    }

    @Test
    fun roundtripPreservesTextBinaryEmptyAndUnicodeEntries() {
        val entries = listOf(
            "hello.txt" to "Hello, world!".encodeToByteArray(),
            "nested/binary.bin" to byteArrayOf(0, 1, 2, 3, -1, -128, 127, 0),
            "empty.txt" to ByteArray(0),
            "unicode-\u65e5\u672c\u8a9e-\ud83c\udf89.txt" to "unicode content".encodeToByteArray(),
        )

        val archive = ZipWriter.write(entries)
        val decoded = ZipReader.read(archive)

        assertEquals(entries.size, decoded.size)
        entries.forEach { (name, content) ->
            assertContentEquals(content, decoded.getValue(name), "content mismatch for '$name'")
        }
    }

    @Test
    fun archiveStartsWithLocalFileHeaderAndEndsWithEocd() {
        val archive = ZipWriter.write(listOf("a.txt" to byteArrayOf(1, 2, 3)))

        // Local file header signature 0x04034b50, little endian.
        assertEquals(0x50, archive[0].toInt() and 0xFF)
        assertEquals(0x4b, archive[1].toInt() and 0xFF)
        assertEquals(0x03, archive[2].toInt() and 0xFF)
        assertEquals(0x04, archive[3].toInt() and 0xFF)

        // End of central directory signature 0x06054b50 in the trailing 22 bytes.
        val eocd = archive.size - 22
        assertEquals(0x50, archive[eocd].toInt() and 0xFF)
        assertEquals(0x4b, archive[eocd + 1].toInt() and 0xFF)
        assertEquals(0x05, archive[eocd + 2].toInt() and 0xFF)
        assertEquals(0x06, archive[eocd + 3].toInt() and 0xFF)
    }
}
