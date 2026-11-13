package cloud.larn.knowledge.domain

/**
 * Shared knowledge layers, ordered from least to most specific. A more specific layer wins.
 * The project layer is not modelled here: it lives in each project repo and the agent applies it last.
 */
enum class Layer { ORG, LANGUAGE, TEAM }

enum class Kind { GUIDELINE, ARCHITECTURE, ADR, LIBRARY, SERVICE }

enum class DocStatus { DRAFT, ACTIVE, DEPRECATED }

enum class RuleStatus { ACTIVE, DEPRECATED }

/** What a project does with a shared rule, declared in its `.knowledge.yaml`. */
enum class OverrideAction {
    /** Keep the shared rule; the project doc adds to it. */
    EXTEND,

    /** The project doc replaces the shared rule. */
    REPLACE,

    /** The rule does not apply to the project. */
    WAIVE,
}
