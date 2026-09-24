# ADR-NNN: Title in the imperative or as a noun phrase

- **Status:** Accepted
- **Date:** YYYY-MM-DD
- **Author:** Name
- **Reviewers:** Names, or "AI-assisted review"
- **Supersedes:** ADR-NNN (optional; remove the line if not used)

## Context

What forces are at play: the problem, the constraints, and what makes the decision necessary now.
State facts, not the decision.

## Decision

What we decided, in active voice ("We will…"). Specific enough that a reviewer can tell
whether a change follows it.

## Alternatives considered

Each option we didn't pick, and why. Be fair to them: an alternative that looks like a straw man
makes the whole record less credible.

## Consequences

What becomes easier, what becomes harder, and the costs we are knowingly accepting.

## Rules

Short, checkable statements that reviewers and tools apply to code. Tag each one:

- `[enforced]` the build fails when it is broken
- `[planned]` will be enforced by the build; link the ticket that tracks it
- `[convention]` checked in code review only
