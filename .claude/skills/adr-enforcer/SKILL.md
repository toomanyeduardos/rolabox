---
name: adr-enforcer
description: Check whether the current git branch adheres to the project's Architecture Decision Records (ADRs) before it is merged into main, then offer fixes for any violations. Use this whenever the user runs /adr-enforcer, asks "does this branch follow our ADRs", "is this ready to merge", "check the branch against the architecture decisions", or wants a pre-merge architecture review, even if they don't say "ADR".
argument-hint: "[--base <branch>]"
---

# ADR Enforcer

Checks what this branch would merge into `main` against every **Accepted** ADR, and gives a verdict with evidence. Arguments passed: `$ARGUMENTS`

- `--base <branch>`: compare against a branch other than `main`.

The point of this skill is a verdict the user can trust before merging. A false "pass" is worse than an honest "needs review", so when evidence is missing or ambiguous, say so instead of guessing.

## Step 1: Collect context (deterministic)

Run the collector from the repo. It resolves the repo root itself, so the current directory doesn't matter:

```bash
bash "$(git rev-parse --show-toplevel)/.claude/skills/adr-enforcer/scripts/collect_context.sh" <base-branch-or-main>
```

If it exits non-zero, **stop**. Report the error and the fix it suggests. Do not continue with a partial check, and never report a pass. Exit codes:
2 = not a git repo, 3 = nothing to check, 4 = no ADRs found, 5 = can't resolve base branch or merge-base.

On success, it prints a summary and writes its output to the `output_dir` it reports:

| File | Contents |
|---|---|
| `context.txt` | branch, head, merge-base, ADR dir, counts |
| `adr_index.tsv` | every ADR: file, parsed status, title |
| `changed_files.tsv` | `git diff --name-status` for merge-base..HEAD |
| `branch.diff` | the full diff (5 lines of context) |
| `commits.txt` | commit subjects on the branch |
| `affected_build_files.txt` | the Gradle build file of every module the branch touches |
| `adrs_changed_in_branch.tsv` | ADRs added or edited on this branch |
| `warnings.txt` | present only if there are warnings (e.g. uncommitted changes); surface these at the top of the report |

The check always covers **committed** state, because that's what merges. Uncommitted work is not checked.

## Step 2: Decide which ADRs apply

1. Read every ADR whose status is `accepted`. Read ADRs with status `unknown` too, and treat them as enforced. Note in the report that their status couldn't be parsed, since silently skipping them could hide a violation.
2. Skip `proposed`, `draft`, `rejected`, `deprecated`, and `superseded`. List them in the report as skipped so the user can see nothing was dropped silently.
3. If the branch itself adds or changes an ADR (`adrs_changed_in_branch.tsv`), use the branch's version. A newly accepted ADR on the branch applies to the branch's own code.
4. For each enforced ADR, write down its **checkable rules** in one line each, e.g. "UI layer must not import from `data` packages" or "new persistence uses SQLDelight, not Room". Some ADRs are purely rationale with nothing checkable. Mark those "Not applicable: no checkable rule" rather than inventing a rule.

## Step 3: Check the code

**Scope**, per the user's choice: the diff plus the files it touches.
- Read the full current contents (`git show HEAD:<path>`) of every changed file, not just the hunks. Violations often depend on surrounding code, such as an import at the top of a file whose hunk is further down.
- Read every file in `affected_build_files.txt`. Dependency and module-boundary ADRs live there. Also read `settings.gradle(.kts)` and `gradle/libs.versions.toml` if they changed.
- When an ADR is about a boundary (layering, module dependencies, who may call what), use Grep to find direct callers or importers of the changed code. Only do this when an ADR actually needs it.
- Skip generated or binary content: anything under `build/`, `generated/`, images, fonts, and lockfiles. If `diff_bytes` is very large, work through it file by file rather than loading it all at once.

**Classify each finding by where it lives:**
- **Introduced by this branch**: the offending line is an added or modified line in `branch.diff`. These are the only findings that can block the merge.
- **Pre-existing in a touched file**: the violation is in a changed file but on lines this branch didn't add. Report these separately as non-blocking. The branch shouldn't be blamed for old debt, but the user should know about it.

**Evidence rules** (these prevent false positives):
- A **Violation** must cite `path:line` from HEAD, quote the offending line, and name the specific ADR rule it breaks. If you can't point to a concrete line, it is not a Violation. Mark it **Needs review** and explain what's unclear.
- A **Pass** for an applicable ADR must say briefly what you checked, e.g. "3 new ViewModels, all expose StateFlow; no UI→repository imports".
- **Needs review** is for real ambiguity: the ADR's wording allows two readings, or checking it would require running the app.

**Uncovered decisions:** also note significant architectural choices the branch makes that no ADR covers, such as a new third-party library, a new Gradle module, a new threading or concurrency approach, or a new persistence or network mechanism. List these as "Consider an ADR". They don't block the merge.

## Step 4: Report

Use this exact structure:

```markdown
# ADR check: <branch> → <base>
**Verdict: <✅ PASS | ❌ BLOCK | ⚠️ NEEDS REVIEW>**
<one sentence why>

<warnings from warnings.txt, if any>

## Results by ADR
| ADR | Result | Summary |
|---|---|---|
| 0001 Use MVVM… | ✅ Pass | … |
| 0004 Module boundaries | ❌ Violation (2) | … |
| 0006 Crash reporting | ➖ Not applicable | branch doesn't touch it |

## Violations introduced by this branch
### V1. <ADR id>: <rule>
- `path/File.kt:42`: `offending line`
- Why it violates: …

## Needs review
…

## Pre-existing issues in touched files (non-blocking)
…

## Consider an ADR (non-blocking)
…

## Skipped ADRs
<file (status)>, one per line

<sub>Checked <n> commits, <n> files, <n> enforced ADRs at <short head sha>.</sub>
```

Omit empty sections, except the verdict, the results table, and skipped ADRs.

**Verdict:** BLOCK if there is at least one Violation introduced by the branch. Otherwise NEEDS REVIEW if there is at least one Needs review item or an ADR with unknown status. Otherwise PASS.

## Step 5: Offer fixes

If there are Violations introduced by the branch:

1. For each one, propose a concrete, minimal fix: which file changes and roughly how. Keep it to the smallest change that satisfies the ADR, not a refactor of the area.
2. Ask the user which to apply (all, some by V-number, or none). **Don't edit anything until they answer.**
3. Apply only the chosen fixes. Then re-read the edited files from the working tree and re-check only the affected ADRs. Label that result "verified in working tree, not committed yet". Do not commit. Tell the user to commit and run `/adr-enforcer` again for the official verdict, since the check runs against committed state.

Never propose editing an Accepted ADR to make a violation go away. If the user says the ADR itself is outdated, suggest writing a new ADR that supersedes it (and updating the old one's status). That keeps the decision history honest, which is the point of having ADRs.

For **Needs review** items, ask the user how to interpret the ADR. If their answer reveals the ADR is ambiguous, suggest clarifying its wording in a follow-up.
