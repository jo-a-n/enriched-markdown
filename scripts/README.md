# Maintainer scripts

Repo-level tooling, mostly for releases. Not needed for regular contribution work — see [CONTRIBUTING.md](../CONTRIBUTING.md) for that.

## generate-changelog.mjs

Prints GitHub-release-style markdown for all commits since a tag, with PR links, author handles, and a New Contributors section. Commits are grouped by conventional-commit prefix: `feat` → New Features, `fix`/`perf` → Fixes & Improvements, `refactor` → Refactors, `test` → Tests, `docs`/`chore`/`build`/`ci` → Docs & Chores, and anything else → Other Changes.

```sh
./scripts/generate-changelog.mjs v0.7.0 | pbcopy          # everything since v0.7.0
./scripts/generate-changelog.mjs v0.6.0 v0.7.0            # explicit range
```

Author handles and the New Contributors section are resolved through an authenticated [GitHub CLI](https://cli.github.com/); without it the script falls back to plain commit author names.

## prepare-npm-publish.sh

`prepack`/`postpack` hooks for the library package: swaps the `cpp` symlink for a real copy of `packages/core/cpp` while packing. Run automatically by npm, not by hand.

## fetch-md4c.sh

Syncs `packages/core/cpp/enrmrkd` from our MD4C fork [software-mansion-labs/md4c](https://github.com/software-mansion-labs/md4c), whose parser carries an `ENRMRKD_`/`enrmrkd_` prefix so it cannot clash with another embedded MD4C copy. Run via `yarn workspace react-native-enriched-markdown sync-md4c`.
