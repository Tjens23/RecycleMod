package com.tjens23;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Recycle implements ModInitializer {
    public static final String MOD_ID = "recycle";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Register the Recycle Chest block
    public static final Block RECYCLE_CHEST = Registry.register(
            Registries.BLOCK,
            new Identifier(MOD_ID, "recycle_chest"),
            new RecycleChestBlock(FabricBlockSettings.copyOf(Blocks.CHEST)
                    .sounds(BlockSoundGroup.WOOD)
                    .strength(2.5f))
    );

    // Register the Recycle Chest item
    public static final BlockItem RECYCLE_CHEST_ITEM = Registry.register(
            Registries.ITEM,
            new Identifier(MOD_ID, "recycle_chest"),
            new BlockItem(RECYCLE_CHEST, new FabricItemSettings())
    );

    @Override
    public void onInitialize() {
        LOGGER.info("Recycle mod initializing with Recycle Chest!");

        // Add to Functional Blocks creative tab
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(content -> {
            content.add(RECYCLE_CHEST_ITEM);
        });
    }
}