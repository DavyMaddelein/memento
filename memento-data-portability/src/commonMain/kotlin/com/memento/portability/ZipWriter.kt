package com.memento.portability

/**
 * Minimal, dependency-free ZIP encoder that writes STORED (compression method `0`) entries.
 *
 * The produced archive is a valid ZIP32 with Local File Headers, a Central Directory and an
 * End Of Central Directory record. File names are UTF-8 encoded and the UTF-8 general purpose
 * bit (bit 11) is set.
 *
 * Entry data is copied verbatim; nothing is compressed, which keeps the format trivially
 * portable across every Kotlin target.
 */
object ZipWriter {

    private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50
    private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50
    private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50
    private const val VERSION_MADE_BY = 20
    private const val VERSION_NEEDED = 20
    private const val STORED = 0
    private const val UTF8_FLAG = 0x0800
    private const val MAX_U16 = 0xFFFF

    /**
     * Builds a ZIP archive from [entries] where each element is `(path, bytes)`.
     *
     * @throws IllegalArgumentException if there are more than 65535 entries or the archive would
     *   exceed the ZIP32 size limits.
     */
    fun write(entries: List<Pair<String, ByteArray>>): ByteArray {
        require(entries.size <= MAX_U16) { "ZIP32 supports at most $MAX_U16 entries, got ${entries.size}" }

        val nameBytes = entries.map { (path, _) -> path.encodeToByteArray() }
        var totalLocalSize = 0L
        entries.forEachIndexed { index, (_, data) ->
            totalLocalSize += LOCAL_HEADER_SIZE.toLong() + nameBytes[index].size + data.size
        }
        require(totalLocalSize <= Int.MAX_VALUE) {
            "ZIP64 archives are not supported (archive would be $totalLocalSize bytes)"
        }

        val body = ByteWriter(totalLocalSize.toInt().coerceAtLeast(64))
        val central = ByteWriter(body.initialCapacity)

        entries.forEachIndexed { index, (_, data) ->
            val name = nameBytes[index]
            val crc = Crc32.compute(data)
            val localOffset = body.size

            body.writeIntLe(LOCAL_FILE_HEADER_SIGNATURE)
            body.writeShortLe(VERSION_NEEDED)
            body.writeShortLe(UTF8_FLAG)
            body.writeShortLe(STORED)
            body.writeShortLe(0) // last modification time
            body.writeShortLe(0) // last modification date
            body.writeIntLe(crc)
            body.writeIntLe(data.size) // compressed size (== uncompressed for STORED)
            body.writeIntLe(data.size) // uncompressed size
            body.writeShortLe(name.size)
            body.writeShortLe(0) // extra field length
            body.writeBytes(name)
            body.writeBytes(data)

            central.writeIntLe(CENTRAL_DIRECTORY_SIGNATURE)
            central.writeShortLe(VERSION_MADE_BY)
            central.writeShortLe(VERSION_NEEDED)
            central.writeShortLe(UTF8_FLAG)
            central.writeShortLe(STORED)
            central.writeShortLe(0) // time
            central.writeShortLe(0) // date
            central.writeIntLe(crc)
            central.writeIntLe(data.size)
            central.writeIntLe(data.size)
            central.writeShortLe(name.size)
            central.writeShortLe(0) // extra field length
            central.writeShortLe(0) // file comment length
            central.writeShortLe(0) // disk number start
            central.writeShortLe(0) // internal file attributes
            central.writeIntLe(0) // external file attributes
            central.writeIntLe(localOffset)
            central.writeBytes(name)
        }

        val centralOffset = body.size
        val centralBytes = central.toByteArray()
        body.writeBytes(centralBytes)

        body.writeIntLe(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
        body.writeShortLe(0) // number of this disk
        body.writeShortLe(0) // disk where central directory starts
        body.writeShortLe(entries.size) // central directory records on this disk
        body.writeShortLe(entries.size) // total central directory records
        body.writeIntLe(centralBytes.size)
        body.writeIntLe(centralOffset)
        body.writeShortLe(0) // comment length

        return body.toByteArray()
    }

    private const val LOCAL_HEADER_SIZE = 30

    /** Small growable little-endian byte sink, standing in for `java.io.DataOutputStream`. */
    internal class ByteWriter(initialCapacity: Int = 64) {
        private var buffer = ByteArray(initialCapacity.coerceAtLeast(16))
        private var length = 0

        val size: Int get() = length
        val initialCapacity: Int get() = buffer.size

        private fun ensure(extra: Int) {
            if (length + extra <= buffer.size) return
            var newSize = buffer.size
            while (newSize < length + extra) newSize *= 2
            buffer = buffer.copyOf(newSize)
        }

        fun writeByte(value: Int) {
            ensure(1)
            buffer[length++] = value.toByte()
        }

        fun writeShortLe(value: Int) {
            ensure(2)
            buffer[length++] = value.toByte()
            buffer[length++] = (value ushr 8).toByte()
        }

        fun writeIntLe(value: Int) {
            ensure(4)
            buffer[length++] = value.toByte()
            buffer[length++] = (value ushr 8).toByte()
            buffer[length++] = (value ushr 16).toByte()
            buffer[length++] = (value ushr 24).toByte()
        }

        fun writeBytes(bytes: ByteArray) {
            ensure(bytes.size)
            bytes.copyInto(buffer, length)
            length += bytes.size
        }

        fun toByteArray(): ByteArray = buffer.copyOf(length)
    }
}
