package live.minehub.polarpaper.schematic;

import net.minecraft.world.level.block.Mirror;

/**
 * Source coordinate axis to negate before rotation
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
