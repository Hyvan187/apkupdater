package org.uniapk.upgrader.glass

/** Client-side obfuscation. The destination is necessarily visible when opened. */
object SupportLink {
    private val payload = intArrayOf(50,127,146,30,87,248,112,254,249,14,156,129,63,185,53,69,157,8,4,185,192,228,219,18,140,162,53,84,35,82,229)
    fun uri(): String {
        val mask = intArrayOf(93,17,203,46,119,164,38)
        return String(ByteArray(payload.size) { i ->
            (payload[i] xor mask[i % mask.size] xor ((i * 19 + 7) and 255)).toByte()
        }, Charsets.UTF_8)
    }
}
