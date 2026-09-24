# Rolabox

## Architecture decisions

The architecture is defined by the ADRs in `docs/adr/`. They are the single source of truth; this
file only points to them.

@docs/adr/README.md

- Before adding a module, adding a dependency between modules, putting code in a layer, or adding
  a repository or use case, read the **Rules** section of the relevant ADR and follow it.
- If a request contradicts an accepted ADR, stop and say which ADR and rule it conflicts with.
  Don't deviate quietly, and don't edit the ADR to make the change fit.
- If a change makes a new architectural decision, propose a new ADR based on
  `docs/adr/template.md` instead of just writing the code.
- Accepted ADRs are only edited as ADR-000 allows.

## Git workflow

Never commit, push, rebase or open pull requests. Leave all changes in the working tree for the
developer to review and commit.

## Common commands

```bash
./gradlew check                  # build, tests and all static analysis
./gradlew ktlintCheck detekt     # lint only
./gradlew ktlintFormat           # auto-fix formatting
./gradlew moduleGraph            # regenerate the module dependency graph
```
