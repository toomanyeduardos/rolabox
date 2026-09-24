# ADR-000: Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-09-24
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review

## Context

Rolabox is built by one developer, and part of its purpose is to show how architectural decisions
are made, not just what the code looks like. Without a written record, the reasoning behind a
decision lives only in a PR description or in someone's head. Future contributors (human or AI)
then either follow a rule without knowing why, or break it without knowing it existed.

## Decision

We will record significant architectural decisions as Architecture Decision Records (ADRs) in
`docs/adr/`, following Michael Nygard's lightweight format with two additions borrowed from
MADR (Markdown Architectural Decision Records): an explicit **Alternatives considered** section,
and a **Rules** section of checkable statements.

**When to write one.** Write an ADR when a decision is hard to reverse, affects more than one
module, or would make a reviewer ask "why is it done this way?". Don't write one for version bumps,
implementation details inside a single module, or anything the code already explains.

**Format.** Copy [`template.md`](template.md). Files are named `NNN-kebab-case-title.md` with a
three-digit, sequential number that is never reused. Every ADR is listed in the
[index](README.md).

**Authorship.** Each ADR names its **Author** and its **Reviewers**. Rolabox is a solo project,
so decisions are reviewed with an AI assistant and recorded as "AI-assisted review".

**Proposal and review.** Each ADR is proposed in its own pull request, and the PR review is the
discussion. Merging the PR accepts the ADR, so there is no separate "Proposed" status. The one
exception is the founding set (ADR-000 to ADR-007), which was reviewed and merged in one PR
because the decisions depend on each other.

**Statuses.**

| Status | Meaning |
| --- | --- |
| Accepted | In force. |
| Rejected | Considered and turned down. Merged only when the reasoning is worth keeping. |
| Superseded by ADR-NNN | Replaced, entirely or in the part named (e.g. "Rule 3 superseded by ADR-012"). |
| Deprecated | No longer relevant, with nothing replacing it. |

**Changing a decision.** An accepted ADR is not rewritten. Fixing typos and links, and updating the
status line, are the only edits allowed. To change a decision, write a new ADR that states what it
supersedes, and update the old ADR's status line in the same PR.

**Target state.** An ADR may describe a state the code doesn't match yet. When it does, it says so
and tags the affected rules `[planned]` with the ticket that will close the gap.

## Alternatives considered

- **No written record; rely on PR descriptions and commit messages.** Free to produce, but the
  reasoning is scattered, hard to find, and never marked as replaced when a decision changes.
- **A single living architecture document.** Easy to read as a snapshot, but editing it in place
  erases the history of why things changed, which is the part we most want to keep.
- **Plain Nygard format (Context, Decision, Status, Consequences) only.** Shorter, but without a
  place for rejected alternatives, and without rules concrete enough to review code against.
- **Full MADR.** More structured (decision drivers, pros and cons for every option), but heavier
  than a project this size needs.

## Consequences

- Reviewers can read the reasoning behind the structure before reading the code.
- Writing an ADR slows down big decisions a little. That's intended.
- The Rules sections give code review, and the tooling that helps write code, one place to check
  against.
- ADRs can go stale if the code drifts from them without anyone writing a superseding ADR. The
  `[enforced]` tag exists to keep that set small and shrinking.

## Rules

1. `[convention]` A change that contradicts an accepted ADR must come with a new ADR that supersedes it.
2. `[convention]` Accepted ADRs are only edited to fix typos and links, or to update their status.
3. `[convention]` Every ADR is listed in [`docs/adr/README.md`](README.md) with its current status.
4. `[convention]` Each new ADR (after the founding set) is proposed in its own pull request.
