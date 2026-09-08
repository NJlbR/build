package com.towny.architecture;

import com.towny.architecture.command.BuildCommand;
import com.towny.architecture.gui.BuildsMenuGui;
import com.towny.architecture.hook.McmmoHook;
import com.towny.architecture.hook.TownyHook;
import com.towny.architecture.manager.BuffManager;
import com.towny.architecture.manager.BuildingHudManager;
import com.towny.architecture.manager.BuildingManager;
import com.towny.architecture.manager.SiegeDamageManager;
import com.towny.architecture.model.ActiveBuilding;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class TownyArchitecturePlugin extends JavaPlugin implements Listener {
    private static TownyArchitecturePlugin instance;

    private TownyHook townyHook;
    private McmmoHook mcmmoHook;
    private BuildingManager buildingManager;
    private BuffManager buffManager;
    private BuildingHudManager hudManager;
    private SiegeDamageManager siegeDamageManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResourceIfNotExists("buildings.yml");

        // Хуки интеграций
        this.townyHook = new TownyHook(this);
        this.mcmmoHook = new McmmoHook(this);

        // Менеджеры
        this.buildingManager = new BuildingManager(this);
        File buildingsFile = new File(getDataFolder(), "buildings.yml");
        this.buildingManager.loadBlueprints(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(buildingsFile));
        this.buildingManager.initDataStorage();

        this.buffManager = new BuffManager(this);
        this.hudManager = new BuildingHudManager(this);
        this.siegeDamageManager = new SiegeDamageManager(this);

        // Регистрация событий
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new BuildsMenuGui(this), this);
        Bukkit.getPluginManager().registerEvents(siegeDamageManager, this);

        // Регистрация команд
        BuildCommand cmd = new BuildCommand(this);
        if (getCommand("builds") != null) {
            getCommand("builds").setExecutor(cmd);
            getCommand("builds").setTabCompleter(cmd);
        }

        // Таймер наложения баффов (каждые N секунд)
        int buffInterval = Math.max(1, getConfig().getInt("settings.buff-check-interval-seconds", 5)) * 20;
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            buffManager.updateAllBuffs();
        }, buffInterval, buffInterval);

        // Таймер обновления живого HUD (каждые 10 тиков = 0.5 с)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                hudManager.updatePlayerHud(p);
            }
        }, 20L, 10L);

        // Периодическая проверка целостности блоков (каждые 30 секунд = 600 тиков)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            buildingManager.performPeriodicIntegrityCheck();
        }, 600L, 600L);

        getLogger().info("==================================================");
        getLogger().info("TownyArchitecture v1.1.0 успешно активирован!");
        getLogger().info("Чанковая система, строгий mcMMO Building и баффы");
        getLogger().info("==================================================");
    }

    @Override
    public void onDisable() {
        if (hudManager != null) {
            hudManager.clearAll();
        }
        if (buildingManager != null) {
            buildingManager.saveData();
        }
        getLogger().info("TownyArchitecture успешно выгружен.");
    }

    private void saveResourceIfNotExists(String resourcePath) {
        File file = new File(getDataFolder(), resourcePath);
        if (!file.exists()) {
            saveResource(resourcePath, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ActiveBuilding building = buildingManager.getBuildingAt(event.getBlock().getLocation());
        if (building != null) {
            boolean allowed = buildingManager.handleBlockPlaced(event.getPlayer(), event.getBlock().getLocation(), event.getBlock().getType());
            if (!allowed) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        ActiveBuilding building = buildingManager.getBuildingAt(event.getBlock().getLocation());
        if (building != null) {
            boolean allowed = buildingManager.handleBlockBroken(event.getPlayer(), event.getBlock().getLocation(), event.getBlock().getType());
            if (!allowed) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().trim().toLowerCase();
        // Перехват синтаксиса Towny: /plot set build
        if (msg.equals("/plot set build") || msg.startsWith("/plot set build ")) {
            event.setCancelled(true);
            buildingManager.markPlotAsBuild(event.getPlayer(), event.getPlayer().getLocation().getChunk());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (hudManager != null) {
            hudManager.removeBossBar(event.getPlayer());
        }
    }

    public static TownyArchitecturePlugin getInstance() { return instance; }
    public TownyHook getTownyHook() { return townyHook; }
    public McmmoHook getMcmmoHook() { return mcmmoHook; }
    public BuildingManager getBuildingManager() { return buildingManager; }
    public BuffManager getBuffManager() { return buffManager; }
    public BuildingHudManager getHudManager() { return hudManager; }
    public SiegeDamageManager getSiegeDamageManager() { return siegeDamageManager; }
}
