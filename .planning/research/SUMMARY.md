# Project Research Summary

**Project:** JBalance — NeoForge 1.21.1 Economy Mod (ALL THE MONS / Cobblemon)
**Domain:** Minecraft server economy mod, NeoForge platform, Brazilian server context
**Researched:** 2026-03-19
**Confidence:** MEDIUM-HIGH overall (stack HIGH, features MEDIUM-HIGH, architecture HIGH, pitfalls MEDIUM-HIGH)

## Executive Summary

JBalance is a comprehensive server economy mod for a Brazilian Cobblemon modpack running NeoForge 1.21.1. The correct approach is to build a layered system: a database-backed virtual currency core (Phase 1), passive earning mechanisms (Phase 2), then progressively more complex marketplace and integration features. All decisions flow from one constraint — the currency storage layer is the root dependency of every other feature, so it must be built atomically-correct and asynchronously from day one. There is no viable path to retrofitting these properties later.

The recommended stack is well-established: Java 21 with NeoForge 21.1.220, ModDevGradle 2.0.x, HikariCP 7.0.2 for connection pooling, MySQL Connector/J 9.6.0 for production, and xerial sqlite-jdbc 3.51.3.0 for local development. All three JDBC libraries must be bundled via NeoForge's jarJar system. Architecture follows a strict Service + Repository pattern — commands and event handlers call services, services call repositories, SQL never leaks upward. This layering is non-negotiable because it prevents business logic from scattering and makes the dual-database (MySQL prod / SQLite dev) abstraction transparent to callers.

The dominant risks are concurrency (balance race conditions enabling double-spend), thread safety (blocking the server tick loop with synchronous DB calls), economy design (inflation with no currency sinks), and item integrity (duplication exploits in shops and auctions via disconnect state desync). All four must be designed in from the start, not retrofitted. Two integration areas — FTB Chunks event API and Cobblemon Arena's Kotlin-based event system — have LOW confidence in available documentation and will require source-level investigation during their respective implementation phases.

## Key Findings

### Recommended Stack

The entire stack is mature and well-documented through official NeoForge sources. Java 21 is mandatory (NeoForge 21.1.x ships Java 21 to end users). ModDevGradle 2.0.x is the current recommended build plugin, replacing NeoGradle for new single-version projects. The database stack (HikariCP + MySQL Connector/J + sqlite-jdbc) is the same pattern used widely across Minecraft mod and plugin ecosystems and has no surprises — the only gotcha is that all three must go through jarJar bundling to be available at runtime.

**Core technologies:**
- Java 21 + NeoForge 21.1.220: mandatory platform — all hooks, events, config, networking, and GUI live here
- ModDevGradle 2.0.x: official recommended build plugin for new projects as of January 2025
- HikariCP 7.0.2: JDBC connection pool — industry standard, Java 21 compatible, resolves virtual thread CPU issue present in 6.x
- MySQL Connector/J 9.6.0 (`com.mysql` groupId): production database driver — new groupId is required; old `mysql:mysql-connector-java` is abandoned
- xerial sqlite-jdbc 3.51.3.0: development / fallback DB driver — bundles native binaries for all platforms, no separate install
- NeoForge ModConfigSpec (SERVER type): TOML configuration — native, world-synced, no extra dependency
- NeoForge CustomPacketPayload: client-server networking — `SimpleChannel` is removed in NeoForge and must not be used
- NeoForge AbstractContainerScreen / AbstractContainerMenu: GUI pattern for shop and auction screens

**Critical version note:** FTB Chunks 2101.1.14 is confirmed for NeoForge 1.21.1 but its server-side API for charge-per-claim is undocumented publicly. This needs source-level investigation.

### Expected Features

Currency storage, commands, and earning mechanics are non-negotiable table stakes. The feature set that makes JBalance worth building (versus using an existing mod) is the integration depth: Cobblemon mob kill rewards, Votifier vote rewards in the NeoForge ecosystem where they don't natively exist, FTB Chunks land economics, and the Cobblemon Arena PvP API. No single existing NeoForge mod for 1.21.1 combines all of these.

**Must have (table stakes) — ship at launch:**
- Per-player balance storage with MySQL + SQLite fallback
- `/eco balance`, `/eco pay`, `/eco top` command suite
- Admin give / take / set balance commands
- Per-mob-type kill rewards (configurable by mob ID, including Cobblemon entities)
- Playtime milestone rewards (configurable thresholds)
- TOML configuration for all reward values and DB connection
- Portuguese (BR) localized messages — this is a Brazilian server; English messages are friction

