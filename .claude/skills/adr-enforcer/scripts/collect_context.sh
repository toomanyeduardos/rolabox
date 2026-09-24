#!/usr/bin/env bash
# ADR Enforcer: collect everything the ADR check needs, deterministically.
#
# Usage: collect_context.sh [base-branch]        (default base: main)
# Env:   ADR_DIR=path/to/adrs                    (optional override of auto-discovery)
#        ADR_ENFORCER_NO_FETCH=1                  (skip the best-effort `git fetch`)
#
# Always checks COMMITTED state (merge-base..HEAD), because that is what merges.
# Writes its output to <git-dir>/adr-enforcer/ (never tracked, survives nothing it shouldn't).
#
# Exit codes (non-zero = fail closed, the check must NOT be reported as passing):
#   0 ok | 2 not a git repo | 3 nothing to check | 4 no ADRs found | 5 cannot resolve base
set -euo pipefail

BASE_BRANCH="${1:-main}"

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "ERROR: not inside a git repository." >&2; exit 2; }
cd "$ROOT"   # never rely on the caller's cwd
GIT_DIR_ABS="$(git rev-parse --absolute-git-dir)"
OUT="$GIT_DIR_ABS/adr-enforcer"
rm -rf "$OUT"
mkdir -p "$OUT"

warn() { echo "WARNING: $*" | tee -a "$OUT/warnings.txt" >&2; }

# ---------- 1. Resolve base ----------
if [ "${ADR_ENFORCER_NO_FETCH:-0}" != "1" ] && git remote get-url origin >/dev/null 2>&1; then
  git fetch --quiet origin "$BASE_BRANCH" 2>/dev/null || warn "could not fetch origin/$BASE_BRANCH; using local refs (may be stale)."
fi

BASE_REF=""
for cand in "origin/$BASE_BRANCH" "$BASE_BRANCH"; do
  if git rev-parse --verify --quiet "${cand}^{commit}" >/dev/null; then BASE_REF="$cand"; break; fi
done
[ -n "$BASE_REF" ] || { echo "ERROR: base branch '$BASE_BRANCH' not found (tried origin/$BASE_BRANCH and $BASE_BRANCH)." >&2; exit 5; }

MERGE_BASE="$(git merge-base "$BASE_REF" HEAD 2>/dev/null)" || {
  echo "ERROR: no merge-base between $BASE_REF and HEAD (shallow clone? in CI use fetch-depth: 0)." >&2; exit 5; }

HEAD_SHA="$(git rev-parse HEAD)"
BRANCH="$(git rev-parse --abbrev-ref HEAD)"
[ "$BRANCH" = "HEAD" ] && BRANCH="(detached at ${HEAD_SHA:0:8})"

if [ "$MERGE_BASE" = "$HEAD_SHA" ]; then
  echo "ERROR: HEAD has no commits beyond $BASE_REF. Nothing to check (are you on $BASE_BRANCH?)." >&2; exit 3
fi

# ---------- 2. Diff + changed files ----------
git diff --name-status -M "$MERGE_BASE" HEAD > "$OUT/changed_files.tsv"
git diff -M -U5 "$MERGE_BASE" HEAD > "$OUT/branch.diff"
git log --no-merges --format='%h %s' "$MERGE_BASE..HEAD" > "$OUT/commits.txt"
[ -s "$OUT/changed_files.tsv" ] || { echo "ERROR: branch has commits but no file changes vs $BASE_REF." >&2; exit 3; }

if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  warn "working tree has uncommitted changes; they are NOT checked. Commit them first for an accurate verdict."
fi

# ---------- 3. Discover ADR directory (in HEAD, not the working tree) ----------
ADR_PATH="${ADR_DIR:-}"
if [ -z "$ADR_PATH" ]; then
  for cand in docs/adr docs/adrs docs/decisions docs/architecture/decisions docs/architecture/adr adr adrs doc/adr decisions; do
    if git ls-tree -r --name-only HEAD -- "$cand" 2>/dev/null | grep -qi '\.md$'; then ADR_PATH="$cand"; break; fi
  done
fi
if [ -z "$ADR_PATH" ]; then
  # Fallback: any tracked dir whose last component is adr/adrs/decisions and holds markdown.
  ADR_PATH="$(git ls-tree -r --name-only HEAD \
    | grep -iE '(^|/)(adr|adrs|decisions)/[^/]+\.md$' \
    | sed -E 's#/[^/]+$##' | sort | uniq -c | sort -rn | awk 'NR==1{print $2}')" || true
