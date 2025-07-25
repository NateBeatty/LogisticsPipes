package logisticspipes.pipes.signs;

import java.util.BitSet;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.guis.item.ItemAmountSignGui;
import logisticspipes.network.packets.pipe.ItemAmountSignUpdatePacket;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.renderer.LogisticsRenderPipe;
import logisticspipes.routing.ExitRoute;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.PipeRoutingConnectionType;
import logisticspipes.routing.ServerRouter;
import logisticspipes.utils.ISimpleInventoryEventHandler;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.string.StringUtils;
import logisticspipes.utils.tuples.Pair;
import lombok.Data;

public class ItemAmountPipeSign implements IPipeSign, ISimpleInventoryEventHandler {

    @Data
    private static class ItemAmountPipeSignData implements IPipeSignData {

        private final ItemIdentifierStack item;
        private final int amount;

        @Override
        @SideOnly(Side.CLIENT)
        public boolean isListCompatible(LogisticsRenderPipe render) {
            return item == null || item.getItem().isRenderListCompatible(render);
        }
    }

    public ItemIdentifierInventory itemTypeInv = new ItemIdentifierInventory(1, "", 1);
    public int amount = 100;
    public CoreRoutedPipe pipe;
    public ForgeDirection dir;
    private boolean hasUpdated = false;

    public ItemAmountPipeSign() {
        itemTypeInv.addListener(this);
    }

    @Override
    public boolean isAllowedFor(CoreRoutedPipe pipe) {
        return true;
    }

    @Override
    public void addSignTo(CoreRoutedPipe pipe, ForgeDirection dir, EntityPlayer player) {
        pipe.addPipeSign(dir, new ItemAmountPipeSign(), player);
        openGUI(pipe, dir, player);
    }

    private void openGUI(CoreRoutedPipe pipe, ForgeDirection dir, EntityPlayer player) {
        NewGuiHandler.getGui(ItemAmountSignGui.class).setDir(dir).setTilePos(pipe.container).open(player);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        itemTypeInv.readFromNBT(tag);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        itemTypeInv.writeToNBT(tag);
    }

    @Override
    public ModernPacket getPacket() {
        return PacketHandler.getPacket(ItemAmountSignUpdatePacket.class).setStack(itemTypeInv.getIDStackInSlot(0))
                .setInteger2(amount).setInteger(dir.ordinal()).setTilePos(pipe.container);
    }

    @Override
    public void updateServerSide() {
        if (!pipe.isNthTick(20)) {
            return;
        }
        if (hasUpdated) {
            hasUpdated = false;
            return;
        }
        int newAmount = 0;
        if (itemTypeInv.getIDStackInSlot(0) != null) {
            Map<ItemIdentifier, Integer> availableItems = SimpleServiceLocator.logisticsManager
                    .getAvailableItems(pipe.getRouter().getIRoutersByCost());
            if (availableItems != null) {
                BitSet set = new BitSet(ServerRouter.getBiggestSimpleID());
                spread(availableItems, set);
                if (availableItems.containsKey(itemTypeInv.getIDStackInSlot(0).getItem())) {
                    newAmount = availableItems.get(itemTypeInv.getIDStackInSlot(0).getItem());
                }
            }
        }
        if (newAmount != amount) {
            amount = newAmount;
            sendUpdatePacket();
        }
    }

    private void spread(Map<ItemIdentifier, Integer> availableItems, BitSet set) { // Improve performance by updating a
                                                                                   // wall of Amount pipe signs all at
                                                                                   // once
        IRouter router = pipe.getRouter();
        if (set.get(router.getSimpleID())) return;
        set.set(router.getSimpleID());
        for (ExitRoute exit : router.getIRoutersByCost()) {
            if (exit.distanceToDestination > 2) break; // Only when the signs are in one wall. To not spread to far.
            if (!exit.filters.isEmpty()) continue;
            if (set.get(exit.destination.getSimpleID())) continue;
            if (exit.connectionDetails.contains(PipeRoutingConnectionType.canRequestFrom)
                    && exit.connectionDetails.contains(PipeRoutingConnectionType.canRouteTo)) {
                CoreRoutedPipe cachedPipe = exit.destination.getCachedPipe();
                if (cachedPipe != null) {
                    List<Pair<ForgeDirection, IPipeSign>> pipeSigns = cachedPipe.getPipeSigns();
                    for (Pair<ForgeDirection, IPipeSign> signPair : pipeSigns) {
                        if (signPair != null && signPair.getValue2() instanceof ItemAmountPipeSign) {
                            ((ItemAmountPipeSign) signPair.getValue2()).updateStats(availableItems, set);
                        }
                    }
                }
            }
        }
    }

