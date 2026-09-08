package com.towny.architecture.model;

import org.bukkit.Material;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class BuildingBlueprint {
    private final String id;
    private final String name;
    private final String nameRu;
    private final String description;
    private final Material icon;
    private final int requiredMcmmoLevel;
    private final boolean worldWonder;
    private final int chunkWidth;
    private final int chunkLength;
    private final int heightY;
    private final Map<Material, Integer> requiredResources;
    private final Map<String, Object> buffs;

    public BuildingBlueprint(String id, String name, String nameRu, String description,
                             Material icon, int requiredMcmmoLevel, boolean worldWonder,
                             int chunkWidth, int chunkLength, int heightY,
                             Map<Material, Integer> requiredResources,
                             Map<String, Object> buffs) {
        this.id = id != null ? id.toLowerCase() : "";
        this.name = name != null ? name : id;
        this.nameRu = nameRu != null ? nameRu : this.name;
        this.description = description != null ? description : "";
        this.icon = icon != null ? icon : Material.STONE_BRICKS;
        this.requiredMcmmoLevel = requiredMcmmoLevel;
        this.worldWonder = worldWonder;
        this.chunkWidth = Math.max(1, chunkWidth);
        this.chunkLength = Math.max(1, chunkLength);
        this.heightY = Math.max(1, heightY);
        this.requiredResources = Collections.unmodifiableMap(new LinkedHashMap<>(requiredResources != null ? requiredResources : Collections.emptyMap()));
        this.buffs = Collections.unmodifiableMap(new LinkedHashMap<>(buffs != null ? buffs : Collections.emptyMap()));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNameRu() {
        return nameRu;
    }

    public String getDescription() {
        return description;
    }

    public Material getIcon() {
        return icon;
    }

    public int getRequiredMcmmoLevel() {
        return requiredMcmmoLevel;
    }

    public boolean isWorldWonder() {
        return worldWonder;
    }

    public int getChunkWidth() {
        return chunkWidth;
    }

    public int getChunkLength() {
        return chunkLength;
    }

    public int getHeightY() {
        return heightY;
    }

    public Map<Material, Integer> getRequiredResources() {
        return requiredResources;
    }

    public Map<String, Object> getBuffs() {
        return buffs;
    }

    public int getTotalRequiredBlocks() {
        return requiredResources.values().stream().mapToInt(Integer::intValue).sum();
    }
}
