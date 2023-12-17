package logisticspipes.network.packets.pipe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.blocks.crafting.LogisticsCraftingTableTileEntity;
import logisticspipes.gui.popup.GuiRecipeImport;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.PipeBlockRequestTable;
import logisticspipes.pipes.PipeItemsCraftingLogistics;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.item.ItemIdentifier;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class FindMostLikelyRecipeComponents extends CoordinatesPacket {

    @Getter
    @Setter
    private List<GuiRecipeImport.Canidates> content;

    public FindMostLikelyRecipeComponents(int id) {
        super(id);
    }

    @Override
    public void processPacket(EntityPlayer player) {
        TileEntity tile = this.getTile(player.getEntityWorld(), TileEntity.class);
        CoreRoutedPipe pipe = null;
        if (tile instanceof LogisticsCraftingTableTileEntity) {
            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                TileEntity conn = ((LogisticsCraftingTableTileEntity) tile).getLPPosition().moveForward(dir)
                        .getTileEntity(player.getEntityWorld());
                if (conn instanceof LogisticsTileGenericPipe) {
                    if (((LogisticsTileGenericPipe) conn).pipe instanceof PipeItemsCraftingLogistics) {
                        pipe = (CoreRoutedPipe) ((LogisticsTileGenericPipe) conn).pipe;
                        break;
                    }
                }
            }
        } else if (tile instanceof LogisticsTileGenericPipe) {
            if (((LogisticsTileGenericPipe) tile).pipe instanceof PipeBlockRequestTable) {
                pipe = (CoreRoutedPipe) ((LogisticsTileGenericPipe) tile).pipe;
            }
        }
        List<Integer> list = new ArrayList<>(content.size());
        while (list.size() < content.size()) {
            list.add(-1);
        }
        if (pipe == null) return;
        LinkedList<ItemIdentifier> craftable = null;
        for (int j = 0; j < content.size(); j++) {
            GuiRecipeImport.Canidates canidates = content.get(j);
            ItemIdentifier maxItem;
            int maxItemPos = -1;
            int max = 0;
            for (int i = 0; i < canidates.order.size(); i++) {
                ItemIdentifier ident = canidates.order.get(i).getItem();
                int newAmount = SimpleServiceLocator.logisticsManager
                        .getAmountFor(ident, pipe.getRouter().getIRoutersByCost());

                if (newAmount > max) {
                    max = newAmount;
                    maxItemPos = i;
                }
            }
            if (max < 64) {
                if (craftable == null) {
                    craftable = SimpleServiceLocator.logisticsManager
                            .getCraftableItems(pipe.getRouter().getIRoutersByCost());
                }
                for (ItemIdentifier craft : craftable) {
                    for (int i = 0; i < canidates.order.size(); i++) {
                        ItemIdentifier ident = canidates.order.get(i).getItem();
                        if (craft == ident) {
                            maxItemPos = i;
                            break;
                        }
                    }
                }
            }
            list.set(j, maxItemPos);
        }
        MainProxy.sendPacketToPlayer(
                PacketHandler.getPacket(MostLikelyRecipeComponentsResponse.class).setResponse(list),
                player);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        content = data.readList(data12 -> {
            GuiRecipeImport.Canidates can = new GuiRecipeImport.Canidates(new TreeSet<>());
            can.order = data12.readList(LPDataInputStream::readItemIdentifierStack);
            return can;
        });
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeList(
                content,
                (data12, object) -> data12.writeList(object.order, LPDataOutputStream::writeItemIdentifierStack));
    }

    @Override
    public ModernPacket template() {
        return new FindMostLikelyRecipeComponents(getId());
    }
}
