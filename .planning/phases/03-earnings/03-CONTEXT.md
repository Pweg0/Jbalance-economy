# Phase 3: Earnings - Context

**Gathered:** 2026-03-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Passive currency earning from two sources: mob kills and playtime milestones. Players earn coins by killing configured hostile mobs and by reaching cumulative playtime thresholds. Configurable reward rates, AFK detection, persistent milestone tracking that survives disconnects, and batched kill notifications to reduce chat spam.

</domain>

<decisions>
## Implementation Decisions

### Recompensa por mob kill
- Somente mobs hostis dao recompensa — passivos (vacas, porcos) nao dao moedas
- Cobblemon (Pokemon) NAO dao recompensa — economia de Cobblemon e separada
- Mobs de spawner (mob spawner block) NAO dao recompensa — previne farm de moeda infinita
- Lista explicita no TOML: somente mobs listados dao moedas. Sem valor default.
- Mobs pre-configurados: Zombie=10, Skeleton=10, Creeper=15, Spider=10, Enderman=25, Witch=20, Blaze=30, Wither Skeleton=40

### Milestones de playtime
- Milestones default: 1h, 2h, 5h, 10h, 24h com recompensas crescentes
- Configuravel no TOML como lista de pares horas->reward
- Cada milestone e recebido exatamente uma vez por jogador
- Tempo AFK NAO conta para playtime — detectar AFK apos X minutos sem movimento/acao
- Timeout de AFK configuravel no TOML (default: 5 minutos)
- Persistencia: salvar progresso periodicamente (a cada 5 minutos) + no logout
- Progresso sobrevive a desconexoes e restarts do servidor

### Notificacoes de ganho
- Mob kill: notificacao ACUMULADA a cada 60 segundos (nao por kill individual)
  - Formato: "Voce recebeu J$ 50 por matar 5 mobs"
  - Intervalo configuravel no TOML (kill_notification_interval)
- Milestone: mensagem DESTAQUE com cor diferente
  - Formato: "§6[JBalance] §aVoce completou 5h de jogo! Recompensa: §6J$ 500"
  - Cor verde (§a) para texto de milestone, dourado (§6) para valores
- Todas as mensagens em PT-BR seguindo o padrao §6[JBalance]

### Config TOML
- Secao [earnings.mob_kills]: lista explicita de mob_type = reward_value
- Secao [earnings.milestones]: lista de pares [[milestones]] hours=N, reward=X
- kill_notification_interval = 60 (segundos, configuravel)
- afk_timeout_minutes = 5 (minutos, configuravel)
- Todos os valores usam defineInRange para evitar o bug de correcao infinita do NeoForge

### Claude's Discretion
- Deteccao de AFK: implementacao exata (movement events, action events, ou tick counter)
- Deteccao de mob spawner: como identificar se o mob veio de spawner (MobSpawnType ou tag)
- Estrutura da tabela de milestones no banco de dados
- Timer de flush periodico: ScheduledExecutorService ou server tick event
- Acumulador de kills: estrutura de dados in-memory para batching de notificacoes

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Planning docs
- `.planning/PROJECT.md` -- Project vision, constraints, key decisions
- `.planning/REQUIREMENTS.md` -- v1 requirements EARN-01 through EARN-05 (this phase)
- `.planning/ROADMAP.md` -- Phase 3 success criteria and plan breakdown

### Prior phase context and patterns
- `.planning/phases/01-foundation/01-CONTEXT.md` -- Currency format (J$ 1.500), message style, color scheme
- `.planning/phases/02-currency/02-CONTEXT.md` -- Message pattern, async DB pattern, config pattern
- `src/main/java/com/pweg0/jbalance/service/EconomyService.java` -- Async service with give() for crediting coins
- `src/main/java/com/pweg0/jbalance/config/JBalanceConfig.java` -- TOML config pattern with defineInRange
- `src/main/java/com/pweg0/jbalance/event/PlayerEventHandler.java` -- NeoForge event listener pattern
- `src/main/java/com/pweg0/jbalance/JBalance.java` -- Mod entry point, event bus wiring

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- EconomyService.give(UUID, long): async credit — use for all earning rewards
- JBalanceConfig: TOML config pattern with defineInRange — extend with earnings section
- PlayerEventHandler: NeoForge event listener pattern for player login — extend for mob kill and playtime events
- Component.literal with ChatFormatting: established message formatting pattern

### Established Patterns
- Async DB: read config on game thread, CompletableFuture.supplyAsync on DB_EXECUTOR
- Message format: §6[JBalance] §7text §6J$ value
- Config: defineInRange for all numeric values (prevents NeoForge config loop)
- Event registration: NeoForge.EVENT_BUS.addListener in JBalance constructor

### Integration Points
- LivingDeathEvent: NeoForge event for mob kills — register listener in JBalance
- PlayerLoggedInEvent / PlayerLoggedOutEvent: already partially wired, extend for playtime tracking
- New DB table needed: jbalance_playtime (uuid, total_seconds, claimed_milestones)
- New TOML sections: [earnings.mob_kills] and [earnings.milestones]
- Schema migration: new SQL file for playtime table

</code_context>

<specifics>
## Specific Ideas

- Kill notification acumulada para evitar spam: jogador matando mobs rapidamente recebe uma unica mensagem a cada 60s com total
- Milestone com cor verde (§a) diferenciada das mensagens normais para destacar a conquista
- Mobs de spawner bloqueados para evitar o exploit classico de farm de moeda

</specifics>

<deferred>
## Deferred Ideas

None -- discussion stayed within phase scope

</deferred>

---

*Phase: 03-earnings*
*Context gathered: 2026-03-19*
