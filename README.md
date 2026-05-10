# Archiv.IO
A useless Meteor Client addon designed specifically for server archivers, explorers, and history preservers.

# Modules:

### SignLogger:
Automatically scans the surrounding area for signs and logs their content into a structured database.

#### Features:
```
Smart JSON Storage || Identical signs are grouped together under a single text entry in sign_logs.json, saving only the new coordinates.
Date Extraction || Automatically scans sign text for various date formats and logs them for easy chronological searching.
Dynamic ESP || Highlights logged signs.
Player Tracking || Keep track of known players. The addon will cross-reference signs against your tracked players list.
```

#### Commands:
```
.signs list || View the total number of unique signs logged.
.signs search text <query> || Search the database for a specific word or phrase.
.signs search date <date> || Find signs that contain a specific date.
.signs search player <name> || Find signs written by or mentioning a tracked player.
.signs player add <name> || Add a specific player to the tracking list.
.signs player add * || Quickly add all currently online players to the tracking list.
.signs player remove <name> || Remove a player from the tracking list.
.signs update-players || Retroactively scans the entire database to tag signs matching your updated player list.
.signs export [optional_query] || Exports the entire database (or filtered results) into a clean sign_export.csv file.
```
