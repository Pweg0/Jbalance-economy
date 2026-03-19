# Pitfalls Research

**Domain:** NeoForge 1.21.1 economy mod (JBalance — virtual currency, shops, auctions, land, Cobblemon Arena integration)
**Researched:** 2026-03-19
**Confidence:** MEDIUM-HIGH (NeoForge-specific claims HIGH via official docs; economy balance claims MEDIUM via community sources)

---

## Critical Pitfalls

### Pitfall 1: Balance Race Condition (Double-Spend / Negative Balance)

**What goes wrong:**
Two concurrent operations read the same balance, both pass the "sufficient funds" check, then both deduct — leaving the player with a negative balance or allowing a payment they could not afford. Classic example: player spams `/eco pay` while simultaneously triggering an auction bid callback.

**Why it happens:**
Minecraft runs mostly on a single server thread, but async database callbacks re-enter game logic off-thread. If the balance is cached in memory and the DB write is deferred, a second read from the cache before the first write completes sees stale data.

**How to avoid:**
- Make all balance mutations atomic at the database layer (SQL `UPDATE balances SET coins = coins - ? WHERE uuid = ? AND coins >= ?` with row-lock, not read-then-write).
- Never read balance from memory cache and write separately. Cache reads for display; always write through to DB.
- Use `CompletableFuture` for async DB calls and re-enter game logic via `server.execute(() -> ...)` only after the DB confirms the transaction.
- Add a per-player in-flight lock (a `ConcurrentHashMap<UUID, AtomicBoolean>`) to reject overlapping mutation requests for the same player.

**Warning signs:**
- Players reporting negative balances after rapid commands.
- Balance in `/eco balance` differing from `/eco top` ranking.
- Database rows with negative coin values.

**Phase to address:** Phase 1 (Core Currency System) — must be built correctly from day one; retrofitting atomicity later is a rewrite.

---

### Pitfall 2: Blocking the Server Thread with Database I/O

**What goes wrong:**
Any synchronous JDBC call on the main server thread stalls the entire tick loop. A slow query (>50ms) causes TPS drop; a timed-out connection causes a server freeze. With MySQL over a network (Pterodactyl → remote MySQL), latency alone can be 5-30ms per query — fatal if synchronous.

**Why it happens:**
It is tempting to write `getBalance(player)` as a direct `SELECT` call. NeoForge does not enforce async discipline; the server won't crash, it just lags.

**How to avoid:**
- Use HikariCP connection pool (shade it into the mod jar via `jarJar` or shadow plugin). Configure `maximumPoolSize` to 5-10 for a small server.
- Wrap all DB queries in `CompletableFuture.supplyAsync(() -> ..., dbExecutor)` where `dbExecutor` is a dedicated `ExecutorService` (2-4 threads).
- Return results to the main thread via `server.execute(runnable)` before touching any game objects.
- Never call `future.get()` (blocking join) on the server thread.

**Warning signs:**
- TPS dropping to 10-15 when multiple players run economy commands simultaneously.
- Server log showing "Can't keep up!" warnings correlated with player transactions.
- JDBC timeout stack traces in logs.

**Phase to address:** Phase 1 (Core Currency System) — establish the async DB layer first; every other feature builds on top of it.

---

### Pitfall 3: Client-Side Code Executing on Dedicated Server (Sided Crash)

**What goes wrong:**
NeoForge loads mod classes on both client and server. Any reference to a client-only class (e.g., `net.minecraft.client.*`, `Minecraft.getInstance()`) in code that runs on a dedicated server causes a `ClassNotFoundException` or `ExceptionInInitializerError` crash at startup.

**Why it happens:**
Developers test in single-player (which runs both client and server in the same JVM), miss that a class is client-only, then ship and crash dedicated servers immediately.

**How to avoid:**
- Annotate all client-specific event subscribers with `@EventBusSubscriber(value = Dist.CLIENT, modid = "jbalance")`.
- Keep all rendering, HUD, and screen code in a `client/` package; never reference it from `common/` code.
- Run the dedicated server configuration in dev (`runServer` Gradle task) before each release — it will crash immediately if sided code is wrong.
- Use `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> clientCode())` for any conditional client logic.

**Warning signs:**
- Mod works in singleplayer but crashes on dedicated server startup.
- Stack trace contains `net.minecraft.client` class names in the cause chain.
- `Invalid dist` error in NeoForge crash report.

