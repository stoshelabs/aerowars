---
layout: home

hero:
  image:
    src: /logo.png
    alt: AeroWars — SkyWars for Hytale
  tagline: "Drop onto a floating island, loot the chests, and be the last one flying. Solo & Teams, spawn cages, kits, spectator mode, parties, cosmetics — in isolated match worlds that run any number of games at once."
  actions:
    - theme: brand
      text: What is AeroWars?
      link: /guide/intro/what-is-aerowars
    - theme: alt
      text: Getting Started
      link: /guide/intro/getting-started
    - theme: alt
      text: Commands
      link: /guide/reference/commands

features:
  - icon: 🏝️
    title: Maps & Arenas
    details: Build a map once — spawns, chests, cages, spectator point — and reuse it across many arenas, each with its own mode and optional random map pool.
  - icon: ⚔️
    title: Solo & Teams
    details: Free-for-all or co-op teams with configurable team size, spawn cages that shatter on start, weighted loot tiers, and scheduled in-match events.
  - icon: 🎒
    title: Kits & Cosmetics
    details: Selectable kits (free, permission-locked, or purchasable) plus cage themes, kill effects, victory shows, and movement trails.
  - icon: 👻
    title: Spectator & Parties
    details: Dead players fly as invulnerable spectators with tracker tools; parties queue together with keep-together or auto-split matchmaking.
  - icon: 📊
    title: Stats & Leaderboards
    details: Kills, wins, KDR and more — with optional SQL persistence (SQLite / MySQL / MariaDB / Postgres) and a JSON fallback.
  - icon: 🔌
    title: Integrations
    details: Optional economy rewards, a PlaceholderAPI expansion, and a TaleGuard protection hook — all soft dependencies, none required.
---

<style>
:root {
  --vp-home-hero-image-filter: drop-shadow(0 12px 40px rgba(30, 144, 255, 0.35));
}
/* Left-align the hero at ALL widths (VitePress centers a has-image hero below 960px) and stack the
   logo ABOVE the tagline. */
.VPHero.has-image .container {
  flex-direction: column;
  align-items: flex-start;
  text-align: left;
}
.VPHero .main {
  order: 2;
}
.VPHero.has-image .tagline {
  margin: 0;
}
.VPHero.has-image .actions {
  justify-content: flex-start;
}
.VPHero .image {
  order: 1;
  margin: 0 0 1.5rem;      /* replace the default negative margins */
  max-width: 100%;
}
/* The default hero image sits absolutely-positioned and centered inside a fixed 320px box; make it
   flow at its natural size, aligned left. */
.VPHero .image-container {
  position: static;
  margin: 0;
  justify-content: flex-start;
  width: auto;
  height: auto;
  transform: none;
}
.VPHero .image-bg { display: none; }
.VPHero .image-src {
  position: static;
  transform: none;
  width: auto;
  max-width: min(100%, 420px);
  max-height: none;
}
</style>
