# JBalance - Economy & Shop Mod

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21.1-green" alt="Minecraft 1.21.1">
  <img src="https://img.shields.io/badge/NeoForge-21.1-orange" alt="NeoForge">
  <img src="https://img.shields.io/badge/Java-21-blue" alt="Java 21">
  <img src="https://img.shields.io/badge/version-1.0.0-brightgreen" alt="Version 1.0.0">
</p>

Mod completo de economia e lojas para NeoForge 1.21.1. Sistema de moeda virtual, lojas de jogadores, ganhos por mobs/playtime, sistema AFK, e integração com Discord via webhook.

Complete economy and shop mod for NeoForge 1.21.1. Virtual currency system, player shops, mob/playtime earnings, AFK system, and Discord webhook integration.

---

## Sumario / Table of Contents

- [Portugues (BR)](#-portugues-br)
- [English](#-english)

---

# 🇧🇷 Portugues (BR)

## Sobre

JBalance e um mod de economia completo construido para servidores NeoForge 1.21.1. Desenvolvido originalmente para o modpack **ALL THE MONS** (baseado em Cobblemon), oferece um sistema robusto de moeda virtual com lojas de jogadores, ganhos passivos, e ferramentas de administracao.

## Funcionalidades

### Sistema de Moeda

- **Saldo por jogador** — cada jogador tem uma carteira virtual persistente
- **Saldo inicial configuravel** — novos jogadores recebem uma quantia ao entrar pela primeira vez
- **Transferencias** — `/eco pay <jogador> <valor>` com cooldown anti-spam
- **Ranking** — `/eco top` mostra os 10 jogadores mais ricos (cache configuravel)
- **Formatacao PT-BR** — valores exibidos no formato brasileiro (ex: `J$ 1.500`)

### Lojas de Jogadores

- **Criar loja** — `/setloja` marca a posicao da loja do jogador
- **Expor itens** — `/jshop venda <qtd> <preco>` para vender, `/jshop compra <qtd> <preco>` para comprar
- **Venda e compra simultanea** — `/jshop venda 1 20 : compra 1 5`
- **Mostruario visual** — item flutuante acima do bloco de exposicao (desativavel via TOML)
- **Sistema de estoque** — itens ficam guardados em bau/barrel no mesmo chunk
- **Interface clicavel** — botoes de quantidade [1] [5] [10] [32] [64] [MAX] no chat
- **Taxa de venda** — porcentagem configuravel em toda transacao (padrao: 3%)
- **Teleporte** — `/lojas` lista todas as lojas, `/loja <jogador>` teleporta direto
- **Remocao** — `/jshop remover` e bater no mostruario
- **Limite de itens** — configuravel por permissao (padrao: 6, VIP pode ter mais)
- **Protecao** — blocos da loja protegidos contra quebra por outros jogadores
- **Blacklist de blocos** — lista de blocos que nao podem ser usados como mostruario
- **Cooldown de relocacao** — dias antes de poder mover a loja (padrao: 30 dias)

### Lojas Admin/OP (Estoque Infinito)

- **Sem bau necessario** — OPs pulam a etapa de selecionar storage
- **Estoque infinito** — itens nunca acabam, mostra "Infinito" no chat
- **Dinheiro gerado** — ao vender para loja admin, o dinheiro e criado (nao sai do saldo do OP)
- **Configuravel** — `admin_infinite_stock = true/false` no TOML

### Ganhos Passivos

#### Mobs

- **Recompensa por mob** — cada tipo de mob tem valor configuravel no TOML
- **Exclusao de spawner** — mobs de spawner nao dao recompensa
- **Notificacoes agrupadas** — acumula kills e notifica a cada 60s (configuravel)
- **Toggle global** — `enabled = true/false` para ligar/desligar
- **Mobs padrao** — zombie (1), skeleton (1), creeper (2), enderman (3), warden (50), dragon (500)...

#### Playtime (Milestones)

- **Marcos de tempo** — recompensas unicas ao atingir horas de jogo (1h, 3h, 6h, 12h, 24h, 48h, 100h)
- **Deteccao AFK** — tempo parado nao conta para milestones
- **Persistencia** — milestones sobrevivem desconexoes e reinicializacoes do servidor
- **Flush periodico** — playtime salvo no DB a cada 5 minutos

### Sistema AFK

- **Comando `/afk`** — alterna status AFK com broadcast para todos os jogadores
- **Permissao** — `jbalance.afk` necessaria para usar o comando
- **Kick automatico** — jogadores SEM a permissao sao kickados apos 20min parados (configuravel)
- **Auto-clear** — ao se mover, status AFK e removido automaticamente com broadcast
- **Protecao para VIPs** — jogadores com `jbalance.afk` podem ficar AFK indefinidamente

### Administracao

- **`/ecoadmin give <jogador> <valor>`** — dar moedas
- **`/ecoadmin take <jogador> <valor>`** — remover moedas
- **`/ecoadmin set <jogador> <valor>`** — definir saldo
- **`/jshop admin setloja <jogador>`** — criar loja para jogador (sem cooldown)
- **`/jshop admin delloja <jogador>`** — deletar loja de jogador

### Discord Webhook

- Logs de transferencias (`/eco pay`)
- Logs de comandos admin (`/ecoadmin`)
- Logs de ganhos (mob kills, milestones)
- Logs de lojas (criacao, remocao, compra, venda, exposicao de item, sem estoque)
- Logs de saldo (primeiro join de jogador)
- Cada tipo de log pode ser ligado/desligado individualmente

### Banco de Dados

- **MySQL/MariaDB** — para producao (recomendado)
- **SQLite** — fallback para desenvolvimento/single-player
- **HikariCP** — connection pool de alta performance
- **Async** — todas as operacoes de DB sao assincronas (nao trava o game thread)
- **Tabelas**: `jbalance_players`, `jbalance_shops`, `jbalance_shop_items`, `jbalance_playtime`

## Comandos

### Jogador

| Comando | Descricao | Permissao |
|---------|-----------|-----------|
| `/eco balance` ou `/bal` | Ver seu saldo | `jbalance.eco.balance` |
| `/eco balance <jogador>` | Ver saldo de outro jogador | `jbalance.eco.balance.other` |
| `/eco pay <jogador> <valor>` | Transferir moedas | `jbalance.eco.pay` |
| `/eco top` | Ranking dos mais ricos | `jbalance.eco.top` |
| `/setloja` | Criar sua loja na posicao atual | `jbalance.shop.create` |
| `/delloja` | Deletar sua loja | `jbalance.shop.create` |
| `/lojas` | Listar todas as lojas | `jbalance.shop.teleport` |
| `/loja <jogador>` | Teleportar para uma loja | `jbalance.shop.teleport` |
| `/jshop venda <qtd> <preco>` | Expor item para venda | `jbalance.shop.sell` |
| `/jshop compra <qtd> <preco>` | Criar ordem de compra | `jbalance.shop.buy` |
| `/jshop remover` | Remover item da loja | — |
| `/jshop cancelar` | Cancelar operacao em andamento | — |
| `/jshop help` | Ajuda dos comandos de loja | — |
| `/afk` | Alternar status AFK | `jbalance.afk` |

### Admin (OP Level 2+)

| Comando | Descricao |
|---------|-----------|
| `/ecoadmin give <jogador> <valor>` | Dar moedas |
| `/ecoadmin take <jogador> <valor>` | Remover moedas |
| `/ecoadmin set <jogador> <valor>` | Definir saldo |
| `/jshop admin setloja <jogador>` | Criar loja para jogador |
| `/jshop admin delloja <jogador>` | Deletar loja de jogador |

## Permissoes

| Node | Padrao | Descricao |
|------|--------|-----------|
| `jbalance.eco.balance` | `true` | Checar proprio saldo |
| `jbalance.eco.balance.other` | `true` | Checar saldo de outros |
| `jbalance.eco.pay` | `true` | Transferir moedas |
| `jbalance.eco.top` | `true` | Ver ranking |
| `jbalance.shop.create` | `false` | Criar/deletar loja |
| `jbalance.shop.teleport` | `true` | Teleportar para lojas |
| `jbalance.shop.sell` | `true` | Vender itens na loja |
| `jbalance.shop.buy` | `true` | Comprar itens na loja |
| `jbalance.shop.limit` | `6` (integer) | Limite de itens por loja |
| `jbalance.afk` | `false` | Usar /afk e nao ser kickado |
| `jbalance.admin.give` | OP 4 | Comando give |
| `jbalance.admin.take` | OP 4 | Comando take |
| `jbalance.admin.set` | OP 4 | Comando set |
| `jbalance.admin.shop` | OP 4 | Comandos admin de loja |

Compativel com **LuckPerms** — todos os nodes sao detectados automaticamente.

## Configuracao (TOML)

O arquivo de configuracao e gerado automaticamente em `config/jbalance-common.toml`:

```toml
# ─── Moeda ───
[currency]
name = "JCoins"
symbol = "J$"
starting_balance = 100
min_transfer = 1
top_cache_seconds = 60
transfer_cooldown_seconds = 3

# ─── Banco de Dados ───
[database]
use_mysql = false
host = "localhost"
port = 3306
database = "jbalance"
user = "jbalance"
password = "changeme"

# ─── Discord Webhook ───
[webhook]
enabled = false
url = ""
log_pay = true
log_admin = true
log_earnings = true
log_balance = true
log_shop = true

# ─── Loja ───
[shop]
display_blacklist = ["minecraft:bedrock", "minecraft:command_block", ...]
tax_percent = 3
relocate_cooldown_days = 30
admin_infinite_stock = true
show_item_display = true

# ─── Ganhos ───
[earnings.mob_kills]
enabled = false
rewards = ["minecraft:zombie=1", "minecraft:skeleton=1", "minecraft:creeper=2", ...]
kill_notification_interval = 60

[earnings.milestones]
milestones = ["hours=1,reward=50", "hours=3,reward=100", "hours=6,reward=200", ...]
afk_timeout_minutes = 5

# ─── AFK ───
[afk]
kick_minutes = 20
```

## Instalacao

### Requisitos

- Minecraft 1.21.1
- NeoForge 21.1+
- Java 21

### Passos

1. Baixe o `.jar` da [pagina de releases](../../releases)
2. Coloque na pasta `mods/` do servidor
3. Inicie o servidor para gerar o arquivo de configuracao
4. Edite `config/jbalance-common.toml` conforme necessario
5. Reinicie o servidor

### Build a partir do codigo-fonte

```bash
git clone https://github.com/Pweg0/Jbalance-economy.git
cd Jbalance-economy
./gradlew build
# O .jar estara em build/libs/
```

## Estrutura do Projeto

```
src/main/java/com/pweg0/jbalance/
├── JBalance.java                    # Classe principal do mod
├── config/
│   └── JBalanceConfig.java          # Configuracao TOML
├── command/
│   ├── CommandRegistrar.java        # Registro central de comandos
│   ├── EcoCommand.java              # /eco balance, pay, top
│   ├── EcoAdminCommand.java         # /ecoadmin give, take, set
│   ├── ShopCommand.java             # /setloja, /delloja, /lojas, /loja
│   ├── JShopCommand.java            # /jshop venda, compra, remover
│   ├── AfkCommand.java              # /afk
│   └── JBalancePermissions.java     # Registro de nodes de permissao
├── service/
│   ├── EconomyService.java          # Logica de economia (saldo, transferencia)
│   ├── ShopService.java             # Logica de lojas (CRUD, taxa)
│   └── PlaytimeService.java         # Tracking de playtime + AFK
├── shop/
│   ├── ShopDisplayManager.java      # Entidades flutuantes de display
│   ├── ShopInteractionHandler.java  # Interacao com blocos de loja
│   ├── ShopSetupSession.java        # Fluxo de criacao de item
│   └── ShopPendingTransaction.java  # Transacoes pendentes (buy/sell)
├── data/db/
│   ├── DatabaseManager.java         # HikariCP + schema migration
│   ├── BalanceRepository.java       # CRUD de saldos
│   ├── ShopRepository.java          # CRUD de lojas e itens
│   └── PlaytimeRepository.java      # CRUD de playtime
├── event/
│   ├── PlayerEventHandler.java      # Eventos de jogador (join, etc)
│   └── EarningsEventHandler.java    # Mob kills + server tick
└── util/
    ├── CurrencyFormatter.java       # Formatacao PT-BR de valores
    └── DiscordWebhook.java          # Integracoes Discord
```

---

# 🇺🇸 English

## About

JBalance is a comprehensive economy mod built for NeoForge 1.21.1 servers. Originally developed for the **ALL THE MONS** modpack (Cobblemon-based), it provides a robust virtual currency system with player shops, passive earnings, and administration tools.

## Features

### Currency System

- **Per-player balance** — each player has a persistent virtual wallet
- **Configurable starting balance** — new players receive an amount on first join
- **Transfers** — `/eco pay <player> <amount>` with anti-spam cooldown
- **Leaderboard** — `/eco top` shows the 10 richest players (configurable cache)
- **PT-BR formatting** — values displayed in Brazilian format (e.g., `J$ 1.500`)

### Player Shops

- **Create shop** — `/setloja` marks the player's shop position
- **Expose items** — `/jshop venda <qty> <price>` to sell, `/jshop compra <qty> <price>` to buy
- **Sell and buy simultaneously** — `/jshop venda 1 20 : compra 1 5`
- **Visual display** — floating item above the display block (can be disabled via TOML)
- **Stock system** — items stored in a chest/barrel in the same chunk
- **Clickable interface** — quantity buttons [1] [5] [10] [32] [64] [MAX] in chat
- **Sales tax** — configurable percentage on every transaction (default: 3%)
- **Teleportation** — `/lojas` lists all shops, `/loja <player>` teleports directly
- **Removal** — `/jshop remover` then hit the display block
- **Item limit** — configurable per permission (default: 6, VIP can have more)
- **Protection** — shop blocks are protected from being broken by other players
- **Block blacklist** — list of blocks that cannot be used as display stands
- **Relocation cooldown** — days before the shop can be moved (default: 30 days)

### Admin/OP Shops (Infinite Stock)

- **No chest required** — OPs skip the storage selection step
- **Infinite stock** — items never run out, shows "Infinito" in chat
- **Money generation** — when selling to an admin shop, money is created (not deducted from OP's balance)
- **Configurable** — `admin_infinite_stock = true/false` in TOML

### Passive Earnings

#### Mobs

- **Per-mob reward** — each mob type has a configurable value in TOML
- **Spawner exclusion** — spawner mobs don't give rewards
- **Batched notifications** — accumulates kills and notifies every 60s (configurable)
- **Global toggle** — `enabled = true/false` to enable/disable
- **Default mobs** — zombie (1), skeleton (1), creeper (2), enderman (3), warden (50), dragon (500)...

#### Playtime (Milestones)

- **Time milestones** — unique rewards upon reaching play hours (1h, 3h, 6h, 12h, 24h, 48h, 100h)
- **AFK detection** — idle time does not count toward milestones
- **Persistence** — milestones survive disconnects and server restarts
- **Periodic flush** — playtime saved to DB every 5 minutes

### AFK System

- **`/afk` command** — toggles AFK status with server-wide broadcast
- **Permission** — `jbalance.afk` required to use the command
- **Auto-kick** — players WITHOUT the permission are kicked after 20min idle (configurable)
- **Auto-clear** — moving automatically removes AFK status with broadcast
- **VIP protection** — players with `jbalance.afk` can stay AFK indefinitely

### Administration

- **`/ecoadmin give <player> <amount>`** — give coins
- **`/ecoadmin take <player> <amount>`** — remove coins
- **`/ecoadmin set <player> <amount>`** — set balance
- **`/jshop admin setloja <player>`** — create shop for a player (no cooldown)
- **`/jshop admin delloja <player>`** — delete a player's shop

### Discord Webhook

- Transfer logs (`/eco pay`)
- Admin command logs (`/ecoadmin`)
- Earnings logs (mob kills, milestones)
- Shop logs (create, delete, buy, sell, expose item, out of stock)
- Balance logs (new player first join)
- Each log type can be individually toggled on/off

### Database

- **MySQL/MariaDB** — for production (recommended)
- **SQLite** — fallback for development/single-player
- **HikariCP** — high-performance connection pool
- **Async** — all DB operations are asynchronous (doesn't block the game thread)
- **Tables**: `jbalance_players`, `jbalance_shops`, `jbalance_shop_items`, `jbalance_playtime`

## Commands

### Player

| Command | Description | Permission |
|---------|-------------|------------|
| `/eco balance` or `/bal` | Check your balance | `jbalance.eco.balance` |
| `/eco balance <player>` | Check another player's balance | `jbalance.eco.balance.other` |
| `/eco pay <player> <amount>` | Transfer coins | `jbalance.eco.pay` |
| `/eco top` | Richest players leaderboard | `jbalance.eco.top` |
| `/setloja` | Create your shop at current position | `jbalance.shop.create` |
| `/delloja` | Delete your shop | `jbalance.shop.create` |
| `/lojas` | List all shops | `jbalance.shop.teleport` |
| `/loja <player>` | Teleport to a shop | `jbalance.shop.teleport` |
| `/jshop venda <qty> <price>` | Expose item for sale | `jbalance.shop.sell` |
| `/jshop compra <qty> <price>` | Create buy order | `jbalance.shop.buy` |
| `/jshop remover` | Remove item from shop | — |
| `/jshop cancelar` | Cancel current operation | — |
| `/jshop help` | Shop command help | — |
| `/afk` | Toggle AFK status | `jbalance.afk` |

### Admin (OP Level 2+)

| Command | Description |
|---------|-------------|
| `/ecoadmin give <player> <amount>` | Give coins |
| `/ecoadmin take <player> <amount>` | Remove coins |
| `/ecoadmin set <player> <amount>` | Set balance |
| `/jshop admin setloja <player>` | Create shop for player |
| `/jshop admin delloja <player>` | Delete player's shop |

## Permissions

| Node | Default | Description |
|------|---------|-------------|
| `jbalance.eco.balance` | `true` | Check own balance |
| `jbalance.eco.balance.other` | `true` | Check others' balance |
| `jbalance.eco.pay` | `true` | Transfer coins |
| `jbalance.eco.top` | `true` | View leaderboard |
| `jbalance.shop.create` | `false` | Create/delete shop |
| `jbalance.shop.teleport` | `true` | Teleport to shops |
| `jbalance.shop.sell` | `true` | Sell items in shop |
| `jbalance.shop.buy` | `true` | Buy items in shop |
| `jbalance.shop.limit` | `6` (integer) | Items per shop limit |
| `jbalance.afk` | `false` | Use /afk and avoid kick |
| `jbalance.admin.give` | OP 4 | Give command |
| `jbalance.admin.take` | OP 4 | Take command |
| `jbalance.admin.set` | OP 4 | Set command |
| `jbalance.admin.shop` | OP 4 | Admin shop commands |

Compatible with **LuckPerms** — all nodes are automatically detected.

## Configuration (TOML)

The configuration file is auto-generated at `config/jbalance-common.toml`:

```toml
# ─── Currency ───
[currency]
name = "JCoins"
symbol = "J$"
starting_balance = 100
min_transfer = 1
top_cache_seconds = 60
transfer_cooldown_seconds = 3

# ─── Database ───
[database]
use_mysql = false
host = "localhost"
port = 3306
database = "jbalance"
user = "jbalance"
password = "changeme"

# ─── Discord Webhook ───
[webhook]
enabled = false
url = ""
log_pay = true
log_admin = true
log_earnings = true
log_balance = true
log_shop = true

# ─── Shop ───
[shop]
display_blacklist = ["minecraft:bedrock", "minecraft:command_block", ...]
tax_percent = 3
relocate_cooldown_days = 30
admin_infinite_stock = true
show_item_display = true

# ─── Earnings ───
[earnings.mob_kills]
enabled = false
rewards = ["minecraft:zombie=1", "minecraft:skeleton=1", "minecraft:creeper=2", ...]
kill_notification_interval = 60

[earnings.milestones]
milestones = ["hours=1,reward=50", "hours=3,reward=100", "hours=6,reward=200", ...]
afk_timeout_minutes = 5

# ─── AFK ───
[afk]
kick_minutes = 20
```

## Installation

### Requirements

- Minecraft 1.21.1
- NeoForge 21.1+
- Java 21

### Steps

1. Download the `.jar` from the [releases page](../../releases)
2. Place it in the server's `mods/` folder
3. Start the server to generate the configuration file
4. Edit `config/jbalance-common.toml` as needed
5. Restart the server

### Building from source

```bash
git clone https://github.com/Pweg0/Jbalance-economy.git
cd Jbalance-economy
./gradlew build
# The .jar will be at build/libs/
```

## Project Structure

```
src/main/java/com/pweg0/jbalance/
├── JBalance.java                    # Main mod class
├── config/
│   └── JBalanceConfig.java          # TOML configuration
├── command/
│   ├── CommandRegistrar.java        # Central command registration
│   ├── EcoCommand.java              # /eco balance, pay, top
│   ├── EcoAdminCommand.java         # /ecoadmin give, take, set
│   ├── ShopCommand.java             # /setloja, /delloja, /lojas, /loja
│   ├── JShopCommand.java            # /jshop venda, compra, remover
│   ├── AfkCommand.java              # /afk
│   └── JBalancePermissions.java     # Permission node registry
├── service/
│   ├── EconomyService.java          # Economy logic (balance, transfers)
│   ├── ShopService.java             # Shop logic (CRUD, tax)
│   └── PlaytimeService.java         # Playtime tracking + AFK
├── shop/
│   ├── ShopDisplayManager.java      # Floating display entities
│   ├── ShopInteractionHandler.java  # Shop block interaction
│   ├── ShopSetupSession.java        # Item creation flow
│   └── ShopPendingTransaction.java  # Pending transactions (buy/sell)
├── data/db/
│   ├── DatabaseManager.java         # HikariCP + schema migration
│   ├── BalanceRepository.java       # Balance CRUD
│   ├── ShopRepository.java          # Shop and item CRUD
│   └── PlaytimeRepository.java      # Playtime CRUD
├── event/
│   ├── PlayerEventHandler.java      # Player events (join, etc)
│   └── EarningsEventHandler.java    # Mob kills + server tick
└── util/
    ├── CurrencyFormatter.java       # PT-BR value formatting
    └── DiscordWebhook.java          # Discord integrations
```

---

## License

This project is proprietary. All rights reserved.

## Author

**Pweg0** — [GitHub](https://github.com/Pweg0)
