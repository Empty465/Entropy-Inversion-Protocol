package dev.entropyinversion.entity;

import dev.entropyinversion.EntropyInversionMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AsteroidEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, EntropyInversionMod.MOD_ID);
    public static final RegistryObject<EntityType<AsteroidFallingBlockEntity>> ASTEROID_BLOCK =
            ENTITY_TYPES.register(
                    "asteroid_block",
                    () -> EntityType.Builder.<AsteroidFallingBlockEntity>of(
                                    AsteroidFallingBlockEntity::new,
                                    MobCategory.MISC
                            )
                            .sized(0.98F, 0.98F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build("asteroid_block")
            );

    private AsteroidEntities() {
    }
}
