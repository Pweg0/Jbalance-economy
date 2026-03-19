# JBalance

## What This Is

JBalance is a comprehensive economy mod for NeoForge 1.21.1, built for the ALL THE MONS modpack server. It provides a full virtual currency system with earnings (playtime milestones, mob kills, voting), player-to-player transactions, player shops, auctions, land sales with rent, FTB Chunks cost integration, and an API for the Cobblemon Arena PvP mod.

## Core Value

Players must be able to earn, spend, and transfer virtual currency reliably — the economy is the backbone that every other feature (shops, land, auctions, arena) depends on.

## Requirements

### Validated

- ✓ All values configurable via TOML config file — Phase 1
- ✓ Data persistence with MySQL/MariaDB support (SQLite fallback) — Phase 1
- ✓ Virtual currency system with per-player balance — Phase 2
- ✓ /eco pay command to send coins between players — Phase 2
- ✓ /eco balance to check own balance — Phase 2
- ✓ /eco top ranking of top 10 richest players — Phase 2
- ✓ Admin commands: /ecoadmin give, take, set — Phase 2

### Active
- [ ] Payload system: earnings by playtime milestones (1h, 2h, 5h, etc — configurable)
- [ ] Payload system: earnings by mobs killed (configurable per mob type)
- [ ] Vote rewards via Votifier/NuVotifier listener integration
- [ ] API for Cobblemon Arena PvP integration (give/take coins, betting, earnings ranking)
- [ ] Player shop system (details TBD in implementation phase)
- [ ] Auction system: /leilao item ofertainicial
- [ ] FTB Chunks integration: charge per claimed chunk + weekly maintenance cost
- [ ] Admin land/terrain system: define sale areas, protection, rent payments
- [ ] Admin commands: /ecoadmin for managing economy, terrains, config
- [ ] All values configurable via TOML config file
- [ ] Data persistence with MySQL/MariaDB support (SQLite fallback)

### Out of Scope

- Physical coin items — currency is virtual only
- Mobile app or web dashboard — server-side mod only
- Custom textures/resource pack — vanilla-compatible UI
- LuckPerms integration — using OP levels for permissions
- Kotlin — mod written in Java

## Context

- **Modpack:** ALL THE MONS (Cobblemon-based)
- **Platform:** NeoForge 1.21.1
- **Hosting:** Pterodactyl panel (RedHosting) with MySQL database available
- **Related mods:** Cobblemon Arena PvP (custom mod by user, needs API integration), FTB Chunks (claim system)
- **Vote system:** Uses Votifier/NuVotifier protocol for vote callback
- **Audience:** Modpack server players; admin configures via TOML
- **Language:** Portuguese (BR) for in-game messages, English for code/docs
- **Mod ID:** jbalance
- **Command prefix:** /eco (subcommands: pay, balance, top, etc)
- **Shops and terrain systems** are conceptually planned but details will be refined during their implementation phases

## Constraints

- **Platform**: NeoForge 1.21.1 — must use NeoForge API and conventions
- **Compatibility**: Must coexist with Cobblemon, FTB Chunks, and other ALL THE MONS mods
- **Config**: TOML format, standard NeoForge config system
- **Permissions**: OP level based (permission level 2 for user commands, 4 for admin)
- **Database**: MySQL primary (Pterodactyl hosting), SQLite fallback for local/single-player

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Virtual-only currency | Simpler, no item duplication exploits, cleaner API | — Pending |
| MySQL + SQLite dual support | Hosting provides MySQL, but SQLite needed for dev/fallback | — Pending |
| TOML config | NeoForge standard, familiar to server admins | — Pending |
| OP-level permissions | Simpler than LuckPerms dependency, sufficient for server | — Pending |
| Votifier protocol for votes | Industry standard for MC vote sites | — Pending |
| Playtime milestones (not continuous) | Clearer goals for players, less DB writes | — Pending |
| Java (not Kotlin) | Maximum NeoForge compatibility, standard tooling | — Pending |

---
*Last updated: 2026-03-19 after Phase 2 completion — Currency (balance, pay, top, admin commands)*
