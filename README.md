# 📚 Archiv.IO - Meteor Addon

![Version](https://img.shields.io/badge/Version-0.1.1-blue?style=for-the-badge)
![Minecraft](https://img.shields.io/badge/Minecraft-%3E=1.21.4-green?style=for-the-badge)

**Archiv.IO** is a goofy ahh Meteor Client addon designed for players who love data archiving and world/player history.

![StupidBanner](https://i.imgur.com/rE4K0dM.png)

---

##  Modules
### SignLogger
#### Features:
- **Automatic Sign Logging**: Every sign you encounter is automatically saved to a local database.
- **Server-Specific Folders**: All the records are separated in server folders.
- **Date Tracking**: Automatically identifies and extracts dates from sign text using regex patterns.
- **Player Tracking**: Detects known players mentioned on signs and keeps a list of "Known Players" for highlighting.
- **CSV Export**: Export your entire sign database to a CSV file for external analysis.
- **Visual Highlights**: ESP but for signs, with special colors for signs containing dates or known players.

---

### KitBot
#### Features:
- **Automated Kit Delivery**: Reads chat requests (`$kit <name>`) and automatically delivers shulker boxes to players.
- **Baritone Pathfinding**: Uses `#goto` to walk to the correct chest.
- **Interactive Chest Setup**: Right-click a chest in setup mode to associate it with a kit name.
- **TPA Delivery**: Sends `/tpa` to the requester, detects teleport, drops the shulker, and returns home.
- **Cooldown System**: Configurable cooldown between deliveries to prevent spam.
- **Whitelist**: Restrict who can request kits (or allow everyone with `*`).
- **Help Command**: `$kit help` lists all available kits via private message.

> ⚠️ **Requires [Baritone](https://github.com/cabaletta/baritone)** installed alongside Meteor Client for pathfinding to work.

---

## 🛠️ Commands

### SignLogger

| Command | Description |
| :--- | :--- |
| `.signs list` | Shows how many unique signs are logged for the current server. |
| `.signs search text <query>` | Search the database for specific text. |
| `.signs search date <query>` | Search specifically for signs containing a certain date. |
| `.signs search player <query>` | Find signs that mention a specific player. |
| `.signs player add <name>` | Manually add a player to the "Known Players" list. |
| `.signs player addonline` | Adds everyone currently online in the server to your known list. |
| `.signs export` | Exports the current server database to a CSV file in your Meteor folder. |

### KitBot

#### In-game Commands (Prefix: `.`)

| Command | Description |
| :--- | :--- |
| `.kitbot set <name>` | Maps the chest/shulker you are currently looking at to the kit name `<name>`. |
| `.kitbot remove <name>` | Removes the configuration for the kit `<name>`. |
| `.kitbot list` | Lists all configured kits and their coordinates. |
| `.kitbot clear` | Clears all configured kits. |

#### Chat Request Commands (Trigger: `$kit`)

| Chat Command | Description |
| :--- | :--- |
| `$kit <name>` | Request a kit delivery (e.g. `$kit pvp`). |
| `$kit help` | Lists all available kits via private message. |

---

## 📋 Requirements

| Dependency | Version | Note |
| :--- | :--- | :--- |
| [Meteor Client](https://meteorclient.com/) | >= 1.21.4 | Base client |
| [Baritone](https://github.com/cabaletta/baritone) | 1.21.4 compatible | **Required for KitBot** pathfinding (`#goto`) |
| Minecraft | 1.21.4 | |

---
