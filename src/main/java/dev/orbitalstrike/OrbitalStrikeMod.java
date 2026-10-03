package dev.orbitalstrike;

import dev.orbitalstrike.item.OrbitalRequestorItem;
import dev.orbitalstrike.network.StrikeNetwork;
import dev.orbitalstrike.world.StrikeManager;
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

@Mod(OrbitalStrikeMod.MOD_ID)
public final class OrbitalStrikeMod {
    public static final String MOD_ID = "orbitalstrike";
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> ORBITAL_REQUESTOR = ITEMS.register(
            "orbital_requestor",
            () -> new OrbitalRequestorItem(new Item.Properties().stacksTo(1))
    );

    public OrbitalStrikeMod(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ITEMS.register(modBus);
        modBus.addListener(this::addCreativeContents);
        StrikeNetwork.register();
        MinecraftForge.EVENT_BUS.register(new StrikeManager());
    }

    private void addCreativeContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ORBITAL_REQUESTOR);
        }
    }
}
