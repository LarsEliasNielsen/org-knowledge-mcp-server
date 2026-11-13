package cloud.larn.knowledge.server

import cloud.larn.knowledge.application.KnowledgeError
import cloud.larn.knowledge.application.ValidateKnowledgeBase
import cloud.larn.knowledge.application.ValidationResult
import cloud.larn.knowledge.domain.KnowledgeBase
import cloud.larn.knowledge.infrastructure.source.ClasspathKnowledgeSource
import cloud.larn.knowledge.infrastructure.source.FileSystemKnowledgeSource
import cloud.larn.knowledge.server.config.ServerConfig
import org.slf4j.Logger

class InvalidKnowledgeBaseException(val errors: List<KnowledgeError>) :
    RuntimeException("Knowledge base has ${errors.size} error(s):\n" + errors.joinToString("\n") { "  $it" })

/** Loads and validates the knowledge base at startup. Logs every error and throws, so startup fails, if invalid. */
fun loadKnowledgeBase(config: ServerConfig, log: Logger): KnowledgeBase {
    val (source, description) = config.knowledgeDir
        ?.let { FileSystemKnowledgeSource(it) to "directory ${it.toAbsolutePath()}" }
        ?: (ClasspathKnowledgeSource() to "bundled classpath copy")

    return when (val result = ValidateKnowledgeBase(source)()) {
        is ValidationResult.Valid -> result.knowledgeBase.also { kb ->
            log.info("Loaded ${kb.docs.size} knowledge documents (${kb.docs.sumOf { it.rules.size }} rules) from $description")
        }
        is ValidationResult.Invalid -> {
            log.error("Knowledge base from $description has ${result.errors.size} error(s)")
            result.errors.forEach { log.error("  {}", it) }
            throw InvalidKnowledgeBaseException(result.errors)
        }
    }
}
