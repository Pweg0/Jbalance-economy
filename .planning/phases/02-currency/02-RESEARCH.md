# Phase 02: Currency - Research

**Researched:** 2026-03-19
**Domain:** NeoForge 1.21.1 Brigadier command registration, async EconomyService integration, admin offline-player lookup
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Resolucao de jogadores**
- Comandos de player (/eco pay, /eco balance <player>): so aceita jogadores online
- Comandos de admin (/ecoadmin give/take/set): resolve jogadores offline buscando por display_name no banco de dados
- Tab-complete em todos os comandos: lista jogadores online do servidor (padrao Minecraft)
- Jogador nao encontrado: mensagem de erro clara em PT-BR

**Visibilidade de saldo**
- /eco balance (sem argumento): mostra o proprio saldo
- /eco balance <player>: qualquer jogador pode ver o saldo de outro jogador online
- Admin pode consultar saldo de jogadores offline via /ecoadmin

**Ranking (/eco top)**
- Mostra top 10 jogadores por saldo
- Se o jogador que executou nao esta no top 10, mostra sua posicao na ultima linha: "Voce: #45 com J$ 1.200"
- Cache de resultados com TTL de 60 segundos (configuravel no TOML)
- Requer nova query SQL com ORDER BY balance DESC LIMIT 10 (nao existe no BalanceRepository atual)

**Limites e validacao**
- Auto-pagamento bloqueado: /eco pay <proprio nome> retorna erro
- Sem limite maximo por transferencia (MIN_TRANSFER ja existe no TOML)
- Cooldown simples entre transferencias do mesmo jogador (configuravel no TOML, ex: 3 segundos)
- In-flight lock do EconomyService ja previne transferencias simultaneas do mesmo remetente

**Mensagens de feedback**
- /eco pay: remetente ve "Voce enviou J$ X para Player" e destinatario ve "Voce recebeu J$ X de Player"
- /ecoadmin give/take/set: somente o admin ve a confirmacao. Jogador alvo NAO e notificado
- /ecoadmin em jogador offline: admin ve confirmacao + aviso "(jogador offline)"
- Todos os textos em PT-BR, seguindo o padrao §6[JBalance] §7texto §6valor da Phase 1

### Claude's Discretion
- Estrutura exata das classes de comando (CommandRegistration, ArgumentTypes)
- Implementacao do cache do ranking (in-memory, ConcurrentHashMap ou Guava/Caffeine)
- Organizacao dos sub-pacotes de comandos (command/player, command/admin, etc.)
- Mensagens de erro tecnicas (DB indisponivel, etc.)
- Implementacao do cooldown (ConcurrentHashMap<UUID, Instant> ou similar)

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| CURR-01 | Player can check own balance with /eco balance | EconomyService.getBalance() already exists; command needs only CommandSourceStack.getPlayerOrException() + async callback |
| CURR-02 | Player can check another player's balance with /eco balance <player> | EntityArgument.player() resolves online player; same getBalance() call |
| CURR-03 | Player can send coins to another player with /eco pay <player> <amount> | EconomyService.transfer() exists with in-flight lock; need MIN_TRANSFER guard, self-pay guard, cooldown |
| CURR-04 | Player can view top 10 richest players with /eco top | New BalanceRepository.getTopBalances() SQL needed; cache pattern documented below |
| CURR-05 | Admin can give coins to a player with /ecoadmin give <player> <amount> | EconomyService.give() exists; admin offline lookup via BalanceRepository by display_name |
| CURR-06 | Admin can take coins from a player with /ecoadmin take <player> <amount> | EconomyService.take() exists; same offline lookup pattern |
| CURR-07 | Admin can set a player's balance with /ecoadmin set <player> <amount> | New BalanceRepository.setBalance() SQL needed (UPDATE SET balance = ?) |
</phase_requirements>

## Summary

Phase 2 builds the complete command surface on top of the already-working Phase 1 service layer. The primary domain is NeoForge 1.21.1 Brigadier command registration: every command is registered via `RegisterCommandsEvent` on `NeoForge.EVENT_BUS`, uses `Commands.literal()` / `Commands.argument()`, enforces permissions with `.requires(src -> src.hasPermission(level))`, and calls back into `EconomyService` via `CompletableFuture`. The pattern for async commands — dispatch DB work on `DB_EXECUTOR`, then re-enter the server thread via `server.execute()` before sending chat — is already established by `PlayerEventHandler` in Phase 1.

