# Phase 1: Foundation - Context

**Gathered:** 2026-03-19
**Status:** Ready for planning

<domain>
## Phase Boundary

NeoForge 1.21.1 project scaffold with ModDevGradle 2.0.x, dual-database layer (MySQL primary + SQLite fallback) with async operations, TOML config system with hot-reload, and project infrastructure that all feature phases build on. Includes player balance persistence, initial balance on first join, and currency formatting.

</domain>

<decisions>
## Implementation Decisions

### Moeda e display
- Nome da moeda: **JCoins**
- Símbolo: **J$** (aparece antes do valor)
- Formato: **J$ 1.500** — separador de milhar com ponto (padrão BR), sem casas decimais
- Cor do valor: **dourado (§6)** quando aparece no chat
- Moeda é inteira (long), sem decimais/centavos

### Valores padrão
- Saldo inicial de novos players: **100 JCoins**
- Mínimo para transferência (/eco pay): **configurável no TOML** (default sugerido: 1)
- Saldo máximo: **sem limite** (sem cap)
- Todos os valores devem ser configuráveis no TOML

### Mensagens PT-BR
- Tom: **limpo e direto**, sem emoji, sem informalidade
- Prefixo: **§6[JBalance]** em todas as mensagens do mod
- Esquema de cores: prefixo dourado (§6) + texto cinza (§7) + valores em dourado (§6)
- Exemplo: `§6[JBalance] §7Você recebeu §6J$ 500 §7de Steve`
- Todas as mensagens em Português do Brasil

### Package e mod
- Group ID: **com.pweg0.jbalance**
- Mod ID: **jbalance**
- Versão: **1.21.1-1.0.0** (MC version prefix + semver)
- Licença: **All Rights Reserved**
- Linguagem: Java 21

### Claude's Discretion
- Estrutura exata de sub-pacotes (config, db, command, service, event, etc.)
- Schema de banco de dados (nomes de tabelas/colunas)
- Implementação do pool de conexões HikariCP
- Mecanismo de hot-reload do TOML
- Tratamento de erros e mensagens de erro técnicas

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

No external specs — requirements are fully captured in decisions above and in the following planning docs:

### Planning docs
- `.planning/PROJECT.md` — Project vision, constraints, key decisions
- `.planning/REQUIREMENTS.md` — v1 requirements with REQ-IDs (INFR-01..05, CURR-08..10 for this phase)
- `.planning/ROADMAP.md` — Phase structure, success criteria, plan breakdown
- `.planning/research/STACK.md` — Technology stack recommendations (ModDevGradle 2.0.x, HikariCP 7.0.2, MySQL Connector/J 9.6.0, sqlite-jdbc)
- `.planning/research/ARCHITECTURE.md` — Component architecture, data flow, build order
- `.planning/research/PITFALLS.md` — Critical pitfalls (race conditions, async DB, sided code)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- None — greenfield project, no existing code

### Established Patterns
- None — patterns will be established in this phase

### Integration Points
- This phase establishes the foundation that Phase 2 (Currency commands), Phase 3 (Earnings), and Phase 4 (Land) all build on
- EconomyService and DatabaseManager created here will be consumed by all subsequent phases

</code_context>

<specifics>
## Specific Ideas

- Formato de chat inspirado em mods de economia populares (EssentialsX style) mas com branding JBalance
- Separador de milhar brasileiro (ponto) é obrigatório — não usar vírgula ou espaço
- Mensagens sempre em PT-BR, código e docs em inglês

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 01-foundation*
*Context gathered: 2026-03-19*
