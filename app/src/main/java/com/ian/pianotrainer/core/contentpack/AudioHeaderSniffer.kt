package com.ian.pianotrainer.core.contentpack

internal enum class DetectedAudioFormat { MP3, OGG, WAV, MP4 }
internal object AudioHeaderSniffer {
    fun detect(header: ByteArray): DetectedAudioFormat? {
        if (header.size >= 10 && header.copyOfRange(0, 3).toString(Charsets.ISO_8859_1) == "ID3") {
            val major = header[3].toInt() and 0xff
            val flags = header[5].toInt() and 0xff
            if (major in 2..4 && flags and 0x0f == 0 && (6..9).all { header[it].toInt() and 0x80 == 0 }) return DetectedAudioFormat.MP3
        }
        if (header.size >= 4 && validMp3Frame(header[0].toInt() and 0xff, header[1].toInt() and 0xff, header[2].toInt() and 0xff)) return DetectedAudioFormat.MP3
        if (header.size >= 4 && header.copyOfRange(0, 4).toString(Charsets.ISO_8859_1) == "OggS") return DetectedAudioFormat.OGG
        if (header.size >= 12 && header.copyOfRange(0, 4).toString(Charsets.ISO_8859_1) == "RIFF" && header.copyOfRange(8, 12).toString(Charsets.ISO_8859_1) == "WAVE") return DetectedAudioFormat.WAV
        if (header.size >= 12 && header.copyOfRange(4, 8).toString(Charsets.ISO_8859_1) == "ftyp") return DetectedAudioFormat.MP4
        return null
    }
    private fun validMp3Frame(a: Int, b: Int, c: Int): Boolean =
        a == 0xff && b and 0xe0 == 0xe0 && (b shr 3 and 3) != 1 && (b shr 1 and 3) != 0 &&
            (c shr 4 and 15) !in setOf(0, 15) && (c shr 2 and 3) != 3
}
