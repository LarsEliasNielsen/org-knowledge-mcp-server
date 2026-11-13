package cloud.larn.knowledge.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class KnowledgeBaseTest {
    private fun doc(id: String, vararg ruleIds: String) = KnowledgeDoc(
        id = DocId(id),
        title = id,
        kind = Kind.GUIDELINE,
        layer = Layer.ORG,
        scope = Scope.EVERYONE,
        owner = "owner",
        status = DocStatus.ACTIVE,
        summary = "Summary.",
        rules = ruleIds.map { Rule(RuleId(it), "Rule $it", "") },
        sourcePath = "org/$id.md",
    )

    @Test
    fun `sorts docs by id and looks up docs and rules`() {
        val b = doc("org-b", "ORG-B-001")
        val a = doc("org-a", "ORG-A-001", "ORG-A-002")
        val kb = KnowledgeBase(listOf(b, a))

        kb.docs shouldContainExactly listOf(a, b)
        kb.doc(DocId("org-b")) shouldBe b
        kb.doc(DocId("org-c")).shouldBeNull()
        kb.rule(RuleId("ORG-A-002")) shouldBe RuleRef(a.rules[1], a)
        kb.rule(RuleId("ORG-X-001")).shouldBeNull()
    }

    @Test
    fun `rejects duplicate doc and rule ids`() {
        shouldThrow<IllegalArgumentException> { KnowledgeBase(listOf(doc("org-a"), doc("org-a"))) }
        shouldThrow<IllegalArgumentException> { KnowledgeBase(listOf(doc("org-a", "ORG-A-001"), doc("org-b", "ORG-A-001"))) }
    }
}
