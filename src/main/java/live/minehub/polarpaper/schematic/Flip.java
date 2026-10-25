package live.minehub.polarpaper.schematic;

import net.minecraft.world.level.block.Mirror;

/**
 * Source coordinate axis to negate before rotation.
 */
public enum Flip {
    X(Mirror.FRONT_BACK),
    Y(Mirror.NONE),
    Z(Mirror.LEFT_RIGHT);

    private final Mirror mcMirror;

    Flip(Mirror mcMirror) {
        this.mcMirror = mcMirror;
    }

    public Mirror getMcMirror() {
        return mcMirror;
    }
}