fi
[ -n "$ADR_PATH" ] || { echo "ERROR: no ADR directory found in HEAD. Set ADR_DIR=path/to/adrs." >&2; exit 4; }

# ---------- 4. Index ADRs (status + title) from HEAD ----------
ADR_FILES="$(git ls-tree -r --name-only HEAD -- "$ADR_PATH" | grep -i '\.md$' \
  | grep -viE '(^|/)(readme|index|template|adr-template|0000-template)[^/]*\.md$' || true)"
[ -n "$ADR_FILES" ] || { echo "ERROR: $ADR_PATH contains no ADR markdown files." >&2; exit 4; }

printf 'file\tstatus\ttitle\n' > "$OUT/adr_index.tsv"
echo "$ADR_FILES" | while IFS= read -r f; do
  body="$(git show "HEAD:$f")"
  title="$(printf '%s\n' "$body" | grep -m1 -E '^#[[:space:]]' | sed -E 's/^#+[[:space:]]*//' || true)"
  # Front matter "status: X" (MADR), else first non-empty line under a "Status" heading (Nygard),
  # else an inline "Status: X" line.
  status="$(printf '%s\n' "$body" | awk 'NR==1&&/^---$/{fm=1;next} fm&&/^---$/{exit} fm&&tolower($0)~/^status:/{sub(/^[^:]*:[[:space:]]*/,"");print;exit}')"
  if [ -z "$status" ]; then
    status="$(printf '%s\n' "$body" | awk 'tolower($0)~/^#+[[:space:]]*status[[:space:]]*$/{g=1;next} g&&/^#/{exit} g&&NF{print;exit}')"
  fi
  if [ -z "$status" ]; then
    status="$(printf '%s\n' "$body" | grep -m1 -iE '^\*{0,2}status\*{0,2}[[:space:]]*:' | sed -E 's/^[^:]*:[[:space:]]*//' || true)"
  fi
  status="$(printf '%s' "$status" | tr -d '*_`"' | awk '{print tolower($1)}')"
  [ -n "$status" ] || status="unknown"
  printf '%s\t%s\t%s\n' "$f" "$status" "${title:-(untitled)}" >> "$OUT/adr_index.tsv"
done

git diff --name-status "$MERGE_BASE" HEAD -- "$ADR_PATH" > "$OUT/adrs_changed_in_branch.tsv" || true

# ---------- 5. Gradle modules the branch touches ----------
: > "$OUT/affected_build_files.txt"
awk -F'\t' '{print $NF}' "$OUT/changed_files.tsv" | while IFS= read -r p; do
  d="$(dirname "$p")"
  while :; do
    for b in build.gradle.kts build.gradle; do
      if git cat-file -e "HEAD:${d#./}/$b" 2>/dev/null || { [ "$d" = "." ] && git cat-file -e "HEAD:$b" 2>/dev/null; }; then
        [ "$d" = "." ] && echo "$b" || echo "$d/$b"
        continue 3
      fi
    done
    [ "$d" = "." ] && break
    d="$(dirname "$d")"
  done
done | sort -u > "$OUT/affected_build_files.txt"

# ---------- 6. Summary ----------
TOTAL_ADRS=$(( $(wc -l < "$OUT/adr_index.tsv") - 1 ))
{
  echo "repo_root: $ROOT"
  echo "branch: $BRANCH"
  echo "head: $HEAD_SHA"
  echo "base_ref: $BASE_REF"
  echo "merge_base: $MERGE_BASE"
  echo "adr_dir: $ADR_PATH"
  echo "adr_count: $TOTAL_ADRS"
  echo "changed_file_count: $(wc -l < "$OUT/changed_files.tsv" | tr -d ' ')"
  echo "commit_count: $(wc -l < "$OUT/commits.txt" | tr -d ' ')"
  echo "diff_bytes: $(wc -c < "$OUT/branch.diff" | tr -d ' ')"
  echo "output_dir: $OUT"
} > "$OUT/context.txt"

cat "$OUT/context.txt"
echo "--- ADR index ---"
column -t -s "$(printf '\t')" "$OUT/adr_index.tsv" 2>/dev/null || cat "$OUT/adr_index.tsv"
[ -f "$OUT/warnings.txt" ] && { echo "--- warnings ---"; cat "$OUT/warnings.txt"; }
exit 0
