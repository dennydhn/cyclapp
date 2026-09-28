package com.example.cyclapp.gpx

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.Instant

object GpxParser {
    fun parse(input: InputStream): GpxTrack {
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")

        val points = mutableListOf<GpxPoint>()
        var name: String? = null
        var lat: Double? = null
        var lon: Double? = null
        var ele: Double? = null
        var time: Long? = null
        var insidePoint = false
        var currentTag: String? = null

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (parser.name == "trkpt") {
                        insidePoint = true
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text.trim()
                    if (text.isNotEmpty()) {
                        when {
                            currentTag == "name" && !insidePoint -> name = text
                            insidePoint && currentTag == "ele" -> ele = text.toDoubleOrNull()
                            insidePoint && currentTag == "time" -> {
                                time = runCatching {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        Instant.parse(text).toEpochMilli()
                                    } else null
                                }.getOrNull()
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "trkpt") {
                        if (lat != null && lon != null) {
                            points.add(GpxPoint(lat, lon, ele, time))
                        }
                        insidePoint = false
                        lat = null
                        lon = null
                        ele = null
                        time = null
                    }
                    currentTag = null
                }
            }
            parser.next()
        }
        return GpxTrack(name, points)
    }
}