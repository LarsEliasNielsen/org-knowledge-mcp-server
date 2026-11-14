package cloud.larn.knowledge.application

import cloud.larn.knowledge.domain.DocId
import cloud.larn.knowledge.domain.DocStatus
import cloud.larn.knowledge.domain.Kind
import cloud.larn.knowledge.domain.KnowledgeBase
import cloud.larn.knowledge.domain.KnowledgeDoc
import cloud.larn.knowledge.domain.Layer
import cloud.larn.knowledge.domain.OverrideAction
import cloud.larn.knowledge.domain.ProjectContext
import cloud.larn.knowledge.domain.ProjectOverride
import cloud.larn.knowledge.domain.Rule
import cloud.larn.knowledge.domain.RuleId
import cloud.larn.knowledge.domain.RuleStatus
import cloud.larn.knowledge.domain.Scope
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class GetGuidelinesTest {
    private val docs = listOf(
        // Listed out of id order on purpose: output order must not depend on input order.
        doc("org-b", Layer.ORG, rules = listOf(rule("ORG-B-001"))),
        doc("org-a", Layer.ORG, rules = listOf(rule("ORG-A-002"), rule("ORG-A-001"))),
        doc("org-adr", Layer.ORG, kind = Kind.ADR, rules = listOf(rule("ORG-ADR-001"))),
        doc("org-old", Layer.ORG, status = DocStatus.DEPRECATED, rules = listOf(rule("ORG-OLD-001"))),
        doc(
            "java-a",
            Layer.LANGUAGE,
            Scope(languages = setOf("java")),
            rules = listOf(
                rule("JAVA-A-001"),
                rule("JAVA-A-002", status = RuleStatus.DEPRECATED, supersededBy = "JAVA-A-003"),
                rule("JAVA-A-003"),
            ),
        ),
        doc("java-spring", Layer.LANGUAGE, Scope(languages = setOf("java"), frameworks = setOf("spring-boot")), rules = listOf(rule("JAVA-SPR-001"))),
        doc("kotlin-a", Layer.LANGUAGE, Scope(languages = setOf("kotlin")), rules = listOf(rule("KT-A-001"))),
        doc(
            "pay-a",
            Layer.TEAM,
            Scope(teams = setOf("payments"), languages = setOf("java")),
            rules = listOf(rule("PAY-A-001", overrides = listOf("JAVA-A-003"))),
        ),
        doc("pay-draft", Layer.TEAM, Scope(teams = setOf("payments")), status = DocStatus.DRAFT, rules = listOf(rule("PAY-D-001"))),
        doc("id-a", Layer.TEAM, Scope(teams = setOf("identity")), rules = listOf(rule("ID-A-001"))),
    )

    private val orgRules = listOf("ORG-A-002", "ORG-A-001", "ORG-ADR-001", "ORG-B-001")

    private fun resolve(context: ProjectContext, knowledge: List<KnowledgeDoc> = docs, includeDrafts: Boolean = false) =
        GetGuidelines({ KnowledgeBase(knowledge) }, Visibility(includeDrafts))(context)

    private fun Guidelines.ids() = rules.map { it.rule.id.value }

    // --- Layers and ordering ---

    @Test
    fun `an empty context gets the org layer only, ordered by doc id then position`() {
        val guidelines = resolve(ProjectContext())

        guidelines.ids() shouldContainExactly orgRules
        guidelines.warnings.shouldBeEmpty()
    }

    @Test
    fun `deprecated documents are never served`() {
        ("ORG-OLD-001" in resolve(ProjectContext(team = "payments", languages = listOf("java"))).ids()) shouldBe false
    }

    @Test
    fun `rules from every document kind are served and carry their kind`() {
        val knowledge = Kind.entries.mapIndexed { index, kind ->
            doc("org-${kind.name.lowercase()}", Layer.ORG, kind = kind, rules = listOf(rule("ORG-K-00$index")))
        }

        val rules = resolve(ProjectContext(), knowledge).rules

        rules.map { it.kind }.toSet() shouldBe Kind.entries.toSet()
        rules.single { it.doc.id == DocId("org-adr") }.kind shouldBe Kind.ADR
    }

    @Test
    fun `a language adds its layer after org and drops deprecated rules`() {
        val guidelines = resolve(ProjectContext(languages = listOf("java")))

        guidelines.ids() shouldContainExactly orgRules + listOf("JAVA-A-001", "JAVA-A-003")
        guidelines.rules.last().language shouldBe "java"
        guidelines.rules.first().language.shouldBeNull()
    }

    @Test
    fun `framework-scoped docs apply only when the framework is requested`() {
        resolve(ProjectContext(languages = listOf("java"), frameworks = listOf("spring-boot"))).ids() shouldContainExactly
            orgRules + listOf("JAVA-A-001", "JAVA-A-003", "JAVA-SPR-001")
        resolve(ProjectContext(frameworks = listOf("spring-boot"))).ids() shouldContainExactly orgRules
    }

    @Test
    fun `the language layer follows the order of requested languages`() {
        resolve(ProjectContext(languages = listOf("kotlin", "java"))).ids() shouldContainExactly
            orgRules + listOf("KT-A-001", "JAVA-A-001", "JAVA-A-003")
        resolve(ProjectContext(languages = listOf("java", "kotlin"))).ids() shouldContainExactly
            orgRules + listOf("JAVA-A-001", "JAVA-A-003", "KT-A-001")
    }

    @Test
    fun `a team adds its layer last`() {
        val guidelines = resolve(ProjectContext(team = "identity", languages = listOf("java")))

        guidelines.ids() shouldContainExactly orgRules + listOf("JAVA-A-001", "JAVA-A-003", "ID-A-001")
        guidelines.rules.map { it.layer }.distinct() shouldContainExactly listOf(Layer.ORG, Layer.LANGUAGE, Layer.TEAM)
    }

    // --- Team overrides ---

    @Test
    fun `a team rule replaces the language rule it overrides`() {
        val guidelines = resolve(ProjectContext(team = "payments", languages = listOf("java")))

        guidelines.ids() shouldContainExactly orgRules + listOf("JAVA-A-001", "PAY-A-001")
        guidelines.rules.single { it.rule.id == RuleId("PAY-A-001") }.replaces shouldContainExactly listOf(RuleId("JAVA-A-003"))
        guidelines.rules.filter { it.rule.id != RuleId("PAY-A-001") }.all { it.replaces.isEmpty() } shouldBe true
    }

    @Test
    fun `a team override applies only to that team and its scope`() {
        resolve(ProjectContext(team = "identity", languages = listOf("java"))).ids().contains("JAVA-A-003") shouldBe true
        // pay-a is scoped to java, so a payments project in kotlin gets neither the team rule nor the language rule
        resolve(ProjectContext(team = "payments", languages = listOf("kotlin"))).ids() shouldContainExactly orgRules + "KT-A-001"
    }

    @Test
    fun `overrides chain across layers`() {
        val knowledge = listOf(
            doc("org-a", Layer.ORG, rules = listOf(rule("ORG-A-001"))),
            doc("java-a", Layer.LANGUAGE, Scope(languages = setOf("java")), rules = listOf(rule("JAVA-A-001", overrides = listOf("ORG-A-001")))),
            doc("pay-a", Layer.TEAM, Scope(teams = setOf("payments")), rules = listOf(rule("PAY-A-001", overrides = listOf("JAVA-A-001")))),
        )

        resolve(ProjectContext(team = "payments", languages = listOf("java")), knowledge).ids() shouldContainExactly listOf("PAY-A-001")
        resolve(ProjectContext(languages = listOf("java")), knowledge).ids() shouldContainExactly listOf("JAVA-A-001")
    }

    @Test
    fun `a deprecated overriding rule does not drop its target`() {
        val knowledge = listOf(
            doc("org-a", Layer.ORG, rules = listOf(rule("ORG-A-001"))),
            doc(
                "pay-a",
                Layer.TEAM,
                Scope(teams = setOf("payments")),
                rules = listOf(rule("PAY-A-001", status = RuleStatus.DEPRECATED, overrides = listOf("ORG-A-001"))),
            ),
        )

        resolve(ProjectContext(team = "payments"), knowledge).ids() shouldContainExactly listOf("ORG-A-001")
    }

    // --- Drafts ---

    @Test
    fun `draft docs are served only when drafts are visible`() {
        val context = ProjectContext(team = "payments")

        resolve(context).ids().contains("PAY-D-001") shouldBe false
        resolve(context, includeDrafts = true).ids().contains("PAY-D-001") shouldBe true
    }

    // --- Project overrides ---

    @Test
    fun `project overrides waive, replace and extend shared rules`() {
        val replace = ProjectOverride(RuleId("ORG-A-001"), OverrideAction.REPLACE, doc = "docs/knowledge/a.md", reason = "Legacy")
        val extend = ProjectOverride(RuleId("JAVA-A-003"), OverrideAction.EXTEND, doc = "docs/knowledge/b.md")
        val context = ProjectContext(
            languages = listOf("java"),
            overrides = listOf(ProjectOverride(RuleId("JAVA-A-001"), OverrideAction.WAIVE, reason = "Not relevant"), replace, extend),
        )

        val guidelines = resolve(context)

        guidelines.ids() shouldContainExactly listOf("ORG-A-002", "ORG-A-001", "ORG-ADR-001", "ORG-B-001", "JAVA-A-003")
        with(guidelines.rules.single { it.rule.id == RuleId("ORG-A-001") }) {
            projectOverride shouldBe replace
            isReplacedByProject shouldBe true
            isExtendedByProject shouldBe false
        }
        with(guidelines.rules.single { it.rule.id == RuleId("JAVA-A-003") }) {
            projectOverride shouldBe extend
            isExtendedByProject shouldBe true
        }
        guidelines.rules.single { it.rule.id == RuleId("ORG-B-001") }.projectOverride.shouldBeNull()
        guidelines.warnings.shouldBeEmpty()
    }

    @Test
    fun `waiving a team rule does not bring back the rule it replaced`() {
        val context = ProjectContext(
            team = "payments",
            languages = listOf("java"),
            overrides = listOf(ProjectOverride(RuleId("PAY-A-001"), OverrideAction.WAIVE)),
        )

        resolve(context).ids() shouldContainExactly orgRules + "JAVA-A-001"
    }

    // --- Warnings ---

    @Test
    fun `warns about overrides that do not apply, in manifest order`() {
        fun waive(id: String) = ProjectOverride(RuleId(id), OverrideAction.WAIVE)
        val context = ProjectContext(
            team = "payments",
            languages = listOf("java"),
            overrides = listOf(
                waive("XX-YY-001"),
                waive("JAVA-A-002"),
                waive("KT-A-001"),
                waive("JAVA-A-003"),
                waive("PAY-D-001"),
                waive("ORG-OLD-001"),
            ),
        )

        resolve(context).warnings shouldContainExactly listOf(
            GuidelineWarning.UnknownRule(RuleId("XX-YY-001")),
            GuidelineWarning.DeprecatedRule(RuleId("JAVA-A-002"), RuleId("JAVA-A-003"), successorInEffect = RuleId("PAY-A-001")),
            GuidelineWarning.RuleNotInEffect(RuleId("KT-A-001"), NotInEffectReason.OutOfScope),
            GuidelineWarning.RuleNotInEffect(RuleId("JAVA-A-003"), NotInEffectReason.OverriddenBy(RuleId("PAY-A-001"))),
            GuidelineWarning.RuleNotInEffect(RuleId("PAY-D-001"), NotInEffectReason.DocumentNotServed(DocStatus.DRAFT)),
            GuidelineWarning.RuleNotInEffect(RuleId("ORG-OLD-001"), NotInEffectReason.DocumentNotServed(DocStatus.DEPRECATED)),
        )
    }

    @Test
    fun `a deprecation warning points at the successor that applies to the project`() {
        fun warning(context: ProjectContext) =
            resolve(context.copy(overrides = listOf(ProjectOverride(RuleId("JAVA-A-002"), OverrideAction.WAIVE)))).warnings.single()

        // JAVA-A-002 is superseded by JAVA-A-003, which applies as is to a plain Java project...
        warning(ProjectContext(languages = listOf("java"))) shouldBe
            GuidelineWarning.DeprecatedRule(RuleId("JAVA-A-002"), RuleId("JAVA-A-003"), successorInEffect = RuleId("JAVA-A-003"))
        // ...but PAY-A-001 replaces JAVA-A-003 for payments, so that is the rule to target.
        warning(ProjectContext(team = "payments", languages = listOf("java"))) shouldBe
            GuidelineWarning.DeprecatedRule(RuleId("JAVA-A-002"), RuleId("JAVA-A-003"), successorInEffect = RuleId("PAY-A-001"))
    }

    @Test
    fun `successors are followed through further deprecations and stop where nothing applies`() {
        val knowledge = listOf(
            doc(
                "org-a",
                Layer.ORG,
                rules = listOf(
                    rule("ORG-A-001", status = RuleStatus.DEPRECATED, supersededBy = "ORG-A-002"),
                    rule("ORG-A-002", status = RuleStatus.DEPRECATED, supersededBy = "ORG-A-003"),
                    rule("ORG-A-003"),
                    rule("ORG-A-004", status = RuleStatus.DEPRECATED, supersededBy = "KT-A-001"),
                ),
            ),
            doc("kotlin-a", Layer.LANGUAGE, Scope(languages = setOf("kotlin")), rules = listOf(rule("KT-A-001"))),
        )
        val context = ProjectContext(
            languages = listOf("java"),
            overrides = listOf(ProjectOverride(RuleId("ORG-A-001"), OverrideAction.WAIVE), ProjectOverride(RuleId("ORG-A-004"), OverrideAction.WAIVE)),
        )

        resolve(context, knowledge).warnings shouldContainExactly listOf(
            GuidelineWarning.DeprecatedRule(RuleId("ORG-A-001"), RuleId("ORG-A-002"), successorInEffect = RuleId("ORG-A-003")),
            GuidelineWarning.DeprecatedRule(RuleId("ORG-A-004"), RuleId("KT-A-001"), successorInEffect = null),
        )
    }

    @Test
    fun `a draft rule override has no warning when drafts are visible`() {
        val context = ProjectContext(team = "payments", overrides = listOf(ProjectOverride(RuleId("PAY-D-001"), OverrideAction.WAIVE)))

        resolve(context, includeDrafts = true).warnings.shouldBeEmpty()
    }

    @Test
    fun `reads the current knowledge base on every call`() {
        var current = KnowledgeBase(emptyList())
        val getGuidelines = GetGuidelines({ current })

        getGuidelines(ProjectContext()).rules.shouldBeEmpty()
        current = KnowledgeBase(docs)
        getGuidelines(ProjectContext()).ids() shouldContainExactly orgRules
    }

    private fun doc(
        id: String,
        layer: Layer,
        scope: Scope = Scope.EVERYONE,
        kind: Kind = Kind.GUIDELINE,
        status: DocStatus = DocStatus.ACTIVE,
        rules: List<Rule>,
    ) = KnowledgeDoc(
        id = DocId(id),
        title = id,
        kind = kind,
        layer = layer,
        scope = scope,
        owner = "owner",
        status = status,
        summary = "Summary of $id.",
        rules = rules,
        sourcePath = "$id.md",
    )

    private fun rule(
        id: String,
        status: RuleStatus = RuleStatus.ACTIVE,
        supersededBy: String? = null,
        overrides: List<String> = emptyList(),
    ) = Rule(
        id = RuleId(id),
        title = "Rule $id",
        body = "Body of $id.",
        status = status,
        supersededBy = supersededBy?.let(::RuleId),
        overrides = overrides.map(::RuleId),
    )
}
