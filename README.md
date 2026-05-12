# 📚 Archiv.IO - Meteor Addon

![Version](https://img.shields.io/badge/Version-0.1.0-blue?style=for-the-badge)
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

## 🛠️ Commands

| Command | Description |
| :--- | :--- |
| `.signs list` | Shows how many unique signs are logged for the current server. |
| `.signs search text <query>` | Search the database for specific text. |
| `.signs search date <query>` | Search specifically for signs containing a certain date. |
| `.signs search player <query>` | Find signs that mention a specific player. |
| `.signs player add <name>` | Manually add a player to the "Known Players" list. |
| `.signs player addonline` | Adds everyone currently online in the server to your known list. |
| `.signs export` | Exports the current server database to a CSV file in your Meteor folder. |

---
