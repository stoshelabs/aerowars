#!/usr/bin/env bash
# Builds the documentation, one directory per release line.
#
#   tools/build_docs.sh              build the current version, plus every version
#                                    listed in docs/.vitepress/versions.ts
#   tools/build_docs.sh 1.1 1.2      build those versions instead of that list
#
# The current version lands at the root of docs/.vitepress/dist and also gets its
# own directory, so a link to /v1.2/guide/reference/commands keeps working after
# 1.3 ships. VitePress has no versioning of its own; this convention is what the
# Vite and Vue sites use.
#
# Two environment variables, both set by the Pages workflow and both optional here:
#
#   AEROWARS_DOCS_BASE      where the site is served from — "/aerowars/" on the
#                           GitHub project page. An archived version is built at
#                           that base plus its own directory.
#   AEROWARS_DOCS_SITE_URL  absolute URL of the site root, for the links that point
#                           from one version to another. See versions.ts for why
#                           those cannot be relative.
set -euo pipefail

HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$HERE"

# npm lives in docs/, not at the repository root — there is no root package.json.
DOCS="$HERE/docs"
OUT="$DOCS/.vitepress/dist"
SITE_BASE=${AEROWARS_DOCS_BASE:-/aerowars/}
# A base VitePress accepts: leading and trailing slash, no doubles.
SITE_BASE="/${SITE_BASE#/}"
SITE_BASE="${SITE_BASE%/}/"

command -v npm >/dev/null || { echo "npm is required" >&2; exit 1; }
[ -d "$DOCS/node_modules" ] || (cd "$DOCS" && npm install)

# What the site contains, read once from *this* checkout and handed to every
# build below — including the ones that happen inside an old tag's worktree,
# whose own versions.ts predates the newer releases (or, for v1.1.0, does not
# exist) and would otherwise have each archived version believing it is still the
# latest. Loudly, not quietly: a failure here would publish a dropdown advertising
# versions that were never built, or an archived page with no "you are reading old
# docs" banner.
if ! AEROWARS_DOCS_SITE=$(node --input-type=module -e '
    import { current, archived, hytale } from "./docs/.vitepress/versions.ts";
    console.log(JSON.stringify({ current, archived, hytale }));
' 2>/dev/null); then
    echo 'cannot read docs/.vitepress/versions.ts' >&2
    echo "node 22.6+ reads TypeScript directly; this needs one." >&2
    exit 1
fi
export AEROWARS_DOCS_SITE

current=$(node -e 'process.stdout.write(JSON.parse(process.argv[1]).current)' "$AEROWARS_DOCS_SITE")

# Without arguments the versions to keep are the ones the site advertises, so the
# script and the version dropdown cannot disagree about what exists.
versions=("$@")
if [ ${#versions[@]} -eq 0 ]; then
    mapfile -t versions < <(
        node -e 'for (const v of JSON.parse(process.argv[1]).archived) console.log(v)' "$AEROWARS_DOCS_SITE"
    )
fi

echo "==> current (base ${SITE_BASE})"
(cd "$DOCS" && AEROWARS_DOCS_BASE="$SITE_BASE" npx vitepress build .)
mkdir -p "$OUT"

# The current version gets its permanent address on the day it ships, not on the
# day it is replaced. Built rather than copied: a copy would still carry the
# root's base, so every link inside /v1.2/ would quietly walk back out to
# whatever the latest docs happen to be by then.
#
# When the next release moves this version into `archived`, the same directory is
# rebuilt from its tag — the URL does not change, and neither does what it says.
if [ -n "$current" ]; then
    echo "==> v${current} (current, pinned)"
    tmp=$(mktemp -d)
    (cd "$DOCS" \
        && AEROWARS_DOCS_VERSION="$current" AEROWARS_DOCS_BASE="${SITE_BASE}v${current}/" \
        AEROWARS_DOCS_SITE_URL="${AEROWARS_DOCS_SITE_URL:-}" \
        npx vitepress build . --outDir "$tmp")
    rm -rf "${OUT:?}/v${current}"
    mkdir -p "$OUT/v${current}"
    cp -r "$tmp/." "$OUT/v${current}/"
    rm -rf "$tmp"
fi

# Older versions are built from their tags into a worktree, so the content is
# whatever that release actually said rather than today's text with a label.
for version in "${versions[@]:-}"; do
    [ -z "$version" ] && continue
    [ "$version" = "$current" ] && continue # already built above, from this checkout

    # A version's directory is that release *line*, so the tag to build is the
    # newest tag in it: /v1.1/ should say what 1.1.3 says, because 1.1.3 is what
    # someone reading /v1.1/ would install. AeroWars tags are `v`-prefixed (see
    # RELEASING.md), so `v1.1.0` and `v1.1.3` are the candidates and the version
    # sort picks between them.
    tag=$(git tag --list "v${version}" "v${version}.*" --sort=-v:refname | head -n 1)
    if [ -z "$tag" ]; then
        echo "no tag for $version, skipping" >&2
        continue
    fi

    # A tag cut before the versioning machinery (v1.1.0 and older) has no
    # versions.ts: its config hardcodes base '/aerowars/', and it has no banner or
    # version menu to render. The base is fixed by passing it on the command line
    # below, which VitePress applies over the config's; the banner and menu simply
    # are not there. Said out loud so nobody goes looking for them.
    if ! git cat-file -e "${tag}:docs/.vitepress/versions.ts" 2>/dev/null; then
        echo "note: ${tag} predates docs versioning — built at its sub-base, but with no banner or version menu" >&2
    fi

    echo "==> v${version} (from ${tag})"
    worktree=$(mktemp -d)
    git worktree add --detach "$worktree" "$tag" >/dev/null
    (
        cd "$worktree/docs"
        ln -s "$DOCS/node_modules" node_modules
        AEROWARS_DOCS_VERSION="$version" \
        AEROWARS_DOCS_BASE="${SITE_BASE}v${version}/" \
        AEROWARS_DOCS_SITE_URL="${AEROWARS_DOCS_SITE_URL:-}" \
            npx vitepress build . --base "${SITE_BASE}v${version}/"
    )
    rm -rf "${OUT:?}/v${version}"
    mkdir -p "$OUT/v${version}"
    cp -r "$worktree/docs/.vitepress/dist/." "$OUT/v${version}/"
    git worktree remove --force "$worktree"
done

echo "==> $OUT"
