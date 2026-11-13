package cloud.larn.knowledge.server

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import kotlin.test.Test

/** Runs the real [module] entry point against fixture knowledge directories. */
class StartupTest {
    private fun fixture(name: String): String =
        Path.of(checkNotNull(javaClass.getResource("/fixtures/$name")).toURI()).toString()

    private fun config(knowledgeDir: String? = null) = MapApplicationConfig("knowledge.allowedHosts" to "localhost").apply {
        knowledgeDir?.let { put("knowledge.dir", it) }
    }

    @Test
    fun `starts from the bundled knowledge base`() = testApplication {
        environment { config = config() }
        application { module() }

        client.get("/health") { header(HttpHeaders.Host, "localhost") }.status shouldBe HttpStatusCode.OK
    }

    @Test
    fun `starts from a knowledge directory`() = testApplication {
        environment { config = config(fixture("valid")) }
        application { module() }

        client.get("/health") { header(HttpHeaders.Host, "localhost") }.status shouldBe HttpStatusCode.OK
    }

    @Test
    fun `fails startup on an invalid knowledge base and lists every error`() {
        val thrown = shouldThrowAny {
            testApplication {
                environment { config = config(fixture("invalid")) }
                application { module() }
                startApplication()
            }
        }

        val cause = generateSequence(thrown) { it.cause }.firstOrNull { it is InvalidKnowledgeBaseException }
        cause.shouldBeInstanceOf<InvalidKnowledgeBaseException>().errors.map { "${it.path}: ${it.message}" } shouldContainExactly
            listOf(
                "org/a.md: Duplicate doc id 'org-dup' (also in org/b.md)",
                "org/a.md: Related doc 'missing-doc' does not exist",
                "org/b.md: Duplicate doc id 'org-dup' (also in org/a.md)",
                "org/c.md: Invalid kind 'howto': expected one of guideline, architecture, adr, library, service",
            )
    }
}
