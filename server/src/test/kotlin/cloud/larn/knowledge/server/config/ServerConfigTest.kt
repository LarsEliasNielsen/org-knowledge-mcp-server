package cloud.larn.knowledge.server.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test

class ServerConfigTest {
    @Test
    fun `splits and trims allowed hosts`() {
        MapApplicationConfig("knowledge.allowedHosts" to " knowledge.internal , localhost,, ")
            .toServerConfig() shouldBe ServerConfig(allowedHosts = listOf("knowledge.internal", "localhost"))
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
            ServerConfig(allowedHosts = listOf("localhost", "127.0.0.1", "[::1]"))
    }
}
