package logisticspipes.proxy.specialinventoryhandler;

import java.util.*;
import java.util.Map.Entry;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.proxy.bs.ICrateStorageProxy;
import logisticspipes.utils.item.ItemIdentifier;

public class CrateInventoryHandler extends SpecialInventoryHandler {

    private final ICrateStorageProxy _tile;
    private final boolean _hideOnePerStack;

    private CrateInventoryHandler(TileEntity tile, boolean hideOnePerStack, boolean hideOne, int cropStart,
            int cropEnd) {
        _tile = SimpleServiceLocator.betterStorageProxy.getCrateStorageProxy(tile);
        _hideOnePerStack = hideOnePerStack || hideOne;
    }

    public CrateInventoryHandler() {
        _tile = null;
        _hideOnePerStack = false;
    }

    @Override
    public boolean init() {
        return true;
    }

    @Override
    public boolean isType(TileEntity tile) {
        return SimpleServiceLocator.betterStorageProxy.isBetterStorageCrate(tile);
    }

    @Override
    public SpecialInventoryHandler getUtilForTile(TileEntity tile, ForgeDirection dir, boolean hideOnePerStack,
            boolean hideOne, int cropStart, int cropEnd) {
        return new CrateInventoryHandler(tile, hideOnePerStack, hideOne, cropStart, cropEnd);
    }

    @Override
    public Set<ItemIdentifier> getItems() {
        Set<ItemIdentifier> result = new TreeSet<>();
        for (ItemStack stack : _tile.getContents()) {
            result.add(ItemIdentifier.get(stack));
        }
        return result;
    }

    @Override
    public Map<ItemIdentifier, Integer> getItemsAndCount() {
        return getItemsAndCount(false);
    }

    private Map<ItemIdentifier, Integer> getItemsAndCount(boolean linked) {
        HashMap<ItemIdentifier, Integer> map = new HashMap<>((int) (_tile.getUniqueItems() * 1.5));
        for (ItemStack stack : _tile.getContents()) {
            ItemIdentifier itemId = ItemIdentifier.get(stack);
            int stackSize = stack.stackSize - (_hideOnePerStack ? 1 : 0);
            map.merge(itemId, stackSize, Integer::sum);
        }
        return map;
    }

    @Override
    public ItemStack getSingleItem(ItemIdentifier itemIdent) {
        int count = _tile.getItemCount(itemIdent.unsafeMakeNormalStack(1));
        if (count <= (_hideOnePerStack ? 1 : 0)) {
            return null;
        }
        return _tile.extractItems(itemIdent.makeNormalStack(1), 1);
    }

    @Override
    public boolean containsUndamagedItem(ItemIdentifier itemIdent) {
        if (!itemIdent.isDamageable()) {
            int count = _tile.getItemCount(itemIdent.unsafeMakeNormalStack(1));
            return (count > 0);
        }
        for (ItemStack stack : _tile.getContents()) {
            ItemIdentifier itemId = ItemIdentifier.get(stack).getUndamaged();
            if (itemId.equals(itemIdent)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int roomForItem(ItemIdentifier item) {
        return roomForItem(item, 0);
    }

    @Override
    public int roomForItem(ItemIdentifier itemIdent, int count) {
        return _tile.getSpaceForItem(itemIdent.unsafeMakeNormalStack(1));
    }

    @Override
    public ItemStack add(ItemStack stack, ForgeDirection from, boolean doAdd) {
        ItemStack st = stack.copy();
        st.stackSize = 0;
        if (doAdd) {
            ItemStack tst = stack.copy();
            ItemStack overflow = _tile.insertItems(tst);
            st.stackSize = stack.stackSize;
            if (overflow != null) {
                st.stackSize -= overflow.stackSize;
            }
        } else {
            int space = roomForItem(ItemIdentifier.get(stack), 0);
            st.stackSize = Math.max(Math.min(space, stack.stackSize), 0);
        }
        return st;
    }

    @Override
    public boolean isSpecialInventory() {
        return true;
    }

    ArrayList<Entry<ItemIdentifier, Integer>> cached;

    @Override
    public int getSizeInventory() {
        if (cached == null) {
            initCache();
        }
        return cached.size();
    }

    public void initCache() {
        Map<ItemIdentifier, Integer> map = getItemsAndCount(true);
        cached = new ArrayList<>();
        cached.addAll(map.entrySet());
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        if (cached == null) {
            initCache();
        }
        Entry<ItemIdentifier, Integer> entry = cached.get(i);
        if (entry.getValue() == 0) {
            return null;
        }
        return entry.getKey().makeNormalStack(entry.getValue());
    }

    @Override
    public ItemStack decrStackSize(int i, int j) {
        if (cached == null) {
            initCache();
        }
        Entry<ItemIdentifier, Integer> entry = cached.get(i);
        ItemStack stack = entry.getKey().makeNormalStack(j);
        ItemStack extracted;
        int count = _tile.getItemCount(stack);
        if (count <= (_hideOnePerStack ? 1 : 0)) {
            return null;
        }
        extracted = _tile.extractItems(stack, 1);
        entry.setValue(entry.getValue() - j);
        return extracted;
    }
}
