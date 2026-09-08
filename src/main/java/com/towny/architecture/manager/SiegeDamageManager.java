package com.towny.architecture.manager;

import com.towny.architecture.TownyArchitecturePlugin;
import com.towny.architecture.model.ActiveBuilding;
import com.towny.architecture.model.BuildingBlueprint;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Map;

public class SiegeDamageManager implements Listener {
    private final TownyArchitecturePlugin plugin;

    public SiegeDamageManager(TownyArchitecturePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        for (Block b : event.blockList()) {
            plugin.getBuildingManager().handleBlockBroken(null, b.getLocation(), b.getType());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        for (Block b : event.blockList()) {
            plugin.getBuildingManager().handleBlockBroken(null, b.getLocation(), b.getType());
        }
    }

    public static String getRussianMaterialName(Material mat) {
        if (mat == null) return "Неизвестный блок";
        switch (mat.name()) {
            case "STONE_BRICKS": return "Каменные кирпичи";
            case "WHITE_CONCRETE": return "Белый бетон";
            case "RED_WOOL": return "Красная шерсть";
            case "GLASS": return "Стекло";
            case "COBBLESTONE": return "Булыжник";
            case "OAK_PLANKS": return "Дубовые доски";
            case "IRON_BLOCK": return "Железный блок";
            case "BOOKSHELF": return "Книжные полки";
            case "OBSIDIAN": return "Обсидиан";
            case "DARK_OAK_PLANKS": return "Доски из темного дуба";
            case "LAPIS_BLOCK": return "Лазуритовый блок";
            case "LADDER": return "Лестницы";
            case "LANTERN": return "Фонари";
            case "SANDSTONE": return "Песчаник";
            case "GOLD_BLOCK": return "Золотой блок";
            case "BEACON": return "Маяк";
            case "NETHERITE_INGOT": return "Незеритовый слиток";
            case "BRICKS": return "Кирпичи";
            case "STONE": return "Камень";
            case "DIRT": return "Земля";
            case "DIAMOND_BLOCK": return "Алмазный блок";
            case "EMERALD_BLOCK": return "Изумрудный блок";
            default: return mat.name().replace('_', ' ').toLowerCase();
        }
    }

    /**
     * Информирует игроков о том, что ремонт осуществляется установкой недостающих блоков на чанках постройки.
     */
    public void showRepairInstructions(Player player, ActiveBuilding building) {
        BuildingBlueprint bp = plugin.getBuildingManager().getBlueprint(building.getBlueprintId());
        String bpName = bp != null ? bp.getNameRu() : building.getBlueprintId();

        if (building.getHealthPercent() >= 100.0 && building.isFullyBuilt()) {
            player.sendMessage(ChatColor.GREEN + "[TownyArchitecture] Здание «" + bpName + "» в идеальном состоянии (Прочность: 100%).");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "═════════ Восстановление здания «" + bpName + "» ═════════");
        player.sendMessage(ChatColor.RED + "✖ Мгновенный ремонт командами отключен!");
        player.sendMessage(ChatColor.YELLOW + "Для восстановления прочности установите недостающие блоки на чанках постройки:");

        boolean anyMissing = false;
        for (Map.Entry<Material, Integer> entry : building.getRequiredMaterials().entrySet()) {
            Material mat = entry.getKey();
            int needed = entry.getValue();
            int placed = building.getPlacedCount(mat);
            if (placed < needed) {
                anyMissing = true;
                player.sendMessage(ChatColor.GRAY + " • " + ChatColor.WHITE + getRussianMaterialName(mat) + ": " +
                        ChatColor.RED + "нужно еще " + (needed - placed) + " шт. " +
                        ChatColor.DARK_GRAY + "(" + placed + "/" + needed + ")");
            }
        }

        if (!anyMissing) {
            player.sendMessage(ChatColor.GREEN + "✔ Все блоки на месте. Выполняется автоматическая синхронизация.");
        }

        double threshold = plugin.getConfig().getDouble("settings.buff-suspension-health-percent", 70.0);
        player.sendMessage(ChatColor.GOLD + "Текущая прочность: " + 
                (building.getHealthPercent() >= threshold ? ChatColor.GREEN : ChatColor.RED) + 
                String.format("%.1f", building.getHealthPercent()) + "% " +
                ChatColor.GRAY + "(баффы активны при прочности >= " + String.format("%.0f", threshold) + "%)");
        player.sendMessage(ChatColor.GOLD + "══════════════════════════════════════════════════");
    }
}
