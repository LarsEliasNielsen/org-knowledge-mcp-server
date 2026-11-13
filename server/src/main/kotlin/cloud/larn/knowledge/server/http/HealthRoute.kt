package cloud.larn.knowledge.server.http

import cloud.larn.knowledge.server.BuildInfo
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun Route.healthRoute(buildInfo: BuildInfo) {
    val body = buildJsonObject {
        put("status", "UP")
        put("version", buildInfo.version)
    }.toString()

    get("/health") {
        call.respondText(body, ContentType.Application.Json)
    }
}
