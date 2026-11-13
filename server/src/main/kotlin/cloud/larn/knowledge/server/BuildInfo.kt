package cloud.larn.knowledge.server

import java.util.Properties

/** Build metadata. [version] is the git SHA the server and its bundled content were built from. */
data class BuildInfo(val name: String, val version: String) {
    companion object {
        const val SERVER_NAME = "knowledge-mcp-server"
        const val FALLBACK_VERSION = "dev"
        private const val RESOURCE = "/build-info.properties"

        fun load(): BuildInfo = fromProperties(
            BuildInfo::class.java.getResourceAsStream(RESOURCE)?.use { stream -> Properties().apply { load(stream) } },
        )

        fun fromProperties(properties: Properties?): BuildInfo = BuildInfo(
            name = SERVER_NAME,
            version = properties?.getProperty("version")?.trim()?.takeIf { it.isNotEmpty() } ?: FALLBACK_VERSION,
        )
    }
}
