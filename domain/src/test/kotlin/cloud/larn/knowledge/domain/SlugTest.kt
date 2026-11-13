package cloud.larn.knowledge.domain

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SlugTest {
    private fun slug(heading: String) = Slug.of(heading).value

    @Test
    fun `lowercases and hyphenates words`() {
        slug("Logging and context") shouldBe "logging-and-context"
    }

    @Test
    fun `drops punctuation`() {
        slug("What's new?") shouldBe "whats-new"
        slug("Step 1: setup (local)") shouldBe "step-1-setup-local"
        slug("API v2.0") shouldBe "api-v20"
    }

    @Test
    fun `keeps hyphens, underscores and digits`() {
        slug("snake_case and kebab-case 42") shouldBe "snake_case-and-kebab-case-42"
    }

    @Test
    fun `keeps unicode letters and turns each space into a hyphen like GitHub`() {
        slug("Ærø & Café") shouldBe "ærø--café"
    }

    @Test
    fun `trims surrounding whitespace`() {
        slug("  Errors  ") shouldBe "errors"
    }
}
