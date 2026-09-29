package com.example.cyclapp.gpx


import android.content.Context
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

object GpxImporter {

    fun parse(context: Context, uri: Uri): List<GpxPoint> {
        val points = mutableListOf<GpxPoint>()
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return emptyList()

            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            var currentLat = 0.0
            var currentLng = 0.0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("trkpt", ignoreCase = true) || tagName.equals("wpt", ignoreCase = true)) {
                            currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            currentLng = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            points.add(GpxPoint(currentLat, currentLng))
                        }
                    }
                }
                eventType = parser.next()
            }
            inputStream.close()
            points
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}