**Phase to address:** Phase 1 — set up the project structure with correct sided separation before writing any feature code.

---

### Pitfall 4: Economy Inflation — No Currency Sink

**What goes wrong:**
The economy only has sources (playtime rewards, mob kills, votes) and no drains. Over weeks, total currency in circulation grows exponentially. Prices in player shops rise to match, making the currency worthless to new players. Wealth gap between Day-1 players and new joiners becomes unbridgeable.

**Why it happens:**
Mods implement the "earn" side first (it's fun, it's visible) and treat shops/auctions as neutral transfers. Transfers are neutral — they move money, they don't remove it. Without active removal, money accumulates.

**How to avoid:**
- Design currency sinks from the start, not as an afterthought:
  - FTB Chunks claim fees and weekly maintenance are sinks (coins go to a void/admin account).
  - Auction listing fees (non-refundable percentage regardless of sale outcome).
  - Land/terrain rent payments go to admin pool, not recycled to players.
  - Shop listing fees or expiry fees.
- Make all sink amounts configurable in TOML so the admin can adjust without a redeployment.
- Log total supply periodically (on server startup, daily) so the admin can observe inflation trends.

**Warning signs:**
- `/eco top` shows top player balances growing 10x every week.
- New players report they cannot afford anything in shops.
- Players stop using the economy because everything is priced in millions and rewards are in hundreds.

**Phase to address:** Phase 1 (define reward rates) and Phase 2/3 (shops, land) — sinks must ship alongside sources; never ship a source-only system.

---

### Pitfall 5: Player Shop / Auction Item Duplication via State Desync

**What goes wrong:**
A player lists an item in a shop or auction. The item is removed from their inventory client-side, but before the server confirms the DB write, the player disconnects (crash, rage-quit, network drop). On reconnect, the item is still in the DB auction listing AND back in the player's inventory (restored from vanilla save). The item has been duplicated.

**Why it happens:**
Vanilla Minecraft saves player inventory to disk at intervals. If the mod removes an item from inventory in memory but the game crashes before writing both the mod DB record and the vanilla player file, the rollback is asymmetric — vanilla file restores the item, mod DB retains the listing.

**How to avoid:**
- Use a two-phase commit pattern: write the DB record first (with a `PENDING` state), then remove the item from inventory, then mark the DB record `ACTIVE`. On server startup, audit `PENDING` records and cancel them (re-give item or discard if player never reconnected).
- For auctions: store a copy of the item's NBT in the DB at listing time. The authoritative source is the DB; never trust the player's current inventory for claimed items.
- Never give items back by inserting into a player's live inventory directly from an async callback — always queue the give via `server.execute()` after verifying the player is still online.

**Warning signs:**
- Players reporting they still have an item after listing it for sale.
- Auction DB has listings with no corresponding deduction from the lister's history.
- Duplicate items appearing on the `/eco top` richest players (item-value tracking).

**Phase to address:** Phase 3 (Player Shops) and Phase 4 (Auctions) — each must implement the two-phase commit pattern before going live.

---

### Pitfall 6: NeoForge Config Infinite Correction Loop

**What goes wrong:**
If a `ModConfigSpec` entry has an invalid default, mismatched type, or an impossible validation predicate, NeoForge's config system enters a correction loop: it detects an invalid value, writes the "corrected" value, then immediately detects the corrected value as also invalid, and writes again — every ~2 seconds. This causes log spam, high disk I/O, and makes it impossible to manually edit the config file because it's overwritten before you can save.

