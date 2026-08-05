package com.tomkestookit.commandtask;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tomkestookit.SignCommandHandler;
import com.tomkestookit.TomkesTookitMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 命令任务存储：任务组 + 开机自启指令集。
 *
 * 采用与 LandmarkStore 相同的 JSON / LinkedHashMap 模式，保证顺序稳定、可直接给菜单展示。
 */
public final class CommandTaskStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(TomkesTookitMod.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String FILE_NAME = "tomkestookit_command_tasks.json";

    private static final class Data {
        Map<String, TaskGroup> groups = new LinkedHashMap<>();
        Map<String, StartupSet> startupSets = new LinkedHashMap<>();
    }

    public static final class TaskGroup {
        public String id;
        public String name;
        public List<String> commands = new ArrayList<>();

        public TaskGroup() {}

        public TaskGroup(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static final class StartupSet {
        public String id;
        public String name;
        public boolean enabled = true;
        public List<String> groupIds = new ArrayList<>();

        public StartupSet() {}

        public StartupSet(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static Data data = new Data();
    private static Path dataFile;

    private CommandTaskStore() {}

    public static void load(MinecraftServer server) {
        dataFile = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve(FILE_NAME);
        if (!Files.exists(dataFile)) return;
        try (Reader r = Files.newBufferedReader(dataFile)) {
            Type type = new TypeToken<Data>() {}.getType();
            Data loaded = GSON.fromJson(r, type);
            if (loaded != null) {
                if (loaded.groups == null) loaded.groups = new LinkedHashMap<>();
                if (loaded.startupSets == null) loaded.startupSets = new LinkedHashMap<>();
                for (TaskGroup g : loaded.groups.values()) normalizeGroup(g);
                for (StartupSet s : loaded.startupSets.values()) normalizeStartupSet(s);
                data = loaded;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load command task store", e);
        }
    }

    public static Collection<TaskGroup> groups() {
        return Collections.unmodifiableCollection(data.groups.values());
    }

    public static Collection<StartupSet> startupSets() {
        return Collections.unmodifiableCollection(data.startupSets.values());
    }

    public static TaskGroup group(String id) {
        return data.groups.get(id);
    }

    public static StartupSet startupSet(String id) {
        return data.startupSets.get(id);
    }

    public static boolean hasGroup(String id) {
        return data.groups.containsKey(id);
    }

    public static boolean hasStartupSet(String id) {
        return data.startupSets.containsKey(id);
    }

    public static TaskGroup createGroup(String name) {
        String id = uniqueId(slugify(name), data.groups);
        TaskGroup group = new TaskGroup(id, sanitizeName(name, "任务组"));
        data.groups.put(id, group);
        save();
        return group;
    }

    public static boolean renameGroup(String id, String name) {
        TaskGroup group = data.groups.get(id);
        if (group == null) return false;
        group.name = sanitizeName(name, group.name);
        save();
        return true;
    }

    public static boolean deleteGroup(String id) {
        if (data.groups.remove(id) == null) return false;
        for (StartupSet set : data.startupSets.values()) {
            set.groupIds.removeIf(id::equals);
        }
        save();
        return true;
    }

    public static boolean addCommand(String groupId, String command) {
        TaskGroup group = data.groups.get(groupId);
        String normalized = SignCommandHandler.normalize(command);
        if (group == null || normalized == null) return false;
        group.commands.add(normalized);
        save();
        return true;
    }

    public static boolean addCommands(String groupId, List<String> commands) {
        TaskGroup group = data.groups.get(groupId);
        if (group == null) return false;
        boolean changed = false;
        for (String command : commands) {
            String normalized = SignCommandHandler.normalize(command);
            if (normalized == null) continue;
            group.commands.add(normalized);
            changed = true;
        }
        if (changed) save();
        return changed;
    }

    public static boolean removeCommand(String groupId, int index) {
        TaskGroup group = data.groups.get(groupId);
        if (group == null || index < 0 || index >= group.commands.size()) return false;
        group.commands.remove(index);
        save();
        return true;
    }

    public static StartupSet createStartupSet(String name) {
        String id = uniqueId(slugify(name), data.startupSets);
        StartupSet set = new StartupSet(id, sanitizeName(name, "开机自启"));
        data.startupSets.put(id, set);
        save();
        return set;
    }

    public static boolean renameStartupSet(String id, String name) {
        StartupSet set = data.startupSets.get(id);
        if (set == null) return false;
        set.name = sanitizeName(name, set.name);
        save();
        return true;
    }

    public static boolean deleteStartupSet(String id) {
        if (data.startupSets.remove(id) == null) return false;
        save();
        return true;
    }

    public static boolean setStartupEnabled(String id, boolean enabled) {
        StartupSet set = data.startupSets.get(id);
        if (set == null) return false;
        set.enabled = enabled;
        save();
        return true;
    }

    public static boolean addGroupToStartupSet(String setId, String groupId) {
        StartupSet set = data.startupSets.get(setId);
        if (set == null || !data.groups.containsKey(groupId)) return false;
        if (!set.groupIds.contains(groupId)) {
            set.groupIds.add(groupId);
            save();
        }
        return true;
    }

    public static boolean removeGroupFromStartupSet(String setId, String groupId) {
        StartupSet set = data.startupSets.get(setId);
        if (set == null) return false;
        boolean removed = set.groupIds.removeIf(groupId::equals);
        if (removed) save();
        return removed;
    }

    public static boolean moveGroupInStartupSet(String setId, String groupId, int delta) {
        StartupSet set = data.startupSets.get(setId);
        if (set == null) return false;
        int idx = set.groupIds.indexOf(groupId);
        if (idx < 0) return false;
        int next = Math.max(0, Math.min(set.groupIds.size() - 1, idx + delta));
        if (next == idx) return false;
        set.groupIds.remove(idx);
        set.groupIds.add(next, groupId);
        save();
        return true;
    }

    public static List<String> collectCommands(String groupId) {
        TaskGroup group = data.groups.get(groupId);
        if (group == null) return List.of();
        return List.copyOf(group.commands);
    }

    private static void normalizeGroup(TaskGroup group) {
        if (group == null) return;
        group.id = sanitizeName(group.id, "");
        group.name = sanitizeName(group.name, group.id);
        if (group.commands == null) group.commands = new ArrayList<>();
        List<String> normalized = new ArrayList<>();
        for (String cmd : group.commands) {
            String n = SignCommandHandler.normalize(cmd);
            if (n != null) normalized.add(n);
        }
        group.commands = normalized;
    }

    private static void normalizeStartupSet(StartupSet set) {
        if (set == null) return;
        set.id = sanitizeName(set.id, "");
        set.name = sanitizeName(set.name, set.id);
        if (set.groupIds == null) set.groupIds = new ArrayList<>();
        set.groupIds.removeIf(id -> !data.groups.containsKey(id));
    }

    private static void save() {
        if (dataFile == null) return;
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer w = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(data, w);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save command task store", e);
        }
    }

    private static String slugify(String name) {
        if (name == null) return "task";
        StringBuilder out = new StringBuilder();
        for (char ch : name.strip().toLowerCase().toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                out.append(ch);
            } else if (out.length() == 0 || out.charAt(out.length() - 1) != '-') {
                out.append('-');
            }
        }
        String s = out.toString().replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "task" : s;
    }

    private static String uniqueId(String base, Map<String, ?> map) {
        String id = base;
        int i = 2;
        while (map.containsKey(id)) {
            id = base + "-" + i++;
        }
        return id;
    }

    private static String sanitizeName(String name, String fallback) {
        if (name == null) return fallback;
        String trimmed = name.strip();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
