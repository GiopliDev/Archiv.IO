package com.example.addon.commands;

import com.example.addon.SignManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.command.CommandSource;
import net.minecraft.client.network.PlayerListEntry;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public class SignSearchCommand extends Command {
    public SignSearchCommand() {
        super("signs", "Searches through logged signs.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("search")
            .then(literal("text").then(argument("query", StringArgumentType.greedyString()).executes(context -> {
                String query = StringArgumentType.getString(context, "query").toLowerCase();
                search(query, "text");
                return SINGLE_SUCCESS;
            })))
            .then(literal("date").then(argument("query", StringArgumentType.greedyString()).executes(context -> {
                String query = StringArgumentType.getString(context, "query").toLowerCase();
                search(query, "date");
                return SINGLE_SUCCESS;
            })))
            .then(literal("player").then(argument("query", StringArgumentType.greedyString()).executes(context -> {
                String query = StringArgumentType.getString(context, "query").toLowerCase();
                search(query, "player");
                return SINGLE_SUCCESS;
            })))
        );
        
        builder.then(literal("list").executes(context -> {
            SignManager.updateServerPath();
            info("Loaded %d unique signs for current server.", SignManager.SIGN_DB.size());
            info("Total known players globally: %d", SignManager.GLOBAL_PLAYERS.size());
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("update-players").executes(context -> {
            SignManager.updateServerPath();
            info("Updating players for current server database using global player list...");
            int updated = SignManager.updateAllPlayers();
            info("Updated players in %d signs.", updated);
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("player")
            .then(literal("addonline").executes(context -> {
                if (mc.getNetworkHandler() == null) return SINGLE_SUCCESS;
                SignManager.updateServerPath();
                int added = 0;
                Collection<PlayerListEntry> players = mc.getNetworkHandler().getPlayerList();
                for (PlayerListEntry player : players) {
                    String name = player.getProfile().getName();
                    if (!SignManager.GLOBAL_PLAYERS.containsKey(name)) {
                        SignManager.addKnownPlayer(name);
                        added++;
                    }
                }
                if (added > 0) {
                    info("Added %d new online players to the global list.", added);
                } else {
                    info("No new players found (all already known).");
                }
                return SINGLE_SUCCESS;
            }))
            .then(literal("add").then(argument("name", StringArgumentType.word())
                .suggests((context, suggester) -> {
                    if (mc.getNetworkHandler() != null) {
                        for (PlayerListEntry player : mc.getNetworkHandler().getPlayerList()) {
                            suggester.suggest(player.getProfile().getName());
                        }
                    }
                    return suggester.buildFuture();
                })
                .executes(context -> {
                String name = StringArgumentType.getString(context, "name");
                SignManager.updateServerPath();
                if (!SignManager.GLOBAL_PLAYERS.containsKey(name)) {
                    SignManager.addKnownPlayer(name);
                    info("Added player '%s' to global list.", name);
                } else {
                    info("Player '%s' is already in global list.", name);
                }
                return SINGLE_SUCCESS;
            })))
            .then(literal("remove").then(argument("name", StringArgumentType.word()).executes(context -> {
                String name = StringArgumentType.getString(context, "name");
                SignManager.updateServerPath();
                if (SignManager.GLOBAL_PLAYERS.remove(name) != null) {
                    SignManager.savePlayers();
                    info("Removed player '%s' from global list.", name);
                } else {
                    info("Player '%s' was not in the list.", name);
                }
                return SINGLE_SUCCESS;
            })))
        );

        builder.then(literal("export")
            .executes(context -> {
                SignManager.updateServerPath();
                exportToCSV(null);
                return SINGLE_SUCCESS;
            })
            .then(argument("param", StringArgumentType.greedyString()).executes(context -> {
                SignManager.updateServerPath();
                String param = StringArgumentType.getString(context, "param");
                exportToCSV(param);
                return SINGLE_SUCCESS;
            }))
        );
    }

    private void search(String query, String type) {
        SignManager.updateServerPath();
        int found = 0;
        
        if (type.equals("player")) {
            for (Map.Entry<String, SignManager.PlayerEntry> pEntry : SignManager.GLOBAL_PLAYERS.entrySet()) {
                if (pEntry.getKey().toLowerCase().contains(query)) {
                    info("--- Global Player Info ---");
                    info("Name: " + pEntry.getKey());
                    info("Seen on: " + String.join(", ", pEntry.getValue().servers));
                }
            }
        }

        for (Map.Entry<String, SignManager.SignEntry> entry : SignManager.SIGN_DB.entrySet()) {
            boolean match = false;
            
            if (type.equals("date")) {
                for (String date : entry.getValue().possibleDates) {
                    if (date.toLowerCase().contains(query)) {
                        match = true;
                        break;
                    }
                }
            } else if (type.equals("player")) {
                for (String p : entry.getValue().players) {
                    if (p.toLowerCase().contains(query)) {
                        match = true;
                        break;
                    }
                }
            } else {
                if (entry.getKey().toLowerCase().contains(query)) {
                    match = true;
                }
            }

            if (match) {
                found++;
                info("--- Sign Match ---");
                info("Text: " + entry.getKey());
                if (!entry.getValue().possibleDates.isEmpty()) {
                    info("Dates: " + String.join(", ", entry.getValue().possibleDates));
                }
                if (!entry.getValue().players.isEmpty()) {
                    info("Players: " + String.join(", ", entry.getValue().players));
                }
                for (SignManager.SignLocation loc : entry.getValue().locations) {
                    info("At: " + loc.toString());
                }
            }
        }
        
        info("Found %d matches for this server.", found);
    }

    private void exportToCSV(String param) {
        File exportFile = new File(MeteorClient.FOLDER, "ArchivIO/export_" + System.currentTimeMillis() + ".csv");
        try (FileWriter writer = new FileWriter(exportFile)) {
            writer.write("Content,Dates,Players,Dimension,X,Y,Z\n");
            int exported = 0;

            for (Map.Entry<String, SignManager.SignEntry> entry : SignManager.SIGN_DB.entrySet()) {
                boolean match = true;
                if (param != null) {
                    match = entry.getKey().toLowerCase().contains(param.toLowerCase());
                }

                if (match) {
                    String cleanContent = entry.getKey().replace("\n", " ").replace(",", ";").replace("\"", "\"\"");
                    String dates = String.join(";", entry.getValue().possibleDates);
                    String players = String.join(";", entry.getValue().players);

                    for (SignManager.SignLocation loc : entry.getValue().locations) {
                        writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",%d,%d,%d\n",
                                cleanContent, dates, players, loc.dimension, loc.x, loc.y, loc.z));
                    }
                    exported++;
                }
            }
            info("Exported %d signs to %s", exported, exportFile.getAbsolutePath());
        } catch (IOException e) {
            error("Failed to export: " + e.getMessage());
        }
    }
}
