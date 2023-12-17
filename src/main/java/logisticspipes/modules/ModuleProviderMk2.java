package logisticspipes.modules;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.pipes.basic.CoreRoutedPipe.ItemSendMode;

public class ModuleProviderMk2 extends ModuleProvider {

    @Override
    protected int neededEnergy() {
        return 2;
    }

    @Override
    protected ItemSendMode itemSendMode() {
        return ItemSendMode.Fast;
    }

    @Override
    protected int itemsToExtract() {
        return 128;
    }

    @Override
    protected int stacksToExtract() {
        return 8;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/ModuleProviderMk2");
    }
}
