package dev.entropyinversion.client;

import dev.entropyinversion.EntropyInversionMod;
import dev.entropyinversion.entity.AsteroidEntities;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = EntropyInversionMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class AsteroidEntityRenderers {
    private AsteroidEntityRenderers() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AsteroidEntities.ASTEROID_BLOCK.get(), FallingBlockRenderer::new);
    }
}
