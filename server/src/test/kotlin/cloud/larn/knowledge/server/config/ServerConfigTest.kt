package cloud.larn.knowledge.server.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import java.nio.file.Path
import kotlin.test.Test

class ServerConfigTest {
    @Test
    fun `splits and trims allowed hosts`() {
        MapApplicationConfig("knowledge.allowedHosts" to " knowledge.internal , localhost,, ")
            .toServerConfig() shouldBe ServerConfig(allowedHosts = listOf("knowledge.internal", "localhost"))
    }

    @Test
    fun `reads knowledge dir and include drafts`() {
        MapApplicationConfig(
            "knowledge.allowedHosts" to "localhost",
            "knowledge.dir" to "../knowledge",
            "knowledge.includeDrafts" to "TRUE",
        ).toServerConfig() shouldBe
            ServerConfig(listOf("localhost"), knowledgeDir = Path.of("../knowledge"), includeDrafts = true)
    }

    @Test
    fun `rejects a non-boolean include drafts`() {
        shouldThrow<IllegalArgumentException> {
            MapApplicationConfig("knowledge.allowedHosts" to "localhost", "knowledge.includeDrafts" to "yes").toServerConfig()
        }
    }

    @Test
    fun `rejects an empty host list`() {
        shouldThrow<IllegalArgumentException> {
            MapApplicationConfig("knowledge.allowedHosts" to " , ").toServerConfig()
        }
    }

    @Test
    fun `application yaml defaults to localhost hosts`() {
        ApplicationConfig("application.yaml").toServerConfig() shouldBe
            ServerConfig(allowedHosts = listOf("localhost", "127.0.0.1", "[::1]"), knowledgeDir = null, includeDrafts = false)
    }
}
