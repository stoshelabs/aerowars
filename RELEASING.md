# Releasing AeroWars

Three things in this list are load-bearing for features that only break *after* release — the in-game
update check and the "what's new" popup both depend on the tag matching what the jar reports.

## Invariants

- **`gradle.properties` is the only place the plugin version is written.** It flows into the jar name,
  the jar manifest, `manifest.json` (via `processResources` token replacement), and back into the
  running plugin through `AeroWars.getVersion()`. The docs read the same file at build time
  (`{{PLUGIN_VERSION}}`). Never hardcode a version anywhere else — with one exception: the docs' release
  *line* (`1.2`, not `1.2.0`) and the Hytale line it targets live in `docs/.vitepress/versions.ts`,
  because the docs site keeps one directory per line and has to know which lines exist. The docs build
  warns if the two disagree.
- **Tags are `v`-prefixed: `v1.2.0`, not `1.2.0`.** `UpdateChecker.fetchReleaseForVersion` looks up
  `/releases/tags/v{version}`. A tag without the `v` makes the "what's new" popup silently fall back to
  the *latest* release's notes. `tools/build_docs.sh` also finds archived docs by `v<line>.*` tags.
- **The tag must point at the commit that bumped the version.** Tagging an earlier commit ships a jar
  reporting the old version, which then compares itself against the new release and shows a permanent
  "update available" banner.
- **`ServerVersion` is derived, not written.** `src/main/resources/manifest.json` carries
  `${hytaleRange}`; the Gradle build expands it from the Hytale line of the `HytaleServer.jar` it
  compiles against (`0.6.5` → `>=0.6.0 <0.7.0`) and puts the same line on the jar name
  (`-hytale-0.6`). Build against the server you tested on; `-PhytaleVersion=0.6` overrides detection.

## Steps

1. Set `version=x.y.z` in `gradle.properties`.
2. Build against the Hytale version you tested with. If the Hytale line moved, update the compatibility
   table in `docs/guide/changelog.md` and the Hytale-server badge in `README.md` — the README is the one
   file with no token substitution, so it is the only place a range is written by hand.
3. Rename `## Unreleased` in `CHANGELOG.md` and `docs/guide/changelog.md` to the new version.
   If this release starts a new line (1.2 → 1.3), also update `docs/.vitepress/versions.ts`: move the
   outgoing line into `archived`, set `current` to the new one and add its `hytale` entry. The outgoing
   line's tag must already be pushed — its archived docs are built from it. The comment at the top of
   that file has the details; a patch release needs none of it.
4. `./gradlew jar` — verify `build/libs/AeroWars-x.y.z-hytale-<line>.jar` and that its bundled
   `manifest.json` shows the right `Version` and `ServerVersion`:
   ```sh
   unzip -p build/libs/AeroWars-x.y.z-hytale-*.jar manifest.json
   ```
5. Commit, then tag **that commit**: `git tag vx.y.z && git push origin main --tags`.
6. Publish the GitHub release on tag `vx.y.z` **with notes in the body** — the body is what the in-game
   popup (`/aerowars changelog`) shows. An empty body means the popup never appears
   (`ChangelogManager.isReady()` is false).
7. Sanity-check the round trip: a server running the new jar should not show an update banner at the
   end of its boot log.

Until step 6 is done, servers running the new jar show the *latest published* release's notes in the
"what's new" popup. That's the intended fallback, not a bug, but it's a reason not to leave a tag
unpublished.

## Docs

`tools/build_docs.sh` builds the whole site the way the Pages workflow does: the current line at the
root and at `/v<line>/`, and every line in `versions.ts` `archived` from its newest tag. For a quick local
preview of the current line only, `cd docs && npm run dev`.
