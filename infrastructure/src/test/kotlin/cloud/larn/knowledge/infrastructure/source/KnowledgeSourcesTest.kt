package cloud.larn.knowledge.infrastructure.source

import cloud.larn.knowledge.application.KnowledgeError
import cloud.larn.knowledge.domain.DocId
import cloud.larn.knowledge.domain.KnowledgeBase
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test

class KnowledgeSourcesTest {
    @TempDir
    lateinit var root: Path

    private fun write(path: String, content: String) {
        val file = root.resolve(path)
        file.parent.createDirectories()
        file.writeText(content)
    }

    private fun validDoc(id: String) = """
        ---
        id: $id
        title: $id
        kind: guideline
        owner: owner
        status: active
        summary: Summary.
        ---
        ## Section
    """.trimIndent()

    @Test
    fun `knowledge files exclude hidden files and the top-level README`() {
        KnowledgeFiles.isKnowledgeFile("org/a.md") shouldBe true
        KnowledgeFiles.isKnowledgeFile("org/README.md") shouldBe true
        KnowledgeFiles.isKnowledgeFile("README.md") shouldBe false
        KnowledgeFiles.isKnowledgeFile(".gitkeep") shouldBe false
        KnowledgeFiles.isKnowledgeFile("org/.hidden/a.md") shouldBe false
    }

    @Test
    fun `filesystem source parses every knowledge file in path order`() {
        write("teams/payments/c.md", validDoc("team-c"))
        write("org/a.md", validDoc("org-a"))
        write("languages/java/b.md", validDoc("java-b"))
        write("README.md", "# Not a knowledge file")
        write(".git/config", "ignored")

        val loaded = FileSystemKnowledgeSource(root).load()

        loaded.errors.shouldBeEmpty()
        loaded.docs.map { it.sourcePath } shouldContainExactly listOf("languages/java/b.md", "org/a.md", "teams/payments/c.md")
    }

    @Test
    fun `filesystem source collects errors from every broken file`() {
        write("org/a.md", "no front matter")
        write("misc/b.md", validDoc("misc-b"))
        write("org/c.md", validDoc("org-c"))

        val loaded = FileSystemKnowledgeSource(root).load()

        loaded.docs.map { it.id } shouldContainExactly listOf(DocId("org-c"))
        loaded.errors.map { it.path } shouldContainExactly listOf("misc/b.md", "org/a.md")
    }

    @Test
    fun `filesystem source reports a missing directory`() {
        val missing = root.resolve("missing")

        FileSystemKnowledgeSource(missing).load().errors shouldContainExactly listOf(
            KnowledgeError(missing.toString(), null, "Knowledge directory does not exist"),
        )
    }

    @Test
    fun `classpath source reads the files listed in the index`() {
        write("kb/org/a.md", validDoc("org-a"))
        write("kb/languages/java/b.md", validDoc("java-b"))
        write("kb/README.md", "# Authoring guide")
        write("kb/index.txt", "README.md\norg/a.md\n\nlanguages/java/b.md\n")

        val loaded = ClasspathKnowledgeSource("kb", classLoaderFor(root)).load()

        loaded.errors.shouldBeEmpty()
        loaded.docs.map { it.id } shouldContainExactly listOf(DocId("java-b"), DocId("org-a"))
    }

    @Test
    fun `classpath source reports a missing index and missing files`() {
        ClasspathKnowledgeSource("kb", classLoaderFor(root)).load().errors.single().message shouldContain "index not found"

        write("kb/index.txt", "org/missing.md\n")
        ClasspathKnowledgeSource("kb", classLoaderFor(root)).load().errors shouldContainExactly listOf(
            KnowledgeError("org/missing.md", null, "Listed in kb/index.txt but missing from the classpath"),
        )
    }

    @Test
    fun `repository serves the current knowledge base and can replace it`() {
        val repository = InMemoryKnowledgeRepository(KnowledgeBase.EMPTY)
        repository.current() shouldBe KnowledgeBase.EMPTY

        val replacement = KnowledgeBase(emptyList())
        repository.replace(replacement)
        repository.current() shouldBe replacement
    }

    private fun classLoaderFor(dir: Path) = URLClassLoader(arrayOf(dir.toUri().toURL()), null)
}
