package cloud.larn.knowledge.infrastructure.parsing

import cloud.larn.knowledge.application.KnowledgeError
import cloud.larn.knowledge.domain.DocId
import cloud.larn.knowledge.domain.DocStatus
import cloud.larn.knowledge.domain.Kind
import cloud.larn.knowledge.domain.KnowledgeDoc
import cloud.larn.knowledge.domain.Rule
import cloud.larn.knowledge.domain.RuleHeading
import cloud.larn.knowledge.domain.RuleId
import cloud.larn.knowledge.domain.RuleStatus
import cloud.larn.knowledge.domain.Scope
import cloud.larn.knowledge.domain.ScopeDimension
import cloud.larn.knowledge.domain.ScopeRefinement
import cloud.larn.knowledge.domain.ScopeValue
import cloud.larn.knowledge.domain.Section
import cloud.larn.knowledge.domain.Slug
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlException
import kotlinx.serialization.DeserializationStrategy
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser
import java.time.LocalDate
import java.time.format.DateTimeParseException

sealed interface ParseResult {
    data class Success(val doc: KnowledgeDoc) : ParseResult

    data class Failure(val errors: List<KnowledgeError>) : ParseResult
}

/**
 * Turns one Markdown knowledge file into a [KnowledgeDoc]. Collects every error it can find in the file
 * instead of stopping at the first; kaml's strict decoding stops at the first YAML-level problem
 * (unknown or missing key, malformed YAML) within a block.
 */
