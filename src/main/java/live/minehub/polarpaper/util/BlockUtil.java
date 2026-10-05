package live.minehub.polarpaper.util;

import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import live.minehub.polarpaper.core.util.CoordConversion;
import live.minehub.polarpaper.core.world.PolarChunk;
import live.minehub.polarpaper.schematic.Flip;
import live.minehub.polarpaper.schematic.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PaletteResize;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public class BlockUtil {

    public static void setBlockFast(World world, int x, int y, int z, BlockState blockState) {
        CraftWorld craftWorld = (CraftWorld) world;
        ServerLevel serverLevel = craftWorld.getHandle();
        ChunkHolderManager chunkHolderManager = serverLevel.moonrise$getChunkTaskScheduler().chunkHolderManager;

        int chunkX = (int)Math.floor(x / 16.0);
        int chunkZ = (int)Math.floor(z / 16.0);
        int section = (int)Math.floor(y / 16.0);

        NewChunkHolder chunkHolder = chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        if (chunkHolder == null) return;
        ChunkAccess chunkAccess = chunkHolder.getCurrentChunk();
        if (chunkAccess == null) return;

        int sectionI = section - chunkAccess.getMinSectionY();
        if (sectionI >= chunkAccess.getSections().length) return;
        if (sectionI < 0) return;

        LevelChunkSection levelChunkSection = chunkAccess.getSection(sectionI);
        int newBlockX = x % 16;
        if (newBlockX < 0) newBlockX = 16 + newBlockX;
        int newBlockY = y % 16;
        if (newBlockY < 0) newBlockY = 16 + newBlockY;
        int newBlockZ = z % 16;
        if (newBlockZ < 0) newBlockZ = 16 + newBlockZ;

        levelChunkSection.setBlockState(newBlockX, newBlockY, newBlockZ, blockState);
    }

    public static @Nullable BlockState getBlockFast(World world, int x, int y, int z) {
        CraftWorld craftWorld = (CraftWorld) world;
        ServerLevel serverLevel = craftWorld.getHandle();
        ChunkHolderManager chunkHolderManager = serverLevel.moonrise$getChunkTaskScheduler().chunkHolderManager;

        int chunkX = (int)Math.floor(x / 16.0);
        int chunkZ = (int)Math.floor(z / 16.0);
        int section = (int)Math.floor(y / 16.0);

        NewChunkHolder chunkHolder = chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        if (chunkHolder == null) return null;
        ChunkAccess chunkAccess = chunkHolder.getCurrentChunk();
        if (chunkAccess == null) return null;

        int sectionI = section - chunkAccess.getMinSectionY();
        if (sectionI >= chunkAccess.getSections().length) return null;
        if (sectionI < 0) return null;

        LevelChunkSection levelChunkSection = chunkAccess.getSection(sectionI);
        int newBlockX = x % 16;
        if (newBlockX < 0) newBlockX = 16 + newBlockX;
        int newBlockY = y % 16;
        if (newBlockY < 0) newBlockY = 16 + newBlockY;
        int newBlockZ = z % 16;
        if (newBlockZ < 0) newBlockZ = 16 + newBlockZ;

        return levelChunkSection.getBlockState(newBlockX, newBlockY, newBlockZ);
    }

    /**
     * Searches a world for a specific block state
     * @return a list of positions of the block state, empty list if none found
     */
    public static List<Vector3i> findBlocks(World world, BlockState blockState) {
        CraftWorld craftWorld = (CraftWorld) world;
        ServerLevel serverLevel = craftWorld.getHandle();
        ChunkHolderManager chunkHolderManager = serverLevel.moonrise$getChunkTaskScheduler().chunkHolderManager;

        List<Vector3i> structureVoidPositions = new ArrayList<>();
        for (NewChunkHolder chunkHolder : chunkHolderManager.getChunkHolders()) {
            List<Vector3i> blocks = findBlocks(world, chunkHolder.chunkX, chunkHolder.chunkZ, blockState);
            structureVoidPositions.addAll(blocks);
        }

        return structureVoidPositions;
    }

    /**
     * Searches a chunk in a world for a specific block state
     * @return a list of positions of the block state, empty list if none found
     */
    public static List<Vector3i> findBlocks(World world, int chunkX, int chunkZ, BlockState blockState) {
        CraftWorld craftWorld = (CraftWorld) world;
        ServerLevel serverLevel = craftWorld.getHandle();
        ChunkHolderManager chunkHolderManager = serverLevel.moonrise$getChunkTaskScheduler().chunkHolderManager;

        List<Vector3i> blockPositions = new ArrayList<>();

        NewChunkHolder chunkHolder = chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        if (chunkHolder == null) return blockPositions;
        ChunkAccess chunkAccess = chunkHolder.getCurrentChunk();
        if (chunkAccess == null) return blockPositions;

        int sectionY = chunkAccess.getMinSectionY();
        for (LevelChunkSection section : chunkAccess.getSections()) {
            if (section.hasOnlyAir()) {
                sectionY++;
                continue;
            }

            boolean hasBlockState = section.maybeHas(b -> b == blockState);
            if (!hasBlockState) {
                sectionY++;
                continue;
            }

            int structureVoidId = section.getStates().data.palette().idFor(blockState, PaletteResize.noResizeExpected());
            int finalSectionY = sectionY;
            section.getStates().data.storage().getAll(new IntConsumer() {
                int i = 0;

                @Override
                public void accept(int n) {
                    if (n == structureVoidId) {
                        Vector3i vec = new Vector3i(
                                CoordConversion.sectionBlockIndexGetX(i) + chunkAccess.locX * 16,
                                CoordConversion.sectionBlockIndexGetY(i) + finalSectionY * 16,
                                CoordConversion.sectionBlockIndexGetZ(i) + chunkAccess.locZ * 16
                        );
                        blockPositions.add(vec);
                    }
                    i++;
                }
            });

            sectionY++;
        }

        return blockPositions;
    }

    public static void setBlockEntity(World world, int x, int y, int z, PolarChunk.BlockEntity blockEntity) {
        if (blockEntity.data() == null) return;

        CraftWorld craftWorld = (CraftWorld) world;
        ServerLevel serverLevel = craftWorld.getHandle();
        ChunkHolderManager chunkHolderManager = serverLevel.moonrise$getChunkTaskScheduler().chunkHolderManager;

        int chunkX = (int)Math.floor(x / 16.0);
        int chunkZ = (int)Math.floor(z / 16.0);

        NewChunkHolder chunkHolder = chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        if (chunkHolder == null) return;
        ChunkAccess chunkAccess = chunkHolder.getCurrentChunk();
        if (chunkAccess == null) return;

        var data = blockEntity.data().copy();
        data.putInt("x", x);
        data.putInt("y", y);
        data.putInt("z", z);

        var registryAccess = ((CraftServer) Bukkit.getServer()).getServer().registryAccess();
        BlockEntity nmsBlockEntity = BlockEntity.loadStatic(new BlockPos(x, y, z), chunkAccess.getBlockState(x, y, z), data, registryAccess);
        if (nmsBlockEntity == null) return;
        serverLevel.getChunk(chunkX, chunkZ).addAndRegisterBlockEntity(nmsBlockEntity);
    }

    public static void rotateLoc(@NotNull Location loc, @NotNull Rotation rotation) {
        Vector3d vec = new Vector3d(loc.x(), loc.y(), loc.z());
        rotatePos(vec, rotation);
        loc.set(vec.x, vec.y, vec.z);
        loc.setYaw(loc.getYaw() + rotation.toDegrees());
    }

    public static void transformPos(@NotNull Vector3d point, @NotNull Rotation rotation, @NotNull Flip flip) {
        flipPos(point, flip);
        rotatePos(point, rotation);
        switch (rotation) {
            case CLOCKWISE_90 -> point.add(1, 0, 0);
            case CLOCKWISE_180 -> point.add(1, 0, 1);
            case CLOCKWISE_270 -> point.add(0, 0, 1);
            default -> {}
        }
    }

    public static void transformLoc(@NotNull Location loc, @NotNull Vector3i schematicOffset,
                                   @NotNull Vector3i pasteOffset, @NotNull Rotation rotation, @NotNull Flip flip) {
        var point = new Vector3d(loc.x(), loc.y(), loc.z())
                .sub(schematicOffset.x, schematicOffset.y, schematicOffset.z);
        transformPos(point, rotation, flip);
        point.add(pasteOffset.x, pasteOffset.y, pasteOffset.z);
        loc.set(point.x, point.y, point.z);
        loc.setYaw(flipYaw(loc.getYaw(), flip) + rotation.toDegrees());
    }

    public static void flipBlockPos(@NotNull Vector3i point, @NotNull Flip flip) {
        switch (flip) {
            case X -> point.x = -point.x - 1;
            case Z -> point.z = -point.z - 1;
            case NONE -> {}
        }
    }

    public static void flipPointPos(@NotNull Vector3i point, @NotNull Flip flip) {
        switch (flip) {
            case X -> point.x = -point.x;
            case Z -> point.z = -point.z;
            case NONE -> {}
        }
    }

    public static float flipYaw(float yaw, @NotNull Flip flip) {
        return switch (flip) {
            case X -> -yaw;
            case Z -> 180 - yaw;
            case NONE -> yaw;
        };
    }

    public static void flipBiomePos(@NotNull Vector3i point, @NotNull Flip flip) {
        switch (flip) {
            case X -> point.x = -point.x - 4;
            case Z -> point.z = -point.z - 4;
            case NONE -> {}
        }
    }

    public static void flipPos(@NotNull Vector3d point, @NotNull Flip flip) {
        switch (flip) {
            case X -> point.x = -point.x;
            case Z -> point.z = -point.z;
            case NONE -> {}
        }
    }

    public static void flipLoc(@NotNull Location loc, @NotNull Flip flip) {
        Vector3d vec = new Vector3d(loc.x(), loc.y(), loc.z());
        flipPos(vec, flip);
        loc.set(vec.x, vec.y, vec.z);
        switch (flip) {
            case X -> loc.setYaw(-loc.getYaw());
            case Z -> loc.setYaw(180 - loc.getYaw());
            case NONE -> {}
        }
    }

    public static void rotatePos(@NotNull Vector3i point, @NotNull Rotation rotation) {
        Vector3d vec = new Vector3d(point);
        rotatePos(vec, rotation);
        point.x = (int) vec.x;
        point.y = (int) vec.y;
        point.z = (int) vec.z;
    }

    public static void rotatePos(@NotNull Vector3d point, @NotNull Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_90 -> {
                double x = -point.z;
                double z = point.x;
                point.x = x;
                point.z = z;
            }
            case CLOCKWISE_180 -> {
                double x = -point.x;
                double z = -point.z;
                point.x = x;
                point.z = z;
            }
            case CLOCKWISE_270 -> {
                double x = point.z;
                double z = -point.x;
                point.x = x;
                point.z = z;
            }
        }
    }

}
