package logisticspipes.network.packets.module;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import logisticspipes.interfaces.IStringBasedModule;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.abstractpackets.ModuleCoordinatesPacket;
import logisticspipes.proxy.MainProxy;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class ItemSinkListPacket extends ModuleCoordinatesPacket {

    @Getter
    @Setter
    private NBTTagCompound nbt;

    public ItemSinkListPacket(int id) {
        super(id);
    }

    @Override
    public ModernPacket template() {
        return new ItemSinkListPacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        IStringBasedModule module = this.getLogisticsModule(player, IStringBasedModule.class);
        if (module == null) {
            return;
        }
        module.readFromNBT(nbt);
        if (MainProxy.isServer(player.getEntityWorld()) && getType().isInWorld()) {
            module.listChanged();
        }
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeNBTTagCompound(nbt);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        nbt = data.readNBTTagCompound();
    }
}
