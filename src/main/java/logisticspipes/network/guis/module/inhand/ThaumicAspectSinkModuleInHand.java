package logisticspipes.network.guis.module.inhand;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.gui.modules.GuiThaumicAspectSink;
import logisticspipes.modules.ModuleThaumicAspectSink;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.network.abstractguis.GuiProvider;
import logisticspipes.network.abstractguis.ModuleInHandGuiProvider;
import logisticspipes.utils.gui.DummyContainer;
import logisticspipes.utils.gui.DummyModuleContainer;
import logisticspipes.utils.item.ItemIdentifierInventory;

public class ThaumicAspectSinkModuleInHand extends ModuleInHandGuiProvider {

    public ThaumicAspectSinkModuleInHand(int id) {
        super(id);
    }

    @Override
    public Object getClientGui(EntityPlayer player) {
        LogisticsModule module = getLogisticsModule(player);
        if (!(module instanceof ModuleThaumicAspectSink)) {
            return null;
        }
        return new GuiThaumicAspectSink(player.inventory, (ModuleThaumicAspectSink) module);
    }

    @Override
    public DummyContainer getContainer(EntityPlayer player) {
        DummyModuleContainer dummy = new DummyModuleContainer(player, getInvSlot());
        if (!(dummy.getModule() instanceof ModuleThaumicAspectSink)) {
            return null;
        }
        dummy.setInventory(new ItemIdentifierInventory(1, "TMP", 1));
        dummy.addDummySlot(0, 0, 0);
        dummy.addNormalSlotsForPlayerInventory(0, 0);
        return dummy;
    }

    @Override
    public GuiProvider template() {
        return new ThaumicAspectSinkModuleInHand(getId());
    }
}
