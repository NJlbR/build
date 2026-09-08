package com.towny.architecture.model;

import org.bukkit.Chunk;

import java.util.Objects;

public class ChunkCoord {
    private final String worldName;
    private final int x;
    private final int z;

    public ChunkCoord(String worldName, int x, int z) {
        this.worldName = worldName != null ? worldName : "world";
        this.x = x;
        this.z = z;
    }

    public static ChunkCoord fromChunk(Chunk chunk) {
        if (chunk == null) {
            return null;
        }
        return new ChunkCoord(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public String getWorldName() {
        return worldName;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkCoord that = (ChunkCoord) o;
        return x == that.x && z == that.z && Objects.equals(worldName, that.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, x, z);
    }

    @Override
    public String toString() {
        return worldName + ":" + x + ":" + z;
    }

    public static ChunkCoord parse(String str) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }
        String[] parts = str.split(":");
        if (parts.length == 3) {
            try {
                return new ChunkCoord(parts[0].trim(), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