**Should have (competitive differentiators) — add after v1 validation:**
- Vote rewards via Votifier/NuVotifier TCP listener (uncommon in NeoForge ecosystem — high value)
- Cobblemon Arena PvP API (EntityCapability surface — expose early, wire when Arena mod is ready)
- Player shop system
- FTB Chunks chunk-claim cost and weekly maintenance rent

**Defer (v2+):**
- Auction system (`/leilao`) — player shops must come first to validate item trading patterns
- Admin land / terrain sale system — significant design and DB complexity
- Economy analytics / transaction logs

**Anti-features to reject:** physical coin items (duplication exploits), LuckPerms integration (OP-level gating is sufficient), multi-currency support (fragmented economy), dynamic buy-back pricing (arbitrage exploit).

### Architecture Approach

The architecture is a classic layered system: Entry Points (commands, event handlers, Votifier TCP server, API surface) → Service Layer (EconomyService, PayloadService, ShopService, AuctionService, TerrainService) → Data Layer (DatabaseManager + repository classes). Services own all business rules; commands and event handlers are thin dispatchers. The dual-database abstraction lives entirely in DatabaseManager — repositories receive a DataSource and are never aware of which engine they talk to.

**Major components:**
1. DatabaseManager — HikariCP pool, schema migration, MySQL/SQLite routing at startup; every other component depends on this
2. EconomyService — balance CRUD and transfer with atomic SQL writes; the central hub called by all entry points
3. PayloadService — playtime milestone tracking (in-memory counter, flushed to DB at thresholds) and mob kill reward dispatch
4. VotifierServer — isolated TCP listener thread; parses vote packets and marshals to game thread via `server.execute()`
5. ShopService / AuctionService — marketplace business logic with two-phase commit pattern for item escrow
6. EconomyCapability — EntityCapability exposing IEconomyAPI for Cobblemon Arena soft-dependency
7. FTBChunksEventHandler — conditionally loaded via `ModList.get().isLoaded("ftbchunks")` to prevent crash if FTB absent

**Key pattern:** All integration points (FTB Chunks, Cobblemon Arena, Votifier) are isolated in dedicated handler classes with conditional class loading guards. This prevents crashes when optional dependencies are absent.

### Critical Pitfalls

1. **Balance race condition (double-spend)** — use atomic SQL (`UPDATE ... WHERE coins >= ?`), per-player in-flight locks, and never read from cache before a write. Must be built correctly in Phase 1; retrofitting atomicity is a rewrite.

2. **Blocking the server thread with DB I/O** — wrap all DB operations in `CompletableFuture.supplyAsync()` with a dedicated executor; re-enter game thread via `server.execute()`. Never call `future.get()` on the main thread. Synchronous MySQL over a network will freeze ticks.

3. **Client-only code crashing dedicated servers** — annotate all client subscribers with `@EventBusSubscriber(value = Dist.CLIENT)`; keep rendering code in a `client/` package; run `./gradlew runServer` before every release.

4. **Economy inflation with no currency sink** — design sinks (FTB Chunks claim fees, auction listing fees, land rent) alongside every earning source. Shipping earn-only systems causes exponential inflation; new players can never catch up.

5. **Item duplication via disconnect state desync in shops/auctions** — use two-phase commit: write DB record as `PENDING`, remove from inventory, mark `ACTIVE`. Audit `PENDING` records on server startup and cancel them.

## Implications for Roadmap

Research strongly suggests a 7-phase structure following the build-order from ARCHITECTURE.md, with each phase being independently deployable and testable before the next begins.

### Phase 1: Foundation — Core Currency and Project Setup

**Rationale:** Currency storage is the root dependency of every single feature. Without an atomic, async-safe DB layer, every subsequent phase inherits race condition risk. This phase must also establish the project structure with correct sided separation before any feature code is written — sided crashes are unrecoverable without a fix and redeploy.

**Delivers:** Working `/eco balance`, `/eco pay`, `/eco top` commands; admin give/take/set; MySQL/SQLite dual-DB with HikariCP; TOML config; PT-BR localization skeleton; balance sync packet to client.

**Addresses features:** All P1 table stakes (currency storage, balance commands, admin commands, TOML config, PT-BR messages).

