# brainstorm: release signing and README memory

## Goal

Remember that important updates and necessary content must be written into the README as they happen, and produce a normal signed release APK instead of continuing to rely on debug builds.

## What I already know

* The user asked to persist this README update rule as project memory.
* The user asked to package a normal release build and sign it personally, not keep using debug packages.
* README currently documents only ssembleDebug.
* .gitignore already excludes keystore.properties, *.jks, and *.keystore.
* pp/build.gradle.kts has a release type with minify and shrink, but no signing config.
* .trellis/spec/android/build-and-env.md already says compile requests should produce debug and release, and the current release is unsigned until signed locally with the debug keystore. That convention is now outdated.

## Assumptions (temporary)

* A new local release keystore can be created and reused for this app.
* Keystore passwords stay local and are never committed.
* README and the Android build spec both record the new rule.

## Open Questions

* None blocking. Use a local release keystore generated for this project.

## Requirements (evolving)

* Persist the README-update rule in Trellis project memory.
* Configure release signing from an untracked local keystore file.
* Generate the keystore locally if it does not exist.
* Build a signed release APK and verify its signature.
* Update README and the build spec so future sessions do not fall back to debug-only packaging.

## Acceptance Criteria (evolving)

* [ ] README states that important updates and necessary content are added as they happen.
* [ ] README documents the signed release build command and local signing setup.
* [ ] Release build is signed with a project keystore, not the debug keystore.
* [ ] ssembleRelease produces a signed APK.
* [ ] Secrets and the keystore remain untracked.

## Definition of Done (team quality bar)

* Tests added/updated where appropriate (build verification instead of new unit tests)
* Lint / typecheck considered against the existing Gradle quality bar
* Docs/notes updated
* Rollout/rollback considered: keystore stays local and gitignored

## Out of Scope (explicit)

* Play Store upload or a new version bump
* Committing the keystore or passwords

## Technical Notes

* Files inspected: README.md, app/build.gradle.kts, .gitignore, .trellis/spec/android/build-and-env.md
