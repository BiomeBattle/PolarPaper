package live.minehub.polarpaper.schematic;

import live.minehub.polarpaper.core.userdata.EntityUtil;
import live.minehub.polarpaper.core.userdata.WorldUserData;
import live.minehub.polarpaper.core.util.CoordConversion;
import live.minehub.polarpaper.core.util.MemorySegmentReader;
import live.minehub.polarpaper.core.util.PaletteUtil;
import live.minehub.polarpaper.core.world.PolarChunk;
import live.minehub.polarpaper.core.world.PolarEntity;
import live.minehub.polarpaper.core.world.PolarSection;
import live.minehub.polarpaper.core.world.PolarWorld;
import live.minehub.polarpaper.util.BlockUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.joml.Vector2i;
import org.joml.Vector3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class Schematic {

    private static final Logger LOGGER = LoggerFactory.getLogger(Schematic.class);

    public static final NamespacedKey POS_1_KEY = new NamespacedKey("polarpaper", "pos1");
    public static final NamespacedKey POS_2_KEY = new NamespacedKey("polarpaper", "pos2");

    public static void paste(PolarWorld polarWorld, Setter setter, Vector3i pasteOffset, Rotation rotation, IgnoreAir ignoreAir) {
        paste(polarWorld, setter, pasteOffset, rotation, Flip.NONE, ignoreAir);
    }

    public static void paste(PolarWorld polarWorld, Setter setter, Vector3i pasteOffset, Rotation rotation, Flip flip, IgnoreAir ignoreAir) {
        Vector3i offset;
        try {
            offset = WorldUserData.readSchematicOffset(polarWorld.userData());
        } catch (Exception e) {
            offset = null;
        }

        paste(polarWorld, setter, pasteOffset, rotation, flip, ignoreAir, offset == null ? new Vector3i() : offset);
    }

    public static void paste(PolarWorld polarWorld, Setter setter, Vector3i pasteOffset, Rotation rotation, IgnoreAir ignoreAir, Vector3i schematicOffset) {
        paste(polarWorld, setter, pasteOffset, rotation, Flip.NONE, ignoreAir, schematicOffset, Biomes.IGNORE);
    }

    public static void paste(PolarWorld polarWorld, Setter setter, Vector3i pasteOffset, Rotation rotation, Flip flip, IgnoreAir ignoreAir, Vector3i schematicOffset) {
        paste(polarWorld, setter, pasteOffset, rotation, flip, ignoreAir, schematicOffset, Biomes.IGNORE);
    }

    public static void paste(PolarWorld polarWorld, Setter setter, Vector3i pasteOffset, Rotation rotation, IgnoreAir ignoreAir, Vector3i schematicOffset, Biomes biomes) {
        paste(polarWorld, setter, pasteOffset, rotation, Flip.NONE, ignoreAir, schematicOffset, biomes);
    }

    public static void paste(PolarWorld polarWorld, Setter setter, Vector3i pasteOffset, Rotation rotation, Flip flip, IgnoreAir ignoreAir, Vector3i schematicOffset, Biomes biomes) {
        pasteAsync(polarWorld, setter, pasteOffset, rotation, flip, ignoreAir, schematicOffset, biomes)
                .exceptionally(error -> {
                    LOGGER.error("Failed to finish schematic paste", error);
                    return null;
                });
    }

    public static CompletableFuture<Void> pasteAsync(PolarWorld polarWorld, Setter setter, Vector3i pasteOffset, Rotation rotation, Flip flip, IgnoreAir ignoreAir, Vector3i schematicOffset, Biomes biomes) {
        var offset = new Vector3i(schematicOffset);
        var destinationOffset = new Vector3i(pasteOffset);
        var deferredWrites = new ArrayList<Supplier<CompletableFuture<Void>>>();

        Map<Vector3i, PolarChunk.BlockEntity> blockEntityMap = new HashMap<>();

        for (PolarChunk chunk : polarWorld.chunks()) {
            int i = 0;
            for (PolarSection section : chunk.sections()) {
                Vector3i blockOffset = new Vector3i(chunk.x() * 16, (i + polarWorld.minSection()) * 16, chunk.z() * 16)
                        .sub(offset);
                boolean shouldPaste = setter.shouldPaste(chunk, section, i + polarWorld.minSection(), blockOffset);
                i++;
                if (!shouldPaste) continue;

                pasteSection(section, setter, blockOffset, destinationOffset, rotation, flip, ignoreAir);
                if (biomes == Biomes.PASTE) pasteBiomes(section, setter, blockOffset, destinationOffset, rotation, flip);
            }

            handleUserData(setter, destinationOffset, rotation, flip, chunk, offset, deferredWrites);

            for (PolarChunk.BlockEntity blockEntity : chunk.blockEntities()) {
                int x = CoordConversion.chunkBlockIndexGetX(blockEntity.index());
                int y = CoordConversion.chunkBlockIndexGetY(blockEntity.index());
                int z = CoordConversion.chunkBlockIndexGetZ(blockEntity.index());

                Vector3i blockOffset = new Vector3i(chunk.x() * 16, 0, chunk.z() * 16).sub(offset).add(x, y, z);
                BlockUtil.flipBlockPos(blockOffset, flip);
                BlockUtil.rotatePos(blockOffset, rotation);
                blockOffset.add(destinationOffset);

                blockEntityMap.put(blockOffset, blockEntity);
            }
        }

        var pending = new ArrayList<CompletableFuture<?>>();
        for (var entry : blockEntityMap.entrySet()) {
            var position = entry.getKey();
            var stored = entry.getValue();
            var copy = new PolarChunk.BlockEntity(stored.index(), stored.id(), stored.data() == null ? null : stored.data().copy());
            pending.add(setter.setBlockEntityAsync(position.x, position.y, position.z, copy));
        }
        for (var write : deferredWrites) pending.add(write.get());
        var chunksToRefresh = new HashSet<Vector2i>();
        for (var chunk : destinationChunks(polarWorld, offset, destinationOffset, rotation, flip)) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) chunksToRefresh.add(new Vector2i(chunk.x + x, chunk.y + z));
            }
        }
        return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                .thenCompose(_ -> setter.finishPasteAsync(chunksToRefresh));
    }

    public static Set<Vector2i> destinationChunks(PolarWorld polarWorld, Vector3i schematicOffset, Vector3i pasteOffset, Rotation rotation, Flip flip) {
        var chunks = new HashSet<Vector2i>();
        for (var chunk : polarWorld.chunks()) {
            var min = new Vector3i(chunk.x() * 16, 0, chunk.z() * 16).sub(schematicOffset);
            var max = new Vector3i(min).add(15, 0, 15);
            BlockUtil.flipBlockPos(min, flip);
            BlockUtil.flipBlockPos(max, flip);
            BlockUtil.rotatePos(min, rotation);
            BlockUtil.rotatePos(max, rotation);
            min.add(pasteOffset);
            max.add(pasteOffset);
            for (int x = Math.min(min.x, max.x) >> 4; x <= (Math.max(min.x, max.x) >> 4); x++) {
                for (int z = Math.min(min.z, max.z) >> 4; z <= (Math.max(min.z, max.z) >> 4); z++) {
                    chunks.add(new Vector2i(x, z));
                }
            }
        }
        return chunks;
    }

    private static void handleUserData(Setter setter, Vector3i pasteOffset, Rotation rotation, Flip flip, PolarChunk chunk, Vector3i offset,
                                       List<Supplier<CompletableFuture<Void>>> deferredWrites) {
        if (chunk.userData() == null || chunk.userData().length == 0) return;

        final List<PolarEntity> entities;
        try {
            MemorySegment segment = MemorySegment.ofArray(chunk.userData());
            MemorySegmentReader reader = new MemorySegmentReader(segment);
            byte version = reader.readByte();
            entities = EntityUtil.getEntities(reader);
        } catch (Exception e) {
            // Chunk userData written by another consumer - not an entity record.
            return;
        }

        for (PolarEntity polarEntity : entities) {
            Location spawnLocation = polarEntity.getLocation(null, chunk.x(), chunk.z());
            BlockUtil.transformLoc(spawnLocation, offset, pasteOffset, rotation, flip);

            deferredWrites.add(() -> setter.spawnEntityAsync(polarEntity, spawnLocation));
        }
    }

    private static void pasteBiomes(PolarSection polarSection, Setter setter, Vector3i offset, Vector3i pasteOffset, Rotation rotation, Flip flip) {
        if (polarSection.isEmpty()) return;

        String[] palette = polarSection.biomePalette();
        if (palette.length == 0) return;

        int[] indices = new int[PolarSection.BIOME_PALETTE_SIZE];
        if (palette.length > 1) {
            long[] packed = polarSection.biomeData();
            if (packed == null || packed.length == 0) return;

            PaletteUtil.unpack(indices, packed, PaletteUtil.getBitsForLongLength(packed.length, PolarSection.BIOME_PALETTE_SIZE));
        }

        int cellIndex = 0;
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) {
                    String biome = palette[Math.min(indices[cellIndex++], palette.length - 1)];

                    Vector3i cellPos = new Vector3i(x * 4, y * 4, z * 4);
                    cellPos.add(offset);
                    BlockUtil.flipBiomePos(cellPos, flip);
                    BlockUtil.rotatePos(cellPos, rotation);
                    cellPos.add(pasteOffset);

                    setter.setBiome(cellPos.x, cellPos.y, cellPos.z, biome);
                }
            }
        }
    }

    private static void pasteSection(PolarSection polarSection, Setter setter, Vector3i offset, Vector3i pasteOffset, Rotation rotation, Flip flip, IgnoreAir ignoreAir) {
        // Blocks
        long[] blockDataLongs = polarSection.blockData();
        int blockDataBits = blockDataLongs == null ? 0 : PaletteUtil.getBitsForLongLength(blockDataLongs.length, PolarSection.BLOCK_PALETTE_SIZE);
        int[] blockData = new int[PolarSection.BLOCK_PALETTE_SIZE];
        if (blockDataBits > 0) {
            PaletteUtil.unpack(blockData, blockDataLongs, blockDataBits);
        }

        String[] rawBlockPalette = polarSection.blockPalette();
        BlockState[] materialPalette = new BlockState[rawBlockPalette.length];
        for (int i = 0; i < rawBlockPalette.length; i++) {
            try {
                materialPalette[i] = ((CraftBlockData) Bukkit.getServer().createBlockData(rawBlockPalette[i])).getState();
            } catch (IllegalArgumentException _) {
                LOGGER.warn("Failed to parse block state: " + rawBlockPalette[i]);
                materialPalette[i] = Blocks.AIR.defaultBlockState();
            }
        }

        if (rawBlockPalette.length <= 1) {
            BlockState blockState = materialPalette[0];
            if (blockState.isAir() && (ignoreAir == IgnoreAir.ALL || ignoreAir == IgnoreAir.EMPTY_SECTION)) return;

            BlockState rotatedState = blockState.mirror(flip.getMcMirror()).rotate(rotation.getMcRot());

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Vector3i blockPos = new Vector3i(x, y, z);
                        blockPos.add(offset);
                        BlockUtil.flipBlockPos(blockPos, flip);
                        BlockUtil.rotatePos(blockPos, rotation);
                        blockPos.add(pasteOffset);

                        setter.setBlock(blockPos.x, blockPos.y, blockPos.z, rotatedState);
                    }
                }
            }
        } else {
            int blockIndex = 0;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState blockState = materialPalette[blockData[blockIndex++]];
                        if (ignoreAir == IgnoreAir.ALL && blockState.isAir()) continue;

                        Vector3i blockPos = new Vector3i(x, y, z);
                        blockPos.add(offset);
                        blockState = blockState.mirror(flip.getMcMirror()).rotate(rotation.getMcRot());
                        BlockUtil.flipBlockPos(blockPos, flip);
                        BlockUtil.rotatePos(blockPos, rotation);
                        blockPos.add(pasteOffset);

                        setter.setBlock(blockPos.x, blockPos.y, blockPos.z, blockState);
                    }
                }
            }
        }
    }

    public enum Biomes {
        /**
         * Leave the destination's biomes alone (default)
         */
        IGNORE,
        /**
         * Apply the schematic's biomes over the destination.
         */
        PASTE
    }

    public enum IgnoreAir {
        /**
         * Ignore all air blocks
         */
        ALL,
        /**
         * Only ignore empty sections (default)
         */
        EMPTY_SECTION,
        /**
         * Do not ignore air
         */
        NONE
    }

}
