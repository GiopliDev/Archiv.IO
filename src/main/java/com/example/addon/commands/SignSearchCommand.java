package com.example.addon.commands;

import com.example.addon.SignManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.player.AbstractClientPlayer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

public class SignSearchCommand extends Command {
    public SignSearchCommand() {
        super("signs", "Searches through logged signs.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
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
            info("Loaded %d unique signs.", SignManager.SIGN_DB.size());
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("update-players").executes(context -> {
            info("WARNING: Updating players. This might take a while depending on the database size...");
            int updated = SignManager.updateAllPlayers();
            info("Updated players in %d signs.", updated);
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("player")
            .then(literal("add").then(argument("name", StringArgumentType.word()).executes(context -> {
                String name = StringArgumentType.getString(context, "name");
                if (name.equals("*")) {
                    if (mc.level == null) return SINGLE_SUCCESS;
                    int added = 0;
                    for (AbstractClientPlayer player : mc.level.players()) {
                        if (SignManager.KNOWN_PLAYERS.add(player.getName().getString())) {
                            added++;
                        }
                    }
                    if (added > 0) SignManager.savePlayers();
                    info("Added %d online players to the known list.", added);
                } else {
                    if (SignManager.KNOWN_PLAYERS.add(name)) {
                        SignManager.savePlayers();
                        info("Added player '%s'.", name);
                    } else {
                        info("Player '%s' is already known.", name);
                    }
                }
                return SINGLE_SUCCESS;
            })))
            .then(literal("remove").then(argument("name", StringArgumentType.word()).executes(context -> {
                String name = StringArgumentType.getString(context, "name");
                if (SignManager.KNOWN_PLAYERS.remove(name)) {
                    SignManager.savePlayers();
                    info("Removed player '%s'.", name);
                } else {
                    info("Player '%s' was not in the list.", name);
                }
                return SINGLE_SUCCESS;
            })))
        );

        builder.then(literal("export")
            .executes(context -> {
                exportToCSV(null);
                return SINGLE_SUCCESS;
            })
            .then(argument("param", StringArgumentType.greedyString()).executes(context -> {
                String param = StringArgumentType.getString(context, "param");
                exportToCSV(param);
                return SINGLE_SUCCESS;
            }))
        );
    }

    private void search(String query, String type) {
        int found = 0;
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
        
        info("Found %d matches.", found);
    }

    private void exportToCSV(String param) {
        File exportFile = new File(MeteorClient.FOLDER, "sign_export.csv");
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