Two SQL additions are needed in `BalanceRepository`: `getTopBalances(int limit)` returning a ranked list, and `setBalance(UUID, long)` for the `/ecoadmin set` command. The admin offline-lookup decision means a third new method is also required: `findByDisplayName(String name)` returning a `UUID` (or null). A `top_cache_seconds` and `transfer_cooldown_seconds` TOML key must be added to `JBalanceConfig`. The ranking cache and cooldown tracker are both simple `ConcurrentHashMap` instances owned by their respective command handler classes — no external caching library is needed given the scale.

The most important pitfall in this phase is forgetting to re-enter the game thread before calling `CommandSourceStack.sendSuccess()` or `sendFailure()` after an async DB call. A secondary pitfall is reading `ConfigValue.get()` from inside a `CompletableFuture` on `DB_EXECUTOR` — Phase 1 already documents this must be done on the game thread before going async.

**Primary recommendation:** Register both `/eco` and `/ecoadmin` command trees in a single `CommandRegistrar` class wired into `RegisterCommandsEvent`. Each sub-command is a separate static method accepting `CommandContext<CommandSourceStack>`. All async callbacks re-enter via `source.getServer().execute(() -> ...)`.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Brigadier (bundled) | Bundled with MC 1.21.1 | Command parsing, argument types, tab-complete | Minecraft's native command framework; mandatory for NeoForge |
| NeoForge API (bundled) | 21.1.220 | `RegisterCommandsEvent`, `CommandSourceStack`, `EntityArgument` | Already a project dependency; no additions needed |
| Java stdlib | Java 21 | `ConcurrentHashMap`, `Instant`, `CompletableFuture` | Already used in Phase 1 patterns |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `com.mojang.brigadier.arguments.LongArgumentType` | bundled | Parses `<amount>` argument as Java `long` | `/eco pay`, `/ecoadmin give|take|set` |
| `net.minecraft.commands.arguments.EntityArgument` | bundled | Resolves online player from name/selector | `/eco pay <player>`, `/eco balance <player>` |
| `net.minecraft.server.players.PlayerList` | bundled | `getPlayerByName(String)` for online lookup | Already used internally by EntityArgument |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| ConcurrentHashMap cooldown | Guava Cache / Caffeine | Overkill for a 3-second TTL on a small server; adds a dependency |
| ConcurrentHashMap ranking cache | Caffeine AsyncLoadingCache | Better invalidation semantics but adds a dependency for a 60s TTL cache |
| SQL display_name lookup | Mojang/Ashcon UUID API | Requires internet call; offline play and LAN servers would break; SQL is correct |

**Installation:** No new dependencies required. All needed APIs are bundled with NeoForge 21.1.220 and MC 1.21.1.

## Architecture Patterns

### Recommended Project Structure
```
src/main/java/com/pweg0/jbalance/
├── command/
│   ├── CommandRegistrar.java      # Wires both /eco and /ecoadmin into RegisterCommandsEvent
│   ├── EcoCommand.java            # /eco balance, /eco pay, /eco top subcommands
│   └── EcoAdminCommand.java       # /ecoadmin give, take, set subcommands
├── config/
│   └── JBalanceConfig.java        # ADD: TOP_CACHE_SECONDS, TRANSFER_COOLDOWN_SECONDS
├── data/db/
│   └── BalanceRepository.java     # ADD: getTopBalances, setBalance, findByDisplayName
├── service/
│   └── EconomyService.java        # ADD: setBalance(), getTopBalances() wrappers
└── [existing files unchanged]
```

### Pattern 1: RegisterCommandsEvent wiring
**What:** A dedicated `CommandRegistrar` class listens to `RegisterCommandsEvent` on `NeoForge.EVENT_BUS`, then calls `register()` on both command trees. Wired from `JBalance` constructor.
**When to use:** Mandatory for all NeoForge server-side commands.

```java
// Source: NeoForge RegisterCommandsEvent JavaDoc (nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge)
// In JBalance constructor:
NeoForge.EVENT_BUS.addListener(CommandRegistrar::onRegisterCommands);

// CommandRegistrar.java:
public static void onRegisterCommands(RegisterCommandsEvent event) {
    CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
    EcoCommand.register(dispatcher);
    EcoAdminCommand.register(dispatcher);
}
```

