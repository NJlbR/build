package com.towny.architecture.command;

import com.towny.architecture.TownyArchitecturePlugin;
import com.towny.architecture.gui.BuildsMenuGui;
import com.towny.architecture.manager.SiegeDamageManager;
import com.towny.architecture.model.ActiveBuilding;
import com.towny.architecture.model.BuildingBlueprint;
import com.towny.architecture.model.ChunkCoord;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class BuildCommand implements CommandExecutor, TabCompleter {
    private final TownyArchitecturePlugin plugin;
    private final BuildsMenuGui gui;

    public BuildCommand(TownyArchitecturePlugin plugin) {
        this.plugin = plugin;
        this.gui = new BuildsMenuGui(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Команда доступна только игрокам на сервере.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            gui.openMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "help":
                sendHelp(player);
                return true;

            case "plot":
                if (args.length >= 3 && args[1].equalsIgnoreCase("set") && args[2].equalsIgnoreCase("build")) {
                    plugin.getBuildingManager().markPlotAsBuild(player, player.getLocation().getChunk());
                    return true;
                } else if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
                    showPlotsList(player);
                    return true;
                }
                player.sendMessage(ChatColor.YELLOW + "Использование: /builds plot set build | /builds plot list");
                return true;

            case "info":
            case "status":
                showBuildingStatus(player);
                return true;

            case "repair":
                ActiveBuilding building = plugin.getBuildingManager().getBuildingAt(player.getLocation());
                if (building == null) {
                    player.sendMessage(ChatColor.RED + "На этом чанке нет зарегистрированного проекта здания!");
                    return true;
                }
                plugin.getSiegeDamageManager().showRepairInstructions(player, building);
                return true;

            case "wonder":
                if (args.length >= 3 && args[1].equalsIgnoreCase("reset")) {
                    if (!player.hasPermission("townyarchitecture.admin") && !player.isOp()) {
                        player.sendMessage(ChatColor.RED + "У вас нет прав администратора на эту команду!");
                        return true;
                    }
                    String wonderId = args[2].toLowerCase();
                    plugin.getBuildingManager().resetWonder(wonderId);
                    player.sendMessage(ChatColor.GREEN + "Статус Чуда Света «" + wonderId + "» успешно сброшен!");
                    return true;
                }
                player.sendMessage(ChatColor.RED + "Использование: /builds wonder reset <id>");
                return true;

            default:
                gui.openMenu(player);
                return true;
        }
    }

    private void sendHelp(Player p) {
        p.sendMessage(ChatColor.GOLD + "═════════════ Команды TownyArchitecture ═════════════");
        p.sendMessage(ChatColor.YELLOW + "/builds " + ChatColor.GRAY + "— Открыть графическое меню городских чертежей");
        p.sendMessage(ChatColor.YELLOW + "/plot set build " + ChatColor.GRAY + "— Назначить/снять строительный чанк города");
        p.sendMessage(ChatColor.YELLOW + "/builds info " + ChatColor.GRAY + "— Прогресс и статус здания на текущем чанке");
        p.sendMessage(ChatColor.YELLOW + "/builds repair " + ChatColor.GRAY + "— Диагностика повреждений и руководство ремонта");
        p.sendMessage(ChatColor.YELLOW + "/builds plot list " + ChatColor.GRAY + "— Список всех строительных чанков вашего города");
        if (p.hasPermission("townyarchitecture.admin") || p.isOp()) {
            p.sendMessage(ChatColor.YELLOW + "/builds wonder reset <id> " + ChatColor.GRAY + "— Сбросить статус Чуда Света");
        }
        p.sendMessage(ChatColor.GOLD + "═════════════════════════════════════════════════════");
    }

    private void showBuildingStatus(Player player) {
        ActiveBuilding building = plugin.getBuildingManager().getBuildingAt(player.getLocation());
        if (building == null) {
            player.sendMessage(ChatColor.RED + "Вы не находитесь на строительном участке здания!");
            return;
        }

        BuildingBlueprint bp = plugin.getBuildingManager().getBlueprint(building.getBlueprintId());
        String bpName = bp != null ? bp.getNameRu() : building.getBlueprintId();

        player.sendMessage(ChatColor.GOLD + "═════════ Прогресс здания «" + bpName + "» ═════════");
        player.sendMessage(ChatColor.YELLOW + "Город-владелец: " + ChatColor.WHITE + building.getTownName());
        
        String statusStr;
        if (building.getStatus() == ActiveBuilding.Status.COMPLETED) {
            statusStr = ChatColor.GREEN + "✔ Построено (Баффы активны)";
        } else if (building.getStatus() == ActiveBuilding.Status.DAMAGED) {
            statusStr = ChatColor.RED + "✖ Повреждено (Требуется восстановление блоков)";
        } else {
            statusStr = ChatColor.AQUA + "🏗 В процессе возведения";
        }
        player.sendMessage(ChatColor.YELLOW + "Состояние: " + statusStr);

        double health = building.getHealthPercent();
        ChatColor healthColor = health >= 99.9 ? ChatColor.GREEN : (health >= 70.0 ? ChatColor.YELLOW : ChatColor.RED);
        player.sendMessage(ChatColor.YELLOW + "Прогресс / Прочность: " + healthColor + String.format("%.1f", health) + "%");
        
        player.sendMessage(ChatColor.YELLOW + "Строительные материалы:");
        for (Map.Entry<org.bukkit.Material, Integer> entry : building.getRequiredMaterials().entrySet()) {
            int needed = entry.getValue();
            int placed = building.getPlacedCount(entry.getKey());
            String matRu = SiegeDamageManager.getRussianMaterialName(entry.getKey());
            ChatColor color = placed >= needed ? ChatColor.GREEN : ChatColor.RED;
            player.sendMessage(ChatColor.GRAY + " • " + matRu + ": " + color + placed + "/" + needed + " шт.");
        }
        player.sendMessage(ChatColor.GOLD + "═════════════════════════════════════════════════════");
    }

    private void showPlotsList(Player player) {
        String town = plugin.getTownyHook().getPlayerTownName(player);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в городе!");
            return;
        }
        Set<ChunkCoord> plots = plugin.getBuildingManager().getTownBuildPlots(town);
        player.sendMessage(ChatColor.GOLD + "══════ Строительные участки города " + town + " ══════");
        if (plots.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "Нет выделенных участков. Мэр может назначить их командой /plot set build.");
        } else {
            for (ChunkCoord c : plots) {
                ActiveBuilding bld = plugin.getBuildingManager().getBuildingAt(
                        new org.bukkit.Location(org.bukkit.Bukkit.getWorld(c.getWorldName()), (c.getX() << 4) + 8, 64, (c.getZ() << 4) + 8));
                String status = bld != null ? "§e(Занят: " + bld.getBlueprintId() + ")" : "§a(Свободен под проект)";
                player.sendMessage(ChatColor.GRAY + " • Чанк [" + c.getX() + ", " + c.getZ() + "] " + status);
            }
        }
        player.sendMessage(ChatColor.GOLD + "════════════════════════════════════════════════════");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>(Arrays.asList("help", "plot", "info", "status", "repair"));
            if (sender.hasPermission("townyarchitecture.admin") || sender.isOp()) {
                subcommands.add("wonder");
            }
            for (String sub : subcommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("plot")) {
            for (String sub : Arrays.asList("set", "list")) {
                if (sub.toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(sub);
                }
            }
            return completions;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("plot") && args[1].equalsIgnoreCase("set")) {
            if ("build".startsWith(args[2].toLowerCase())) {
                completions.add("build");
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("wonder")) {
            if ("reset".startsWith(args[1].toLowerCase())) {
                completions.add("reset");
            }
            return completions;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("wonder") && args[1].equalsIgnoreCase("reset")) {
            for (String bpId : plugin.getBuildingManager().getBlueprints().keySet()) {
                if (bpId.startsWith(args[2].toLowerCase())) {
                    completions.add(bpId);
                }
            }
            return completions;
        }

        return Collections.emptyList();
    }
}
