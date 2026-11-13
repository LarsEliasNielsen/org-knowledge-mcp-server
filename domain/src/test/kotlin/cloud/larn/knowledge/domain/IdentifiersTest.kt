package cloud.larn.knowledge.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class IdentifiersTest {
    @Test
    fun `accepts valid rule ids`() {
        listOf("JAVA-ERR-003", "A-000", "ORG-SEC-API-120").forEach { RuleId(it).value shouldBe it }
    }

    @Test
    fun `rejects malformed rule ids`() {
        listOf("java-err-003", "JAVA-ERR-03", "JAVA-ERR-0003", "JAVA--ERR-003", "-ERR-003", "JAVA-ERR", "003", "JAVA_ERR-003", "")
            .forEach { id ->
                RuleId.parseOrNull(id).shouldBeNull()
                shouldThrow<IllegalArgumentException> { RuleId(id) }
            }
    }

    @Test
    fun `rule ids sort by value`() {
        listOf(RuleId("JAVA-ERR-010"), RuleId("JAVA-ERR-002"), RuleId("GO-ERR-001")).sorted() shouldContainExactly
            listOf(RuleId("GO-ERR-001"), RuleId("JAVA-ERR-002"), RuleId("JAVA-ERR-010"))
    }

    @Test
    fun `accepts kebab-case doc ids`() {
        listOf("java-error-handling", "adr-0012-idempotency-keys", "x").forEach { DocId(it).value shouldBe it }
    }

    @Test
    fun `rejects non-kebab doc ids`() {
        listOf("Java-error", "java_error", "java--error", "-java", "java-", "java error", "").forEach { id ->
            DocId.parseOrNull(id).shouldBeNull()
            shouldThrow<IllegalArgumentException> { DocId(id) }
        }
    }

    @Test
    fun `scope values are lowercase identifiers`() {
        listOf("java", "spring-boot", "c++", "c#", "node.js", "team_a", "k8s").forEach { ScopeValue.isValid(it) shouldBe true }
        listOf("Java", "", "-java", "spring boot", "ja/va").forEach { ScopeValue.isValid(it) shouldBe false }
    }
}
