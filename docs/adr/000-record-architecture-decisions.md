# ADR-000: Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-09-24
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Revised 2026-09-26:** Before 1.0, accepted ADRs may be revised in place with a dated Revised
  line (rules 1 and 2, "Changing a decision"). The module structure was still changing, and a chain
  of superseding ADRs would bury the current rules. The old rule applies again from 1.0.
- **Revised 2026-10-01:** A third party's rules for its own element take precedence over the rules
  of any ADR (rule 5, "Third-party rules come first"). Google's branding rules for the Sign in with
  Google button conflicted with ADR-017's large-text rules, and the ADRs didn't say which one wins.

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

**Changing a decision.** Before the 1.0 release, the architecture is still settling, so an accepted
ADR may be revised in place. Each revision adds a dated **Revised** line to the ADR's header saying
what changed and why, and the revision is proposed in a PR like a new ADR. From 1.0 on, an accepted
ADR is not rewritten: fixing typos and links, and updating the status line, are the only edits
allowed. To change a decision after 1.0, write a new ADR that states what it supersedes, and update
the old ADR's status line in the same PR.

**Third-party rules come first.** Some elements aren't Rolabox's to design: a third party publishes
rules for them that an app must follow to use them, such as Google's branding guidelines for the
Sign in with Google button. Where those rules and an ADR's rules can't both be followed, the third
party's rules take precedence, for that element only. The element still follows every ADR as far as
the third party's rules allow, and each place where it can't carries a comment that names this rule
and the ADR rule it sets aside, so every exception is visible in review. Where an ADR's rule is
`[enforced]`, the exception is a `@Suppress` with that comment, not a change to the check. This is
about rules a third party imposes, not about a library's defaults or conventions.

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

1. `[convention]` A change that contradicts an accepted ADR must come with a new ADR that supersedes
   it or, before 1.0, a revision of that ADR (rule 2).
2. `[convention]` Before 1.0, an accepted ADR may be revised in place, and each revision adds a
   dated **Revised** line saying what changed and why. From 1.0 on, accepted ADRs are only edited to fix
   typos and links, or to update their status.
3. `[convention]` Every ADR is listed in [`docs/adr/README.md`](README.md) with its current status.
4. `[convention]` Each new ADR (after the founding set) is proposed in its own pull request.
5. `[convention]` A third party's rules for its own element, such as branding guidelines, take
   precedence over the rules of any ADR, for that element only. The element follows the ADRs as far
   as those rules allow, and each exception carries a comment (a `@Suppress` where the rule is
   enforced) that names this rule and the ADR rule it sets aside.
