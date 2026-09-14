package com.ian.pianotrainer.core.contentpack

import android.util.Xml
import java.io.File
import org.xmlpull.v1.XmlPullParser

internal object MusicXmlValidator {
    private val roots = setOf("score-partwise", "score-timewise")

    fun validate(file: File): Result<Unit> = runCatching {
        file.inputStream().buffered().use { input ->
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                // DOCTYPE is syntactically accepted, but its external/internal declarations
                // are never processed, so the parser cannot read a file or open a network URL.
                setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
                setInput(input, null)
            }
            var root: String? = null
            var rootDepth = -1
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> if (root == null) {
                        root = parser.name
                        rootDepth = parser.depth
                        require(root in roots) { "Root '$root' không phải score-partwise/score-timewise." }
                    }
                    XmlPullParser.END_TAG -> if (parser.depth == rootDepth && parser.name != root) {
                        error("Thẻ đóng MusicXML không khớp root.")
                    }
                    XmlPullParser.ENTITY_REF -> require(parser.name in setOf("amp", "lt", "gt", "quot", "apos")) {
                        "MusicXML chứa custom/external entity không được phép xử lý."
                    }
                }
                parser.nextToken()
            }
            require(root != null) { "MusicXML không có phần tử gốc." }
        }
    }
}
