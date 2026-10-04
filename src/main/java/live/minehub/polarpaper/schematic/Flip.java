package live.minehub.polarpaper.schematic;

import net.minecraft.world.level.block.Mirror;

/**
 * Source coordinate axis to negate before rotation. Minecraft's FRONT_BACK mirror negates X,
 * while LEFT_RIGHT negates Z; the names here describe coordinates rather than block facing.
 * <pre>{@code var flip = Flip.X;}</pre>
 * @since 2.2.5-bb
 */
public enum Flip {
    NONE(Mirror.NONE),
    X(Mirror.FRONT_BACK),
    Z(Mirror.LEFT_RIGHT);

    private final Mirror mcMirror;

    Flip(Mirror mcMirror) {
        this.mcMirror = mcMirror;
    }

    public Mirror getMcMirror() {
        return mcMirror;
    }
}
