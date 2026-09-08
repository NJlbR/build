package com.towny.architecture.gui;

import com.towny.architecture.TownyArchitecturePlugin;
import com.towny.architecture.manager.SiegeDamageManager;
import com.towny.architecture.model.ActiveBuilding;
import com.towny.architecture.model.BuildingBlueprint;
import com.towny.architecture.model.ChunkCoord;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class BuildsMenuGui implements Listener {
    private final TownyArchitecturePlugin plugin;
    public static final String GUI_TITLE = "§8✦ §6§lГОРОДСКИЕ ЧЕРТЕЖИ §8✦";

    public BuildsMenuGui(TownyArchitecturePlugin plugin) {
        this.plugin = plugin;
    }

    public void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE);

        // 1. Декоративный фон
        fillBackground(inv);

        // 2. Верхняя центральная плашка (Бюро)
        ItemStack bureau = createItem(Material.BEACON, "§6§lАРХИТЕКТУРНОЕ БЮРО ГОРОДА", Arrays.asList(
                "§8§m────────────────────────────────",
                "§7Возводите монументальные здания и",
                "§7Чудеса Света для усиления жителей города.",
                "§7Каждая постройка дарует уникальные",
                "§7пассивные эффекты и характеристики.",
                "§8§m────────────────────────────────"
        ));
        inv.setItem(4, bureau);

        // 3. Сетка чертежей (Слоты 19, 21, 23, 25, 31 и т.д.)
        int[] blueprintSlots = {19, 21, 23, 25, 31, 29, 33, 20, 22, 24};
        int index = 0;

        for (BuildingBlueprint bp : plugin.getBuildingManager().getBlueprints().values()) {
            if (index >= blueprintSlots.length) break;
            int slot = blueprintSlots[index];
            ItemStack item = createBlueprintItem(player, bp);
            inv.setItem(slot, item);
            index++;
        }

        // 4. Нижняя информационная панель
        // Слот 47: Профиль строителя
        ItemStack builderProfile = createBuilderProfileItem(player);
        inv.setItem(47, builderProfile);

        // Слот 49: Статус построек города
        ItemStack townStatus = createTownStatusItem(player);
        inv.setItem(49, townStatus);

        // Слот 51: Руководство по строительству
        ItemStack guide = createGuideItem();
        inv.setItem(51, guide);

        // Слот 53: Кнопка закрытия
        ItemStack closeBtn = createItem(Material.BARRIER, "§c§lЗакрыть меню", Collections.singletonList("§7Нажмите, чтобы закрыть окно чертежей."));
        inv.setItem(53, closeBtn);

        player.openInventory(inv);
        if (plugin.getConfig().getBoolean("settings.sound-effects-enabled", true)) {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.1f);
        }
    }

    private void fillBackground(Inventory inv) {
        ItemStack blueGlass = createGlass(Material.BLUE_STAINED_GLASS_PANE, "§8✦");
        ItemStack cyanGlass = createGlass(Material.CYAN_STAINED_GLASS_PANE, "§8✦");
        ItemStack grayGlass = createGlass(Material.GRAY_STAINED_GLASS_PANE, " ");
        ItemStack blackGlass = createGlass(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack orangeGlass = createGlass(Material.ORANGE_STAINED_GLASS_PANE, "§6✦");

        // Верхняя и нижняя рамка
        int[] blueSlots = {0, 1, 7, 8, 45, 46, 52};
        for (int s : blueSlots) inv.setItem(s, blueGlass);

        int[] cyanSlots = {2, 3, 5, 6, 48, 50};
        for (int s : cyanSlots) inv.setItem(s, cyanGlass);

        int[] orangeSlots = {9, 17, 36, 44};
        for (int s : orangeSlots) inv.setItem(s, orangeGlass);

        // Разделительная линия над нижней панелью
        for (int i = 37; i <= 43; i++) {
            inv.setItem(i, grayGlass);
        }

        // Внутренние пустые слоты
        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, blackGlass);
            }
        }
    }

    private ItemStack createGlass(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBlueprintItem(Player player, BuildingBlueprint bp) {
        ItemStack item = new ItemStack(bp.getIcon());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        int playerLevel = plugin.getMcmmoHook().getPlayerBuildingSkillLevel(player);
        boolean hasLevel = plugin.getMcmmoHook().hasRequiredLevel(player, bp.getRequiredMcmmoLevel());
        boolean isCompletedWonder = bp.isWorldWonder() && plugin.getBuildingManager().isWonderCompleted(bp.getId());
        boolean isMayorOrAssist = plugin.getTownyHook().isMayorOrAssistant(player);

        String town = plugin.getTownyHook().getPlayerTownName(player);
        boolean isAlreadyBuiltInTown = false;
        if (town != null) {
            for (ActiveBuilding ab : plugin.getBuildingManager().getActiveBuildings().values()) {
                if (ab.getTownName().equalsIgnoreCase(town) && ab.getBlueprintId().equalsIgnoreCase(bp.getId())) {
                    isAlreadyBuiltInTown = true;
                    break;
                }
            }
        }

        // Заголовок
        if (bp.isWorldWonder()) {
            meta.setDisplayName("§6§l★ [ЧУДО СВЕТА] §e§l" + bp.getNameRu() + " §6§l★");
        } else {
            meta.setDisplayName("§e§l🏛 " + bp.getNameRu() + " §7(Чертеж)");
        }

        List<String> lore = new ArrayList<>();
        lore.add("§8§m────────────────────────────────");
        lore.add("§7" + bp.getDescription());
        lore.add("");
        lore.add("§6⚙ Характеристики проекта:");
        lore.add(" §8▪ §7Размер на карте: §b" + bp.getChunkWidth() + "x" + bp.getChunkLength() + " чанк(а)");
        lore.add(" §8▪ §7Высота конструкции: §b" + bp.getHeightY() + " блоков");
        lore.add(" §8▪ §7Требуемый mcMMO (Building): " + (hasLevel ? "§a" : "§c") + "Ур. " + bp.getRequiredMcmmoLevel() +
                " §8(Ваш: " + (hasLevel ? "§a" : "§c") + playerLevel + "§8)");

        lore.add("");
        lore.add("§6📦 Необходимые строительные материалы:");
        for (Map.Entry<Material, Integer> res : bp.getRequiredResources().entrySet()) {
            String matRu = SiegeDamageManager.getRussianMaterialName(res.getKey());
            lore.add(" §8▪ §f" + matRu + ": §e" + res.getValue() + " шт.");
        }

        lore.add("");
        lore.add("§6✨ Пассивные эффекты здания:");
        for (Map.Entry<String, Object> buff : bp.getBuffs().entrySet()) {
            lore.add(formatBuffDescription(buff.getKey(), buff.getValue()));
        }

        lore.add("§8§m────────────────────────────────");

        // Статусный футер
        if (isAlreadyBuiltInTown) {
            lore.add("§a✔ Этот проект уже возводится/построен в вашем городе!");
        } else if (isCompletedWonder) {
            lore.add("§4✖ Чудо Света уже воздвигнуто на этом сервере!");
            lore.add("§8(Может существовать только в одном экземпляре)");
        } else if (!isMayorOrAssist) {
            lore.add("§c✖ Утверждать проекты может только мэр или помощник!");
        } else if (!hasLevel) {
            lore.add("§c✖ Недостаточный уровень навыка mcMMO Building!");
        } else {
            lore.add("§a► Нажмите ЛКМ, чтобы утвердить проект на чанках!");
            // Добавим свечение доступным чертежам
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_POTION_EFFECTS);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String formatBuffDescription(String key, Object value) {
        switch (key) {
            case "health-boost-hearts":
                return " §8▪ §dДополнительное здоровье: §f+" + value + " Сердце (" + ((int) value * 2) + " HP)";
            case "regeneration-level":
                return " §8▪ §dРегенерация здоровья: §fУровень " + value;
            case "strength-level":
                return " §8▪ §cУвеличение силы: §fУровень " + value;
            case "speed-level":
                return " §8▪ §bСкорость передвижения: §fУровень " + value;
            case "haste-level":
                return " §8▪ §eСпешка (Быстрое копание): §fУровень " + value;
            case "resistance-level":
                return " §8▪ §9Сопротивление урону: §fУровень " + value;
            case "night-vision":
                return " §8▪ §9Ночное зрение: §fПостоянный эффект";
            default:
                return " §8▪ §a" + key + ": §f" + value;
        }
    }

    private ItemStack createBuilderProfileItem(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName("§e§lПрофиль Строителя");

            String town = plugin.getTownyHook().getPlayerTownName(player);
            int mcmmoLevel = plugin.getMcmmoHook().getPlayerBuildingSkillLevel(player);

            String role = "§7Житель";
            if (player.isOp()) role = "§cАдминистратор";
            else if (plugin.getTownyHook().isMayorOrAssistant(player)) role = "§6Мэр / Помощник";
            else if (plugin.getTownyHook().hasBuilderPermission(player, town)) role = "§aСтроитель города";

            int plotsCount = town != null ? plugin.getBuildingManager().getTownBuildPlots(town).size() : 0;

            List<String> lore = Arrays.asList(
                    "§8§m────────────────────────────────",
                    "§7Игрок: §f" + player.getName(),
                    "§7Город: " + (town != null ? "§a" + town : "§cБез города"),
                    "§7Статус в городе: " + role,
                    "§7Навык mcMMO Building: §b" + mcmmoLevel + " ур.",
                    "§7Строительных участков: §e" + plotsCount + " чанк(ов)",
                    "§8§m────────────────────────────────"
            );
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createTownStatusItem(Player player) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lСтатус Построек Города");

            String town = plugin.getTownyHook().getPlayerTownName(player);
            List<String> lore = new ArrayList<>();
            lore.add("§8§m────────────────────────────────");

            if (town == null) {
                lore.add("§cВы не состоите в городе.");
            } else {
                lore.add("§7Город: §a" + town);
                lore.add("");
                lore.add("§6Текущие проекты города:");

                boolean hasProjects = false;
                for (ActiveBuilding ab : plugin.getBuildingManager().getActiveBuildings().values()) {
                    if (ab.getTownName().equalsIgnoreCase(town)) {
                        hasProjects = true;
                        BuildingBlueprint bp = plugin.getBuildingManager().getBlueprint(ab.getBlueprintId());
                        String name = bp != null ? bp.getNameRu() : ab.getBlueprintId();

                        String statusText;
                        if (ab.getStatus() == ActiveBuilding.Status.COMPLETED) {
                            statusText = "§a✔ Готово (100%)";
                        } else if (ab.getStatus() == ActiveBuilding.Status.DAMAGED) {
                            statusText = "§e⚠ Повреждено (" + String.format("%.1f", ab.getHealthPercent()) + "%)";
                        } else {
                            statusText = "§b🏗 В процессе (" + String.format("%.1f", ab.getHealthPercent()) + "%)";
                        }
                        lore.add(" §8▪ §f" + name + " §8- " + statusText);
                    }
                }

                if (!hasProjects) {
                    lore.add(" §8▪ §7Нет активных проектов.");
                }
            }

            lore.add("§8§m────────────────────────────────");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGuideItem() {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a§lРуководство Архитектора");
            List<String> lore = Arrays.asList(
                    "§8§m────────────────────────────────",
                    "§eКак начать строительство:",
                    " §61. §7Мэр выделяет чанки города: §f/plot set build",
                    " §62. §7Мэр выбирает проект в этом меню",
                    " §63. §7Строители возводят блоки чертежа",
                    " §64. §7Город получает вечные баффы!",
                    "",
                    "§eКоманды плагина:",
                    " §8▪ §f/builds §8- §7Открыть это меню",
                    " §8▪ §f/builds info §8- §7Прогресс текущего здания",
                    " §8▪ §f/builds repair §8- §7Диагностика ремонта",
                    " §8▪ §f/builds plot list §8- §7Список участков",
                    "§8§m────────────────────────────────"
            );
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;

        Material itemType = item.getType();

        // Кнопка закрытия
        if (itemType == Material.BARRIER) {
            player.closeInventory();
            if (plugin.getConfig().getBoolean("settings.sound-effects-enabled", true)) {
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 1.0f);
            }
            return;
        }

        // Информационные кнопки (не требуют действий)
        if (itemType == Material.BEACON || itemType == Material.PLAYER_HEAD ||
            itemType == Material.WRITABLE_BOOK || itemType == Material.KNOWLEDGE_BOOK ||
            itemType.name().endsWith("GLASS_PANE")) {
            return;
        }

        String rawName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        String cleanName = rawName.replace("★ [ЧУДО СВЕТА] ", "").replace(" ★", "")
                .replace("🏛 ", "").replace(" (Чертеж)", "").trim();

        BuildingBlueprint selectedBp = null;
        for (BuildingBlueprint bp : plugin.getBuildingManager().getBlueprints().values()) {
            if (bp.getNameRu().equalsIgnoreCase(cleanName) || bp.getName().equalsIgnoreCase(cleanName) ||
                rawName.contains(bp.getNameRu()) || rawName.contains(bp.getName())) {
                selectedBp = bp;
                break;
            }
        }

        if (selectedBp != null) {
            player.closeInventory();
            ActiveBuilding ab = plugin.getBuildingManager().startBuilding(player, selectedBp);
            if (plugin.getConfig().getBoolean("settings.sound-effects-enabled", true)) {
                if (ab != null) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            }
        }
    }
}