### Pattern 2: Subcommand tree with permission guard
**What:** The root command is a literal with no `.executes()`. Each subcommand is a `.then()` child. The admin command uses `.requires()` at the root to enforce OP level 4.
**When to use:** All commands in this phase follow this structure.

```java
// Source: Brigadier pattern confirmed via Fabric wiki + NeoForge CommandSourceStack JavaDoc
dispatcher.register(
    Commands.literal("eco")
        .requires(src -> src.hasPermission(2))  // permission level 2 = regular player command
        .then(Commands.literal("balance")
            .executes(ctx -> EcoCommand.balance(ctx))                          // own balance
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> EcoCommand.balanceOther(ctx))))
        .then(Commands.literal("pay")
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                    .executes(ctx -> EcoCommand.pay(ctx)))))
        .then(Commands.literal("top")
            .executes(ctx -> EcoCommand.top(ctx)))
);

dispatcher.register(
    Commands.literal("ecoadmin")
        .requires(src -> src.hasPermission(4))  // permission level 4 = OP only
        .then(Commands.literal("give")
            .then(Commands.argument("player", StringArgumentType.word())
                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                    .executes(ctx -> EcoAdminCommand.give(ctx)))))
        // ... take, set follow same pattern
);
```

**Note on `/ecoadmin` player argument:** Because `/ecoadmin` resolves offline players by `display_name` (not online EntitySelector), use `StringArgumentType.word()` for the player argument — not `EntityArgument.player()`. `EntityArgument.player()` would reject names of offline players at parse time.

### Pattern 3: Async command handler with game-thread re-entry
**What:** All DB operations go to `DB_EXECUTOR` via `CompletableFuture`. The callback must re-enter the server thread before sending chat.
**When to use:** Every command that touches `EconomyService` (all of them).

```java
// Source: Established in Phase 1 PlayerEventHandler.java
public static int balance(CommandContext<CommandSourceStack> ctx) {
    CommandSourceStack src = ctx.getSource();
    ServerPlayer player = src.getPlayerOrException();  // throws if not a player
    // Read config values on game thread BEFORE going async
    long minTransfer = JBalanceConfig.MIN_TRANSFER.get();

    EconomyService.getInstance().getBalance(player.getUUID())
        .whenComplete((balance, ex) -> {
            // Re-enter game thread before calling sendSuccess/sendFailure
            src.getServer().execute(() -> {
                if (ex != null) {
                    src.sendFailure(Component.literal("§6[JBalance] §cErro ao consultar saldo."));
                    return;
                }
                String formatted = CurrencyFormatter.formatBalance(balance);
                src.sendSuccess(
                    () -> Component.literal("§6[JBalance] §7Seu saldo: §6" + formatted),
                    false
                );
            });
        });
    return Command.SINGLE_SUCCESS;
}
```

### Pattern 4: Cooldown with ConcurrentHashMap<UUID, Instant>
**What:** A static `ConcurrentHashMap<UUID, Instant>` in `EcoCommand` tracks last-transfer timestamp per player.
**When to use:** `/eco pay` command only.

```java
// Source: Java stdlib pattern — no NeoForge-specific API needed
private static final ConcurrentHashMap<UUID, Instant> lastTransfer = new ConcurrentHashMap<>();

// Check in /eco pay handler (game thread, before going async):
long cooldownSeconds = JBalanceConfig.TRANSFER_COOLDOWN_SECONDS.get();
Instant last = lastTransfer.get(playerId);
if (last != null && Duration.between(last, Instant.now()).getSeconds() < cooldownSeconds) {
    src.sendFailure(Component.literal("§6[JBalance] §cAguarde antes de enviar novamente."));
    return Command.SINGLE_SUCCESS;
}
// Update AFTER successful transfer in whenComplete callback (game thread re-entry):
lastTransfer.put(playerId, Instant.now());
```

### Pattern 5: Ranking cache with ConcurrentHashMap
**What:** A `volatile` reference to a cached result list, plus a `volatile Instant` for expiry tracking. Both owned by `EcoCommand`.
**When to use:** `/eco top` command.