class MarkdownDocParser {
    private val yaml = Yaml(configuration = YamlConfiguration(strictMode = true))
    private val markdown = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS).build()

    fun parse(relativePath: String, content: String): ParseResult {
        val path = KnowledgePath.normalize(relativePath)
        val errors = Errors(path)
        val text = content.removePrefix("﻿").replace("\r\n", "\n")

        val location = KnowledgePath.locate(path).getOrElse { errors.add(null, it.message!!); null }
        val split = FrontMatterSplit.of(text)
        if (split == null) {
            errors.add(1, "Missing front matter: the file must start with a '---' line and close it with another '---' line")
            return ParseResult.Failure(errors.list)
        }

        val frontMatter = decode(FrontMatterYaml.serializer(), split.frontMatter, split.frontMatterLine, errors, "Front matter")
        val header = frontMatter?.let { HeaderMapper(it, split, location, errors).map() }
        val body = BodyParser(split.body, split.bodyLine, errors).parse()

        if (errors.list.isNotEmpty() || location == null || header == null) return ParseResult.Failure(errors.list)
        return ParseResult.Success(
            KnowledgeDoc(
                id = header.id,
                title = header.title,
                kind = header.kind,
                layer = location.layer,
                scope = header.scope,
                owner = header.owner,
                status = header.status,
                summary = header.summary,
                related = header.related,
                lastReviewed = header.lastReviewed,
                sections = body.sections,
                rules = body.rules,
                sourcePath = path,
                markdown = split.body,
            ),
        )
    }

    private fun <T> decode(strategy: DeserializationStrategy<T>, source: String, firstLine: Int, errors: Errors, what: String): T? =
        try {
            yaml.decodeFromString(strategy, source)
        } catch (e: YamlException) {
            errors.add(firstLine + e.line - 1, "$what: ${e.message}")
            null
        }

    private class Errors(private val path: String) {
        val list = mutableListOf<KnowledgeError>()

        fun add(line: Int?, message: String) {
            list += KnowledgeError(path, line, message)
        }
    }

    /** The file split at its front-matter fences, with the 1-based line where each part starts. */
    private class FrontMatterSplit(val frontMatter: String, val frontMatterLine: Int, val body: String, val bodyLine: Int) {
        companion object {
            fun of(text: String): FrontMatterSplit? {
                val lines = text.split('\n')
                if (lines.first().trimEnd() != "---") return null
                val close = (1 until lines.size).firstOrNull { lines[it].trimEnd() == "---" } ?: return null
                return FrontMatterSplit(
                    frontMatter = lines.subList(1, close).joinToString("\n"),
                    frontMatterLine = 2,
                    body = lines.subList(close + 1, lines.size).joinToString("\n"),
                    bodyLine = close + 2,
                )
            }
        }
    }

    private class Header(
        val id: DocId,
        val title: String,
        val kind: Kind,
        val scope: Scope,
        val owner: String,
        val status: DocStatus,
        val summary: String,
        val related: List<DocId>,
        val lastReviewed: LocalDate?,
    )

    /** Checks front-matter values, reporting each problem at the line of its key. */
    private class HeaderMapper(
        private val yaml: FrontMatterYaml,
        private val split: FrontMatterSplit,
        private val location: DocLocation?,
        private val errors: Errors,
    ) {
        private val lines = split.frontMatter.split('\n')

        private fun lineOf(key: String): Int? {
            val index = lines.indexOfFirst { it.trimStart().startsWith("$key:") }
            return if (index < 0) null else split.frontMatterLine + index
        }

        private fun fail(key: String, message: String): Nothing? {
            errors.add(lineOf(key), message)
            return null
        }

        fun map(): Header? {
            val id = DocId.parseOrNull(yaml.id) ?: fail("id", "Invalid id '${yaml.id}': expected lowercase kebab-case")
            val title = text("title", yaml.title)
            val owner = text("owner", yaml.owner)
            val summary = text("summary", yaml.summary)
            val kind = enumValue<Kind>("kind", yaml.kind)
            val status = enumValue<DocStatus>("status", yaml.status)
            val scope = scope()
            val related = yaml.related.map { DocId.parseOrNull(it) ?: fail("related", "Invalid related doc id '$it'") }
            val lastReviewed = yaml.lastReviewed?.let {
                try {
                    LocalDate.parse(it)
                } catch (_: DateTimeParseException) {
                    fail("last-reviewed", "Invalid last-reviewed date '$it': expected YYYY-MM-DD")
                }
            }

            if (id == null || title == null || owner == null || summary == null || kind == null || status == null ||
                scope == null || related.any { it == null } || (yaml.lastReviewed != null && lastReviewed == null)
            ) {
                return null
            }
            return Header(id, title, kind, scope, owner, status, summary, related.filterNotNull(), lastReviewed)
        }

        private fun text(key: String, value: String): String? =
            value.trim().ifEmpty { null } ?: fail(key, "'$key' must not be blank")

        private inline fun <reified E : Enum<E>> enumValue(key: String, value: String): E? =
            enumValues<E>().firstOrNull { it.name.lowercase() == value }
                ?: fail(key, "Invalid $key '$value': expected one of ${enumValues<E>().joinToString { it.name.lowercase() }}")

        private fun scope(): Scope? {
            val written = yaml.scope ?: return location?.defaultScope ?: Scope.EVERYONE
            var valid = true
            fun dimension(dimension: ScopeDimension, values: List<String>?): Set<String> {
                if (values == null) return emptySet()
                if (values.isEmpty()) {
                    fail("scope", "scope.${dimension.key} must not be empty: omit it to inherit the folder scope")
                    valid = false
                }
                values.filterNot(ScopeValue::isValid).forEach {
                    fail("scope", "Invalid scope.${dimension.key} value '$it': expected a lowercase identifier")
                    valid = false
                }
                return values.filter(ScopeValue::isValid).toSet()
            }

            val narrowing = Scope(
                languages = dimension(ScopeDimension.LANGUAGES, written.languages),
                teams = dimension(ScopeDimension.TEAMS, written.teams),
                frameworks = dimension(ScopeDimension.FRAMEWORKS, written.frameworks),
            )
            if (!valid || location == null) return null

            return when (val refined = location.defaultScope.refineWith(narrowing)) {
                is ScopeRefinement.Narrowed -> refined.scope
                is ScopeRefinement.Widened -> {
                    refined.violations.forEach {
                        fail(
                            "scope",
                            "scope.${it.dimension.key} widens the folder scope: ${it.outside.sorted().joinToString()} " +
                                "not in ${it.allowed.sorted().joinToString()}",
                        )
                    }
                    null
                }
            }
        }
    }

    private class Body(val sections: List<Section>, val rules: List<Rule>)

    /** Extracts H2 sections and H3 rules, slicing the original source lines so Markdown is returned verbatim. */
    private inner class BodyParser(body: String, private val firstLine: Int, private val errors: Errors) {
        private val lines = body.split('\n')
        private val blocks: List<Node> = generateSequence(markdown.parse(body).firstChild) { it.next }.toList()

        private fun startIndex(node: Node) = node.sourceSpans.first().lineIndex
        private fun endIndex(node: Node) = node.sourceSpans.last().lineIndex
        private fun lineOf(node: Node) = firstLine + startIndex(node)

        /** Index of the first line of the next heading at [maxLevel] or above after [from], or the end of the body. */
        private fun boundaryAfter(from: Int, maxLevel: Int): Int =
            blocks.drop(from + 1).firstOrNull { it is Heading && it.level <= maxLevel }?.let(::startIndex) ?: lines.size

        private fun slice(from: Int, until: Int): String =
            lines.subList(from, until).dropWhile(String::isBlank).dropLastWhile(String::isBlank).joinToString("\n")

        fun parse(): Body {
            val sections = mutableListOf<Section>()
            val rules = mutableListOf<Rule>()
            val ruleBlocks = mutableSetOf<Node>()

            blocks.forEachIndexed { index, block ->
                if (block !is Heading) return@forEachIndexed
                when (block.level) {
                    2 -> {
                        val heading = plainText(block)
                        sections += Section(
                            slug = Slug.of(heading),
                            heading = heading,
                            markdown = slice(startIndex(block), boundaryAfter(index, 2)),
                            line = lineOf(block),
                        )
                    }
                    3 -> RuleHeading.parse(plainText(block))?.let { ruleHeading ->
                        val ruleBlock = blocks.getOrNull(index + 1)?.takeIf(::isRuleBlock) as FencedCodeBlock?
                        ruleBlock?.let(ruleBlocks::add)
                        val bodyStart = endIndex(ruleBlock ?: block) + 1
                        rule(block, ruleHeading, ruleBlock, slice(bodyStart, boundaryAfter(index, 3)))?.let(rules::add)
                    }
                }
            }

            blocks.filter { isRuleBlock(it) && it !in ruleBlocks }.forEach {
                errors.add(lineOf(it), "A rule block must directly follow a rule heading (### <RULE-ID> <title>)")
            }
            return Body(sections, rules)
        }

        private fun isRuleBlock(node: Node) = node is FencedCodeBlock && node.info.trim() == "rule"

        private fun rule(heading: Heading, ruleHeading: RuleHeading, block: FencedCodeBlock?, body: String): Rule? {
            val meta = if (block == null || block.literal.isBlank()) {
                RuleBlockYaml()
            } else {
                decode(RuleBlockYaml.serializer(), block.literal, lineOf(block) + 1, errors, "Rule block of ${ruleHeading.id}")
                    ?: return null
            }
            val line = block?.let(::lineOf) ?: lineOf(heading)
            fun fail(message: String): Nothing? {
                errors.add(line, "Rule ${ruleHeading.id}: $message")
                return null
            }

            val status = meta.status?.let { value ->
                RuleStatus.entries.firstOrNull { it.name.lowercase() == value }
                    ?: fail("invalid status '$value': expected one of active, deprecated")
            }
            val supersededBy = meta.supersededBy?.let { RuleId.parseOrNull(it) ?: fail("invalid superseded-by '$it'") }
            val overrides = meta.overrides.map { RuleId.parseOrNull(it) ?: fail("invalid overrides entry '$it'") }
            if ((meta.status != null && status == null) || (meta.supersededBy != null && supersededBy == null) ||
                overrides.any { it == null }
            ) {
                return null
            }

            return try {
                Rule(
                    id = ruleHeading.id,
                    title = ruleHeading.title,
                    body = body,
                    status = status ?: RuleStatus.ACTIVE,
                    supersededBy = supersededBy,
                    overrides = overrides.filterNotNull(),
                    line = lineOf(heading),
                )
            } catch (e: IllegalArgumentException) {
                errors.add(line, e.message!!)
                null
            }
        }
    }

    private fun plainText(node: Node): String = buildString {
        fun visit(current: Node) {
            when (current) {
                is Text -> append(current.literal)
                is Code -> append(current.literal)
                is SoftLineBreak, is HardLineBreak -> append(' ')
                else -> generateSequence(current.firstChild) { it.next }.forEach(::visit)
            }
        }
        visit(node)
    }.trim()
}
