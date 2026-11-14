# Sample project

A minimal project repo wired to the knowledge server, showing what a real project adds:

| File | Purpose |
|---|---|
| `.knowledge.yaml` | The project manifest: team, languages, frameworks, and overrides of shared rules. |
| `docs/knowledge/*.md` | Project docs that replace (`error-handling.md`) or extend (`configuration.md`) shared rules. |
| `CLAUDE.md` | Tells Claude Code to call `get_guidelines` with the manifest and to give project docs precedence. |
| `.mcp.json` | Registers the knowledge server for Claude Code in this directory. |

The build checks that this manifest resolves against the real `knowledge/` folder without warnings, so it stays in
sync with the shared content. See the repo [README](../../README.md#connecting-claude-code) to try it.