```java
// Source: Java stdlib pattern
private static volatile List<TopEntry> cachedTop = Collections.emptyList();
private static volatile Instant cacheExpiry = Instant.EPOCH;

// In /eco top handler:
long cacheTtlSeconds = JBalanceConfig.TOP_CACHE_SECONDS.get();
if (Instant.now().isBefore(cacheExpiry)) {
    // serve from cache immediately on game thread
    renderTop(src, cachedTop);
    return Command.SINGLE_SUCCESS;
}
// Otherwise go async and refresh
EconomyService.getInstance().getTopBalances(10).whenComplete((entries, ex) ->
    src.getServer().execute(() -> {
        if (ex == null) {
            cachedTop = entries;
            cacheExpiry = Instant.now().plusSeconds(cacheTtlSeconds);
        }
        renderTop(src, cachedTop);
    })
);
```

### Pattern 6: Admin offline-player lookup
**What:** `/ecoadmin` accepts a raw `StringArgumentType.word()` for player name. The handler queries `BalanceRepository.findByDisplayName(name)` to get the UUID, then proceeds with the operation. If no record found, returns error in PT-BR.
**When to use:** All three `/ecoadmin give|take|set` subcommands.

```java
// In EcoAdminCommand (async, DB_EXECUTOR):
String targetName = StringArgumentType.getString(ctx, "player");
long amount = LongArgumentType.getLong(ctx, "amount");

EconomyService.getInstance().findByDisplayName(targetName).whenComplete((result, ex) -> {
    src.getServer().execute(() -> {
        if (result == null) {
            src.sendFailure(Component.literal("§6[JBalance] §cJogador '" + targetName + "' nao encontrado."));
            return;
        }
        boolean isOnline = src.getServer().getPlayerList().getPlayer(result.uuid()) != null;
        String offlineTag = isOnline ? "" : " §7(jogador offline)";
        // proceed with give/take/set...
    });
});
```

### New BalanceRepository methods needed

```java
// getTopBalances: returns ordered list of (display_name, balance) rows
public List<TopEntry> getTopBalances(int limit) {
    String sql = "SELECT display_name, balance FROM jbalance_players ORDER BY balance DESC LIMIT ?";
    // ... standard PreparedStatement pattern
}

// setBalance: absolute set (no increment) for /ecoadmin set
public boolean setBalance(UUID uuid, long newBalance) {
    String sql = "UPDATE jbalance_players SET balance = ? WHERE uuid = ?";
    // ... returns executeUpdate() == 1
}

// findByDisplayName: case-insensitive lookup for admin commands
// MySQL: LIKE (case-insensitive by default on utf8mb4_general_ci collation)
// SQLite: LIKE is case-insensitive for ASCII; use LOWER() for safety
public record PlayerRecord(UUID uuid, String displayName, long balance) {}
public PlayerRecord findByDisplayName(String name) {
    String sql = isMysql
        ? "SELECT uuid, display_name, balance FROM jbalance_players WHERE display_name = ? LIMIT 1"
        : "SELECT uuid, display_name, balance FROM jbalance_players WHERE LOWER(display_name) = LOWER(?) LIMIT 1";
    // ...
}
```

### New JBalanceConfig values needed

```java
// In JBalanceConfig, inside the "currency" section:
public static final ModConfigSpec.LongValue TOP_CACHE_SECONDS;
public static final ModConfigSpec.LongValue TRANSFER_COOLDOWN_SECONDS;

// In static initializer (push("currency") block):
TOP_CACHE_SECONDS = builder.comment("Seconds to cache /eco top results")
                           .defineInRange("top_cache_seconds", 60L, 1L, 3600L);
TRANSFER_COOLDOWN_SECONDS = builder.comment("Minimum seconds between /eco pay transfers for the same player")
                                   .defineInRange("transfer_cooldown_seconds", 3L, 0L, 300L);
```

