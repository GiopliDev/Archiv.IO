package com.example.addon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SignManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\b(\\d{1,2}[/\\.\\-]\\d{1,2}[/\\.\\-]\\d{2,4})\\b|" +
                    "\\b(\\d{1,2}\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-zA-Z]*\\s+\\d{2,4})\\b|" +
                    "\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-zA-Z]*\\s+\\d{1,2}(?:st|nd|rd|th)?\\s*,?\\s*\\d{2,4}\\b",
            Pattern.CASE_INSENSITIVE);

    public static final Map<String, SignEntry> SIGN_DB = new HashMap<>();
    
    // Global player database
    public static final Map<String, PlayerEntry> GLOBAL_PLAYERS = new HashMap<>();
    
    private static String currentServerPath = "default";

    public static void updateServerPath() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String path;
        if (mc.isInSingleplayer()) {
            path = "singleplayer";
        } else {
            ServerInfo info = mc.getCurrentServerEntry();
            path = (info != null) ? info.address.replace(":", "_") : "unknown";
        }
        
        if (!currentServerPath.equals(path)) {
            currentServerPath = path;
            load();
        }
    }

    public static String getCurrentServer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.isInSingleplayer()) return "singleplayer";
        ServerInfo info = mc.getCurrentServerEntry();
        return (info != null) ? info.address : "unknown";
    }

    private static File getRootFolder() {
        File folder = new File(MeteorClient.FOLDER, "ArchivIO");
        if (!folder.exists()) folder.mkdirs();
        return folder;
    }

    private static File getServerFolder() {
        File folder = new File(getRootFolder(), currentServerPath);
        if (!folder.exists()) folder.mkdirs();
        return folder;
    }

    public static void load() {
        SIGN_DB.clear();
        
        // Load server-specific signs
        File file = new File(getServerFolder(), "sign_logs.json");
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Type type = new TypeToken<Map<String, SignEntry>>() {}.getType();
                Map<String, SignEntry> loaded = GSON.fromJson(reader, type);
                if (loaded != null) SIGN_DB.putAll(loaded);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Load global players if not loaded yet
        if (GLOBAL_PLAYERS.isEmpty()) {
            File playersFile = new File(getRootFolder(), "global_players.json");
            if (playersFile.exists()) {
                try (FileReader reader = new FileReader(playersFile)) {
                    Type type = new TypeToken<Map<String, PlayerEntry>>() {}.getType();
                    Map<String, PlayerEntry> loaded = GSON.fromJson(reader, type);
                    if (loaded != null) GLOBAL_PLAYERS.putAll(loaded);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void save() {
        File file = new File(getServerFolder(), "sign_logs.json");
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(SIGN_DB, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void savePlayers() {
        File file = new File(getRootFolder(), "global_players.json");
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(GLOBAL_PLAYERS, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addKnownPlayer(String name) {
        String server = getCurrentServer();
        PlayerEntry entry = GLOBAL_PLAYERS.computeIfAbsent(name, k -> new PlayerEntry());
        if (entry.servers.add(server)) {
            savePlayers();
        }
    }

    public static boolean processSign(String content, BlockPos pos, World level) {
        updateServerPath();
        SignLocation loc = new SignLocation(pos,
                level != null && level.getRegistryKey() != null ? level.getRegistryKey().getValue().toString() : "unknown");

        if (SIGN_DB.containsKey(content)) {
            SignEntry entry = SIGN_DB.get(content);
            if (!entry.locations.contains(loc)) {
                entry.locations.add(loc);
                save();
                return true;
            }
            return false;
        }

        SignEntry entry = new SignEntry();
        entry.content = content;
        entry.locations.add(loc);

        Matcher matcher = DATE_PATTERN.matcher(content);
        while (matcher.find()) {
            entry.possibleDates.add(matcher.group(0).trim());
        }

        String lowerContent = content.toLowerCase();
        for (String player : GLOBAL_PLAYERS.keySet()) {
            if (lowerContent.contains(player.toLowerCase())) {
                entry.players.add(player);
                // Also update that the player was seen on this server
                addKnownPlayer(player);
            }
        }

        SIGN_DB.put(content, entry);
        save();
        return true;
    }

    public static int updateAllPlayers() {
        int updated = 0;
        String currentServer = getCurrentServer();
        for (SignEntry entry : SIGN_DB.values()) {
            String lowerContent = entry.content.toLowerCase();
            boolean changed = false;
            for (String player : GLOBAL_PLAYERS.keySet()) {
                if (lowerContent.contains(player.toLowerCase()) && !entry.players.contains(player)) {
                    entry.players.add(player);
                    GLOBAL_PLAYERS.get(player).servers.add(currentServer);
                    changed = true;
                }
            }
            if (changed) updated++;
        }
        if (updated > 0) {
            save();
            savePlayers();
        }
        return updated;
    }

    public static class PlayerEntry {
        public long firstSeen = System.currentTimeMillis();
        public Set<String> servers = new HashSet<>();
    }

    public static class SignEntry {
        public String content;
        public Set<String> possibleDates = new HashSet<>();
        public Set<String> players = new HashSet<>();
        public Set<SignLocation> locations = new HashSet<>();
    }

    public static class SignLocation {
        public int x, y, z;
        public String dimension;

        public SignLocation() {}

        public SignLocation(BlockPos pos, String dimension) {
            this.x = pos.getX();
            this.y = pos.getY();
            this.z = pos.getZ();
            this.dimension = dimension;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SignLocation that = (SignLocation) o;
            return x == that.x && y == that.y && z == that.z && Objects.equals(dimension, that.dimension);
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z, dimension);
        }

        @Override
        public String toString() {
            return "[" + x + ", " + y + ", " + z + " in " + dimension + "]";
        }
    }
}
