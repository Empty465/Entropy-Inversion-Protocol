package dev.entropyinversion;

import dev.entropyinversion.item.EntropyInversionRequestorItem;
import dev.entropyinversion.entity.AsteroidEntities;
import dev.entropyinversion.network.StrikeNetwork;
import dev.entropyinversion.world.StrikeManager;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(EntropyInversionMod.MOD_ID)
public final class EntropyInversionMod {
    public static final String MOD_ID = "entropyinversion";
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> ENTROPY_INVERSION_REQUESTOR = ITEMS.register(
            "entropy_inversion_requestor",
            () -> new EntropyInversionRequestorItem(
                    new Item.Properties()
                            .rarity(EntropyInversionRequestorItem.COSMIC_RARITY)
                            .stacksTo(1)
            )
    );

    public EntropyInversionMod(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ITEMS.register(modBus);
        AsteroidEntities.ENTITY_TYPES.register(modBus);
        modBus.addListener(this::addCreativeContents);
        StrikeNetwork.register();
        MinecraftForge.EVENT_BUS.register(new StrikeManager());
    }

    private void addCreativeContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ENTROPY_INVERSION_REQUESTOR);
        }
    }
}
