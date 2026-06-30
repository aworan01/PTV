package com.personal.tv.data.parser

import android.util.Xml
import com.personal.tv.data.model.Programme
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*

object XmltvParser {

    private val dateFormats = listOf(
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US),
        SimpleDateFormat("yyyyMMddHHmmss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US),
    )

    fun parse(content: String): List<Programme> {
        val programmes = mutableListOf<Programme>()

        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(content))

            var eventType = parser.eventType
            var currentProgramme: ProgrammeBuilder? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "programme" -> {
                                currentProgramme = ProgrammeBuilder(
                                    channelId = parser.getAttributeValue(null, "channel") ?: "",
                                    startTime = parseDate(parser.getAttributeValue(null, "start") ?: ""),
                                    endTime = parseDate(parser.getAttributeValue(null, "stop") ?: "")
                                )
                            }
                            "title" -> currentProgramme?.title = parser.nextText()
                            "desc" -> currentProgramme?.description = parser.nextText()
                            "category" -> currentProgramme?.category = parser.nextText()
                            "icon" -> currentProgramme?.icon = parser.getAttributeValue(null, "src") ?: ""
                            "rating" -> {
                                // skip to value tag
                            }
                            "value" -> {
                                if (currentProgramme != null) {
                                    currentProgramme?.rating = parser.nextText()
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "programme") {
                            currentProgramme?.build()?.let { programmes.add(it) }
                            currentProgramme = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return programmes
    }

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        for (fmt in dateFormats) {
            try {
                return fmt.parse(dateStr.trim())?.time ?: 0L
            } catch (e: Exception) { /* try next */ }
        }
        return 0L
    }

    private class ProgrammeBuilder(
        val channelId: String,
        val startTime: Long,
        val endTime: Long
    ) {
        var title: String = ""
        var description: String = ""
        var category: String = ""
        var icon: String = ""
        var rating: String = ""

        fun build() = Programme(
            channelId = channelId,
            title = title,
            description = description,
            startTime = startTime,
            endTime = endTime,
            category = category,
            icon = icon,
            rating = rating
        )
    }
}
