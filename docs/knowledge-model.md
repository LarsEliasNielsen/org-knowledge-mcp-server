# Knowledge model

How the server turns documents and a project context into the rules in effect. For writing documents (front matter
fields, rule syntax), see [knowledge/README.md](../knowledge/README.md).

## Layers and scope

Layers run from general to specific, and a more specific layer wins:

```
org  →  language  →  team  →  project
```

The first three live in `knowledge/`. The folder sets a document's layer and default scope (`languages/java/` gives
`languages: [java]`). Front matter can narrow the scope, never widen it.

A scope has three dimensions: `languages`, `teams` and `frameworks`. A document matches a project context when every
dimension matches. An omitted dimension matches anything, and a dimension matches when the context has any one of its
values.

## Rules

A rule is an H3 whose heading starts with a rule id, such as `### JAVA-ERR-003 Never swallow exceptions`. Its body
runs until the next H2 or H3. Ids are unique across the knowledge base and never reused.

Rules are the unit `get_guidelines` serves, whatever the document kind. A binding constraint in an architecture doc or
ADR is written as a rule and served like a coding guideline; the surrounding rationale stays prose, reachable through
`get_doc`. The document `kind` drives `list_docs` filtering and tells the agent what kind of source a rule comes from.

A rule can override rules in lower layers only. Where the overriding rule applies, `get_guidelines` returns it marked
with the rules it overrides and drops the originals. A deprecated rule names its successor with `superseded-by`.

H2 sections are addressed by GitHub-style slugs (`## Logging and context` becomes `logging-and-context`).

## Visibility

- `draft` documents load and validate but are not served unless `INCLUDE_DRAFTS` is on, which is for local previews
  only. `list_docs` leaves them out, `get_doc` returns a `draft_not_served` error, and `get_guidelines` leaves out their
  rules and says so in its warnings.
- Deprecated rules, and all rules of deprecated documents, are left out of `get_guidelines`. `get_doc` still returns
  them with their status. A superseded ADR gets `status: deprecated`, which removes its rules.

## Validation

The build and the server's startup reject a knowledge base with any of:

- A front matter schema violation, such as a missing field, an unknown key or a bad enum value.
- Duplicate document ids, rule ids, or section slugs within a document.
- A `related`, `overrides` or `superseded-by` reference that doesn't resolve.
- An override that targets a rule in the same or a higher layer.
- A front matter scope that widens the folder default.

## Project layer

The project layer lives in each project's own repo. The server can't read the developer's workspace, so the agent
reads the project's manifest and passes it to `get_guidelines`:

```yaml
# .knowledge.yaml
team: payments
languages: [java, kotlin]
frameworks: [spring-boot]
overrides:
  - rule: JAVA-ERR-003
    action: replace          # extend | replace | waive
    doc: docs/knowledge/error-handling.md
    reason: Legacy SOAP client forces checked-exception wrapping
```

Project docs live under `docs/knowledge/`. The work is split:

- The server resolves the shared layers for the context and applies each project override by its action:
  - `waive` drops the rule.
  - `replace` returns only a stub: rule id, title, the project doc and the reason.
  - `extend` returns the full rule marked as extended by the project doc.
- The agent reads the project docs locally and applies them last.

A project override that doesn't apply returns a warning instead: an unknown rule id, a rule not in effect for the
context (out of scope, its document not served, or replaced by a higher-layer rule, which the warning names), or a
deprecated rule. For a deprecated rule the warning follows `superseded-by`, further deprecations and higher-layer
overrides to the rule that applies in its place, and names it.

## Rule order

`get_guidelines` orders rules by layer (org, language, team), then document id, then position in the document. Within
the language layer, languages follow the order of the request's `languages` list, so a project lists its main language
first.

## Server instructions

On connect, the server sends clients MCP instructions with the precedence rule and the call flow: read
`.knowledge.yaml`, call `get_guidelines` with it, follow the project docs for extended or replaced rules, report
warnings, and use `list_docs` and `get_doc` for background.
