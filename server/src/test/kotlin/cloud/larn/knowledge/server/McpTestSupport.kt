package cloud.larn.knowledge.server

import cloud.larn.knowledge.application.ValidateKnowledgeBase
import cloud.larn.knowledge.application.ValidationResult
import cloud.larn.knowledge.domain.KnowledgeBase
import cloud.larn.knowledge.infrastructure.source.FileSystemKnowledgeSource
import cloud.larn.knowledge.infrastructure.source.InMemoryKnowledgeRepository
import cloud.larn.knowledge.server.config.ServerConfig
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.nio.file.Path

/** Shared setup for MCP integration tests against the fixture knowledge base in `fixtures/kb`. */
object McpTestSupport {
    val buildInfo = BuildInfo("knowledge-mcp-server", "abc123")

    val fixtureKnowledgeBase: KnowledgeBase by lazy {
        val root = Path.of(checkNotNull(McpTestSupport::class.java.getResource("/fixtures/kb")).toURI())
        when (val result = ValidateKnowledgeBase(FileSystemKnowledgeSource(root))()) {
            is ValidationResult.Valid -> result.knowledgeBase
            is ValidationResult.Invalid -> error("Fixture knowledge base is invalid:\n" + result.errors.joinToString("\n"))
        }
    }

    /** Starts the server on the fixture knowledge base and runs [block] with a connected MCP client. */
    fun mcpTest(includeDrafts: Boolean = false, block: suspend (Client) -> Unit) = testApplication {
        application {
            configureServer(
                ServerConfig(allowedHosts = listOf("localhost"), includeDrafts = includeDrafts),
                buildInfo,
                InMemoryKnowledgeRepository(fixtureKnowledgeBase),
            )
        }
        // Ktor's test engine sends no Host header, unlike real HTTP clients.
        val http = createClient {
            install(SSE)
            defaultRequest { header(HttpHeaders.Host, "localhost") }
        }
        val client = http.mcpStreamableHttp("/mcp")
        try {
            block(client)
        } finally {
            client.close()
        }
    }

    val CallToolResult.text: String get() = content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
}
