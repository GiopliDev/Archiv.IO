package com.example.addon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SignManager {
    public static final File FILE = new File(MeteorClient.FOLDER, "sign_logs.json");
    public static final File PLAYERS_FILE = new File(MeteorClient.FOLDER, "sign_players.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // goofy date regex
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\b(\\d{1,2}[/\\.\\-]\\d{1,2}[/\\.\\-]\\d{2,4})\\b|" +
                    "\\b(\\d{1,2}\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-zA-Z]*\\s+\\d{2,4})\\b|" +
                    "\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-zA-Z]*\\s+\\d{1,2}(?:st|nd|rd|th)?\\s*,?\\s*\\d{2,4}\\b",
            Pattern.CASE_INSENSITIVE);

    // uses sign content as key
    public static final Map<String, SignEntry> SIGN_DB = new HashMap<>();

    // Known players tracking
    public static final Set<String> KNOWN_PLAYERS = new HashSet<>();

    public static void load() {
        if (FILE.exists()) {
            try (FileReader reader = new FileReader(FILE)) {
                Type type = new TypeToken<Map<String, SignEntry>>() {
                }.getType();
                Map<String, SignEntry> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    SIGN_DB.clear();
                    SIGN_DB.putAll(loaded);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (PLAYERS_FILE.exists()) {
            try (FileReader reader = new FileReader(PLAYERS_FILE)) {
                Type type = new TypeToken<Set<String>>() {
                }.getType();
                Set<String> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    KNOWN_PLAYERS.clear();
                    KNOWN_PLAYERS.addAll(loaded);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void save() {
        try {
            if (!FILE.getParentFile().exists()) {
                FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(SIGN_DB, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void savePlayers() {
        try {
            if (!PLAYERS_FILE.getParentFile().exists()) {
                PLAYERS_FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(PLAYERS_FILE)) {
                GSON.toJson(KNOWN_PLAYERS, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean processSign(String content, BlockPos pos, Level level) {
        SignLocation loc = new SignLocation(pos,
                level != null && level.dimension() != null ? level.dimension().toString() : "unknown");

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

        // Extract possible dates using Regex
        Matcher matcher = DATE_PATTERN.matcher(content);
        while (matcher.find()) {
            entry.possibleDates.add(matcher.group(0).trim());
        }

        // Search for known players in the sign text
        String lowerContent = content.toLowerCase();
        for (String player : KNOWN_PLAYERS) {
            if (lowerContent.contains(player.toLowerCase())) {
                entry.players.add(player);
            }
        }

        SIGN_DB.put(content, entry);
        save();
        return true;
    }

    public static int updateAllPlayers() {
        int updated = 0;
        for (SignEntry entry : SIGN_DB.values()) {
            String lowerContent = entry.content.toLowerCase();
            boolean changed = false;
            for (String player : KNOWN_PLAYERS) {
                if (lowerContent.contains(player.toLowerCase()) && !entry.players.contains(player)) {
                    entry.players.add(player);
                    changed = true;
                }
            }
            if (changed) updated++;
        }
        if (updated > 0) save();
        return updated;
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

        public SignLocation() {
        }

        public SignLocation(BlockPos pos, String dimension) {
            this.x = pos.getX();
            this.y = pos.getY();
            this.z = pos.getZ();
            this.dimension = dimension;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
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
