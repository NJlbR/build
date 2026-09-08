package com.towny.architecture.manager;

import com.towny.architecture.TownyArchitecturePlugin;
import com.towny.architecture.model.ActiveBuilding;
import com.towny.architecture.model.BuildingBlueprint;
import com.towny.architecture.model.ChunkCoord;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;

public class BuffManager {
    private final TownyArchitecturePlugin plugin;

    public BuffManager(TownyArchitecturePlugin plugin) {
        this.plugin = plugin;
    }

    public void updateAllBuffs() {
        double suspensionThreshold = plugin.getConfig().getDouble("settings.buff-suspension-health-percent", 70.0);

        for (ActiveBuilding bld : plugin.getBuildingManager().getActiveBuildings().values()) {
            if (!bld.isConstructed()) continue;
            if (bld.getHealthPercent() < suspensionThreshold) continue;

            BuildingBlueprint bp = plugin.getBuildingManager().getBlueprint(bld.getBlueprintId());
            if (bp == null) continue;

            Map<String, Object> buffs = bp.getBuffs();
            if (buffs == null || buffs.isEmpty()) continue;

            int chunkWidth = bp.getChunkWidth();
            int chunkLength = bp.getChunkLength();

            for (Player player : Bukkit.getOnlinePlayers()) {
                // ПРАВИЛО 1: СТРОГАЯ ПРОВЕРКА ГРАЖДАНСТВА — ВСЕ БАФФЫ ТОЛЬКО ДЛЯ ЖИТЕЛЕЙ ЭТОГО ГОРОДА!
                String pTown = plugin.getTownyHook().getPlayerTownName(player);
                if (pTown == null || !pTown.equalsIgnoreCase(bld.getTownName())) {
                    continue; // Чужие игроки и бродяги НИКОГДА не получают городские баффы
                }

                // ПРАВИЛО 2: ЗОНА ДЕЙСТВИЯ НА ОСНОВЕ ПЛОЩАДИ ЗДАНИЯ В ЧАНКАХ
                // - 1x1: активны в радиусе 5 чанков от постройки
                // - 2x1 / 1x2: активны на всей территории города Towny
                // - 2x2 (Чудеса Света): активны везде на всей карте (во всех мирах)
                boolean eligible = false;
                Location pLoc = player.getLocation();

                if (chunkWidth == 1 && chunkLength == 1 && !bp.isWorldWonder()) {
                    // 1x1 чанк: баффы действуют только в радиусе вокруг здания
                    int radiusChunks = plugin.getConfig().getInt("settings.buff-1x1-chunk-radius", 5);
                    if (pLoc.getWorld() != null && pLoc.getWorld().getName().equalsIgnoreCase(bld.getWorldName())) {
                        int pChunkX = pLoc.getBlockX() >> 4;
                        int pChunkZ = pLoc.getBlockZ() >> 4;
                        for (ChunkCoord bChunk : bld.getAssignedChunks()) {
                            if (Math.abs(pChunkX - bChunk.getX()) <= radiusChunks && Math.abs(pChunkZ - bChunk.getZ()) <= radiusChunks) {
                                eligible = true;
                                break;
                            }
                        }
                    }
                } else if (!bp.isWorldWonder() && ((chunkWidth == 1 && chunkLength == 2) || (chunkWidth == 2 && chunkLength == 1))) {
                    // 2x1 или 1x2 чанка: баффы действуют на всей территории города!
                    eligible = plugin.getTownyHook().isLocationInTown(pLoc, bld.getTownName());
                } else if (bp.isWorldWonder() || (chunkWidth >= 2 && chunkLength >= 2)) {
                    // 2x2 чанка (Чудеса Света): баффы действуют везде на всей карте во всех мирах!
                    eligible = true;
                } else {
                    // По умолчанию: вся территория города
                    eligible = plugin.getTownyHook().isLocationInTown(pLoc, bld.getTownName());
                }

                if (eligible) {
                    applyEffectsToPlayer(player, buffs);
                }
            }
        }
    }

    private int getIntValue(Object obj, int def) {
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        if (obj instanceof String) {
            try {
                return Integer.parseInt((String) obj);
            } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private void applyEffectsToPlayer(Player player, Map<String, Object> buffs) {
        int durationTicks = 140; // 7 секунд (обновляется каждые 5 секунд)

        if (buffs.containsKey("health-boost-hearts")) {
            int hearts = getIntValue(buffs.get("health-boost-hearts"), 1);
            int amplifier = Math.max(0, hearts - 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, durationTicks, amplifier, true, false, true));
        }

        if (buffs.containsKey("regeneration-level")) {
            int level = getIntValue(buffs.get("regeneration-level"), 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, durationTicks, Math.max(0, level - 1), true, false, true));
        }

        if (buffs.containsKey("strength-level")) {
            int level = getIntValue(buffs.get("strength-level"), 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, durationTicks, Math.max(0, level - 1), true, false, true));
        }

        if (buffs.containsKey("speed-level")) {
            int level = getIntValue(buffs.get("speed-level"), 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, Math.max(0, level - 1), true, false, true));
        }

        if (buffs.containsKey("haste-level")) {
            int level = getIntValue(buffs.get("haste-level"), 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, durationTicks, Math.max(0, level - 1), true, false, true));
        }

        if (buffs.containsKey("resistance-level")) {
            int level = getIntValue(buffs.get("resistance-level"), 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, durationTicks, Math.max(0, level - 1), true, false, true));
        }

        if (buffs.containsKey("night-vision")) {
            Object val = buffs.get("night-vision");
            boolean enabled = (val instanceof Boolean && (Boolean) val) || "true".equalsIgnoreCase(String.valueOf(val));
            if (enabled) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, durationTicks + 60, 0, true, false, false));
            }
        }
    }
}
