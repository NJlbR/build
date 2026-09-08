package com.towny.architecture.manager;

import com.towny.architecture.TownyArchitecturePlugin;
import com.towny.architecture.model.ActiveBuilding;
import com.towny.architecture.model.BuildingBlueprint;
import com.towny.architecture.model.ChunkCoord;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BuildingManager {
    private final TownyArchitecturePlugin plugin;
    private final Map<String, BuildingBlueprint> blueprints = new LinkedHashMap<>();
    private final Map<String, ActiveBuilding> activeBuildings = new ConcurrentHashMap<>();
    private final Map<ChunkCoord, String> chunkToBuildingMap = new ConcurrentHashMap<>();
    private final Set<String> completedWorldWonders = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, Set<ChunkCoord>> townBuildPlots = new ConcurrentHashMap<>();

    private File dataFile;
    private FileConfiguration dataConfig;

    public BuildingManager(TownyArchitecturePlugin plugin) {
        this.plugin = plugin;
    }

    public void loadBlueprints(FileConfiguration config) {
        blueprints.clear();
        ConfigurationSection section = config.getConfigurationSection("blueprints");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection bSection = section.getConfigurationSection(key);
            if (bSection == null) continue;

            String name = bSection.getString("name", key);
            String nameRu = bSection.getString("name-ru", name);
            String desc = bSection.getString("description", "");
            String iconName = bSection.getString("icon", "STONE_BRICKS");
            Material icon = Material.matchMaterial(iconName);
            if (icon == null) icon = Material.STONE_BRICKS;

            int mcmmoLevel = bSection.getInt("required-mcmmo-level", 1);
            boolean isWonder = bSection.getBoolean("is-world-wonder", false);

            int chunkWidth = bSection.getInt("chunk-footprint.width-chunks", 1);
            int chunkLength = bSection.getInt("chunk-footprint.length-chunks", 1);
            int heightY = bSection.getInt("dimensions.height-y", 12);

            Map<Material, Integer> resources = new LinkedHashMap<>();
            ConfigurationSection resSec = bSection.getConfigurationSection("resources");
            if (resSec != null) {
                for (String matKey : resSec.getKeys(false)) {
                    Material mat = Material.matchMaterial(matKey);
                    if (mat != null) {
                        resources.put(mat, resSec.getInt(matKey, 1));
                    }
                }
            }

            Map<String, Object> buffs = new LinkedHashMap<>();
            ConfigurationSection buffSec = bSection.getConfigurationSection("buffs");
            if (buffSec != null) {
                for (String buffKey : buffSec.getKeys(false)) {
                    buffs.put(buffKey, buffSec.get(buffKey));
                }
            }

            BuildingBlueprint bp = new BuildingBlueprint(
                    key, name, nameRu, desc, icon, mcmmoLevel, isWonder,
                    chunkWidth, chunkLength, heightY, resources, buffs
            );
            blueprints.put(key.toLowerCase(), bp);
        }
        plugin.getLogger().info("Загружено чертежей зданий: " + blueprints.size());
    }

    public void initDataStorage() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            plugin.saveResource("data.yml", false);
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadData();
    }

    public void loadData() {
        activeBuildings.clear();
        chunkToBuildingMap.clear();
        completedWorldWonders.clear();
        townBuildPlots.clear();

        List<String> wonders = dataConfig.getStringList("completed-wonders");
        for (String w : wonders) {
            if (w != null && !w.isEmpty()) {
                completedWorldWonders.add(w.toLowerCase());
            }
        }

        ConfigurationSection plotsSec = dataConfig.getConfigurationSection("town-build-plots");
        if (plotsSec != null) {
            for (String town : plotsSec.getKeys(false)) {
                List<String> coordStrings = plotsSec.getStringList(town);
                Set<ChunkCoord> coords = new HashSet<>();
                for (String s : coordStrings) {
                    ChunkCoord cc = ChunkCoord.parse(s);
                    if (cc != null) coords.add(cc);
                }
                townBuildPlots.put(town.toLowerCase(), coords);
            }
        }

        ConfigurationSection bldSec = dataConfig.getConfigurationSection("active-buildings");
        if (bldSec != null) {
            for (String bldId : bldSec.getKeys(false)) {
                ConfigurationSection sec = bldSec.getConfigurationSection(bldId);
                if (sec == null) continue;

                String bpId = sec.getString("blueprint-id");
                String town = sec.getString("town");
                String world = sec.getString("world");
                List<String> chunksList = sec.getStringList("chunks");
                Set<ChunkCoord> chunks = new HashSet<>();
                for (String c : chunksList) {
                    ChunkCoord cc = ChunkCoord.parse(c);
                    if (cc != null) chunks.add(cc);
                }

                BuildingBlueprint bp = blueprints.get(bpId != null ? bpId.toLowerCase() : "");
                if (bp == null) continue;

                ActiveBuilding ab = new ActiveBuilding(bldId, bp.getId(), town, world, chunks, bp.getRequiredResources());
                ConfigurationSection placedSec = sec.getConfigurationSection("placed");
                if (placedSec != null) {
                    for (String matKey : placedSec.getKeys(false)) {
                        Material m = Material.matchMaterial(matKey);
                        if (m != null) {
                            ab.setPlacedBlockCount(m, placedSec.getInt(matKey, 0));
                        }
                    }
                }

                String savedStatus = sec.getString("status");
                if (savedStatus != null) {
                    try {
                        ab.setStatus(ActiveBuilding.Status.valueOf(savedStatus));
                    } catch (IllegalArgumentException ignored) {}
                }

                ab.recalculateProgress();

                activeBuildings.put(bldId, ab);
                for (ChunkCoord cc : chunks) {
                    chunkToBuildingMap.put(cc, bldId);
                }
            }
        }
    }

    public synchronized void saveData() {
        if (dataConfig == null || dataFile == null) return;

        dataConfig.set("completed-wonders", new ArrayList<>(completedWorldWonders));

        dataConfig.set("town-build-plots", null);
        for (Map.Entry<String, Set<ChunkCoord>> entry : townBuildPlots.entrySet()) {
            List<String> list = new ArrayList<>();
            for (ChunkCoord cc : entry.getValue()) {
                list.add(cc.toString());
            }
            dataConfig.set("town-build-plots." + entry.getKey(), list);
        }

        dataConfig.set("active-buildings", null);
        for (ActiveBuilding ab : activeBuildings.values()) {
            String path = "active-buildings." + ab.getId();
            dataConfig.set(path + ".blueprint-id", ab.getBlueprintId());
            dataConfig.set(path + ".town", ab.getTownName());
            dataConfig.set(path + ".world", ab.getWorldName());
            dataConfig.set(path + ".status", ab.getStatus().name());
            dataConfig.set(path + ".health", ab.getHealthPercent());

            List<String> cList = new ArrayList<>();
            for (ChunkCoord cc : ab.getAssignedChunks()) {
                cList.add(cc.toString());
            }
            dataConfig.set(path + ".chunks", cList);

            for (Map.Entry<Material, Integer> placed : ab.getPlacedMaterials().entrySet()) {
                dataConfig.set(path + ".placed." + placed.getKey().name(), placed.getValue());
            }
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить data.yml: " + e.getMessage());
        }
    }

    public boolean markPlotAsBuild(Player player, Chunk chunk) {
        String town = plugin.getTownyHook().getPlayerTownName(player);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Вы должны состоять в городе!");
            return false;
        }

        if (!plugin.getTownyHook().isMayorOrAssistant(player)) {
            player.sendMessage(ChatColor.RED + "Только мэр или помощник города могут выделять участки под стройку!");
            return false;
        }

        ChunkCoord coord = ChunkCoord.fromChunk(chunk);
        if (!plugin.getTownyHook().areChunksInTown(Collections.singleton(coord), town)) {
            player.sendMessage(ChatColor.RED + "Этот чанк не принадлежит вашему городу!");
            return false;
        }

        Set<ChunkCoord> plots = townBuildPlots.computeIfAbsent(town.toLowerCase(), k -> new HashSet<>());
        if (plots.contains(coord)) {
            if (chunkToBuildingMap.containsKey(coord)) {
                player.sendMessage(ChatColor.RED + "На этом чанке уже возводится проект! Сначала завершите или удалите его.");
                return false;
            }
            plots.remove(coord);
            player.sendMessage(ChatColor.YELLOW + "[TownyBuilds] Чанк [" + coord.getX() + ", " + coord.getZ() + "] больше не является строительным участком.");
        } else {
            plots.add(coord);
            player.sendMessage(ChatColor.GREEN + "[TownyBuilds] Чанк [" + coord.getX() + ", " + coord.getZ() + "] успешно назначен строительным участком (/plot set build)!");
        }
        saveData();
        return true;
    }

    public Set<ChunkCoord> findAvailablePlotPattern(String townName, int widthChunks, int lengthChunks, Chunk currentChunk) {
        Set<ChunkCoord> plots = townBuildPlots.get(townName.toLowerCase());
        if (plots == null || plots.isEmpty()) return null;

        String world = currentChunk.getWorld().getName();
        int curX = currentChunk.getX();
        int curZ = currentChunk.getZ();

        // Поиск нормальной ориентации (width x length)
        Set<ChunkCoord> result = checkOrientation(plots, world, curX, curZ, widthChunks, lengthChunks);
        if (result != null) return result;

        // Поиск повернутой ориентации (length x width) если размеры отличаются
        if (widthChunks != lengthChunks) {
            result = checkOrientation(plots, world, curX, curZ, lengthChunks, widthChunks);
            if (result != null) return result;
        }

        return null;
    }

    private Set<ChunkCoord> checkOrientation(Set<ChunkCoord> plots, String world, int curX, int curZ, int w, int l) {
        for (int dx = -(w - 1); dx <= 0; dx++) {
            for (int dz = -(l - 1); dz <= 0; dz++) {
                Set<ChunkCoord> candidate = new HashSet<>();
                boolean valid = true;

                for (int x = 0; x < w; x++) {
                    for (int z = 0; z < l; z++) {
                        ChunkCoord check = new ChunkCoord(world, curX + dx + x, curZ + dz + z);
                        if (!plots.contains(check) || chunkToBuildingMap.containsKey(check)) {
                            valid = false;
                            break;
                        }
                        candidate.add(check);
                    }
                    if (!valid) break;
                }

                if (valid && candidate.size() == (w * l)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    public ActiveBuilding startBuilding(Player player, BuildingBlueprint bp) {
        String town = plugin.getTownyHook().getPlayerTownName(player);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Вы должны состоять в городе!");
            return null;
        }

        if (!plugin.getTownyHook().isMayorOrAssistant(player)) {
            player.sendMessage(ChatColor.RED + "Только мэр или помощник могут утверждать проекты!");
            return null;
        }

        if (bp.isWorldWonder() && completedWorldWonders.contains(bp.getId().toLowerCase())) {
            player.sendMessage(ChatColor.DARK_RED + "[!] Это Чудо Света уже воздвигнуто другим городом на сервере и заблокировано навсегда!");
            return null;
        }

        Chunk currentChunk = player.getLocation().getChunk();
        Set<ChunkCoord> footprint = findAvailablePlotPattern(town, bp.getChunkWidth(), bp.getChunkLength(), currentChunk);

        if (footprint == null || footprint.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Для проекта «" + bp.getNameRu() + "» требуется " +
                    bp.getChunkWidth() + "x" + bp.getChunkLength() + " свободных смежных чанков типа /plot set build!");
            return null;
        }

        String buildingId = town.toLowerCase() + "_" + bp.getId().toLowerCase() + "_" + System.currentTimeMillis();
        ActiveBuilding ab = new ActiveBuilding(buildingId, bp.getId(), town, currentChunk.getWorld().getName(), footprint, bp.getRequiredResources());

        activeBuildings.put(buildingId, ab);
        for (ChunkCoord cc : footprint) {
            chunkToBuildingMap.put(cc, buildingId);
        }

        saveData();

        player.sendMessage(ChatColor.GREEN + "══════════════════════════════════════════════");
        player.sendMessage(ChatColor.GOLD + "Проект «" + bp.getNameRu() + "» утвержден в городе " + town + "!");
        player.sendMessage(ChatColor.YELLOW + "Размер: " + bp.getChunkWidth() + "x" + bp.getChunkLength() + " чанков (" + footprint.size() + " чанка(-ов)).");
        player.sendMessage(ChatColor.GRAY + "Назначенные строители могут приступать к размещению блоков чертежа.");
        player.sendMessage(ChatColor.GREEN + "══════════════════════════════════════════════");

        return ab;
    }

    public ActiveBuilding getBuildingAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        ChunkCoord coord = new ChunkCoord(loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        String id = chunkToBuildingMap.get(coord);
        return id != null ? activeBuildings.get(id) : null;
    }

    public boolean handleBlockPlaced(Player player, Location loc, Material mat) {
        ActiveBuilding building = getBuildingAt(loc);
        if (building == null) return true; // Разрешить обычное строительство вне зон проектов

        // Строгая проверка прав: игрок должен быть строителем города
        if (!plugin.getTownyHook().hasBuilderPermission(player, building.getTownName())) {
            player.sendMessage(ChatColor.RED + "Только мэр или назначенный строитель могут строить на участках постройки города " + building.getTownName() + "!");
            return false;
        }

        BuildingBlueprint bp = blueprints.get(building.getBlueprintId().toLowerCase());
        if (bp == null) return false;

        // Строгая проверка mcMMO навыка
        if (!plugin.getMcmmoHook().hasRequiredLevel(player, bp.getRequiredMcmmoLevel())) {
            int currentLevel = plugin.getMcmmoHook().getPlayerBuildingSkillLevel(player);
            player.sendMessage(ChatColor.RED + "Ваш навык mcMMO Building (" + currentLevel +
                    ") ниже требуемого (" + bp.getRequiredMcmmoLevel() + ")!");
            return false;
        }

        int needed = building.getRequiredMaterials().getOrDefault(mat, 0);
        if (needed <= 0) {
            return true; // Вспомогательные блоки разрешены для строителей
        }

        int current = building.getPlacedCount(mat);

        if (building.getStatus() == ActiveBuilding.Status.UNDER_CONSTRUCTION) {
            if (current >= needed) {
                String matRu = SiegeDamageManager.getRussianMaterialName(mat);
                try {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent("§e✔ Квота для блока §f" + matRu + " §eуже заполнена (" + needed + "/" + needed + ")!"));
                } catch (Exception ignored) {}
                return true;
            }

            int newCurrent = building.addPlacedBlock(mat);
            awardBuilderXp(player);
            playBuildEffects(loc, player);

            String matRu = SiegeDamageManager.getRussianMaterialName(mat);
            String percent = String.format("%.1f", building.getHealthPercent());
            try {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent("§a✔ +" + matRu + " (" + newCurrent + "/" + needed + ") §8| §6Прогресс: §a" + percent + "%"));
            } catch (Exception ignored) {}

            if (building.isFullyBuilt()) {
                completeBuilding(building, bp, player);
            }
            return true;
        } else {
            // Восстановление поврежденного здания
            if (current < needed) {
                int newCurrent = building.addPlacedBlock(mat);
                awardBuilderXp(player);
                playBuildEffects(loc, player);

                String matRu = SiegeDamageManager.getRussianMaterialName(mat);
                double newHealth = building.getHealthPercent();
                try {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent("§a✔ Восстановление " + matRu + " (" + newCurrent + "/" + needed + ") §8| §eПрочность: §a" + String.format("%.1f", newHealth) + "%"));
                } catch (Exception ignored) {}

                if (newHealth >= 100.0 && building.isFullyBuilt()) {
                    building.setStatus(ActiveBuilding.Status.COMPLETED);
                    saveData();
                    String msg = ChatColor.GREEN + "✔ Здание «" + bp.getNameRu() + "» полностью восстановлено (Прочность: 100%)! Баффы снова активны!";
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        String pTown = plugin.getTownyHook().getPlayerTownName(p);
                        if (pTown != null && pTown.equalsIgnoreCase(building.getTownName())) {
                            p.sendMessage(msg);
                            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                        }
                    }
                }
                return true;
            }
        }
        return true;
    }

    public boolean handleBlockBroken(Player playerOrNull, Location loc, Material brokenMat) {
        ActiveBuilding building = getBuildingAt(loc);
        if (building == null) return true;

        if (playerOrNull != null) {
            if (!plugin.getTownyHook().hasBuilderPermission(playerOrNull, building.getTownName())) {
                playerOrNull.sendMessage(ChatColor.RED + "Вы не можете ломать блоки на строительном участке без прав строителя города " + building.getTownName() + "!");
                return false;
            }
        }

        int needed = building.getRequiredMaterials().getOrDefault(brokenMat, 0);
        if (needed <= 0) return true;

        int current = building.getPlacedCount(brokenMat);
        if (current <= 0) return true;

        String matRu = SiegeDamageManager.getRussianMaterialName(brokenMat);

        if (building.getStatus() == ActiveBuilding.Status.UNDER_CONSTRUCTION) {
            int newCurrent = building.removePlacedBlock(brokenMat);
            double pct = building.getHealthPercent();
            if (playerOrNull != null) {
                try {
                    playerOrNull.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent("§c✖ -" + matRu + " (" + newCurrent + "/" + needed + ") §8| §6Прогресс: §c" + String.format("%.1f", pct) + "%"));
                } catch (Exception ignored) {}
            }
        } else {
            int newCurrent = building.removePlacedBlock(brokenMat);
            double newHealth = building.getHealthPercent();
            double threshold = plugin.getConfig().getDouble("settings.buff-suspension-health-percent", 70.0);

            if (newHealth < threshold && building.getStatus() != ActiveBuilding.Status.DAMAGED) {
                building.setStatus(ActiveBuilding.Status.DAMAGED);
                BuildingBlueprint bp = blueprints.get(building.getBlueprintId());
                String bpName = bp != null ? bp.getNameRu() : building.getBlueprintId();
                Bukkit.broadcastMessage(ChatColor.RED + "[TownyArchitecture] Здание «" + bpName +
                        "» города " + building.getTownName() + " повреждено (" + String.format("%.1f", newHealth) + "%)! Баффы отключены до починки!");
            }

            if (playerOrNull != null) {
                try {
                    playerOrNull.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent("§c✖ Разрушен " + matRu + " §8| §cПрочность: " + String.format("%.1f", newHealth) + "%"));
                } catch (Exception ignored) {}
            }
        }
        return true;
    }

    /**
     * Периодическая проверка целостности блоков через ChunkSnapshot.
     * Запускается только когда ВСЕ чанки постройки загружены, чтобы исключить ложные срабатывания.
     */
    public void performPeriodicIntegrityCheck() {
        for (ActiveBuilding ab : activeBuildings.values()) {
            World world = Bukkit.getWorld(ab.getWorldName());
            if (world == null) continue;

            BuildingBlueprint bp = blueprints.get(ab.getBlueprintId().toLowerCase());
            if (bp == null) continue;

            // Проверяем, загружены ли абсолютно ВСЕ чанки постройки
            boolean allLoaded = true;
            for (ChunkCoord coord : ab.getAssignedChunks()) {
                if (!world.isChunkLoaded(coord.getX(), coord.getZ())) {
                    allLoaded = false;
                    break;
                }
            }
            if (!allLoaded) continue; // Пропускаем здания с выгруженными чанками

            Map<Material, Integer> blockCountInChunks = new HashMap<>();
            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight();

            for (ChunkCoord coord : ab.getAssignedChunks()) {
                Chunk chunk = world.getChunkAt(coord.getX(), coord.getZ());
                ChunkSnapshot snapshot = chunk.getChunkSnapshot();

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = minY; y < maxY; y++) {
                            Material mat = snapshot.getBlockType(x, y, z);
                            if (ab.getRequiredMaterials().containsKey(mat)) {
                                blockCountInChunks.put(mat, blockCountInChunks.getOrDefault(mat, 0) + 1);
                            }
                        }
                    }
                }
            }

            for (Map.Entry<Material, Integer> req : ab.getRequiredMaterials().entrySet()) {
                Material mat = req.getKey();
                int found = blockCountInChunks.getOrDefault(mat, 0);
                int needed = req.getValue();
                ab.setPlacedBlockCount(mat, Math.min(found, needed));
            }
            ab.recalculateProgress();
        }
    }

    private void awardBuilderXp(Player player) {
        int xp = plugin.getConfig().getInt("settings.mcmmo-xp-per-block", 15);
        plugin.getMcmmoHook().addBuildingXp(player, xp);
    }

    private void playBuildEffects(Location loc, Player player) {
        if (loc == null || loc.getWorld() == null) return;
        if (plugin.getConfig().getBoolean("settings.particles-enabled", true)) {
            loc.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, loc.clone().add(0.5, 1.0, 0.5), 6, 0.3, 0.3, 0.3, 0.05);
        }
        if (plugin.getConfig().getBoolean("settings.sound-effects-enabled", true) && player != null) {
            player.playSound(loc, Sound.BLOCK_STONE_PLACE, 1.0f, 1.2f);
        }
    }

    private void completeBuilding(ActiveBuilding building, BuildingBlueprint bp, Player lastBuilder) {
        building.setStatus(ActiveBuilding.Status.COMPLETED);
        building.setHealthPercent(100.0);

        if (bp.isWorldWonder()) {
            completedWorldWonders.add(bp.getId().toLowerCase());
            Bukkit.broadcastMessage(ChatColor.GOLD + "★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "ВЕЛИКОЕ ЧУДО СВЕТА «" + bp.getNameRu() + "» воздвигнуто городом " + building.getTownName() + "!");
            Bukkit.broadcastMessage(ChatColor.GOLD + "★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★");
        } else {
            String raw = plugin.getConfig().getString("messages.building-completed", "&6&l[ГОРОД]&a Строительство «&e%name%&a» завершено на 100%%! Баффы активированы!");
            String msg = ChatColor.translateAlternateColorCodes('&', raw.replace("%name%", bp.getNameRu()));
            for (Player p : Bukkit.getOnlinePlayers()) {
                String town = plugin.getTownyHook().getPlayerTownName(p);
                if (town != null && town.equalsIgnoreCase(building.getTownName())) {
                    p.sendMessage(msg);
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                }
            }
        }
        saveData();
    }

    public Map<String, BuildingBlueprint> getBlueprints() { return Collections.unmodifiableMap(blueprints); }
    public BuildingBlueprint getBlueprint(String id) { return blueprints.get(id != null ? id.toLowerCase() : ""); }
    public Map<String, ActiveBuilding> getActiveBuildings() { return Collections.unmodifiableMap(activeBuildings); }
    public Set<ChunkCoord> getTownBuildPlots(String town) { return townBuildPlots.getOrDefault(town.toLowerCase(), Collections.emptySet()); }
    public boolean isWonderCompleted(String wonderId) { return completedWorldWonders.contains(wonderId.toLowerCase()); }
    public void resetWonder(String wonderId) { completedWorldWonders.remove(wonderId.toLowerCase()); saveData(); }
}