**Why it happens:**
This is a known NeoForge bug/behavior (GitHub issue #1768). It occurs when `define()` range predicates or type constraints conflict with the actual value being stored.

**How to avoid:**
- Test all config entries with invalid values during development; verify the correction result is stable.
- Avoid open-ended `String` configs that should be numeric — use typed `defineInRange()` for all numeric values.
- After any config schema change, delete the existing TOML file and let it regenerate cleanly on first run.
- Document in your TOML comments what valid values look like to help server admins avoid bad configs.

**Warning signs:**
- Log file growing at 1MB/minute with config-related lines.
- Server admin reports they can't edit the config file.
- CPU usage elevated even when no players are online.

**Phase to address:** Phase 1 — define the full config schema correctly before any feature uses it; a bad schema baked in early is hard to change after players have customized their configs.

---

### Pitfall 7: Votifier Vote Processing on the Main Thread

**What goes wrong:**
Votifier/NuVotifier delivers vote notifications over a TCP socket (default port 8192). If the mod handles the incoming socket connection synchronously on the server thread, any delay in the vote handler (DB write, network hiccup from the vote site) freezes the server tick.

**Why it happens:**
The simplest implementation opens a ServerSocket in the same thread that handles game ticks. Developers copy-paste socket examples without considering threading.

**How to avoid:**
- Open the vote listener socket in a dedicated background thread (or use Java NIO with a separate thread).
- On receiving a vote packet: validate the token/key, then hand off the reward logic to `server.execute(runnable)` on the main thread — do not call NeoForge/game APIs from the listener thread.
- The listener thread should only parse the packet and enqueue; never touch game state.
- If the vote listener crashes (bad packet, attacker sends garbage), it must not propagate and crash the server. Wrap the listener loop in a `try-catch(Exception)` that logs and continues.

**Warning signs:**
- TPS drops at irregular intervals, especially if the server is on a vote listing site that sends many votes.
- Vote rewards delivered inconsistently.
- Server crash reports with socket-related stack traces.

**Phase to address:** Phase 2 (Vote Rewards) — design the listener as an isolated, threaded component from the start.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Store balance in player NBT/attachment instead of DB | No DB setup needed | Data inaccessible for offline players, no `/eco top`, no audit trail | Never — this project explicitly requires MySQL |
| Synchronous DB calls on main thread | Simpler code, no CompletableFuture | Server lag, TPS drops under load | Never — even SQLite has measurable latency |
| Single DB connection (no pool) | Less setup | Connection exhaustion under concurrent load, timeouts | Only for SQLite dev/test, never MySQL prod |
| Skip the two-phase commit for shops/auctions | Faster to ship | Item duplication exploit on disconnect | Never |
| Hard-code coin reward amounts | No config system needed | Every balance change requires a new mod release | MVP only — move to TOML before first player uses it |
| Cache all balances in a static Map | Fast reads | Cache invalidation bugs if server restarts, cache is wrong after admin adjustments | Acceptable as a read-through cache with TTL; never as the write target |
| Use `@SubscribeEvent` on a class with client-only imports | Quick event wiring | Crashes dedicated server on startup | Never |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| FTB Chunks | Listening for claim events on the wrong event bus (NeoForge bus vs. mod bus); FTB uses its own event API | Check FTB Chunks' GitHub for `ClaimedChunkEvent` or equivalent; verify it fires on NeoForge bus or requires direct API call |
| FTB Chunks | Charging per chunk at the moment of claim, then forgetting weekly maintenance | Store claim timestamps and owner in JBalance's DB; run a scheduled task (server tick counter or real-time scheduler) for weekly fees |
| Cobblemon Arena | Assuming Cobblemon's battle result fires a standard NeoForge event | Cobblemon has its own Kotlin-based event API; must listen for battle completion via Cobblemon's `BattleVictoryEvent` or callback, not NeoForge bus |
| Cobblemon Arena | Kotlin interop — Cobblemon is written in Kotlin; calling Kotlin-specific constructs from Java can be awkward | Use Cobblemon's public Java-friendly API surface; avoid calling Kotlin extension functions or coroutines directly |
| NuVotifier | Using Votifier v1 key format — v1 is RSA-2048, v2 is token-based HMAC | NuVotifier 2.x supports both protocols; generate a v2 token for modern vote sites, keep v1 key for legacy compatibility |
| NuVotifier | Assuming vote packets always arrive while server is running | Votes can queue at the vote site; implement a "pending votes" table so votes are awarded when the player next logs in |
| MySQL (Pterodactyl) | Using root credentials in the TOML config | Create a dedicated `jbalance` DB user with only SELECT/INSERT/UPDATE/DELETE on the jbalance schema; no GRANT/DROP |
| SQLite (dev fallback) | Using SQLite connection pool — SQLite does not support concurrent writers | Configure HikariCP with `maximumPoolSize=1` when using SQLite; concurrent writes will corrupt the DB otherwise |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| `/eco top` full table scan on every call | Command takes 1-2 seconds; DB CPU spikes | Cache the leaderboard result for 60 seconds; rebuild on a scheduled async task | 100+ players, large balance table |
| Per-tick playtime check querying DB | Constant low-level DB load, 20 queries/second per online player | Track playtime in memory; only write to DB at milestones and on player quit | Any server with 5+ players online |
| No DB index on `player_uuid` column | Balance lookups slow as table grows | Create index on `uuid` column in schema migration; create index on `last_active` for top queries | 1,000+ transaction rows |
| Loading all player balances into memory on startup | Startup lag; excessive RAM on large servers | Lazy-load balances on first access per player session; evict on logout | 500+ unique player records |
| Holding DB connection open for the lifetime of a player session | Connection pool exhaustion with many online players | Open connection, execute query, close — HikariCP returns it to the pool | Pool size of 10, 15+ concurrent DB operations |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Not validating coin amount in `/eco pay` | Negative payment transfers coins from recipient to sender; player sends -1000 to steal | Reject any payment where `amount <= 0`; enforce maximum payment cap in config |
| Not checking that sender != recipient in `/eco pay` | Player sends coins to themselves, triggering callbacks that could double-count | Explicitly reject `sender.getUUID().equals(recipient.getUUID())` |
| Votifier listener accepting packets without key verification | Attacker sends fake votes to farm rewards | Always verify RSA signature (v1) or HMAC token (v2); drop and log packets that fail verification |
| Admin commands accessible at wrong OP level | Players use `/ecoadmin` to give themselves coins | Enforce `requires(src -> src.hasPermission(4))` on all admin subcommands; test as non-OP |
| Logging player balances or transaction amounts at INFO level verbosely | Log files expose financial activity; privacy concern on shared hosting | Use DEBUG level for individual transaction logs; INFO only for errors and server-level events |
| SQL string concatenation for player names/UUIDs | SQL injection if player names ever touch raw queries | Always use PreparedStatements with `?` placeholders; UUIDs are safe as strings but use parameterized queries regardless |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Displaying raw decimal balances (1234567.89123) | Confusing, hard to read | Format with thousands separator and fixed decimal places: "1.234.567,89 moedas" (PT-BR locale) |
| No feedback when a milestone reward is earned | Players don't know the economy is working; feel unrewarded | Send a chat message ("Voce recebeu 100 moedas por 1 hora de jogo!") on every milestone |
| `/eco top` showing UUID instead of display name for offline players | Leaderboard is unreadable | Store last-known display name in the DB at login; use it for the leaderboard |
| Auction expiry with no notification | Players forget bids; miss reclaiming unsold items | Send a chat message on login if the player has expired auctions with items to reclaim |
| FTB Chunks charge with no warning | Player claims a chunk, coins are silently deducted | Confirm cost before charging: "Reclamar este chunk custara 50 moedas. Voce tem 200 moedas. Confirmar?" |
| Error messages in English when all UI is Portuguese | Players can't understand errors | All player-facing messages (including errors) in Portuguese (BR); English only in server logs |

---

## "Looks Done But Isn't" Checklist

- [ ] **Balance transfer:** Verify the sender's balance is atomically reduced and recipient's increased — test by running 50 concurrent `/eco pay` commands via a test script and checking no coins are created or destroyed.
- [ ] **Playtime milestones:** Verify milestone fires exactly once per threshold, not repeatedly if the player disconnects and reconnects mid-milestone.
- [ ] **Offline player balance:** Verify `/eco pay` to an offline player queues correctly and the balance is present when they log in.
- [ ] **Vote rewards when offline:** Verify a vote received while the player is offline is stored and delivered on their next login — don't just reward online players.
- [ ] **FTB Chunks weekly fee:** Verify a player with insufficient balance loses their claim rather than going negative — not just deducted if possible.
- [ ] **Auction item recovery:** Verify that if the server restarts mid-auction, items are not lost — all listed items must be recoverable from DB on restart.
- [ ] **SQLite fallback:** Verify all features work with SQLite, not just MySQL — run the full feature set against SQLite in dev before each release.
- [ ] **Admin commands:** Verify `/ecoadmin` subcommands all fail gracefully if run by a non-OP player, with a clear "sem permissao" message.
- [ ] **Config reload:** Verify changing a reward rate in the TOML config and reloading does not corrupt in-flight transactions or reset player balances.
- [ ] **Sided code:** Run `./gradlew runServer` (dedicated server) before every release — if it crashes on startup, there is client-only code in common scope.

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Race condition caused negative balances | MEDIUM | Write a one-time SQL migration to clamp negative balances to 0; add the atomic DB check going forward; inform affected players |
| Inflation already severe | HIGH | Admin-only `/ecoadmin setbalance` to correct top offenders; add currency sinks; announce an economy reset with compensation if necessary |
| Item duplication via disconnect exploit | HIGH | Audit auction/shop DB for listings where the item also exists in the player's current inventory; invalidate duplicates; add two-phase commit going forward |
| Votifier listener crash took down server | LOW | Restart server; add `try-catch` around listener loop; check NuVotifier logs for malformed packet source |
| Config correction loop filling disk | LOW | Stop server; manually delete the corrupt TOML config; fix the `ModConfigSpec` predicate; restart to regenerate |
| DB connection pool exhausted | MEDIUM | Restart server to clear connections; increase `maximumPoolSize` in HikariCP config; identify which code path is not closing connections |
| Sided crash on dedicated server | LOW | Remove the client-only reference or wrap in `DistExecutor`; rebuild and redeploy |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Balance race condition | Phase 1: Core Currency | Run concurrent `/eco pay` stress test; verify no coin creation/destruction |
| Blocking server thread with DB I/O | Phase 1: Core Currency | Profile TPS under simultaneous balance queries; confirm <1ms main thread impact |
| Client-only code on server | Phase 1: Project Setup | `./gradlew runServer` passes without ClassNotFoundException |
| Currency inflation / no sinks | Phase 1 (reward rates) + Phase 3/4 (shops/auctions) | Confirm every earn path has a corresponding sink path in the design doc |
| Item duplication via disconnect | Phase 3: Player Shops + Phase 4: Auctions | Simulate disconnect mid-listing; confirm item not in both inventory and DB listing |
| Config correction loop | Phase 1: Project Setup | Test all `ModConfigSpec` definitions with edge-case values; confirm stable correction |
| Votifier off-main-thread | Phase 2: Vote Rewards | Verify listener runs in its own thread; no server thread access from listener |
| FTB Chunks integration event | Phase 5: FTB Chunks | Confirm claim events fire and charge correctly; test claim with insufficient balance |
| Cobblemon Arena API mismatch | Phase 6: Cobblemon API | Verify `BattleVictoryEvent` or equivalent hooks in Cobblemon's actual API; confirm Java-Kotlin interop works |
| SQL injection / permission bypass | All phases | Code review of all PreparedStatement usage; test all commands as non-OP player |
| Offline vote delivery | Phase 2: Vote Rewards | Simulate a vote while player is offline; confirm reward on next login |
| Auction state on server restart | Phase 4: Auctions | Restart server mid-auction; confirm items and bids are correctly restored |

---

## Sources

- NeoForge Official Docs — Configuration: https://docs.neoforged.net/docs/1.21.1/misc/config/
- NeoForge Official Docs — Data Attachments: https://docs.neoforged.net/docs/datastorage/attachments/
- NeoForge Official Docs — Events: https://docs.neoforged.net/docs/concepts/events/
- NeoForge Official Docs — Saved Data: https://docs.neoforged.net/docs/datastorage/saveddata/
- NeoForge GitHub Issue #1768 — Config infinite correction loop: https://github.com/neoforged/NeoForge/issues/1768
- NeoForge GitHub Discussion #1815 — Invalid Player Data 1.21.1: https://github.com/neoforged/NeoForge/discussions/1815
- SpigotMC — Async DB queries with connection pool (applicable to NeoForge pattern): https://www.spigotmc.org/threads/tutorial-async-database-queries-using-a-connection-pool.114282/
- SpigotMC — MySQL database integration guide: https://www.spigotmc.org/wiki/mysql-database-integration-with-your-plugin/
- Hypixel Forums — Auction house dupe post-mortem: https://hypixel.net/threads/the-dupe-how-it-works-post-mortem-analysis.4913928/
- SpigotMC — Ways to fix inflated economy: https://www.spigotmc.org/threads/ways-to-fix-a-super-inflated-economy.199590/
- Minecraft Forum — Constructing a balanced economy: https://www.minecraftforum.net/forums/minecraft-java-edition/discussion/191757-constructing-a-good-balanced-minecraft-economy
- NuVotifier Setup Guide: https://github.com/NuVotifier/NuVotifier/wiki/Setup-Guide
- HikariCP About Pool Sizing: https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing
- NeoForge JarJar Documentation: https://docs.neoforged.net/toolchain/docs/plugins/mdg/
- Brandeis Hoot — Minecraft economy inflation analysis: https://brandeishoot.com/minecraft-multiplayer-servers-struggle-with-excess-money-supply-and-inflation/

---
*Pitfalls research for: NeoForge 1.21.1 economy mod (JBalance)*
*Researched: 2026-03-19*
