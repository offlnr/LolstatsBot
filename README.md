# LoL Stats Bot

A Discord bot built with Java 21 and JDA 5 that lets you look up League of Legends player stats, match history, live games, and Clash schedules directly from your server using slash commands.

## Commands

| Command | Description |
|---|---|
| `/stats [summoner] [region]` | Rank and general stats for a player |
| `/matches [summoner] [region]` | Last 10 matches with KDA, CS and result |
| `/live [summoner] [region]` | Check if a player is currently in a live game |
| `/lastmatch [summoner] [region]` | Detailed breakdown of the most recent match |
| `/clash [region]` | Upcoming Clash tournament schedule |

**Riot ID format:** `Name#TAG` — for example `Faker#KR1`

**Region values:** `na1`, `euw1`, `kr`, `la1`, `la2`, `br1`, `jp1`, `eun1`, `tr1`, `oc1`
Default region is `la1` (configurable in `config.properties`).

## Tech stack

- **Java 21** — virtual threads for non-blocking API calls
- **JDA 5** — Discord API wrapper
- **OkHttp 4** — HTTP client for Riot API requests
- **Jackson** — JSON deserialization
- **Logback** — structured logging
- **Maven** — build and dependency management

## Setup

### 1. Prerequisites

- Java 21+
- Maven 3.8+
- A Discord bot token — [Discord Developer Portal](https://discord.com/developers/applications)
- A Riot Games API key — [Riot Developer Portal](https://developer.riotgames.com)

### 2. Configuration

Create `src/main/resources/config.properties`:

```properties
DISCORD_TOKEN=your_discord_bot_token
RIOT_API_KEY=your_riot_api_key
DEFAULT_REGION=la1
```

Alternatively, set the values as environment variables with the same names.

### 3. Build

```bash
mvn package
```

### 4. Run

```bash
java -jar target/lolstatsbot-1.0-SNAPSHOT.jar
```

### 5. Invite the bot

In the Discord Developer Portal, enable the `applications.commands` scope when generating the invite URL. Slash commands are registered globally on startup (can take up to 1 hour to appear on all servers).

## Project structure

```
src/main/java/com/lolbot/
├── Main.java                        # Entry point, JDA setup, command registration
├── config/
│   └── BotConfig.java               # Loads tokens from config.properties or env vars
├── commands/
│   └── CommandManager.java          # Slash command router and processors
├── services/
│   └── RiotApiService.java          # All Riot API calls (Account, Match, Spectator, Clash)
├── models/
│   ├── AccountDto.java
│   ├── SummonerDto.java
│   ├── LeagueEntryDto.java
│   ├── MatchDto.java
│   ├── MatchParticipantDto.java
│   ├── LiveGameDto.java
│   └── ClashTournamentDto.java
└── util/
    ├── StatsEmbedBuilder.java        # Builds all Discord embeds
    ├── ChampionCache.java            # DDragon champion ID → name resolver
    └── RegionUtil.java               # Platform / cluster routing helpers
```

## Notes

- Development API keys from Riot expire every 24 hours. Use a production key for long-term deployments.
- Champion names in `/live` are resolved from Riot's Data Dragon CDN and cached in memory for the bot's lifetime.
- Slash commands are registered globally. For faster testing during development, switch to guild-scoped commands in `Main.java`.
