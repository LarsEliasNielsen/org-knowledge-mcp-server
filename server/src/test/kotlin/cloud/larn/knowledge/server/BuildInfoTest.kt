package cloud.larn.knowledge.server

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import java.util.Properties
import kotlin.test.Test

class BuildInfoTest {
    @Test
    fun `reads version from properties`() {
        BuildInfo.fromProperties(Properties().apply { setProperty("version", " abc123 ") }) shouldBe
            BuildInfo("knowledge-mcp-server", "abc123")
    }

    @Test
    fun `falls back to dev when the resource is missing`() {
        BuildInfo.fromProperties(null).version shouldBe "dev"
    }

    @Test
    fun `falls back to dev when the version is blank or absent`() {
        BuildInfo.fromProperties(Properties().apply { setProperty("version", "  ") }).version shouldBe "dev"
        BuildInfo.fromProperties(Properties()).version shouldBe "dev"
    }

    @Test
    fun `load finds the generated build-info resource`() {
        BuildInfo.load().version.shouldNotBeBlank()
    }
}
