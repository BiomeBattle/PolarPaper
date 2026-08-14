package live.minehub.polarpaper.core.generator;

import live.minehub.polarpaper.core.config.Config;
import live.minehub.polarpaper.core.source.PolarSource;
import live.minehub.polarpaper.core.world.PolarWorld;
import live.minehub.polarpaper.core.world.PolarWorldAccess;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

public abstract class PolarGenerator extends ChunkGenerator {
    private Config config;
    private @Nullable PolarSource source;
    private final PolarWorldAccess worldAccess;

    public PolarGenerator(Config config, @Nullable PolarSource source, PolarWorldAccess worldAccess) {
        this.config = config;
        this.source = source;
        this.worldAccess = worldAccess;
    }

    public Config getConfig() {
        return this.config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public @Nullable PolarSource getSource() {
        return source;
    }

    public void setSource(@Nullable PolarSource source) {
        this.source = source;
    }

    public PolarWorldAccess getWorldAccess() {
        return this.worldAccess;
    }

    public abstract @Nullable PolarWorld getPolarWorld();

    public abstract Component getInfoComponent(World world);

    @Override
    public @Nullable Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        Location loc = getConfig().spawn();
        loc.setWorld(world);
        return loc;
    }

    public @Nullable BiomeProvider biomeProvider() {
        String key = getConfig().fallbackBiome();
        if (key.isEmpty()) return null;

        NamespacedKey biomeKey = NamespacedKey.fromString(key);
        if (biomeKey == null) return null;

        Biome biome = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(biomeKey);
        if (biome == null) return null;

        return new BiomeProvider() {
            @Override
            public @NotNull Biome getBiome(@NotNull WorldInfo info, int x, int y, int z) {
                return biome;
            }

            @Override
            public @NotNull List<Biome> getBiomes(@NotNull WorldInfo info) {
                return List.of(biome);
            }
        };
    }

    @Override
    public @Nullable BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return biomeProvider();
    }

    public static @Nullable PolarGenerator fromWorld(World world) {
        if (world == null) return null;
        ChunkGenerator generator = world.getGenerator();
        if (generator instanceof PolarGenerator polarGenerator) return polarGenerator;
        return null;
    }
}
