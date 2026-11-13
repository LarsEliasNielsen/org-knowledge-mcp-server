package cloud.larn.knowledge.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class ScopeTest {
    private val javaPaymentsSpring = ProjectContext(team = "payments", languages = listOf("java", "kotlin"), frameworks = listOf("spring-boot"))

    @Test
    fun `everyone matches any context, including an empty one`() {
        Scope.EVERYONE.matches(ProjectContext()) shouldBe true
        Scope.EVERYONE.matches(javaPaymentsSpring) shouldBe true
    }

    @Test
    fun `language dimension matches when any context language is listed`() {
        Scope(languages = setOf("java")).matches(javaPaymentsSpring) shouldBe true
        Scope(languages = setOf("go", "kotlin")).matches(javaPaymentsSpring) shouldBe true
        Scope(languages = setOf("go")).matches(javaPaymentsSpring) shouldBe false
        Scope(languages = setOf("java")).matches(ProjectContext()) shouldBe false
    }

    @Test
    fun `team dimension matches the context team`() {
        Scope(teams = setOf("payments", "identity")).matches(javaPaymentsSpring) shouldBe true
        Scope(teams = setOf("identity")).matches(javaPaymentsSpring) shouldBe false
        Scope(teams = setOf("payments")).matches(ProjectContext(languages = listOf("java"))) shouldBe false
    }

    @Test
    fun `framework dimension matches when any context framework is listed`() {
        Scope(frameworks = setOf("spring-boot")).matches(javaPaymentsSpring) shouldBe true
        Scope(frameworks = setOf("ktor")).matches(javaPaymentsSpring) shouldBe false
    }

    @Test
    fun `dimensions are ANDed`() {
        val scope = Scope(languages = setOf("java"), frameworks = setOf("spring-boot"))

        scope.matches(javaPaymentsSpring) shouldBe true
        scope.matches(javaPaymentsSpring.copy(frameworks = listOf("ktor"))) shouldBe false
        scope.matches(javaPaymentsSpring.copy(languages = listOf("go"))) shouldBe false
        Scope(languages = setOf("java"), teams = setOf("payments"), frameworks = setOf("spring-boot"))
            .matches(javaPaymentsSpring.copy(team = "identity")) shouldBe false
    }

    @Test
    fun `rejects invalid scope values`() {
        shouldThrow<IllegalArgumentException> { Scope(languages = setOf("Java")) }
        shouldThrow<IllegalArgumentException> { Scope(teams = setOf("")) }
        shouldThrow<IllegalArgumentException> { Scope(frameworks = setOf("spring boot")) }
    }

    @Test
    fun `refining with an empty scope keeps the default`() {
        val default = Scope(languages = setOf("java"))

        default.refineWith(Scope.EVERYONE) shouldBe ScopeRefinement.Narrowed(default)
    }

    @Test
    fun `refining narrows a restricted dimension to a subset`() {
        Scope(languages = setOf("java", "kotlin")).refineWith(Scope(languages = setOf("kotlin"))) shouldBe
            ScopeRefinement.Narrowed(Scope(languages = setOf("kotlin")))
    }

    @Test
    fun `refining restricts an unrestricted dimension and inherits the rest`() {
        Scope(languages = setOf("java")).refineWith(Scope(frameworks = setOf("spring-boot"), teams = setOf("payments"))) shouldBe
            ScopeRefinement.Narrowed(Scope(languages = setOf("java"), teams = setOf("payments"), frameworks = setOf("spring-boot")))
        Scope.EVERYONE.refineWith(Scope(languages = setOf("go"))) shouldBe
            ScopeRefinement.Narrowed(Scope(languages = setOf("go")))
    }

    @Test
    fun `refining with values outside the default is a widening`() {
        val result = Scope(languages = setOf("java"), teams = setOf("payments"))
            .refineWith(Scope(languages = setOf("java", "go"), teams = setOf("identity")))

        result.shouldBeInstanceOf<ScopeRefinement.Widened>().violations shouldBe listOf(
            ScopeWidening(ScopeDimension.LANGUAGES, outside = setOf("go"), allowed = setOf("java")),
            ScopeWidening(ScopeDimension.TEAMS, outside = setOf("identity"), allowed = setOf("payments")),
        )
    }
}
