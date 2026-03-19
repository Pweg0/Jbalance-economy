# Requirements: JBalance

**Defined:** 2026-03-19
**Core Value:** Players must be able to earn, spend, and transfer virtual currency reliably — the economy is the backbone that every other feature depends on.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Currency

- [x] **CURR-01**: Player can check own balance with /eco balance
- [x] **CURR-02**: Player can check another player's balance with /eco balance <player>
- [x] **CURR-03**: Player can send coins to another player with /eco pay <player> <amount>
- [x] **CURR-04**: Player can view top 10 richest players with /eco top
- [x] **CURR-05**: Admin can give coins to a player with /ecoadmin give <player> <amount>
- [x] **CURR-06**: Admin can take coins from a player with /ecoadmin take <player> <amount>
- [x] **CURR-07**: Admin can set a player's balance with /ecoadmin set <player> <amount>
- [x] **CURR-08**: Player balances persist across server restarts (MySQL primary, SQLite fallback)
- [x] **CURR-09**: New players start with configurable initial balance
- [x] **CURR-10**: All currency values configurable via TOML config

### Earnings

- [x] **EARN-01**: Player earns configurable coins per mob killed
- [x] **EARN-02**: Different mob types can have different reward values (configured in TOML)
- [ ] **EARN-03**: Player earns coins upon reaching playtime milestones (1h, 2h, 5h, etc — configurable)
- [ ] **EARN-04**: Playtime milestones are per-player and persist across sessions
- [x] **EARN-05**: Player receives chat message when earning coins from any source

### Land

- [ ] **LAND-01**: Admin can select two opposite corners of a terrain using a predefined item
- [ ] **LAND-02**: Admin can mark a terrain for sale with /ecoadmin venda <price>
- [ ] **LAND-03**: Terrains for sale are protected (no building/breaking by non-owners)
- [ ] **LAND-04**: Player can purchase a terrain for sale (deducts balance)
- [ ] **LAND-05**: Terrain owner has build/break permission within their terrain
- [ ] **LAND-06**: Terrain owner must pay monthly rent (configurable in TOML)
- [ ] **LAND-07**: Terrains data persists across server restarts
- [ ] **LAND-08**: Admin can view all terrains and their status

### Infrastructure

- [x] **INFR-01**: Mod loads on NeoForge 1.21.1 dedicated server without errors
- [x] **INFR-02**: All monetary transactions are atomic (no double-spend via race conditions)
- [x] **INFR-03**: Database operations run async (no server tick blocking)
- [x] **INFR-04**: TOML config with hot-reload support for value changes
- [x] **INFR-05**: Currency name and symbol configurable in TOML

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Commerce

- **SHOP-01**: Player can create a shop to buy/sell items
- **AUCT-01**: Player can list items for auction with /leilao <item> <starting_bid>

### Integrations

- **VOTE-01**: Player receives coins for voting via Votifier/NuVotifier listener
- **FTBC-01**: Charge per claimed FTB Chunk + weekly maintenance cost based on total chunks
- **ARENA-01**: API for Cobblemon Arena PvP integration (premiacao, apostas, earnings ranking)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Physical coin items | Virtual-only currency — no item duplication exploits |
| Multi-currency support | Complexity without value for single-server setup |
| Web dashboard | Server-side mod only, no external UI |
| Mobile app | Not applicable for NeoForge mod |
| LuckPerms integration | OP levels sufficient for server needs |
| Custom textures/resource pack | Vanilla-compatible UI |
| Server buy-back shops | Creates inflation exploit vectors |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| CURR-01 | Phase 2 | Complete |
| CURR-02 | Phase 2 | Complete |
| CURR-03 | Phase 2 | Complete |
| CURR-04 | Phase 2 | Complete |
| CURR-05 | Phase 2 | Complete |
| CURR-06 | Phase 2 | Complete |
| CURR-07 | Phase 2 | Complete |
| CURR-08 | Phase 1 | Complete |
| CURR-09 | Phase 1 | Complete |
| CURR-10 | Phase 1 | Complete |
| EARN-01 | Phase 3 | Complete |
| EARN-02 | Phase 3 | Complete |
| EARN-03 | Phase 3 | Pending |
| EARN-04 | Phase 3 | Pending |
| EARN-05 | Phase 3 | Complete |
| LAND-01 | Phase 4 | Pending |
| LAND-02 | Phase 4 | Pending |
| LAND-03 | Phase 4 | Pending |
| LAND-04 | Phase 4 | Pending |
| LAND-05 | Phase 4 | Pending |
| LAND-06 | Phase 4 | Pending |
| LAND-07 | Phase 4 | Pending |
| LAND-08 | Phase 4 | Pending |
| INFR-01 | Phase 1 | Complete |
| INFR-02 | Phase 1 | Complete |
| INFR-03 | Phase 1 | Complete |
| INFR-04 | Phase 1 | Complete |
| INFR-05 | Phase 1 | Complete |

**Coverage:**
- v1 requirements: 28 total
- Mapped to phases: 28
- Unmapped: 0

---
*Requirements defined: 2026-03-19*
*Last updated: 2026-03-19 after roadmap creation — all 28 requirements mapped*
