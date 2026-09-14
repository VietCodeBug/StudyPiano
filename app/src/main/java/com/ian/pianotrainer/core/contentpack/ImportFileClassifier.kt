package com.ian.pianotrainer.core.contentpack

enum class ImportFileKind { MIDI, PIANO_PACK, UNSUPPORTED }

object ImportFileClassifier {
    fun classify(displayName: String?, mimeType: String?, header: ByteArray): ImportFileKind {
        if (header.startsWith(0x4D, 0x54, 0x68, 0x64)) return ImportFileKind.MIDI
        if (header.startsWith(0x50, 0x4B, 0x03, 0x04) ||
            header.startsWith(0x50, 0x4B, 0x05, 0x06) ||
            header.startsWith(0x50, 0x4B, 0x07, 0x08)) return ImportFileKind.PIANO_PACK

        // A claimed extension/MIME never overrides contradictory content bytes.
        @Suppress("UNUSED_VARIABLE")
        val claimedType = displayName.orEmpty() to mimeType.orEmpty()
        return ImportFileKind.UNSUPPORTED
    }

    private fun ByteArray.startsWith(vararg bytes: Int): Boolean =
        size >= bytes.size && bytes.indices.all { (this[it].toInt() and 0xff) == bytes[it] }
}
