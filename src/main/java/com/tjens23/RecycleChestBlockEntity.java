package com.tjens23;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class RecycleChestBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, ImplementedInventory {

    public static final BlockEntityType<RecycleChestBlockEntity> RECYCLE_CHEST_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            new Identifier(Recycle.MOD_ID, "recycle_chest"),
            BlockEntityType.Builder.create(RecycleChestBlockEntity::new, Recycle.RECYCLE_CHEST).build()
    );

    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(27, ItemStack.EMPTY);

    public RecycleChestBlockEntity(BlockPos pos, BlockState state) {
        super(RECYCLE_CHEST_BLOCK_ENTITY, pos, state);
    }

    @Override
    public DefaultedList<ItemStack> getItems() {
        return inventory;
    }

    @Override
    public Text getDisplayName() {
        return Text.literal("Recycle Chest");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory, net.minecraft.entity.player.PlayerEntity player) {
        return new RecycleChestScreenHandler(syncId, playerInventory, this);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, inventory);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        Inventories.readNbt(nbt, inventory);
    }

    public static void tick(World world, BlockPos pos, BlockState state, RecycleChestBlockEntity blockEntity) {
        if (world.isClient) return;

        // Check each slot for items to recycle
        for (int i = 0; i < blockEntity.inventory.size(); i++) {
            ItemStack stack = blockEntity.inventory.get(i);

            if (!stack.isEmpty()) {
                // Try to recycle this item
                if (blockEntity.recycleItem(i, stack, world, pos)) {
                    blockEntity.markDirty();
                }
            }
        }
    }

    private boolean recycleItem(int slot, ItemStack stack, World world, BlockPos pos) {
        // Find a crafting recipe for this item
        RecipeEntry<CraftingRecipe> found = null;
        for (RecipeEntry<CraftingRecipe> entry : world.getServer().getRecipeManager().listAllOfType(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = entry.value();
            ItemStack output = recipe.getResult(world.getRegistryManager());

            if (output.getItem().equals(stack.getItem())) {
                found = entry;
                break;
            }
        }

        if (found == null) {
            return false; // No recipe found
        }

        CraftingRecipe recipe = found.value();
        int outputCount = recipe.getResult(world.getRegistryManager()).getCount();
        int times = stack.getCount() / outputCount;

        if (times <= 0) return false;

        // Get ingredients
        DefaultedList<Ingredient> ingredients = recipe.getIngredients();

        // Spawn ingredients in the world
        for (Ingredient ing : ingredients) {
            ItemStack[] matches = ing.getMatchingStacks();
            if (matches == null || matches.length == 0) continue;

            ItemStack toGive = matches[0].copy();
            if (toGive.isEmpty()) continue;

            toGive.setCount(toGive.getCount() * times);

            // Spawn item entity above the chest
            ItemEntity itemEntity = new ItemEntity(
                    world,
                    pos.getX() + 0.5,
                    pos.getY() + 1.0,
                    pos.getZ() + 0.5,
                    toGive
            );
            itemEntity.setVelocity(0, 0.1, 0);
            world.spawnEntity(itemEntity);
        }

        // Remove the item from the slot
        inventory.set(slot, ItemStack.EMPTY);

        Recycle.LOGGER.info("Recycled {} x{} in Recycle Chest at {}",
                stack.getItem().toString(), times, pos);

        return true;
    }
}