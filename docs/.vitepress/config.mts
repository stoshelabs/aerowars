import { readFileSync } from 'node:fs'
import { defineConfig } from 'vitepress'
import { hytaleLine, isArchived, version, versionBanner, versionMenu } from './versions'

// AeroWars documentation — plain VitePress. Each release line is built with its own base path
// into its own directory, and the newest is also built at the root; see versions.ts and
// tools/build_docs.sh. A hand-maintained Changelog page (guide/changelog.md) records what changed
// per version.
//
// Versions are NOT written by hand in the markdown. They are read here from the same files the build
// uses and substituted at build time, so a release bump never leaves a stale number behind:
//
//   {{PLUGIN_VERSION}}   -> gradle.properties `version`        (e.g. AeroWars-{{PLUGIN_VERSION}}-hytale-{{HYTALE_LINE}}.jar)
//   {{SERVER_VERSION}}   -> manifest.json `ServerVersion`      (the supported Hytale range)
//   {{HYTALE_LINE}}      -> versions.ts `hytale[version]`      (the jar's `hytale-<line>` suffix, e.g. 0.6)
//
// See the markdown.config hook at the bottom for the substitution itself.
//
// Badge convention for anything that won't survive the next server release. <Badge> is a VitePress
// global component, so no import is needed. Put it on the section heading and follow it with a
// container that says what replaces the feature and when:
//
//   ## Some feature <Badge type="warning" text="changes in 0.7" />   -- still works, goes away later
//   ## Old thing <Badge type="danger" text="deprecated" />           -- works today, do not build on it
//   ## Gone thing <Badge type="danger" text="removed in 0.7" />      -- no longer functional
//
// The version in the badge text is the *server* version (manifest ServerVersion), not the plugin's.
// When the plugin's ServerVersion range moves past it, drop the badge and rewrite the section.

const repoFile = (path: string) => readFileSync(new URL(`../../${path}`, import.meta.url), 'utf8')

const pluginVersion = /^\s*version\s*=\s*(.+)$/m.exec(repoFile('gradle.properties'))?.[1]?.trim()
if (!pluginVersion) {
    throw new Error("Could not read 'version' from gradle.properties — docs versions would be wrong.")
}

// The manifest in src/ carries the literal "${hytaleRange}" — the Gradle build expands it from the
// HytaleServer.jar it compiles against (see build.gradle.kts), so there is no range to read here.
// Derive the same range from the Hytale line versions.ts records for this version instead; a
// manifest with a hand-written range is still honoured.
const manifestRange: string | undefined = JSON.parse(repoFile('src/main/resources/manifest.json')).ServerVersion
const derivedRange = hytaleLine
    ? `>=${hytaleLine}.0 <${hytaleLine.split('.')[0]}.${Number(hytaleLine.split('.')[1]) + 1}.0`
    : undefined
const serverVersion = manifestRange && !manifestRange.includes('${') ? manifestRange : derivedRange
if (!serverVersion) {
    throw new Error("Could not work out the Hytale server range — check versions.ts `hytale` and manifest.json `ServerVersion`.")
}

// The release line versions.ts says this build documents, against the version gradle.properties
// says this checkout builds. A warning rather than an error: the two are edited in separate steps of
// a release (see versions.ts), and a local build in between is legitimate. A deploy is not.
if (!pluginVersion.startsWith(`${version}.`)) {
    console.warn(`[docs] versions.ts documents ${version} but gradle.properties says ${pluginVersion} — bump one of them.`)
}

if (!hytaleLine) {
    throw new Error(`No Hytale line for ${version} in docs/.vitepress/versions.ts \`hytale\` — the jar name in the docs would be wrong.`)
}

const VERSION_TOKENS: Record<string, string> = {
    '{{PLUGIN_VERSION}}': pluginVersion,
    '{{SERVER_VERSION}}': serverVersion,
    '{{HYTALE_LINE}}': hytaleLine,
}

const base = process.env.AEROWARS_DOCS_BASE ?? '/aerowars/' // GitHub Pages: https://stoshelabs.github.io/aerowars/

