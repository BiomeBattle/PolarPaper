package live.minehub.polarpaper.schematic;

import net.minecraft.world.level.block.Mirror;

public enum Flip {
    NONE(Mirror.NONE),
    X(Mirror.LEFT_RIGHT),
    Z(Mirror.FRONT_BACK);

    private final Mirror mcMirror;

    Flip(Mirror mcMirror) {
        this.mcMirror = mcMirror;
    }

    public Mirror getMcMirror() {
        return mcMirror;
    }
}
