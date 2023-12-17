package logisticspipes.recipes;

import static gregtech.api.enums.Mods.GregTech;
import static gregtech.api.util.GT_ModHandler.getModItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import gregtech.api.enums.Mods;
import logisticspipes.LogisticsPipes;
import logisticspipes.interfaces.ICraftingResultHandler;
import logisticspipes.proxy.interfaces.ICraftingParts;

// @formatter:off
// CHECKSTYLE:OFF

public class SolderingStationRecipes {

    public static class SolderingStationRecipe {
        public final ItemStack[] source;
        public final ItemStack result;
        public final ICraftingResultHandler handler;

        public SolderingStationRecipe(ItemStack[] stacks, ItemStack result, ICraftingResultHandler handler) {
            source = stacks;
            this.result = result;
            this.handler = handler;
        }
    }

    private static final ArrayList<SolderingStationRecipe> recipes = new ArrayList<>();

    public static void loadRecipe(ICraftingParts parts) {
		ItemStack missing = new ItemStack(Blocks.fire);
		SolderingStationRecipes.recipes.add(new SolderingStationRecipe(new ItemStack[] {
			getModItem(GregTech.ID, "gt.metaitem.01", 1, 32683, missing),
			null,
			getModItem(GregTech.ID, "gt.metaitem.01", 1, 32693, missing),
			getModItem(Mods.LogisticsPipes.ID, "item.itemModule", 1, 1, missing),
			new ItemStack(Items.ender_pearl,1),
			getModItem(Mods.LogisticsPipes.ID, "item.itemModule", 1, 500, missing),
			null,
			null,
			null },
			new ItemStack(LogisticsPipes.LogisticsItemCard,2,0), new ICraftingResultHandler() {
			@Override
			public void handleCrafting(ItemStack stack) {
				stack.stackTagCompound = new NBTTagCompound();
				stack.stackTagCompound.setString("UUID", UUID.randomUUID().toString());
			}
		}));
    }

    public static List<SolderingStationRecipe> getRecipes() {
        return Collections.unmodifiableList(SolderingStationRecipes.recipes);
    }
}
