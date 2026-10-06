package dev.entropyinversion.item;

import net.minecraft.world.item.ItemStack;

public enum AttackMode {
    ENTROPY_INVERSION("entropy_inversion"),
    ASTEROID_BOMBARDMENT("asteroid_bombardment"),
    ANTI_ORGANIC_MICROBOTS("anti_organic_microbots");

    private static final String STACK_TAG = "AttackMode";
    private final String id;

    AttackMode(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getNameKey() {
        return "attack_mode.entropyinversion." + id;
    }

    public String getDescriptionKey() {
        return getNameKey() + ".description";
    }

    public static AttackMode fromId(String id) {
        for (AttackMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return ENTROPY_INVERSION;
    }

    public static AttackMode fromOrdinal(int ordinal) {
        AttackMode[] modes = values();
        return ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : null;
    }

    public static AttackMode fromStack(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(STACK_TAG)
                ? fromId(stack.getTag().getString(STACK_TAG))
                : ENTROPY_INVERSION;
    }

    public static void writeToStack(ItemStack stack, AttackMode mode) {
        stack.getOrCreateTag().putString(STACK_TAG, mode.id);
    }
}