**Avoids pitfalls:** Balance race condition (atomic SQL from day one), server thread blocking (async DB layer established), client-code crash (sided structure set up), config correction loop (ModConfigSpec defined correctly).

**Research flag:** Standard patterns — well-documented via NeoForge official docs. No additional research-phase needed.

---

### Phase 2: Earning System — Mob Kills and Playtime Milestones

**Rationale:** Once currency exists and persists correctly, earning mechanics give players a reason to accumulate it. Mob kill rewards and playtime milestones are closely coupled (both are passive earning; both use the same PlayerEventHandler / TickEventHandler infrastructure) and deliver the core gameplay loop.

**Delivers:** Per-mob-type configurable kill rewards, playtime milestone rewards (in-memory tracking, DB-flushed at milestones), player session tracking (login/logout), correct tick-based playtime accumulation without DB thrash.

**Addresses features:** Per-mob kill rewards (P1), playtime milestones (P1).

**Avoids pitfalls:** Per-tick DB writes (in-memory counter, flush only at milestones or logout), inflation design (reward rates set conservatively and made configurable).

**Research flag:** Standard patterns — NeoForge LivingDeathEvent and ServerTickEvent are well-documented. No additional research-phase needed.

---

### Phase 3: Vote Rewards

**Rationale:** Vote rewards are a standalone integration (Votifier TCP protocol) that depends only on Phase 1 EconomyService. Implementing it before shops keeps complexity bounded and delivers a high-value feature before the more complex marketplace phases.

**Delivers:** Votifier v1/v2 TCP listener (isolated background thread), vote-to-reward dispatch via `server.execute()`, offline vote queuing (pending votes table), vote reward config in TOML.

**Addresses features:** Vote rewards (P2).

**Avoids pitfalls:** Votifier on main thread (dedicated listener thread, `server.execute()` dispatch), offline vote loss (pending votes table).

**Research flag:** Moderate complexity. The raw Votifier v1/v2 protocol and RSA/HMAC verification patterns are documented but non-trivial. Consider `/gsd:research-phase` if the Votifier for NeoForge optional dependency (Modrinth) can be used instead — it would eliminate the TCP listener implementation entirely.

---

### Phase 4: Player Shops

**Rationale:** Player shops transform a passive economy (earn from mobs/votes) into an active marketplace. Shops must precede the auction system — they share item listing concepts, and building shops first validates item escrow patterns before the more complex auction state machine is built.

**Delivers:** Player shop listing, browse, and purchase; two-phase commit item escrow; shop listing/expiry fees (currency sink); shop GUI (AbstractContainerScreen).

**Addresses features:** Player shops (P2).

**Avoids pitfalls:** Item duplication via disconnect (two-phase commit mandatory), inflation (listing fees as currency sink).

**Research flag:** Standard pattern for container GUIs in NeoForge is well-documented. The two-phase commit pattern for item escrow is the critical design decision — no additional research-phase needed, but the implementation checklist in PITFALLS.md must be followed.

---

### Phase 5: Auction System

**Rationale:** Auctions build on the item escrow and listing patterns established in Phase 4. The state machine (open → bids → expired → resolved) adds significant complexity; it must not be attempted before shop patterns are validated.

**Delivers:** `/leilao` auction command, timed bidding with item escrow, winner resolution, outbid refunds, auction expiry notification on login, listing fees.

**Addresses features:** Auction system (P3).

**Avoids pitfalls:** Item duplication (two-phase commit, same pattern as shops but with bid state), auction state on restart (all state in DB, recoverable on startup).

**Research flag:** Standard patterns. No additional research-phase needed — PITFALLS.md "Looks Done But Isn't" checklist covers the edge cases.

---

### Phase 6: FTB Chunks Integration and Land System

**Rationale:** Land economics (chunk claim costs, weekly rent, admin terrain sales) are the most complex integration because they require FTB Chunks' server-side event API, which is not well-documented publicly. Both FTB Chunks and the admin terrain sale system deal with land economics and must share a DB schema — designing them together prevents duplication.

**Delivers:** FTB Chunks chunk-claim cost (charged on claim, refusable via event cancel if balance insufficient), weekly maintenance rent scheduler, admin terrain zone definition and sale/rent system, conditional class loading guard.

**Addresses features:** FTB Chunks cost integration (P2), admin land/terrain sale (P3).

