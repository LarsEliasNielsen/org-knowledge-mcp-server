package cloud.larn.knowledge.server

import cloud.larn.knowledge.application.GetGuidelines
import cloud.larn.knowledge.application.ValidateKnowledgeBase
import cloud.larn.knowledge.application.ValidationResult
import cloud.larn.knowledge.domain.OverrideAction
import cloud.larn.knowledge.infrastructure.parsing.ManifestResult
import cloud.larn.knowledge.infrastructure.parsing.ProjectManifestParser
import cloud.larn.knowledge.infrastructure.source.ClasspathKnowledgeSource
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test

/**
 * Keeps examples/sample-project in sync with the bundled knowledge/ folder: its manifest must resolve without
 * warnings, and every project doc it names must exist.
 */
class SampleProjectTest {
    private val projectDir = Path.of(checkNotNull(System.getProperty("sampleProject")) { "sampleProject system property not set" })

    private val context = ProjectManifestParser.parse(".knowledge.yaml", projectDir.resolve(".knowledge.yaml").readText())
        .shouldBeInstanceOf<ManifestResult.Success>().context

    private val guidelines = run {
        val knowledgeBase = ValidateKnowledgeBase(ClasspathKnowledgeSource())().shouldBeInstanceOf<ValidationResult.Valid>().knowledgeBase
        GetGuidelines({ knowledgeBase })(context)
    }

    @Test
    fun `manifest resolves without warnings`() {
        guidelines.warnings.shouldBeEmpty()
    }

    @Test
    fun `replaced and extended rules are in effect and their project docs exist`() {
        val marked = guidelines.rules.mapNotNull { rule -> rule.projectOverride?.let { rule.rule.id to it } }.toMap()

        marked.keys shouldContainAll context.overrides.filter { it.action != OverrideAction.WAIVE }.map { it.rule }
        marked.values.forEach { override -> projectDir.resolve(override.doc!!).exists() shouldBe true }
    }

    @Test
    fun `CLAUDE md tells the agent to call get_guidelines with the manifest`() {
        val instructions = projectDir.resolve("CLAUDE.md").readText()

        listOf(".knowledge.yaml", "get_guidelines", "docs/knowledge/").forEach { (it in instructions) shouldBe true }
    }
}
