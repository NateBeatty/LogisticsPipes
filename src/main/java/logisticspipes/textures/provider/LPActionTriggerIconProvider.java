package logisticspipes.textures.provider;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.renderer.IIconProvider;

public class LPActionTriggerIconProvider implements IIconProvider {

    public static int actionDisablePipeIconIndex = 0;
    public static int triggerCraftingIconIndex = 1;
    public static int triggerPowerDischargingIconIndex = 2;
    public static int triggerPowerNeededIconIndex = 3;
    public static int triggerSupplierFailedIconIndex = 4;
    public static int triggerHasDestinationIconIndex = 5;
    public static int actionRobotRoutingIconIndex = 6;

    private final IIcon[] icons;

    public LPActionTriggerIconProvider() {
        icons = new IIcon[7];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int iconIndex) {
        if (iconIndex > 6) {
            return null;
        }
        return icons[iconIndex];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister iconRegister) {
        icons[LPActionTriggerIconProvider.actionDisablePipeIconIndex] = iconRegister
                .registerIcon("logisticspipes:actionTriggers/DisablePipe");
        icons[LPActionTriggerIconProvider.triggerCraftingIconIndex] = iconRegister
                .registerIcon("logisticspipes:actionTriggers/CraftingWaiting");
        icons[LPActionTriggerIconProvider.triggerPowerDischargingIconIndex] = iconRegister
                .registerIcon("logisticspipes:actionTriggers/PowerDischarging");
        icons[LPActionTriggerIconProvider.triggerPowerNeededIconIndex] = iconRegister
                .registerIcon("logisticspipes:actionTriggers/PowerNeeded");
        icons[LPActionTriggerIconProvider.triggerSupplierFailedIconIndex] = iconRegister
                .registerIcon("logisticspipes:actionTriggers/ActionTrigger1");
        icons[LPActionTriggerIconProvider.triggerHasDestinationIconIndex] = iconRegister
                .registerIcon("logisticspipes:actionTriggers/ActionTrigger17");
        icons[LPActionTriggerIconProvider.actionRobotRoutingIconIndex] = iconRegister
                .registerIcon("logisticspipes:actionTriggers/RobotRouting");
    }
}
