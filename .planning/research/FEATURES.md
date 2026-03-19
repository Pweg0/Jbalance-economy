# Feature Research

**Domain:** NeoForge 1.21.1 server economy mod (Cobblemon / ALL THE MONS modpack)
**Researched:** 2026-03-19
**Confidence:** MEDIUM — core economy feature landscape is HIGH confidence; Cobblemon-specific integrations are MEDIUM; NeoForge-specific ecosystem gaps vs Bukkit/Spigot world are LOW (few direct comparables on Forge)

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features players and server admins assume exist. Missing these = the mod feels broken or incomplete before it begins.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Per-player balance storage | Economy is meaningless without persistent balances | LOW | Must survive restarts; needs DB backend |
| `/eco balance` (self) | Universal in every economy system; first command players learn | LOW | Shows own balance; localized output |
| `/eco pay <player> <amount>` | Player-to-player transfer is the foundation of any multiplayer economy | LOW | Must validate: sufficient funds, valid target, positive amount |
| Admin give/take/set balance | Server owner must be able to manage the economy | LOW | OP-gated; essential for onboarding and corrections |
| Balance leaderboard (`/eco top`) | Players expect to compete; ranking is inherent social glue | LOW-MEDIUM | Top 10 richest; DB query with ordering |
| TOML configuration file | NeoForge servers configure via TOML; hardcoded values are unusable | LOW | Currency name, starting balance, reward values |
| Data persistence with DB | Balances must survive crashes and restarts | MEDIUM | MySQL primary, SQLite fallback — standard NeoForge pattern |
| Earn currency (at minimum one source) | If players can't earn money, economy never starts | MEDIUM | Mob kills are the lowest-friction entry point |

### Differentiators (Competitive Advantage)

Features that go beyond what players assume. These are where JBalance competes and adds value specific to the ALL THE MONS server context.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Playtime milestone rewards | Rewards loyalty and session length; gives players goals without constant grind | MEDIUM | Configurable thresholds (1h, 2h, 5h…); one-time trigger per milestone; needs playtime tracking per player |
| Per-mob-type kill rewards | Incentivizes exploring different Cobblemon encounters and combat; configurable by mob ID | MEDIUM | Config map of mob ID → reward amount; must handle Cobblemon entity types |
| Vote rewards (Votifier/NuVotifier) | Industry standard server promotion loop; players vote on listing sites for currency | MEDIUM | Requires Votifier protocol listener on the NeoForge side — uncommon in Forge ecosystem, more common in Bukkit |
| Player shop system | Transforms admin-run economy into player-driven marketplace | HIGH | Chest-based or command-based; buy/sell, item listing, shop browsing |
| Auction system (`/leilao`) | Creates real-time bidding event; drives engagement and price discovery | HIGH | Timed bidding, item escrow, winner resolution; significant state machine complexity |
| FTB Chunks integration (chunk cost + weekly rent) | Ties land ownership to economy; creates ongoing currency drain (inflation sink) | HIGH | Hook into FTB Chunks API; charge on claim, periodic deduction for maintenance |
| Admin-defined land/terrain sale system | Structured real-estate economy; admin defines zones, players buy or rent them | HIGH | Define zones by coordinates, sale price, rent price, protection handover |
| Cobblemon Arena PvP API | Betting and earnings integration with custom PvP mod; makes arena fights economically meaningful | MEDIUM | Java API surface: give/take coins, query balance, post-battle hooks — internal integration, not a public plugin API |
| Currency earnings leaderboard for Arena | Shows top earners from PvP; separate from `/eco top`; creates competitive Arena identity | LOW-MEDIUM | Separate query filtered by Arena source transactions |
| Portuguese (BR) localized messages | ALL THE MONS is a Brazilian server; native-language messages increase adoption and feel polished | LOW | All player-facing strings in pt-BR; config or resource bundle |

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem appealing but create disproportionate complexity, exploit surface, or maintenance burden relative to the value they deliver.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Physical coin items (in-game currency items) | Feels immersive, tradeable by hand | Item duplication exploits (dupe bugs) break the economy instantly; inventory management friction; incompatible with virtual-only API design | Virtual-only currency is the correct call; already decided in PROJECT.md |
| LuckPerms / permission plugin integration | Fine-grained permission control per player/group | Adds a hard dependency on LuckPerms; OP-level gating is sufficient for a single-server modpack context | OP-level permissions (already decided) |
| Web dashboard / external economy UI | Real-time balance viewing outside game | Separate service to host and maintain; auth complexity; scope creep away from mod | In-game commands cover all use cases |
| Market price history / analytics | Price transparency, economy health tracking | Requires storing all transaction history; significant DB schema complexity; not requested by server audience | Simple balance top is sufficient; defer analytics entirely |
| Multi-currency support | Different currencies for different systems (PokeDollars, Arena coins, etc.) | Fragmented economy creates confusion; inter-currency exchange rates are a balance nightmare; much higher implementation cost | Single currency with tagged transaction sources; distinguish by source label in DB, not separate balances |
| Real-time economy events / WebSocket push | Instant notifications for balance changes | Infrastructure requirement far exceeds value; Minecraft server can't host WebSocket reliably | Chat notifications on relevant events (pay received, auction won) are sufficient |
| Automated buy-back / server shop with dynamic pricing | Server buys items from players at fluctuating prices | Classic exploit: arbitrage between player shops and server shop at different price points breaks the economy | Fixed admin shop prices, or no server buy-back at all |