    private void updateStats(Map<ItemIdentifier, Integer> availableItems, BitSet set) {
        hasUpdated = true;
        int newAmount = 0;
        if (itemTypeInv.getIDStackInSlot(0) != null) {
            if (availableItems.containsKey(itemTypeInv.getIDStackInSlot(0).getItem())) {
                newAmount = availableItems.get(itemTypeInv.getIDStackInSlot(0).getItem());
            }
        }
        if (newAmount != amount) {
            amount = newAmount;
            sendUpdatePacket();
        }
        spread(availableItems, set);
    }

    @Override
    public void activate(EntityPlayer player) {
        openGUI(pipe, dir, player);
    }

    @Override
    public void init(CoreRoutedPipe pipe, ForgeDirection dir) {
        this.pipe = pipe;
        this.dir = dir;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void render(CoreRoutedPipe pipe, LogisticsRenderPipe renderer) {
        FontRenderer var17 = renderer.func_147498_b();
        if (pipe != null) {
            String name = "";
            if (itemTypeInv != null && itemTypeInv.getIDStackInSlot(0) != null) {
                ItemStack itemstack = itemTypeInv.getIDStackInSlot(0).unsafeMakeNormalStack();

                renderer.renderItemStackOnSign(itemstack);
                Item item = itemstack.getItem();

                GL11.glDepthMask(false);
                GL11.glRotatef(-180.0F, 1.0F, 0.0F, 0.0F);
                GL11.glTranslatef(0.5F, +0.08F, 0.0F);
                GL11.glScalef(1.0F / 90.0F, 1.0F / 90.0F, 1.0F / 90.0F);

                try {
                    name = item.getItemStackDisplayName(itemstack);
                } catch (Exception e) {
                    try {
                        name = item.getUnlocalizedName();
                    } catch (Exception ignored) {}
                }

                var17.drawString(
                        "ID: " + Item.getIdFromItem(item),
                        -var17.getStringWidth("ID: " + Item.getIdFromItem(item)) / 2,
                        -4 * 5,
                        0);
                String displayAmount = StringUtils.getFormatedStackSize(amount, false);
                var17.drawString("Amount:", -var17.getStringWidth("Amount:") / 2, 10 - 4 * 5, 0);
                var17.drawString(displayAmount, -var17.getStringWidth(displayAmount) / 2, 2 * 10 - 4 * 5, 0);
            } else {
                GL11.glRotatef(-180.0F, 1.0F, 0.0F, 0.0F);
                GL11.glTranslatef(0.5F, +0.08F, 0.0F);
                GL11.glScalef(1.0F / 90.0F, 1.0F / 90.0F, 1.0F / 90.0F);
                name = "Empty";
            }

            name = renderer.cut(name, var17);

            var17.drawString(name, -var17.getStringWidth(name) / 2 - 15, 3 * 10 - 4 * 5, 0);

            GL11.glDepthMask(true);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    @Override
    public IPipeSignData getRenderData(CoreRoutedPipe pipe) {
        ItemAmountPipeSignData result = null;
        if (pipe != null) {
            final ItemIdentifierStack idStackInSlot = itemTypeInv.getIDStackInSlot(0);
            if (itemTypeInv != null && idStackInSlot != null) {
                String displayAmount = StringUtils.getFormatedStackSize(amount, false);
                return new ItemAmountPipeSignData(idStackInSlot, amount);
            } else {
                return new ItemAmountPipeSignData(null, -1);
            }
        }
        return null;
    }

    @Override
    public void InventoryChanged(IInventory inventory) {
        if (inventory == itemTypeInv) {
            sendUpdatePacket();
        }
    }

    private void sendUpdatePacket() {
        if (MainProxy.isServer(pipe.getWorld())) {
            MainProxy.sendPacketToAllWatchingChunk(
                    pipe.getX(),
                    pipe.getZ(),
                    MainProxy.getDimensionForWorld(pipe.getWorld()),
                    getPacket());
        }
    }
}
