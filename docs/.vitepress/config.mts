import { defineConfig } from 'vitepress'

// AeroWars documentation — plain VitePress. The docs always describe the current release; a
// hand-maintained Changelog page (guide/changelog.md) records what changed per version.
export default defineConfig({
    title: 'AeroWars',
    description: 'SkyWars for Hytale — a competitive Solo & Teams sky-island minigame plugin.',
    base: '/aerowars/', // GitHub Pages: https://stoshelabs.github.io/aerowars/
    lang: 'en-US',
    cleanUrls: true,
    lastUpdated: true,
    head: [
        ['link', { rel: 'icon', href: '/aerowars/icon.png' }],
        ['meta', { name: 'theme-color', content: '#41d1ff' }],
        ['meta', { property: 'og:title', content: 'AeroWars — SkyWars for Hytale' }],
        ['meta', { property: 'og:description', content: 'A competitive SkyWars-style minigame for Hytale: Solo & Teams, spawn cages, loot, kits, spectator, parties, cosmetics.' }],
        ['meta', { property: 'og:image', content: '/aerowars/logo.png' }],
    ],
    themeConfig: {
        logo: '/icon.png',
        nav: [
            { text: 'Home', link: '/' },
            { text: 'Guide', link: '/guide/intro/what-is-aerowars' },
            { text: 'Commands', link: '/guide/reference/commands' },
            { text: 'Changelog', link: '/guide/changelog' },
        ],
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