### Anti-Patterns to Avoid
- **Reading `ConfigValue.get()` inside `CompletableFuture`:** Phase 1 explicit decision — always read config on game thread before going async.
- **Sending chat inside `whenComplete` without `server.execute()`:** `sendSuccess()` and `sendFailure()` are not thread-safe from `DB_EXECUTOR`. Always re-enter game thread.
- **Using `EntityArgument.player()` for `/ecoadmin`:** This argument type rejects offline players at parse time. Must use `StringArgumentType.word()` for the admin offline-lookup case.
- **Calling `getPlayerOrException()` in `/eco balance` without a try-catch or ensuring the source is a player:** If called from console, this throws `CommandSyntaxException`. Check `src.isPlayer()` first or let brigadier's exception bubble as a `sendFailure`.
- **Letting `whenComplete` swallow exceptions silently:** Always log `ex != null` cases to `JBalance.LOGGER`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Argument parsing and tab-complete | Custom string parser for player names | `EntityArgument.player()` (online) / `StringArgumentType.word()` + custom suggestion provider (offline) | Brigadier handles partial completion, error messaging, selector syntax for free |
| Permission gating | Manual OP check in each command body | `.requires(src -> src.hasPermission(level))` in the command tree | NeoForge/Brigadier hides the command from tab-complete for unauthorized users automatically |
| Number parsing with min/max | `Long.parseLong()` with manual bounds check | `LongArgumentType.longArg(min)` | Gives the player a clear "must be at least X" error message for free |
| Thread-safe cooldown | `synchronized` blocks or `ReentrantLock` | `ConcurrentHashMap<UUID, Instant>` + check-then-update on game thread | Commands execute on the game (main) thread so no concurrent modification; ConcurrentHashMap is sufficient |

**Key insight:** Brigadier's argument type system provides input validation, error messages, and tab-complete suggestions for free. The only custom logic needed is in `.executes()` bodies.

## Common Pitfalls

### Pitfall 1: Missing game-thread re-entry after CompletableFuture
**What goes wrong:** `sendSuccess()` or `sendFailure()` called from `DB_EXECUTOR` thread causes a `ConcurrentModificationException` or silent NPE when iterating player list to broadcast the feedback.
**Why it happens:** `CommandSourceStack` chat methods are not thread-safe.
**How to avoid:** Every `whenComplete` block wraps its content in `source.getServer().execute(() -> ...)`.
**Warning signs:** Intermittent NPE or ClassCastException in logs with no stack trace pointing to application code; feedback message sometimes not delivered.

### Pitfall 2: Config read off game thread
**What goes wrong:** `JBalanceConfig.MIN_TRANSFER.get()` called inside `CompletableFuture.supplyAsync` throws or returns stale value.
**Why it happens:** `ModConfigSpec.ConfigValue.get()` is not documented as thread-safe in NeoForge.
**How to avoid:** Capture all config values as local `long` variables at the top of the command handler (game thread), then pass into the async closure.
**Warning signs:** Occasional wrong-value behavior that only appears under load; NeoForge config correction loop warnings in log.

### Pitfall 3: EntityArgument for offline players
**What goes wrong:** `/ecoadmin give OfflinePlayerName 100` fails with "No player was found" even though the intent is to credit an offline player.
**Why it happens:** `EntityArgument.player()` resolves against the live player list at dispatch time.
**How to avoid:** Use `StringArgumentType.word()` for the admin player argument; resolve UUID via `BalanceRepository.findByDisplayName()`.
**Warning signs:** Admin command errors during testing when target player is not logged in.

### Pitfall 4: Self-pay not blocked at DB layer
**What goes wrong:** Player pays themselves; EconomyService.transfer() deducts and credits the same row — balance unchanged but the transaction "succeeds".
**Why it happens:** The SQL transfer does deduct then credit, net zero for same UUID.
**How to avoid:** Guard in the `/eco pay` command handler before calling `EconomyService.transfer()`: `if (sender.getUUID().equals(targetUUID))` — return PT-BR error.
**Warning signs:** No funds change but "sent successfully" message appears; discovered only during player testing.

### Pitfall 5: Race between cooldown check and cooldown update
**What goes wrong:** Two simultaneous `/eco pay` invocations from the same player both pass the cooldown check before either updates `lastTransfer`.
**Why it happens:** The check (`get`) and the update (`put`) are two separate map operations.
**How to avoid:** This is acceptable because `/eco pay` also has the `in-flight lock` in `EconomyService.transfer()` — a second concurrent transfer from the same sender returns `false` immediately. The cooldown is a UX guard, not a security boundary. The in-flight lock is the true race-condition guard.
**Warning signs:** Seeing double-transfer errors despite cooldown being set — investigate the in-flight lock, not the cooldown map.