**Avoids pitfalls:** FTB Chunks wrong event bus, insufficient balance during claim (cancel the event), weekly fee causing negative balance (clamp + claim revocation).

**Research flag:** NEEDS `/gsd:research-phase`. FTB Chunks server-side API for charge-per-claim is LOW confidence — documented event names and API surface require source-level investigation. Do not begin implementation without confirming the exact event API.

---

### Phase 7: Cobblemon Arena PvP API

**Rationale:** The Arena API is a soft-dependency integration with an external mod whose API surface is determined by the Arena mod team. JBalance can expose the EntityCapability surface and IEconomyAPI interface at any time; the actual wiring happens when the Arena mod is ready. This phase is a parallel workstream, not strictly sequential — it can begin after Phase 1 (EconomyService exists) even while Phases 2-6 are in progress.

**Delivers:** IEconomyAPI interface (give, take, getBalance), EconomyCapability EntityCapability registration, currency earnings leaderboard filtered by Arena source, Arena mod integration guide.

**Addresses features:** Cobblemon Arena PvP API (P2), Arena earnings leaderboard (P2).

**Avoids pitfalls:** Kotlin interop complexity (use Cobblemon's Java-friendly API surface only), hard dependency crash (EntityCapability query returns null if JBalance absent — Arena handles gracefully).

**Research flag:** NEEDS `/gsd:research-phase`. Cobblemon's battle result event API uses Kotlin-based events (`BattleVictoryEvent` or equivalent). Java interop with Kotlin APIs is non-trivial. Confirm the exact callback contract before implementing.

---

### Phase Ordering Rationale

- Phase 1 is first because it is the literal root dependency of every other feature. This is confirmed by the FEATURES.md dependency graph: "Currency Storage + Balance Commands is the root dependency of everything."
- Phases 2 and 3 are ordered before shops because they are simpler integrations (event hooks and a TCP listener) that validate the earning side of the economy. Shops should not launch until players have balances to spend.
- Phases 4 and 5 are ordered shops-then-auctions because auctions share item escrow logic with shops, and building shops first validates the pattern before the more complex auction state machine.
- Phase 6 (FTB Chunks / Land) is intentionally late because its API surface requires source-level investigation and because land economics are only meaningful once an active player economy exists.
- Phase 7 (Arena API) can technically begin immediately after Phase 1 as a parallel workstream. The roadmapper may choose to interleave it with earlier phases rather than placing it strictly last.

### Research Flags

Phases needing deeper research during planning:
- **Phase 6 (FTB Chunks / Land):** FTB Chunks server-side claim event API is LOW confidence — requires reading FTB Chunks source code to identify correct event names and API surface
- **Phase 7 (Cobblemon Arena API):** Cobblemon uses Kotlin-based events; Java interop and exact battle callback API need investigation before implementation can begin
- **Phase 3 (Vote Rewards) — conditional:** If using the "Votifier for NeoForge" optional mod dependency instead of a raw TCP listener, research that mod's event API; if implementing raw TCP, the Votifier v1/v2 protocol is documented but the RSA/HMAC verification needs a working reference implementation

Phases with standard patterns (skip research-phase):
- **Phase 1 (Foundation):** All patterns (ModDevGradle, HikariCP jarJar, ModConfigSpec, CustomPacketPayload) are fully documented in official NeoForge docs
- **Phase 2 (Earning System):** LivingDeathEvent, ServerTickEvent, PlayerTickEvent.Post are standard NeoForge patterns
- **Phase 4 (Player Shops):** AbstractContainerScreen GUI pattern is well-documented; two-phase commit is a design decision, not a research question
- **Phase 5 (Auctions):** Builds on shop patterns; no new API surface required

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Core stack (NeoForge, ModDevGradle, HikariCP, MySQL Connector/J, sqlite-jdbc) confirmed via official docs and Maven Central. FTB Chunks API is the only LOW confidence area. |
| Features | MEDIUM-HIGH | Table stakes and earning features are HIGH confidence. Votifier integration in NeoForge ecosystem is MEDIUM (less documented than Bukkit). FTB Chunks and Cobblemon Arena integrations are MEDIUM due to limited public API docs. |
| Architecture | HIGH | Service + Repository pattern, event handler separation, EntityCapability for inter-mod API, and dual-DB abstraction all confirmed via official NeoForge documentation and established community patterns. |
| Pitfalls | MEDIUM-HIGH | Core pitfalls (race conditions, thread safety, sided crashes, inflation) are HIGH confidence via NeoForge docs and established Minecraft mod/plugin community knowledge. FTB Chunks and Cobblemon-specific gotchas are MEDIUM. |

**Overall confidence:** MEDIUM-HIGH

### Gaps to Address

- **FTB Chunks server-side event API:** The exact event name for chunk claim interception, whether it can be canceled, and how to query claimed chunk counts are not confirmed from public documentation. Resolve by reading FTB Chunks source (GitHub) before Phase 6 begins.
- **Cobblemon Arena battle callback:** Cobblemon uses a Kotlin event system. The Java-accessible API for `BattleVictoryEvent` (or equivalent) needs verification against Cobblemon's actual source before Phase 7 can be scoped. The Arena mod team must also define their expected API contract.
- **Votifier for NeoForge mod depth:** The optional "Votifier for NeoForge" mod exists on Modrinth for 1.21.1 but its page was not fully accessible during research. If used as an optional dependency instead of a raw TCP implementation, its event API needs verification. Decision: raw TCP vs. optional mod dependency should be made before Phase 3 planning.
- **HikariCP virtual thread behavior:** HikariCP 6.x had reported high CPU issues with Java 21 virtual threads; 7.0.x is stated to resolve this but this was not verified in a NeoForge runtime context specifically. Monitor on first deployment.

## Sources

### Primary (HIGH confidence)
- `https://docs.neoforged.net/docs/1.21.1/gettingstarted/` — Java 21 requirement, MDK setup
- `https://neoforged.net/news/moddevgradle2/` — ModDevGradle 2.0.x stable, recommended for new projects
- `https://docs.neoforged.net/docs/1.21.1/misc/config/` — ModConfigSpec, SERVER type, config correction loop behavior
- `https://docs.neoforged.net/toolchain/docs/dependencies/jarinjar/` — jarJar bundling pattern
- `https://docs.neoforged.net/docs/networking/` — CustomPacketPayload, RegisterPayloadHandlersEvent
- `https://docs.neoforged.net/docs/1.21.1/gui/menus/` — AbstractContainerMenu, MenuType registration
- `https://docs.neoforged.net/docs/concepts/events/` — event handler patterns, @EventBusSubscriber
- `https://docs.neoforged.net/docs/1.21.5/inventories/capabilities/` — EntityCapability API
- `https://central.sonatype.com/artifact/com.zaxxer/HikariCP` — HikariCP 7.0.2
- `https://mvnrepository.com/artifact/com.mysql/mysql-connector-j` — MySQL Connector/J 9.6.0, new groupId
- `https://mvnrepository.com/artifact/org.xerial/sqlite-jdbc` — xerial sqlite-jdbc 3.51.3.0
- `https://github.com/NeoForgeMDKs/MDK-1.21.1-NeoGradle` — NeoForge 21.1.220, Parchment 2024.11.17

### Secondary (MEDIUM confidence)
- `https://github.com/The-Fireplace-Minecraft-Mods/Grand-Economy` — NeoForge economy mod reference architecture
- `https://modrinth.com/mod/votifier-for-neoforge` — Votifier for NeoForge 1.21.1 existence confirmed
- `https://www.curseforge.com/minecraft/mc-mods/ftb-chunks-forge/files/7608681` — FTB Chunks 2101.1.14 for 1.21.1 confirmed
- `https://modrinth.com/mod/cobblemon-economy` — Cobblemon economy mod feature reference
- `https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing` — HikariCP connection pool sizing
- `https://www.spigotmc.org/threads/tutorial-async-database-queries-using-a-connection-pool.114282/` — async DB pattern (applicable to NeoForge)
- `https://essentialsx.net/` — economy feature baseline reference
- `https://github.com/NuVotifier/NuVotifier/wiki/Setup-Guide` — NuVotifier protocol documentation

### Tertiary (LOW confidence)
- FTB Chunks server-side API for charge-per-claim — not found in public docs; needs source investigation
- Cobblemon Arena `BattleVictoryEvent` or equivalent — not confirmed; needs Cobblemon source investigation
- `https://modrinth.com/mod/votifier-for-neoforge` — page content not fully accessible during research

---
*Research completed: 2026-03-19*
*Ready for roadmap: yes*
