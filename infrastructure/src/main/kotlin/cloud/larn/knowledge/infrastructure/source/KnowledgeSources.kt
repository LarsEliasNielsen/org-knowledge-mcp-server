package cloud.larn.knowledge.infrastructure.source

import cloud.larn.knowledge.application.KnowledgeError
import cloud.larn.knowledge.application.KnowledgeSource
import cloud.larn.knowledge.application.SourceLoad
import cloud.larn.knowledge.domain.KnowledgeDoc
import cloud.larn.knowledge.infrastructure.parsing.KnowledgePath
import cloud.larn.knowledge.infrastructure.parsing.MarkdownDocParser
import cloud.larn.knowledge.infrastructure.parsing.ParseResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.streams.asSequence

/** Which files under the knowledge root are knowledge files. Shared by every source and the build's bundling. */
object KnowledgeFiles {
    /** Every non-hidden file except a top-level README.md, which documents the folder for authors. */
    fun isKnowledgeFile(relativePath: String): Boolean {
        val segments = KnowledgePath.normalize(relativePath).split('/')
        return segments.none { it.startsWith(".") } && segments != listOf("README.md")
    }

    internal fun parseAll(parser: MarkdownDocParser, files: Sequence<Pair<String, () -> String>>): SourceLoad {
        val docs = mutableListOf<KnowledgeDoc>()
        val errors = mutableListOf<KnowledgeError>()
        files.filter { (path, _) -> isKnowledgeFile(path) }.sortedBy { it.first }.forEach { (path, read) ->
            when (val result = parser.parse(path, read())) {
                is ParseResult.Success -> docs += result.doc
                is ParseResult.Failure -> errors += result.errors
            }
        }
        return SourceLoad(docs, errors)
    }
}

/** Reads knowledge files from a directory, e.g. the repo's `knowledge/` folder while editing locally. */
class FileSystemKnowledgeSource(
    private val root: Path,
    private val parser: MarkdownDocParser = MarkdownDocParser(),
) : KnowledgeSource {
    override fun load(): SourceLoad {
        if (!root.isDirectory()) {
            return SourceLoad(emptyList(), listOf(KnowledgeError(root.toString(), null, "Knowledge directory does not exist")))
        }
        val files = Files.walk(root).use { paths ->
            paths.asSequence().filter { it.isRegularFile() }.toList()
        }
        return KnowledgeFiles.parseAll(
            parser,
            files.asSequence().map { file -> root.relativize(file).invariantSeparatorsPathString to { file.readText() } },
        )
    }
}

/**
 * Reads knowledge files bundled as classpath resources under [root]. Classpath directories can't be listed
 * inside a JAR, so the build writes `<root>/index.txt` with one relative path per line.
 */
class ClasspathKnowledgeSource(
    private val root: String = "knowledge",
    private val classLoader: ClassLoader = ClasspathKnowledgeSource::class.java.classLoader,
    private val parser: MarkdownDocParser = MarkdownDocParser(),
) : KnowledgeSource {
    override fun load(): SourceLoad {
        val indexPath = "$root/$INDEX"
        val index = classLoader.getResource(indexPath)?.readText()
            ?: return SourceLoad(emptyList(), listOf(KnowledgeError(indexPath, null, "Knowledge index not found on the classpath")))

        val missing = mutableListOf<KnowledgeError>()
        val files = index.lineSequence().map(String::trim).filter(String::isNotEmpty).mapNotNull { path ->
            val resource = classLoader.getResource("$root/$path")
            if (resource == null) {
                missing += KnowledgeError(path, null, "Listed in $indexPath but missing from the classpath")
                null
            } else {
                path to { resource.readText() }
            }
        }.toList()

        val loaded = KnowledgeFiles.parseAll(parser, files.asSequence())
        return loaded.copy(errors = missing + loaded.errors)
    }

    companion object {
        const val INDEX = "index.txt"
    }
}
