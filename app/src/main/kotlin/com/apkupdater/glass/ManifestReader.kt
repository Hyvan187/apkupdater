package com.apkupdater.glass

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bounded binary XML reader. Unsupported or malformed data fails closed. */
object ManifestReader {
    data class Tag(val name: String, val attrs: Map<String, String>)
    fun read(bytes: ByteArray): List<Tag> {
        require(bytes.size in 8..(8 * 1024 * 1024))
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun u16(p: Int) = b.getShort(p).toInt() and 65535
        require(u16(0) == 3 && b.getInt(4) == bytes.size)
        var strings = emptyList<String>()
        val tags = mutableListOf<Tag>()
        var p = u16(2)
        while (p < bytes.size) {
            require(p + 8 <= bytes.size)
            val type = u16(p); val header = u16(p + 2); val size = b.getInt(p + 4)
            require(size >= header && header >= 8 && size <= bytes.size - p)
            if (type == 1) {
                require(header >= 28)
                val count = b.getInt(p + 8); val utf8 = b.getInt(p + 16) and 256 != 0
                val start = p + b.getInt(p + 20)
                require(count in 0..100000 && header.toLong() + count * 4L <= size)
                fun str(offset: Int): String {
                    var q = offset
                    fun length(): Int {
                        return if (utf8) {
                            val n = bytes[q++].toInt() and 255
                            if (n and 128 == 0) n else ((n and 127) shl 8) or (bytes[q++].toInt() and 255)
                        } else {
                            val n = u16(q); q += 2
                            if (n and 32768 == 0) n else ((n and 32767) shl 16) or u16(q).also { q += 2 }
                        }
                    }
                    require(q >= start && q < p + size)
                    var len = length()
                    if (utf8) len = length()
                    val byteCount = if (utf8) len.toLong() else len * 2L
                    require(byteCount >= 0 && q + byteCount <= p + size)
                    return String(bytes, q, byteCount.toInt(), if (utf8) Charsets.UTF_8 else Charsets.UTF_16LE)
                }
                strings = (0 until count).map { str(start + b.getInt(p + header + it * 4)) }
            }
            if (type == 0x102) {
                require(header >= 16 && p + header + 20 <= p + size)
                val ext = p + header
                val name = strings[b.getInt(ext + 4)]
                val attrStart = u16(ext + 8); val attrSize = u16(ext + 10); val count = u16(ext + 12)
                require(attrSize >= 20 && ext.toLong() + attrStart + attrSize.toLong() * count <= p + size)
                val attrs = (0 until count).associate { i ->
                    val a = ext + attrStart + i * attrSize
                    val key = strings[b.getInt(a + 4)]
                    val raw = b.getInt(a + 8); val valueType = bytes[a + 15].toInt() and 255; val value = b.getInt(a + 16)
                    key to when {
                        raw >= 0 -> strings[raw]
                        valueType == 3 -> strings[value]
                        valueType == 18 -> (value != 0).toString()
                        valueType in 16..17 -> value.toString()
                        else -> "@unresolved"
                    }
                }
                tags += Tag(name, attrs)
            }
            p += size
        }
        require(tags.any { it.name == "manifest" })
        return tags
    }
}
