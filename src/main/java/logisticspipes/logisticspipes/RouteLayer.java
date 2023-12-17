/*
 * "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public License 1.0, or MMPL. Please check the
 * contents of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package logisticspipes.logisticspipes;

import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.modules.ModuleCrafter;
import logisticspipes.pipes.PipeLogisticsChassi;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.ExitRoute;
import logisticspipes.routing.IRouter;

/**
 * @author Krapht This class is responsible for resolving where incoming items should go.
 */
public class RouteLayer {

    protected final IRouter _router;
    private final TransportLayer _transport;
    private final CoreRoutedPipe _pipe;

    public RouteLayer(IRouter router, TransportLayer transportLayer, CoreRoutedPipe pipe) {
        _router = router;
        _transport = transportLayer;
        _pipe = pipe;
    }

    private void handleCaseLostCraftingModuleItem(IRoutedItem item) {
        if (!item.getIsLostItem()) {
            return;
        }
        System.out.println("Tz03");
        if (!(item.getAdditionalSourceInformation() instanceof ModuleCrafter.CraftingChassieInformation)) {
            return;
        }
        // Is Lost Crafting Module Item
        System.out.println("Tz04");
        int moduleSlot = ((ModuleCrafter.CraftingChassieInformation) (item.getAdditionalSourceInformation()))
                .getModuleSlot();

        int routerId = ((ModuleCrafter.CraftingChassieInformation) item.getAdditionalSourceInformation()).getRouterID();
        System.out.println(
                "Tz04 " + moduleSlot + " | " + routerId + " | " + item.getItemIdentifierStack().getStackSize());

        IRouter r = SimpleServiceLocator.routerManager.getRouter(routerId);
        ModuleCrafter craftingModule = ((ModuleCrafter) ((PipeLogisticsChassi) r.getPipe()).getModules()
                .getModule(moduleSlot));

        craftingModule.UpdateOverflowedItems(item.getItemIdentifierStack());
        craftingModule.requestMoreItems();
    }

    public ForgeDirection getOrientationForItem(IRoutedItem item, ForgeDirection blocked) {

        item.checkIDFromUUID();
        // If a item has no destination, find one
        if (item.getDestination() < 0) {
            System.out.println("Tz01");
            item = SimpleServiceLocator.logisticsManager.assignDestinationFor(item, _router.getSimpleID(), false);
            _pipe.debug.log("No Destination, assigned new destination: (" + item.getInfo());
        }

        // If the destination is unknown / unroutable or it already arrived at its destination and somehow looped back
        if (item.getDestination() >= 0 && (!_router.hasRoute(
                item.getDestination(),
                item.getTransportMode() == TransportMode.Active,
                item.getItemIdentifierStack().getItem()) || item.getArrived())) {

            if (item.getAdditionalTargetInformation() instanceof ModuleCrafter.CraftingChassieInformation) {
                System.out.println("Tz02: Lost Item: " + item.getItemIdentifierStack().getFriendlyName());
                item.setIsLostItem(true);
                item.setAdditionalSourceInformation(item.getAdditionalTargetInformation());
            }

            item = SimpleServiceLocator.logisticsManager.assignDestinationFor(item, _router.getSimpleID(), false);
            _pipe.debug.log("Unreachable Destination, sssigned new destination: (" + item.getInfo());
        }

        item.checkIDFromUUID();
        // If we still have no destination or client side unroutable, drop it
        if (item.getDestination() < 0) {
            return ForgeDirection.UNKNOWN;
        }

        // Is the destination ourself? Deliver it
        if (item.getDestinationUUID().equals(_router.getId())) {

            _transport.handleItem(item);

            if (item.getDistanceTracker() != null) {
                item.getDistanceTracker().setCurrentDistanceToTarget(0);
                item.getDistanceTracker().setDestinationReached();
            }

            if (item.getTransportMode() != TransportMode.Active && !_transport.stillWantItem(item)) {
                return getOrientationForItem(
                        SimpleServiceLocator.logisticsManager.assignDestinationFor(item, _router.getSimpleID(), true),
                        null);
            }

            item.setDoNotBuffer(true);
            item.setArrived(true);

            if (item.getAdditionalTargetInformation() instanceof ModuleCrafter.CraftingChassieInformation) {
                System.out.println("Arrivedc: " + item.getInfo().destinationint + " " + item.getDestination());

            } else {
                System.out.println("Arrived: " + item.getInfo().destinationint + " " + item.getDestination());

                // handleCaseLostCraftingModuleItem(item);

            }

            ForgeDirection o = _transport.itemArrived(item, blocked);
            return o != null ? o : ForgeDirection.UNKNOWN;
        }

        // Do we now know the destination?
        if (!_router.hasRoute(
                item.getDestination(),
                item.getTransportMode() == TransportMode.Active,
                item.getItemIdentifierStack().getItem())) {
            return ForgeDirection.UNKNOWN;
        }

        // Which direction should we send it
        ExitRoute exit = _router.getExitFor(
                item.getDestination(),
                item.getTransportMode() == TransportMode.Active,
                item.getItemIdentifierStack().getItem());
        if (exit == null) {
            return ForgeDirection.UNKNOWN;
        }

        if (item.getDistanceTracker() != null) {
            item.getDistanceTracker().setCurrentDistanceToTarget(exit.blockDistance);
        }

        return exit.exitOrientation;
    }
}
