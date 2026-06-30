<!--
Thanks for contributing to Monaka! Please fill out the sections below.
Keep the PR focused — one logical change per PR is easier to review.
-->

## Summary

<!-- What does this PR do and why? Link any related issue, e.g. "Closes #123". -->

## Type of change

- [ ] 🐞 Bug fix (non-breaking change that fixes an issue)
- [ ] ✨ New feature (non-breaking change that adds functionality)
- [ ] 💥 Breaking change (fix or feature that changes existing public API)
- [ ] 📖 Documentation only
- [ ] 🧹 Refactor / chore / build / CI

## Affected modules

- [ ] `:monaka` (core)
- [ ] `:monaka-compose`
- [ ] `:monaka-test`
- [ ] `:monaka-transitions`
- [ ] `:monaka-gradle-plugin`
- [ ] Sample app
- [ ] Docs / website

## Checklist

- [ ] `./gradlew allTests` passes locally.
- [ ] `./gradlew spotlessCheck` passes (run `./gradlew spotlessApply` to fix formatting).
- [ ] `./gradlew apiCheck` passes. If this PR intentionally changes public API, I ran `./gradlew apiDump` and committed the updated `.api` files.
- [ ] Added or updated tests using `:monaka-test` where applicable.
- [ ] Updated KDoc and all relevant `docs/` pages (EN **and** JA) for any code change.
- [ ] Added a `CHANGELOG.md` entry under the appropriate heading.
- [ ] My change preserves explicit-API mode (all new public declarations are explicitly marked).

## Notes for reviewers

<!-- Anything reviewers should pay particular attention to: tricky logic, design trade-offs, follow-ups deliberately left out. -->
