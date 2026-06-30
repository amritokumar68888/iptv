package com.iptvplayer.app.data.parser

import com.iptvplayer.app.data.model.Channel

object M3uParser {

    /**
     * Parse raw M3U playlist content into a list of Channel objects.
     */
    fun parse(content: String, playlistId: Long): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = content.lines()

        if (lines.isEmpty() || !lines[0].trim().startsWith("#EXTM3U")) {
            return channels
        }

        var i = 1
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.startsWith("#EXTINF:")) {
                val name = extractName(line)
                val logo = extractAttribute(line, "tvg-logo")
                val group = extractAttribute(line, "group-title").ifEmpty { "Uncategorized" }

                // Next non-empty line should be the stream URL
                var urlLine = ""
                var j = i + 1
                while (j < lines.size) {
                    val nextLine = lines[j].trim()
                    if (nextLine.isNotEmpty() && !nextLine.startsWith("#")) {
                        urlLine = nextLine
                        i = j
                        break
                    }
                    j++
                }

                if (urlLine.isNotEmpty() && name.isNotEmpty()) {
                    channels.add(
                        Channel(
                            name = name,
                            url = urlLine,
                            logoUrl = logo,
                            group = group,
                            playlistId = playlistId
                        )
                    )
                }
            }
            i++
        }

        return channels
    }

    /**
     * Extract channel name from #EXTINF line.
     * Format: #EXTINF:-1 tvg-id="..." tvg-name="..." group-title="...",Channel Name
     */
    private fun extractName(line: String): String {
        val commaIndex = line.lastIndexOf(',')
        return if (commaIndex >= 0 && commaIndex < line.length - 1) {
            line.substring(commaIndex + 1).trim()
        } else {
            ""
        }
    }

    /**
     * Extract attribute value from #EXTINF line.
     * e.g. extractAttribute(line, "group-title") returns value of group-title="..."
     */
    private fun extractAttribute(line: String, attribute: String): String {
        val pattern = Regex("$attribute=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
        return pattern.find(line)?.groupValues?.get(1) ?: ""
    }
}
