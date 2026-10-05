package live.minehub.polarpaper.schematic;

import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.core.event.PolarEntitySpawnEvent;
import live.minehub.polarpaper.core.userdata.EntitySerializer;
import live.minehub.polarpaper.core.userdata.EntityUtil;
import live.minehub.polarpaper.core.world.BlockSelector;
import live.minehub.polarpaper.core.world.PolarChunk;
import live.minehub.polarpaper.core.world.PolarEntity;
import live.minehub.polarpaper.core.world.PolarSection;
import live.minehub.polarpaper.nms.VersionUtil;
import live.minehub.polarpaper.util.BlockUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.joml.Vector2i;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public interface Setter {
    void setBlock(int x, int y, int z, BlockState newBlockState);

    void setBlockEntity(int x, int y, int z, PolarChunk.BlockEntity blockEntity);

    void spawnEntity(PolarEntity polarEntity, Location spawnLocation);

    default void setBiome(int x, int y, int z, String biomeKey) {}

    default boolean shouldPaste(PolarChunk polarChunk, PolarSection section, int sectionY, Vector3i cornerPos) {
        return true;
    }

    default CompletableFuture<Void> setBlockEntityAsync(int x, int y, int z, PolarChunk.BlockEntity blockEntity) {
        setBlockEntity(x, y, z, blockEntity);
        return CompletableFuture.completedFuture(null);
    }


    default CompletableFuture<Void> spawnEntityAsync(PolarEntity polarEntity, Location spawnLocation) {
        spawnEntity(polarEntity, spawnLocation);
        return CompletableFuture.completedFuture(null);
    }


    default CompletableFuture<Void> finishPasteAsync(Set<Vector2i> chunksToRefresh) {
        return CompletableFuture.completedFuture(null);
    }

    class World implements Setter {
        private final org.bukkit.World world;
        private final BlockSelector selector;

        public World(org.bukkit.World world) {
            this.world = world;
            this.selector = BlockSelector.ALL;
        }

        public World(org.bukkit.World world, BlockSelector selector) {
            this.world = world;
            this.selector = selector;
        }

        public org.bukkit.World getWorld() {
            return world;
        }

        public BlockSelector getBlockSelector() {
            return selector;
        }

        @Override
        public void setBlock(int x, int y, int z, BlockState newBlockState) {
            if (!includes(x, y, z)) return;

            BlockUtil.setBlockFast(world, x, y, z, newBlockState);
        }

        @Override
        public void setBiome(int x, int y, int z, String biomeKey) {
            if (!includes(x, y, z)) return;

            NamespacedKey key = NamespacedKey.fromString(biomeKey);
            if (key == null) return;

            Biome biome = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(key);
            if (biome == null) return;

            world.setBiome(x, y, z, biome);
        }

        @Override
        public void setBlockEntity(int x, int y, int z, PolarChunk.BlockEntity blockEntity) {
            if (!includes(x, y, z)) return;

            BlockUtil.setBlockEntity(world, x, y, z, blockEntity);
        }

        @Override
        public void spawnEntity(PolarEntity polarEntity, Location spawnLocation) {
            spawnEntityAsync(polarEntity, spawnLocation).exceptionally(error -> {
                PolarPaper.getPlugin().getLogger().log(java.util.logging.Level.SEVERE, "Failed to paste entity", error);
                return null;
            });
        }

        @Override
        public CompletableFuture<Void> setBlockEntityAsync(int x, int y, int z, PolarChunk.BlockEntity blockEntity) {
            if (!includes(x, y, z)) return CompletableFuture.completedFuture(null);
            return runAt(new Location(world, x, y, z), () -> setBlockEntity(x, y, z, blockEntity));
        }

        @Override
        public CompletableFuture<Void> spawnEntityAsync(PolarEntity polarEntity, Location spawnLocation) {
            if (!includes(spawnLocation.blockX(), spawnLocation.blockY(), spawnLocation.blockZ())) {
                return CompletableFuture.completedFuture(null);
            }
            var location = spawnLocation.clone();
            location.setWorld(world);
            return runAt(location, () -> {
                EntitySerializer entitySerializer = VersionUtil.getEntitySerializer();
                var nmsEntity = polarEntity.toNMSEntity(entitySerializer, world, location);
                if (nmsEntity == null) throw new IllegalArgumentException("Failed to decode pasted entity at " + location);
                CraftEntity entity = nmsEntity.getBukkitEntity();
                PolarEntitySpawnEvent event = new PolarEntitySpawnEvent(polarEntity, entity, location, true);
                event.callEvent();
                if (!event.isCancelled()) {
                    EntityUtil.spawnEntity(entity, world);
                }
            });
        }

        public void refreshChunks(Set<Vector2i> chunksToRefresh) {
            finishPasteAsync(chunksToRefresh).exceptionally(error -> {
                PolarPaper.getPlugin().getLogger().log(java.util.logging.Level.SEVERE, "Failed to finish paste", error);
                return null;
            });
        }

        @Override
        public CompletableFuture<Void> finishPasteAsync(Set<Vector2i> chunksToRefresh) {
            if (chunksToRefresh.isEmpty()) return CompletableFuture.completedFuture(null);
            CraftWorld craftWorld = (CraftWorld) world;
            ServerLevel serverLevel = craftWorld.getHandle();
            var completion = new CompletableFuture<Void>();
            Runnable relight = () -> {
                try {
                    var loadedChunks = chunksToRefresh.stream().map(Vector2i::new)
                            .filter(chunk -> world.isChunkLoaded(chunk.x, chunk.y)).toList();
                    if (loadedChunks.isEmpty()) {
                        completion.complete(null);
                        return;
                    }
                    serverLevel.getChunkSource().getLightEngine().starlight$serverRelightChunks(vecsToChunkPos(loadedChunks), _ -> {}, _ -> {
                        try {
                            var refreshes = new ArrayList<CompletableFuture<Void>>();
                            for (var chunk : loadedChunks) {
                                if (!world.isChunkLoaded(chunk.x, chunk.y)) continue;
                                refreshes.add(runAt(new Location(world, chunk.x * 16, 0, chunk.y * 16),
                                        () -> world.refreshChunk(chunk.x, chunk.y)));
                            }
                            CompletableFuture.allOf(refreshes.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> {
                                if (error == null) completion.complete(null);
                                else completion.completeExceptionally(error);
                            });
                        } catch (Exception error) {
                            completion.completeExceptionally(error);
                        }
                    });
                } catch (Exception error) {
                    completion.completeExceptionally(error);
                }
            };
            try {
                if (Bukkit.isPrimaryThread()) relight.run();
                else Bukkit.getGlobalRegionScheduler().execute(PolarPaper.getPlugin(), relight);
            } catch (Exception error) {
                completion.completeExceptionally(error);
            }
            return completion;
        }

        private CompletableFuture<Void> runAt(Location location, Runnable action) {
            var completion = new CompletableFuture<Void>();
            Runnable task = () -> {
                try {
                    action.run();
                    completion.complete(null);
                } catch (Exception error) {
                    completion.completeExceptionally(error);
                }
            };
            try {
                if (Bukkit.isOwnedByCurrentRegion(location)) task.run();
                else Bukkit.getRegionScheduler().execute(PolarPaper.getPlugin(), location, task);
            } catch (Exception error) {
                completion.completeExceptionally(error);
            }
            return completion;
        }

        private boolean includes(int x, int y, int z) {
            return selector.testChunk(x >> 4, z >> 4) && selector.test(x, y, z);
        }

        private List<ChunkPos> vecsToChunkPos(Collection<Vector2i> vecs) {
            List<ChunkPos> chunkPos = new ArrayList<>(vecs.size());
            for (Vector2i vec : vecs) {
                chunkPos.add(new ChunkPos(vec.x(), vec.y()));
            }
            return chunkPos;
        }
    }

}
