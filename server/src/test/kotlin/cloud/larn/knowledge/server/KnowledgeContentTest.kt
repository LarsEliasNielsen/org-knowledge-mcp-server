package cloud.larn.knowledge.server

import cloud.larn.knowledge.application.ValidateKnowledgeBase
import cloud.larn.knowledge.application.ValidationResult
import cloud.larn.knowledge.infrastructure.source.ClasspathKnowledgeSource
import io.kotest.matchers.collections.shouldNotBeEmpty
import kotlin.test.Test
import kotlin.test.fail

/**
 * Gate for the repo's knowledge/ folder: validates the copy bundled into the server, exactly as it ships.
 * Fails the build with every error listed.
 */
class KnowledgeContentTest {
    @Test
    fun `bundled knowledge base is valid`() {
        when (val result = ValidateKnowledgeBase(ClasspathKnowledgeSource())()) {
            is ValidationResult.Valid -> result.knowledgeBase.docs.shouldNotBeEmpty()
            is ValidationResult.Invalid -> fail(
                "knowledge/ has ${result.errors.size} error(s):\n" + result.errors.joinToString("\n") { "  $it" },
            )
        }
    }
}
