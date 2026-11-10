package com.bunc.tv.data.remote

import com.bunc.tv.data.model.Content
import com.bunc.tv.data.model.Section

/** One entry parsed out of an M3U playlist. */
data class M3uChannel(
    val index: Int,
    val name: String,
    val logo: String?,
    val group: String,
    val url: String
)

/**
 * Minimal, forgiving M3U / M3U8 parser.
 * Handles the common `#EXTINF:-1 tvg-logo="..." group-title="...",Name` form.
 */
object M3uParser {

    private val attrRegex = Regex("([a-zA-Z0-9-]+)=\"([^\"]*)\"")

    fun parse(text: String): List<M3uChannel> {
        val out = ArrayList<M3uChannel>()
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup = "Other"
        var idx = 0
        for (line in lines) {
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attrRegex.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                    pendingLogo = attrs["tvg-logo"]
                    pendingGroup = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: "Other"
                    pendingName = line.substringAfterLast(',').trim().ifBlank { attrs["tvg-name"] ?: "Channel" }
                }
                line.startsWith("#") -> { /* skip other directives */ }
                else -> {
                    val name = pendingName ?: line.substringAfterLast('/')
                    out.add(
                        M3uChannel(
                            index = idx++,
                            name = name,
                            logo = pendingLogo,
                            group = pendingGroup,
                            url = line
                        )
                    )
                    pendingName = null; pendingLogo = null; pendingGroup = "Other"
                }
            }
        }
        return out
    }

    fun toContent(ch: M3uChannel): Content =
        Content(id = ch.index, name = ch.name, logo = ch.logo, section = Section.LIVE)
}