---

## Feature Dependencies

```
[Currency Storage + Balance Commands]
    └──requires──> [DB Layer (MySQL/SQLite)]

[Pay Command]
    └──requires──> [Currency Storage + Balance Commands]

[Balance Leaderboard]
    └──requires──> [Currency Storage + Balance Commands]

[Playtime Milestone Rewards]
    └──requires──> [Currency Storage + Balance Commands]
    └──requires──> [Playtime Tracking per Player]

[Per-Mob Kill Rewards]
    └──requires──> [Currency Storage + Balance Commands]
    └──requires──> [NeoForge mob kill event hook]

[Vote Rewards]
    └──requires──> [Currency Storage + Balance Commands]
    └──requires──> [Votifier/NuVotifier TCP listener]

[Player Shops]
    └──requires──> [Currency Storage + Balance Commands]
    └──requires──> [Pay Command] (shop purchase = directed pay)

[Auction System]
    └──requires──> [Currency Storage + Balance Commands]
    └──requires──> [Item escrow mechanism]
    └──enhances──> [Player Shops] (alternative sale channel)

[FTB Chunks Integration]
    └──requires──> [Currency Storage + Balance Commands]
    └──requires──> [FTB Chunks API hooks]

[Land/Terrain Sale System]
    └──requires──> [Currency Storage + Balance Commands]
    └──requires──> [Zone definition data model]
    └──optionally-enhances──> [FTB Chunks Integration] (protection handover)

[Cobblemon Arena PvP API]
    └──requires──> [Currency Storage + Balance Commands]
    └──requires──> [Cobblemon Arena mod (external dependency)]
    └──enhances──> [Balance Leaderboard] (Arena earnings visible in top rankings)

[Admin Commands (/ecoadmin)]
    └──requires──> [Currency Storage + Balance Commands]
    └──enhances──> All other features (config and override surface)
```

### Dependency Notes

- **Currency Storage + Balance Commands is the root dependency of everything.** This must be Phase 1 or no other feature can be built.
- **DB Layer gates Currency Storage:** Without persistent storage, balance commands are in-memory and useless across restarts. DB must be set up before any gameplay feature.
- **Vote Rewards has an unusual dependency:** NeoForge does not natively support Votifier; a TCP socket listener must be implemented within the mod. This is atypical for a Forge mod — most vote integrations exist in Bukkit. Treat this as higher implementation risk than it appears.
- **Auction System conflicts with Player Shops (scope-wise):** Building both simultaneously risks overlap in item escrow and listing logic. Implement shops first, then extract common patterns for auctions.
- **FTB Chunks Integration conflicts with Land/Terrain Sale System:** They both deal with land economics. FTB Chunks is chunk-based claiming with costs; Terrain Sale is admin-defined zones. Design the DB schema to accommodate both without duplication before implementing either.
- **Cobblemon Arena API requires the external mod to define its callback contract first.** JBalance can expose the API surface, but the Arena mod must be ready to consume it. Treat as a parallel workstream, not a blocking dependency.

---

## MVP Definition

### Launch With (v1)

Minimum viable product — what's needed for the server economy to be functional for players from day one.

- [ ] Currency Storage + DB layer (MySQL + SQLite fallback) — without this, nothing persists
- [ ] `/eco balance` — players must be able to check their money
- [ ] `/eco pay` — players must be able to send money to each other
- [ ] `/eco top` — leaderboard creates social engagement from day one
- [ ] Admin give/take/set commands — server owner must be able to manage the economy
- [ ] Per-mob kill rewards — passive earning; players earn just by playing Cobblemon
- [ ] Playtime milestone rewards — rewards loyalty; pairs naturally with mob kill rewards
- [ ] TOML configuration — all reward values configurable; hardcoded values are unusable
- [ ] Portuguese (BR) localized messages — this is a Brazilian server; non-localized messages are friction

### Add After Validation (v1.x)

Features to add once core currency loop is validated working.

- [ ] Vote Rewards (Votifier integration) — add once players are active and server is listed on vote sites; requires Votifier TCP implementation
- [ ] Cobblemon Arena PvP API — add once Arena mod team is ready to integrate; expose API surface early, wire up when Arena is ready
- [ ] Player Shop system — add once players have established balances and demand a marketplace
- [ ] FTB Chunks cost integration — add once chunk claiming usage is high enough to warrant economic gating

### Future Consideration (v2+)

Features to defer until core economy is mature and validated.