export default defineConfig({
    title: 'AeroWars',
    description: 'SkyWars for Hytale — a competitive Solo & Teams sky-island minigame plugin.',
    base,
    lang: 'en-US',
    cleanUrls: true,
    lastUpdated: true,
    head: [
        // Derived from base, not hardcoded: a versioned build serves its own copy of
        // public/, and a fixed /aerowars/ path would point every version at the root's.
        ['link', { rel: 'icon', href: `${base}icon.png` }],
        ['meta', { name: 'theme-color', content: '#41d1ff' }],
        ['meta', { property: 'og:title', content: 'AeroWars — SkyWars for Hytale' }],
        ['meta', { property: 'og:description', content: 'A competitive SkyWars-style minigame for Hytale: Solo & Teams, spawn cages, loot, kits, spectator, parties, cosmetics.' }],
        ['meta', { property: 'og:image', content: `${base}logo.png` }],
        // The layout-top slot sits above a fixed navbar, so the layout only leaves room
        // for a banner whose height it was told. Set here rather than in custom.css
        // because the current build has no banner and must not reserve the space.
        // Keep in step with the height in theme/VersionBanner.vue.
        ...(isArchived ? [['style', {}, ':root{--vp-layout-top-height:40px}'] as const] : []),
    ],
    markdown: {
        // Substitute the version tokens in the raw markdown, before parsing — so they work everywhere,
        // including inside inline code and fenced blocks (where Vue interpolation would not).
        config: (md) => {
            md.core.ruler.before('normalize', 'aerowars-versions', (state) => {
                for (const [token, value] of Object.entries(VERSION_TOKENS)) {
                    state.src = state.src.split(token).join(value)
                }
            })
        },
    },
    themeConfig: {
        logo: '/icon.png',
        nav: [
            { text: 'Home', link: '/' },
            { text: 'Guide', link: '/guide/intro/what-is-aerowars' },
            { text: 'Commands', link: '/guide/reference/commands' },
            { text: 'Changelog', link: '/guide/changelog' },
            versionMenu(),
        ],

        // Read by theme/VersionBanner.vue. Null on the current build, so the banner
        // only ever appears on a version that is no longer the latest.
        versionBanner: versionBanner(),
        sidebar: [
            {
                text: 'Introduction',
                items: [
                    { text: 'What is AeroWars?', link: '/guide/intro/what-is-aerowars' },
                    { text: 'Getting Started', link: '/guide/intro/getting-started' },
                ],
            },
            {
                text: 'Gameplay',
                items: [
                    { text: 'Maps & Arenas', link: '/guide/features/maps-arenas' },
                    { text: 'Match Flow', link: '/guide/features/match-flow' },
                    { text: 'Kits', link: '/guide/features/kits' },
                    { text: 'Spectating', link: '/guide/features/spectating' },
                    { text: 'Parties & Queues', link: '/guide/features/parties-queues' },
                    { text: 'Cosmetics', link: '/guide/features/cosmetics' },
                    { text: 'Cages', link: '/guide/features/cages' },
                ],
            },
            {
                text: 'Configuration',
                items: [
                    { text: 'Config Reference', link: '/guide/setup/config' },
                    { text: 'Loot & Events', link: '/guide/setup/loot-events' },
                ],
            },
            {
                text: 'Integrations',
                items: [
                    { text: 'Economy', link: '/guide/integrations/economy' },
                    { text: 'PlaceholderAPI', link: '/guide/integrations/placeholders' },
                    { text: 'TaleGuard', link: '/guide/integrations/taleguard' },
                ],
            },
            {
                text: 'Reference',
                items: [
                    { text: 'Commands', link: '/guide/reference/commands' },
                    { text: 'Permissions', link: '/guide/reference/permissions' },
                    { text: 'Placeholders', link: '/guide/reference/placeholders' },
                ],
            },
            {
                text: 'Releases',
                items: [
                    { text: 'Changelog', link: '/guide/changelog' },
                ],
            },
        ],
        socialLinks: [
            { icon: 'github', link: 'https://github.com/stoshelabs/aerowars' },
        ],
        search: {
            provider: 'local',
        },
        editLink: {
            pattern: 'https://github.com/stoshelabs/aerowars/edit/main/docs/:path',
            text: 'Edit this page on GitHub',
        },
        lastUpdated: {
            text: 'Last updated',
            formatOptions: { dateStyle: 'short', timeStyle: 'short' },
        },
        footer: {
            message: 'Released under the MIT License.',
            copyright: 'Copyright © 2026-present Stoshe Labs',
        },
    },
})
