# Phase 2: Currency - Context

**Gathered:** 2026-03-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Full player and admin currency command suite. Players can check balances, send coins to each other, and view wealth rankings. Admins can give, take, and set any player's balance. All commands produce PT-BR feedback messages following the established §6[JBalance] §7text §6value color scheme.

</domain>

<decisions>
## Implementation Decisions

### Resolucao de jogadores
- Comandos de player (/eco pay, /eco balance <player>): so aceita jogadores online
- Comandos de admin (/ecoadmin give/take/set): resolve jogadores offline buscando por display_name no banco de dados
- Tab-complete em todos os comandos: lista jogadores online do servidor (padrao Minecraft)
- Jogador nao encontrado: mensagem de erro clara em PT-BR

### Visibilidade de saldo
- /eco balance (sem argumento): mostra o proprio saldo
- /eco balance <player>: qualquer jogador pode ver o saldo de outro jogador online
- Admin pode consultar saldo de jogadores offline via /ecoadmin

### Ranking (/eco top)
- Mostra top 10 jogadores por saldo
- Se o jogador que executou nao esta no top 10, mostra sua posicao na ultima linha: "Voce: #45 com J$ 1.200"
- Cache de resultados com TTL de 60 segundos (configuravel no TOML)
- Requer nova query SQL com ORDER BY balance DESC LIMIT 10 (nao existe no BalanceRepository atual)

### Limites e validacao
- Auto-pagamento bloqueado: /eco pay <proprio nome> retorna erro
- Sem limite maximo por transferencia (MIN_TRANSFER ja existe no TOML)
- Cooldown simples entre transferencias do mesmo jogador (configuravel no TOML, ex: 3 segundos)
- In-flight lock do EconomyService ja previne transferencias simultaneas do mesmo remetente

### Mensagens de feedback
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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Planning docs
- `.planning/PROJECT.md` -- Project vision, constraints, key decisions
- `.planning/REQUIREMENTS.md` -- v1 requirements CURR-01 through CURR-07 (this phase)
- `.planning/ROADMAP.md` -- Phase 2 success criteria and plan breakdown

### Phase 1 context and patterns
- `.planning/phases/01-foundation/01-CONTEXT.md` -- Currency format (J$ 1.500), message style, color scheme decisions
- `src/main/java/com/pweg0/jbalance/service/EconomyService.java` -- Existing async service with getBalance, give, take, transfer, in-flight locks
- `src/main/java/com/pweg0/jbalance/data/db/BalanceRepository.java` -- SQL CRUD, transfer with transactions, dialect-aware SQL
- `src/main/java/com/pweg0/jbalance/config/JBalanceConfig.java` -- TOML config with CURRENCY_NAME, CURRENCY_SYMBOL, MIN_TRANSFER
- `src/main/java/com/pweg0/jbalance/JBalance.java` -- Mod entry point, event bus wiring, singleton access

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- EconomyService: already has getBalance, give, take, transfer with CompletableFuture async pattern
- BalanceRepository: atomic SQL operations, dialect-aware (MySQL/SQLite), transaction support
- JBalanceConfig: TOML config with CURRENCY_NAME, CURRENCY_SYMBOL, STARTING_BALANCE, MIN_TRANSFER
- PlayerEventHandler: example of NeoForge event listener pattern with PT-BR messages

### Established Patterns
- Async DB pattern: read config on game thread, then CompletableFuture.supplyAsync on DB_EXECUTOR
- Per-player in-flight lock: ConcurrentHashMap<UUID, AtomicBoolean> in EconomyService
- Message format: §6[JBalance] §7text §6J$ value -- use Component.literal with ChatFormatting
- Singleton access: EconomyService.getInstance() available after server start

### Integration Points
- Commands register on NeoForge EVENT_BUS via RegisterCommandsEvent
- Commands need access to EconomyService.getInstance() for all balance operations
- New methods needed in BalanceRepository: getTopBalances (ORDER BY balance DESC LIMIT), getPlayerRank (COUNT WHERE balance >)
- New TOML config values needed: top_cache_seconds, transfer_cooldown_seconds
- JBalance.java may need to wire new event listeners if cooldown uses a separate handler

</code_context>

<specifics>
## Specific Ideas

- Formato do ranking inspirado em plugins de economia (EssentialsX /baltop): posicao numerada, nome, valor alinhado
- Admin commands silenciosos para o alvo -- admin pode ajustar economia sem o jogador saber
- Aviso "(jogador offline)" nos comandos admin para deixar claro o status

</specifics>

<deferred>
## Deferred Ideas

None -- discussion stayed within phase scope

</deferred>

---

*Phase: 02-currency*
*Context gathered: 2026-03-19*
