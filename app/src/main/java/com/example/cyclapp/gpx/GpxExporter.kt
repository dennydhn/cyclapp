package com.example.cyclapp.gpx

import com.example.cyclapp.data.db.TrackPointEntity
import java.text.SimpleDateFormat
import java.util.*

object GpxExporter {
    fun export(name: String, points: List<TrackPointEntity>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<gpx version=\"1.1\" creator=\"CycleTracker\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
            appendLine("  <trk>")
            appendLine("    <name>${escapeXml(name)}</name>")
            appendLine("    <trkseg>")

            points.forEach { p ->
                appendLine("      <trkpt lat=\"${p.latitude}\" lon=\"${p.longitude}\">")
                p.altitudeMeters?.let { ele ->
                    appendLine("        <ele>$ele</ele>")
                }
                appendLine("        <time>${dateFormat.format(Date(p.timestamp))}</time>")
                appendLine("      </trkpt>")
            }

            appendLine("    </trkseg>")
            appendLine("  </trk>")
            appendLine("</gpx>")
        }
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}