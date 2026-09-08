package com.towny.architecture.manager;

import com.towny.architecture.TownyArchitecturePlugin;
import com.towny.architecture.model.ActiveBuilding;
import com.towny.architecture.model.BuildingBlueprint;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BuildingHudManager {
    private final TownyArchitecturePlugin plugin;
    private final Map<Player, BossBar> playerBossBars = new ConcurrentHashMap<>();

    public BuildingHudManager(TownyArchitecturePlugin plugin) {
        this.plugin = plugin;
    }

    public void updatePlayerHud(Player player) {
        if (player == null || !player.isOnline()) return;

        ActiveBuilding building = plugin.getBuildingManager().getBuildingAt(player.getLocation());

        if (building == null) {
            removeBossBar(player);
            return;
        }

        BuildingBlueprint bp = plugin.getBuildingManager().getBlueprint(building.getBlueprintId());
        String bpName = bp != null ? bp.getNameRu() : building.getBlueprintId();

        BossBar bar = playerBossBars.computeIfAbsent(player, p -> {
            BossBar newBar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SEGMENTED_10);
            newBar.addPlayer(p);
            return newBar;
        });

        double health = building.getHealthPercent();
        double progress = Math.min(1.0, Math.max(0.0, health / 100.0));
        bar.setProgress(progress);

        double threshold = plugin.getConfig().getDouble("settings.buff-suspension-health-percent", 70.0);

        if (building.getStatus() == ActiveBuilding.Status.COMPLETED) {
            bar.setColor(BarColor.GREEN);
            bar.setTitle("§a✔ §l" + bpName + " §a(Готово 100%) §8| §eБаффы активны");
        } else if (building.getStatus() == ActiveBuilding.Status.DAMAGED) {
            if (health >= threshold) {
                bar.setColor(BarColor.YELLOW);
                bar.setTitle("§e⚠ §l" + bpName + " §e(Прочность: " + String.format("%.1f", health) + "%) §8| §aБаффы активны");
            } else {
                bar.setColor(BarColor.RED);
                bar.setTitle("§c✖ §l" + bpName + " §c(Повреждено: " + String.format("%.1f", health) + "%) §8| §4Баффы сняты!");
            }
        } else {
            bar.setColor(BarColor.BLUE);
            bar.setTitle("§b🏗 §l" + bpName + " §8| §fПрогресс: §e" + String.format("%.1f", health) + "%");
        }

        // Action Bar для зданий в процессе возведения или ремонта
        if (building.getStatus() == ActiveBuilding.Status.UNDER_CONSTRUCTION || building.getStatus() == ActiveBuilding.Status.DAMAGED) {
            StringBuilder ab = new StringBuilder("§eТребуется: ");
            int shown = 0;
            for (Map.Entry<Material, Integer> entry : building.getRequiredMaterials().entrySet()) {
                int needed = entry.getValue();
                int placed = building.getPlacedCount(entry.getKey());
                if (placed < needed) {
                    if (shown > 0) ab.append(" §8| ");
                    String matName = SiegeDamageManager.getRussianMaterialName(entry.getKey());
                    ab.append("§f").append(matName).append(": §a").append(placed).append("§7/§e").append(needed);
                    shown++;
                    if (shown >= 2) break;
                }
            }
            if (shown > 0) {
                try {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ab.toString()));
                } catch (Exception ignored) {}
            }
        }
    }

    public void removeBossBar(Player player) {
        if (player == null) return;
        BossBar bar = playerBossBars.remove(player);
        if (bar != null) {
            bar.removeAll();
        }
    }

    public void clearAll() {
        for (BossBar bar : playerBossBars.values()) {
            bar.removeAll();
        }
        playerBossBars.clear();
    }
}
