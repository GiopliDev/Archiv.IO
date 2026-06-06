package com.example.addon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.*;

public class KitManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File(new File(MeteorClient.FOLDER, "ArchivIO"), "kit_config.json");
    private static final File STATS_FILE = new File(new File(MeteorClient.FOLDER, "ArchivIO"), "kit_stats.json");
    private static final File HISTORY_FILE = new File(new File(MeteorClient.FOLDER, "ArchivIO"), "kit_history.json");
    public static final Map<String, KitChest> KITS = new HashMap<>();

    // Stats
    public static int totalKitsDelivered = 0;
    public static final Set<String> uniqueUsers = new HashSet<>();
    public static final Map<String, Integer> kitDeliveryCounts = new HashMap<>();

    // History
    public static final List<HistoryEntry> HISTORY = new ArrayList<>();

    public static void load() {
        KITS.clear();
        if (FILE.exists()) {
            try (FileReader reader = new FileReader(FILE)) {
                Type type = new TypeToken<Map<String, KitChest>>() {
                }.getType();
                Map<String, KitChest> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    KITS.putAll(loaded);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Load stats
        totalKitsDelivered = 0;
        uniqueUsers.clear();
        kitDeliveryCounts.clear();
        if (STATS_FILE.exists()) {
            try (FileReader reader = new FileReader(STATS_FILE)) {
                StatsData data = GSON.fromJson(reader, StatsData.class);
                if (data != null) {
                    totalKitsDelivered = data.totalKitsDelivered;
                    if (data.uniqueUsers != null) {
                        for (String user : data.uniqueUsers) {
                            uniqueUsers.add(user.toLowerCase());
                        }
                    }
                    if (data.kitDeliveryCounts != null) {
                        kitDeliveryCounts.putAll(data.kitDeliveryCounts);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Load history
        HISTORY.clear();
        if (HISTORY_FILE.exists()) {
            try (FileReader reader = new FileReader(HISTORY_FILE)) {
                Type type = new TypeToken<List<HistoryEntry>>() {
                }.getType();
                List<HistoryEntry> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    HISTORY.addAll(loaded);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void save() {
        File dir = FILE.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(KITS, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveStats() {
        File dir = STATS_FILE.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try (FileWriter writer = new FileWriter(STATS_FILE)) {
            StatsData data = new StatsData();
            data.totalKitsDelivered = totalKitsDelivered;
            data.uniqueUsers = new ArrayList<>(uniqueUsers);
            data.kitDeliveryCounts = new HashMap<>(kitDeliveryCounts);
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveHistory() {
        File dir = HISTORY_FILE.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try (FileWriter writer = new FileWriter(HISTORY_FILE)) {
            GSON.toJson(HISTORY, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addDelivery(String requester, String kitName) {
        totalKitsDelivered++;
        uniqueUsers.add(requester.toLowerCase());
        if (kitName != null) {
            kitDeliveryCounts.merge(kitName.toLowerCase(), 1, Integer::sum);
        }
        saveStats();
    }

    public static void logHistory(String requester, String kitName, double x, double y, double z, String dimension) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        HISTORY.add(new HistoryEntry(timestamp, requester, kitName, x, y, z, dimension));
        saveHistory();
    }

    public static void setKit(String name, BlockPos pos, String dimension) {
        KITS.put(name.toLowerCase(), new KitChest(pos.getX(), pos.getY(), pos.getZ(), dimension));
        save();
    }

    public static KitChest getKit(String name) {
        return KITS.get(name.toLowerCase());
    }

    public static class KitChest {
        public int x, y, z;
        public String dimension;

        public KitChest() {
        }

        public KitChest(int x, int y, int z, String dimension) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
        }

        public BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }
    }

    public static class StatsData {
        public int totalKitsDelivered;
        public List<String> uniqueUsers;
        public Map<String, Integer> kitDeliveryCounts;
    }

    public static class HistoryEntry {
        public String timestamp;
        public String requester;
        public String kit;
        public double x, y, z;
        public String dimension;

        public HistoryEntry(String timestamp, String requester, String kit, double x, double y, double z,
                String dimension) {
            this.timestamp = timestamp;
            this.requester = requester;
            this.kit = kit;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
        }
    }
}
