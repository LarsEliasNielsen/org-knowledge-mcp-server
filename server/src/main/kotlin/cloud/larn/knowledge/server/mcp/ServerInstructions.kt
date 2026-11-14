package cloud.larn.knowledge.server.mcp

/** Sent to clients on initialize: the precedence rule and the expected call flow. */
val SERVER_INSTRUCTIONS = """
    This server holds the organization's shared engineering knowledge: rules for every project, per language and per
    team, plus architecture docs and ADRs.

    Precedence: org < language < team < project. A more specific layer wins, and project docs win over everything
    this server returns.

    Before writing or reviewing code in a repository:
    1. Read `.knowledge.yaml` at the repository root and call get_guidelines with its team, languages, frameworks and
       overrides. Without a manifest, call get_guidelines with the languages and frameworks the code uses.
    2. Follow the returned rules. A rule marked as replaced or extended names a project doc: read it from the
       repository and follow it instead of, or on top of, the shared rule.
    3. Tell the user about any warnings; they point to overrides in `.knowledge.yaml` that need fixing.
    4. Use list_docs and get_doc for background, architecture docs and ADRs.

    Cite rule ids (for example JAVA-ERR-003) when a rule shapes your code or review comments.
""".trimIndent()
