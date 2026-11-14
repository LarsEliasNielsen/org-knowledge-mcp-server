package cloud.larn.knowledge.server

import cloud.larn.knowledge.domain.KnowledgeBase
import cloud.larn.knowledge.infrastructure.source.InMemoryKnowledgeRepository
import cloud.larn.knowledge.server.config.ServerConfig
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

class McpEndpointTest {
    private val buildInfo = BuildInfo("knowledge-mcp-server", "abc123")

    private fun serverTest(
        allowedHosts: List<String> = listOf("localhost"),
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            configureServer(ServerConfig(allowedHosts), buildInfo, InMemoryKnowledgeRepository(KnowledgeBase.EMPTY))
        }
        block()
    }

    // Ktor's test engine sends no Host header, unlike real HTTP clients, so tests set it explicitly.
    private fun ApplicationTestBuilder.clientFor(host: String): HttpClient = createClient {
        install(SSE)
        defaultRequest { header(HttpHeaders.Host, host) }
    }

    private suspend fun ApplicationTestBuilder.connect(host: String = "localhost"): Client =
        clientFor(host).mcpStreamableHttp("/mcp")

    @Test
    fun `health reports status and version`() = serverTest {
        val response = clientFor("localhost").get("/health")

        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldBe """{"status":"UP","version":"abc123"}"""
    }

    @Test
    fun `initialize reports server name and version`() = serverTest {
        val mcp = connect()

        mcp.serverVersion?.name shouldBe "knowledge-mcp-server"
        mcp.serverVersion?.version shouldBe "abc123"
        mcp.close()
    }

    @Test
    fun `lists the server_info tool as read-only`() = serverTest {
        val mcp = connect()

        val serverInfo = mcp.listTools().tools.single { it.name == "server_info" }
        serverInfo.annotations?.readOnlyHint shouldBe true
        mcp.close()
    }

    @Test
    fun `server_info returns the version`() = serverTest {
        val mcp = connect()

        val result = mcp.callTool("server_info", emptyMap())
        result.content shouldBe listOf(TextContent("knowledge-mcp-server abc123: 0 documents, 0 rules"))
        result.structuredContent?.get("version")?.jsonPrimitive?.content shouldBe "abc123"
        mcp.close()
    }

    @Test
    fun `accepts a configured non-localhost host`() = serverTest(allowedHosts = listOf("knowledge.internal")) {
        val mcp = connect("knowledge.internal")

        mcp.serverVersion?.version shouldBe "abc123"
        mcp.close()
    }

    @Test
    fun `rejects a host outside allowedHosts`() = serverTest {
        val response = clientFor("evil.example").post("/mcp") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "application/json, text/event-stream")
            setBody(INITIALIZE_REQUEST)
        }

        response.status shouldBe HttpStatusCode.Forbidden
        response.bodyAsText() shouldContain "Invalid Host"
    }

    @Test
    fun `GET on mcp is not a health check`() = serverTest {
        clientFor("localhost").get("/mcp").status shouldBe HttpStatusCode.MethodNotAllowed
    }

    private companion object {
        const val INITIALIZE_REQUEST =
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25",""" +
                """"capabilities":{},"clientInfo":{"name":"test","version":"1"}}}"""
    }
}