- [ ] Auction system — high complexity; player shops must come first to validate item trading patterns
- [ ] Admin land/terrain sale system — significant design and DB complexity; defer until shop/FTB pattern is solid
- [ ] Admin economy analytics / transaction logs — useful for balance tuning but not required for launch

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Currency Storage + DB Layer | HIGH | MEDIUM | P1 |
| `/eco balance` | HIGH | LOW | P1 |
| `/eco pay` | HIGH | LOW | P1 |
| `/eco top` leaderboard | HIGH | LOW | P1 |
| Admin give/take/set | HIGH | LOW | P1 |
| Per-mob kill rewards | HIGH | MEDIUM | P1 |
| Playtime milestone rewards | HIGH | MEDIUM | P1 |
| TOML configuration | HIGH | LOW | P1 |
| PT-BR localization | HIGH (for this server) | LOW | P1 |
| Vote rewards (Votifier) | MEDIUM | HIGH | P2 |
| Cobblemon Arena PvP API | HIGH (unique value) | MEDIUM | P2 |
| Player shops | HIGH | HIGH | P2 |
| FTB Chunks integration | MEDIUM | HIGH | P2 |
| Auction system | MEDIUM | HIGH | P3 |
| Admin terrain/land sale | MEDIUM | HIGH | P3 |

**Priority key:**
- P1: Must have for launch
- P2: Should have, add when possible
- P3: Nice to have, future consideration

---

## Competitor Feature Analysis

Economy mods and plugins surveyed for feature baseline:

| Feature | EssentialsX (Bukkit) | Grand Economy (Forge) | Cobblemon Economy (Fabric/NeoForge) | JBalance approach |
|---------|---------------------|----------------------|-------------------------------------|-------------------|
| Per-player balance | Yes | Yes | Yes | Yes — MySQL/SQLite |
| Balance / pay / top commands | Yes (full suite) | Partial (balance, pay, wallet) | Unknown | Yes — full suite |
| Initial balance on first join | Yes | Yes (100 credits) | Unknown | Yes — configurable |
| Daily/passive income | No (Vault handles) | Yes (50 credits/day) | Unknown | Playtime milestones (not daily) |
| Mob kill rewards | No (separate plugin) | No | Unknown | Yes — per-mob config |
| Vote rewards | Via VotingPlugin | No | No | Yes — Votifier listener built-in |
| Player shops | Via ChestShop | No | Yes | Yes — planned P2 |
| Auction house | Via AuctionHouse plugin | No | Unknown | Yes — planned P3 |
| Land/chunk economy | Via Lands plugin | No | No | Yes — FTB Chunks + terrain |
| Custom mod API | Vault API | Basic API | Unknown | Yes — Cobblemon Arena API |
| Config format | YAML | TOML (via Fireplace Lib) | TOML | TOML (NeoForge native) |
| Platform | Bukkit/Spigot/Paper | Forge/NeoForge/Fabric | Fabric/NeoForge | NeoForge only |

**Key gap JBalance fills:** No existing NeoForge 1.21.1 economy mod combines all of: mob kill rewards, playtime milestones, vote integration, player shops, auctions, FTB Chunks cost integration, admin land sales, and a custom PvP mod API in one package. Most Forge mods provide only the core currency layer and rely on separate plugins — which don't exist in the Forge ecosystem.

---

## Sources

- [Minecraft Economy Plugins Guide - Oraxen Blog](https://oraxen.com/blog/minecraft-economy-plugins-guide-server) — feature landscape for economy plugins (MEDIUM confidence, Bukkit-focused)
- [Grand Economy GitHub - smallxiaoxiaoB](https://github.com/smallxiaoxiaoB/Grand-Economy) — Forge economy mod feature reference (HIGH confidence, official repo)
- [Grand Economy - Modrinth](https://modrinth.com/mod/grand-economy) — NeoForge economy mod for comparison
- [Cobblemon Economy - Modrinth](https://modrinth.com/mod/cobblemon-economy) — Cobblemon-specific economy reference
- [Cobblemon Sport Betting - Modrinth](https://modrinth.com/mod/cobblemon-sport-betting) — betting/economy precedent in Cobblemon ecosystem
- [VotingPlugin - SpigotMC](https://www.spigotmc.org/resources/votingplugin.15358/) — vote reward system feature reference (HIGH confidence)
- [NuVotifier & Rewards - Apex Hosting](https://apexminecrafthosting.com/guides/minecraft/plugins/nuvotifier-rewards-plugin/) — Votifier integration patterns
- [Minecraft Forum — Thoughts on Minecraft Economies](https://www.minecraftforum.net/forums/support/server-support-and/1916959-thoughts-on-minecraft-economies) — inflation and exploit pitfalls (MEDIUM confidence)
- [EssentialsX](https://essentialsx.net/) — economy feature baseline reference (HIGH confidence)

---

*Feature research for: JBalance — NeoForge 1.21.1 Economy Mod (ALL THE MONS / Cobblemon)*
*Researched: 2026-03-19*
