package com.towny.architecture.model;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveBuilding {
    public enum Status {
        UNDER_CONSTRUCTION,
        COMPLETED,
        DAMAGED
    }

    private final String id;
    private final String blueprintId;
    private final String townName;
    private final String worldName;
    private final Set<ChunkCoord> assignedChunks;
    private final Map<Material, Integer> requiredMaterials;
    private final Map<Material, Integer> placedMaterials = new ConcurrentHashMap<>();
    private Status status;
    private double healthPercent;

    public ActiveBuilding(String id, String blueprintId, String townName, String worldName,
                          Set<ChunkCoord> assignedChunks,
                          Map<Material, Integer> requiredMaterials) {
        this.id = id;
        this.blueprintId = blueprintId != null ? blueprintId.toLowerCase() : "";
        this.townName = townName != null ? townName : "";
        this.worldName = worldName != null ? worldName : "world";
        this.assignedChunks = new HashSet<>(assignedChunks != null ? assignedChunks : Collections.emptySet());
        this.requiredMaterials = new LinkedHashMap<>(requiredMaterials != null ? requiredMaterials : Collections.emptyMap());
        this.status = Status.UNDER_CONSTRUCTION;
        this.healthPercent = 0.0;
    }

    public boolean isInBuildingArea(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().getName().equalsIgnoreCase(worldName)) {
            return false;
        }
        ChunkCoord coord = new ChunkCoord(worldName, loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        return assignedChunks.contains(coord);
    }

    public synchronized int addPlacedBlock(Material mat) {
        int current = placedMaterials.getOrDefault(mat, 0) + 1;
        placedMaterials.put(mat, current);
        recalculateProgress();
        return current;
    }

    public synchronized int removePlacedBlock(Material mat) {
        int current = placedMaterials.getOrDefault(mat, 0);
        if (current > 0) {
            current--;
            placedMaterials.put(mat, current);
            recalculateProgress();
        }
        return current;
    }

    public synchronized void setPlacedBlockCount(Material mat, int count) {
        placedMaterials.put(mat, Math.max(0, count));
        recalculateProgress();
    }

    public synchronized void recalculateProgress() {
        int totalNeeded = 0;
        int totalPlaced = 0;

        for (Map.Entry<Material, Integer> entry : requiredMaterials.entrySet()) {
            int needed = entry.getValue();
            int placed = Math.min(placedMaterials.getOrDefault(entry.getKey(), 0), needed);
            totalNeeded += needed;
            totalPlaced += placed;
        }

        if (totalNeeded == 0) {
            healthPercent = 100.0;
            if (status == Status.UNDER_CONSTRUCTION) {
                status = Status.COMPLETED;
            }
            return;
        }

        healthPercent = Math.min(100.0, ((double) totalPlaced / totalNeeded) * 100.0);

        boolean fullyBuilt = isFullyBuilt();

        if (status == Status.UNDER_CONSTRUCTION) {
            if (fullyBuilt) {
                status = Status.COMPLETED;
            }
        } else if (status == Status.COMPLETED) {
            if (!fullyBuilt || healthPercent < 99.99) {
                status = Status.DAMAGED;
            }
        } else if (status == Status.DAMAGED) {
            if (fullyBuilt && healthPercent >= 99.99) {
                status = Status.COMPLETED;
            }
        }
    }

    public synchronized boolean isFullyBuilt() {
        for (Map.Entry<Material, Integer> entry : requiredMaterials.entrySet()) {
            if (placedMaterials.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public boolean isConstructed() {
        return status != Status.UNDER_CONSTRUCTION;
    }

    public int getPlacedCount(Material mat) {
        return placedMaterials.getOrDefault(mat, 0);
    }

    public String getId() { return id; }
    public String getBlueprintId() { return blueprintId; }
    public String getTownName() { return townName; }
    public String getWorldName() { return worldName; }
    public Set<ChunkCoord> getAssignedChunks() { return Collections.unmodifiableSet(assignedChunks); }
    public Map<Material, Integer> getRequiredMaterials() { return Collections.unmodifiableMap(requiredMaterials); }
    public Map<Material, Integer> getPlacedMaterials() { return Collections.unmodifiableMap(placedMaterials); }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public double getHealthPercent() { return healthPercent; }
    public void setHealthPercent(double healthPercent) { this.healthPercent = healthPercent; }
}