### Pitfall 6: /eco top rank query for caller not in top-N
**What goes wrong:** The rank query `SELECT COUNT(*) FROM jbalance_players WHERE balance > ?` returns 0-based count; must add 1 to get rank.
**Why it happens:** `COUNT(*)` counts players with a higher balance, so rank = count + 1.
**How to avoid:** In `getPlayerRank(UUID)`: `return (int) count + 1`.
**Warning signs:** Rank shows as 0 or is off by one compared to the displayed leaderboard.

## Code Examples

### Command registration wiring (JBalance.java addition)
```java
// Source: NeoForge RegisterCommandsEvent pattern confirmed via JavaDoc
// Add to JBalance constructor:
NeoForge.EVENT_BUS.addListener(CommandRegistrar::onRegisterCommands);
```

### Extracting typed arguments from CommandContext
```java
// Source: Brigadier standard pattern (Mojang/brigadier README + Fabric wiki tutorial:commands)
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.StringArgumentType;

// Online player (player commands):
ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

// Offline name string (admin commands):
String targetName = StringArgumentType.getWord(ctx, "player");

// Long amount:
long amount = LongArgumentType.getLong(ctx, "amount");
```

### Sending feedback to command source
```java
// Source: CommandSourceStack JavaDoc (nekoyue.github.io 1.21.x-neoforge)
// Success (logged in server console if second param true):
src.sendSuccess(() -> Component.literal("§6[JBalance] §7Operacao concluida."), false);

// Failure (shown in red, not logged):
src.sendFailure(Component.literal("§6[JBalance] §cSaldo insuficiente."));
```

### PlayerList lookup pattern for online/offline check
```java
// Source: PlayerList JavaDoc (nekoyue.github.io 1.21.x-neoforge)
// Online check by UUID:
ServerPlayer onlineTarget = src.getServer().getPlayerList().getPlayer(uuid);
boolean isOnline = (onlineTarget != null);

// Online lookup by name (player commands):
ServerPlayer namedPlayer = src.getServer().getPlayerList().getPlayerByName(name);
```

