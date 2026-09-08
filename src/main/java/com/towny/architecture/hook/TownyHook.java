package com.towny.architecture.hook;

import com.towny.architecture.TownyArchitecturePlugin;
import com.towny.architecture.model.ChunkCoord;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Set;

public class TownyHook {
    private final TownyArchitecturePlugin plugin;
    private boolean townyEnabled = false;

    public TownyHook(TownyArchitecturePlugin plugin) {
        this.plugin = plugin;
        if (Bukkit.getPluginManager().isPluginEnabled("Towny")) {
            this.townyEnabled = true;
            plugin.getLogger().info("Успешно подключен хук Towny 0.98+!");
        } else {
            plugin.getLogger().warning("Towny не обнаружен. Будет использован автономный режим.");
        }
    }

    public boolean isTownyEnabled() {
        return townyEnabled;
    }

    public String getPlayerTownName(Player player) {
        if (player == null) return null;
        if (!townyEnabled) {
            return player.isOp() ? "OpTown" : "DefaultTown";
        }

        try {
            Class<?> townyUniverseClass = Class.forName("com.palmergames.bukkit.towny.TownyUniverse");
            Method getInstanceMethod = townyUniverseClass.getMethod("getInstance");
            Object universe = getInstanceMethod.invoke(null);

            Method getResidentMethod = universe.getClass().getMethod("getResident", String.class);
            Object resident = getResidentMethod.invoke(universe, player.getName());
            if (resident == null) return null;

            Method getTownOrNull = resident.getClass().getMethod("getTownOrNull");
            Object town = getTownOrNull.invoke(resident);
            if (town == null) return null;

            Method getNameMethod = town.getClass().getMethod("getName");
            return (String) getNameMethod.invoke(town);
        } catch (Exception e) {
            try {
                // Fallback через TownyAPI
                Class<?> townyApiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
                Object apiInstance = townyApiClass.getMethod("getInstance").invoke(null);
                Method getTownMethod = townyApiClass.getMethod("getTown", Player.class);
                Object town = getTownMethod.invoke(apiInstance, player);
                if (town != null) {
                    Method getNameMethod = town.getClass().getMethod("getName");
                    return (String) getNameMethod.invoke(town);
                }
            } catch (Exception ignored) {}
            return null;
        }
    }

    public boolean isMayorOrAssistant(Player player) {
        if (player == null) return false;
        if (player.isOp() || player.hasPermission("townyarchitecture.admin")) return true;
        if (!townyEnabled) return true;

        try {
            Class<?> townyUniverseClass = Class.forName("com.palmergames.bukkit.towny.TownyUniverse");
            Method getInstanceMethod = townyUniverseClass.getMethod("getInstance");
            Object universe = getInstanceMethod.invoke(null);

            Method getResidentMethod = universe.getClass().getMethod("getResident", String.class);
            Object resident = getResidentMethod.invoke(universe, player.getName());
            if (resident == null) return false;

            Method isMayorMethod = resident.getClass().getMethod("isMayor");
            boolean isMayor = (boolean) isMayorMethod.invoke(resident);
            if (isMayor) return true;

            try {
                Method hasRankMethod = resident.getClass().getMethod("hasRank", String.class);
                if ((boolean) hasRankMethod.invoke(resident, "assistant") ||
                    (boolean) hasRankMethod.invoke(resident, "comayor")) {
                    return true;
                }
            } catch (Exception ignored) {}

            try {
                Method hasTownRankMethod = resident.getClass().getMethod("hasTownRank", String.class);
                if ((boolean) hasTownRankMethod.invoke(resident, "assistant") ||
                    (boolean) hasTownRankMethod.invoke(resident, "comayor")) {
                    return true;
                }
            } catch (Exception ignored) {}

            return player.hasPermission("townyarchitecture.admin");
        } catch (Exception e) {
            return player.isOp();
        }
    }

    /**
     * Строгая проверка: игрок должен быть Мэром, Помощником или иметь ранг строителя ('builder')
     * назначенный мэром в Towny (/town rank add <player> builder) либо право townyarchitecture.builder!
     */
    public boolean hasBuilderPermission(Player player, String townName) {
        if (player == null) return false;
        if (player.isOp() || player.hasPermission("townyarchitecture.admin")) return true;
        if (!townyEnabled) return true;

        try {
            Class<?> townyApiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Object apiInstance = townyApiClass.getMethod("getInstance").invoke(null);
            Method getResidentMethod = townyApiClass.getMethod("getResident", Player.class);
            Object resident = getResidentMethod.invoke(apiInstance, player);
            if (resident == null) return false;

            // Проверка принадлежности игрока к этому городу
            Method getTownOrNull = resident.getClass().getMethod("getTownOrNull");
            Object town = getTownOrNull.invoke(resident);
            if (town == null) return false;

            Method getNameMethod = town.getClass().getMethod("getName");
            String pTownName = (String) getNameMethod.invoke(town);
            if (townName != null && !pTownName.equalsIgnoreCase(townName)) {
                return false;
            }

            // 1. Мэр города
            Method isMayorMethod = resident.getClass().getMethod("isMayor");
            if ((boolean) isMayorMethod.invoke(resident)) return true;

            // 2. Помощник или Со-мэр
            try {
                Method hasRankMethod = resident.getClass().getMethod("hasRank", String.class);
                if ((boolean) hasRankMethod.invoke(resident, "assistant") ||
                    (boolean) hasRankMethod.invoke(resident, "comayor")) {
                    return true;
                }
            } catch (Exception ignored) {}

            // 3. Ранг 'builder' в Towny
            try {
                Method hasRankMethod = resident.getClass().getMethod("hasRank", String.class);
                if ((boolean) hasRankMethod.invoke(resident, "builder")) {
                    return true;
                }
            } catch (Exception ignored) {}

            try {
                Method hasTownRankMethod = resident.getClass().getMethod("hasTownRank", String.class);
                if ((boolean) hasTownRankMethod.invoke(resident, "builder")) {
                    return true;
                }
            } catch (Exception ignored) {}

            // 4. Bukkit права
            if (player.hasPermission("townyarchitecture.builder") || 
                player.hasPermission("towny.rank.builder")) {
                return true;
            }

        } catch (Exception ignored) {}

        return false;
    }

    public boolean isLocationInTown(Location loc, String townName) {
        if (loc == null || townName == null) return false;
        if (!townyEnabled) return true;

        try {
            Class<?> townyApiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Object apiInstance = townyApiClass.getMethod("getInstance").invoke(null);
            Method getTownBlockMethod = townyApiClass.getMethod("getTownBlock", Location.class);
            Object townBlock = getTownBlockMethod.invoke(apiInstance, loc);
            if (townBlock == null) return false;

            Method getTownMethod = townBlock.getClass().getMethod("getTown");
            Object town = getTownMethod.invoke(townBlock);
            if (town != null) {
                Method getNameMethod = town.getClass().getMethod("getName");
                String blockTownName = (String) getNameMethod.invoke(town);
                return blockTownName != null && blockTownName.equalsIgnoreCase(townName);
            }
        } catch (Exception ignored) {}

        return false;
    }

    public boolean areChunksInTown(Set<ChunkCoord> coords, String townName) {
        if (!townyEnabled || coords == null || coords.isEmpty()) return true;
        for (ChunkCoord coord : coords) {
            World world = Bukkit.getWorld(coord.getWorldName());
            if (world == null) return false;
            Location center = new Location(world, (coord.getX() << 4) + 8, 64, (coord.getZ() << 4) + 8);
            if (!isLocationInTown(center, townName)) {
                return false;
            }
        }
        return true;
    }
}
