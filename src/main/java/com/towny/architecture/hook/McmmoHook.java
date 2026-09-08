package com.towny.architecture.hook;

import com.towny.architecture.TownyArchitecturePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public class McmmoHook {
    private final TownyArchitecturePlugin plugin;
    private boolean mcmmoEnabled = false;

    public McmmoHook(TownyArchitecturePlugin plugin) {
        this.plugin = plugin;
        if (Bukkit.getPluginManager().isPluginEnabled("mcMMO")) {
            this.mcmmoEnabled = true;
            plugin.getLogger().info("Успешно подключен хук mcMMO 2.1+!");
        } else {
            plugin.getLogger().warning("mcMMO не обнаружен. Проверки уровней будут отключены.");
        }
    }

    public boolean isMcmmoEnabled() {
        return mcmmoEnabled;
    }

    /**
     * Строгая проверка: запрашивает ИСКЛЮЧИТЕЛЬНО указанный навык ('BUILDING' по умолчанию).
     * Новые/пустые аккаунты с уровнем 0 возвращают 0 и блокируются от строительства.
     */
    public int getPlayerBuildingSkillLevel(Player player) {
        if (player == null) {
            return 0;
        }
        if (!mcmmoEnabled) {
            return 100; // Режим без mcMMO
        }

        String skillName = plugin.getConfig().getString("settings.mcmmo-skill-name", "BUILDING");

        try {
            Class<?> expApiClass = Class.forName("com.gmail.nossr50.api.ExperienceAPI");
            Method getLevelMethod = expApiClass.getMethod("getLevel", Player.class, String.class);
            return (int) getLevelMethod.invoke(null, player, skillName);
        } catch (Exception e) {
            try {
                // Вторичная проверка с разным регистром (Building / BUILDING)
                Class<?> expApiClass = Class.forName("com.gmail.nossr50.api.ExperienceAPI");
                Method getLevelMethod = expApiClass.getMethod("getLevel", Player.class, String.class);
                try {
                    return (int) getLevelMethod.invoke(null, player, "BUILDING");
                } catch (Exception ignored) {
                    return (int) getLevelMethod.invoke(null, player, "Building");
                }
            } catch (Exception ignored) {
                // НЕ делать откат на getPowerLevel! Пустой аккаунт возвращает 0.
                return 0;
            }
        }
    }

    public boolean hasRequiredLevel(Player player, int requiredLevel) {
        if (!plugin.getConfig().getBoolean("settings.enforce-mcmmo-level", true)) {
            return true;
        }
        if (!mcmmoEnabled) {
            return true;
        }
        if (player == null) {
            return false;
        }
        if (player.isOp() || player.hasPermission("townyarchitecture.admin")) {
            return true;
        }
        return getPlayerBuildingSkillLevel(player) >= requiredLevel;
    }

    public void addBuildingXp(Player player, int xp) {
        if (!mcmmoEnabled || player == null || xp <= 0) return;
        String skillName = plugin.getConfig().getString("settings.mcmmo-skill-name", "BUILDING");
        try {
            Class<?> expApiClass = Class.forName("com.gmail.nossr50.api.ExperienceAPI");
            Method addXpMethod = expApiClass.getMethod("addRawXP", Player.class, String.class, int.class, String.class);
            addXpMethod.invoke(null, player, skillName, xp, "UNKNOWN");
        } catch (Exception e) {
            try {
                Class<?> expApiClass = Class.forName("com.gmail.nossr50.api.ExperienceAPI");
                Method addXpMethod = expApiClass.getMethod("addRawXP", Player.class, String.class, int.class, String.class);
                addXpMethod.invoke(null, player, "BUILDING", xp, "UNKNOWN");
            } catch (Exception ignored) {}
        }
    }
}
