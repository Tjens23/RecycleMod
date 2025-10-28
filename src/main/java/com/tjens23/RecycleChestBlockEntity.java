package com.tjens23;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class RecycleChestBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, ImplementedInventory {

    // Will be set by Recycle.java during initialization
    public static BlockEntityType<RecycleChestBlockEntity> RECYCLE_CHEST_BLOCK_ENTITY;

    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(27, ItemStack.EMPTY);
    private int tickCounter = 0;
    private static final int RECYCLE_DELAY = 40; // 2 seconds at 20 ticks per second

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
        nbt.putInt("TickCounter", tickCounter);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        Inventories.readNbt(nbt, inventory);
        tickCounter = nbt.getInt("TickCounter");
    }

    public static void tick(World world, BlockPos pos, BlockState state, RecycleChestBlockEntity blockEntity) {
        if (world.isClient) return;

        blockEntity.tickCounter++;
        
        // Only process recycling every RECYCLE_DELAY ticks (2 seconds)
        if (blockEntity.tickCounter < RECYCLE_DELAY) return;
        
        blockEntity.tickCounter = 0;

        // Check each slot for items to recycle
        for (int i = 0; i < blockEntity.inventory.size(); i++) {
            ItemStack stack = blockEntity.inventory.get(i);

            if (!stack.isEmpty()) {
                // Try to recycle this item
                if (blockEntity.recycleItem(i, stack, world, pos)) {
                    blockEntity.markDirty();
                    break; // Only recycle one item per tick cycle
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

            // Check if this recipe produces the item we want to recycle
            if (ItemStack.areItemsEqual(output, stack)) {
                found = entry;
                break;
            }
        }

        if (found == null) {
            return false; // No recipe found
        }

        CraftingRecipe recipe = found.value();
        ItemStack output = recipe.getResult(world.getRegistryManager());
        int outputCount = output.getCount();
        
        // Calculate how many times we can recycle based on input stack
        int times = stack.getCount() / outputCount;
        if (times <= 0) return false;

        // Get ingredients and spawn them in the world
        DefaultedList<Ingredient> ingredients = recipe.getIngredients();
        boolean successfullyRecycled = false;

        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            
            ItemStack[] matches = ingredient.getMatchingStacks();
            if (matches.length == 0) continue;

            // Use the first matching stack as the ingredient to give back
            ItemStack toGive = matches[0].copy();
            toGive.setCount(toGive.getCount() * times);

            // Try to insert into existing slots first, otherwise spawn in world
            if (!tryInsertIntoInventory(toGive)) {
                spawnItemInWorld(toGive, world, pos);
            }
            
            successfullyRecycled = true;
        }

        if (successfullyRecycled) {
            // Remove the recycled items from the slot
            int newCount = stack.getCount() - (times * outputCount);
            if (newCount <= 0) {
                inventory.set(slot, ItemStack.EMPTY);
            } else {
                stack.setCount(newCount);
            }

            Recycle.LOGGER.info("Recycled {} x{} in Recycle Chest at {}", 
                    stack.getItem().toString(), times, pos);
        }

        return successfullyRecycled;
    }

    private boolean tryInsertIntoInventory(ItemStack stack) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack slotStack = inventory.get(i);
            
            if (slotStack.isEmpty()) {
                inventory.set(i, stack.copy());
                return true;
            } else if (ItemStack.canCombine(slotStack, stack)) {
                int maxStackSize = Math.min(stack.getMaxCount(), slotStack.getMaxCount());
                int availableSpace = maxStackSize - slotStack.getCount();
                
                if (availableSpace > 0) {
                    int toAdd = Math.min(availableSpace, stack.getCount());
                    slotStack.increment(toAdd);
                    stack.decrement(toAdd);
                    
                    if (stack.isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void spawnItemInWorld(ItemStack stack, World world, BlockPos pos) {
        if (stack.isEmpty()) return;
        
        ItemEntity itemEntity = new ItemEntity(
                world,
                pos.getX() + 0.5,
                pos.getY() + 1.0,
                pos.getZ() + 0.5,
                stack
        );
        itemEntity.setVelocity(
                (world.random.nextDouble() - 0.5) * 0.1,
                0.2,
                (world.random.nextDouble() - 0.5) * 0.1
        );
        world.spawnEntity(itemEntity);
    }
}