### /eco top display format (EssentialsX-inspired)
```
§6[JBalance] §7--- Top 10 ---
§7#1 §6Steve §7- §6J$ 15.000
§7#2 §6Alex §7- §6J$ 12.500
...
§7Voce: §6#45 §7com §6J$ 1.200    <- shown only if caller not in top 10
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Forge `CommandEvent` | NeoForge `RegisterCommandsEvent` | Forge -> NeoForge split | `getDispatcher()` replaces old `ForgeCommandManager`; method name confirmed as `getDispatcher()` not `getCommandDispatcher()` |
| `@SubscribeEvent` on modbus | `NeoForge.EVENT_BUS.addListener()` lambda | NeoForge 21.x | Already established in Phase 1 — same pattern for commands |
| `minecraft:selector` for all player args | `EntityArgument.player()` (online) vs `StringArgumentType.word()` (offline) | 1.18+ NeoForge convention | Admin offline commands need string arg, not entity selector |

**Deprecated/outdated:**
- `IForgeCommand` interface: replaced entirely by Brigadier in 1.13+; not present in NeoForge 1.21.1
- `CommandHandler.registerCommand()`: pre-1.13 only; does not exist
- `ModConfigSpec.ConfigValue.get()` off-thread: documented pitfall from Phase 1 — still applies

## Open Questions

1. **display_name uniqueness for admin offline lookup**
   - What we know: `jbalance_players.display_name` stores the name at last login via `initPlayerIfAbsent`. Players can change their Minecraft username.
   - What's unclear: If two players ever had the same name, `findByDisplayName` could match the wrong one. The schema has `uuid` as PK but no UNIQUE constraint on `display_name`.
   - Recommendation: `LIMIT 1` in the query is sufficient for v1. A future improvement could add a UUID argument fallback for `/ecoadmin`. This is not a blocker for the current phase.

2. **Suggestion provider for `/ecoadmin` offline player argument**
   - What we know: `StringArgumentType.word()` does not auto-suggest player names for offline players.
   - What's unclear: Whether to add a custom `SuggestionProvider` that queries the DB or to simply use online player list (which is what the locked decision specifies for tab-complete).
   - Recommendation: Per locked decision, tab-complete lists online players. Use `EntityArgument.player()` suggestion behavior via a custom `SuggestionsBuilder` that wraps `SharedSuggestionProvider.suggest(server.getPlayerList().getPlayerNames(), builder)`. This keeps tab-complete simple without a DB query.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (needs to be added — no test infrastructure exists) |
| Config file | none — Wave 0 task |
| Quick run command | `./gradlew test` |
| Full suite command | `./gradlew test` |

**Note:** This is a NeoForge mod. Integration tests that actually invoke command handlers require a full server environment and cannot run as plain JUnit tests without additional tooling (e.g., GameTestFramework). For this phase, the practical test strategy is:

- **Unit tests** for pure logic: `CurrencyFormatter`, cooldown arithmetic, rank calculation (`count + 1`), self-pay guard, `MIN_TRANSFER` guard
- **Manual/smoke tests** for command registration and async DB callbacks (verified via in-game execution)

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CURR-01 | Own balance displayed in PT-BR format | unit (formatter) | `./gradlew test --tests "*.CurrencyFormatterTest"` | Wave 0 |
| CURR-02 | Other player balance lookup (online-only guard) | manual-only | n/a — requires live ServerPlayer | n/a |
| CURR-03 | Pay: self-pay blocked, MIN_TRANSFER enforced, cooldown | unit (guard logic) | `./gradlew test --tests "*.PayCommandGuardTest"` | Wave 0 |
| CURR-04 | Top 10 ranking + caller rank shown when outside top 10 | unit (rank calc) | `./gradlew test --tests "*.TopCommandRankTest"` | Wave 0 |
| CURR-05 | Admin give: offline player resolved by display_name | manual-only | n/a — requires live DB | n/a |
| CURR-06 | Admin take: offline player resolved, balance decremented | manual-only | n/a — requires live DB | n/a |
| CURR-07 | Admin set: balance set to exact value | manual-only | n/a — requires live DB | n/a |

### Sampling Rate
- **Per task commit:** `./gradlew test` (unit tests only; < 10 seconds)
- **Per wave merge:** `./gradlew test` + manual in-game smoke test of each command
- **Phase gate:** All unit tests green + manual checklist complete before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/com/pweg0/jbalance/util/CurrencyFormatterTest.java` — covers CURR-01 (format verification)
- [ ] `src/test/java/com/pweg0/jbalance/command/PayCommandGuardTest.java` — covers CURR-03 (self-pay, MIN_TRANSFER, cooldown logic as pure unit logic)
- [ ] `src/test/java/com/pweg0/jbalance/command/TopCommandRankTest.java` — covers CURR-04 (rank = count + 1 arithmetic)
- [ ] JUnit 5 dependency added to `build.gradle`: `testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'` + `test { useJUnitPlatform() }`

## Sources

### Primary (HIGH confidence)
- NeoForge 1.21.x JavaDoc — `RegisterCommandsEvent.getDispatcher()`, `Commands.literal()`, `Commands.argument()`, `CommandSourceStack.hasPermission()`, `sendSuccess()`, `sendFailure()`, `PlayerList.getPlayerByName()`, `PlayerList.getPlayer(UUID)` — https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/
- Phase 1 source code — `EconomyService`, `BalanceRepository`, `JBalanceConfig`, `PlayerEventHandler`, `CurrencyFormatter` — established patterns directly observable in codebase
- Brigadier README / Minecraft Wiki — `LongArgumentType.longArg()`, `StringArgumentType.word()`, `.then()`, `.requires()` subcommand tree — https://minecraft.wiki/w/Brigadier + https://github.com/Mojang/brigadier

### Secondary (MEDIUM confidence)
- Fabric wiki tutorial:commands — `.then()` subcommand nesting, `.requires()` permission predicate pattern — https://wiki.fabricmc.net/tutorial:commands (Fabric uses same Brigadier API; NeoForge wiring differs at registration only)

### Tertiary (LOW confidence)
- EssentialsX `/baltop` display format inspiration — community convention for ranking display, not directly verified from source

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all APIs are bundled with MC/NeoForge; no external dependencies needed
- Architecture: HIGH — patterns directly derived from Phase 1 code + verified JavaDocs
- Pitfalls: HIGH — game-thread and config-thread pitfalls directly documented in Phase 1 STATE.md decisions; command-specific pitfalls (EntityArgument offline, self-pay, rank off-by-one) verified via logic analysis

**Research date:** 2026-03-19
**Valid until:** 2026-06-19 (stable NeoForge 1.21.1 ecosystem)
