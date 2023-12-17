package logisticspipes.ticks;

import java.util.BitSet;

import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.IRouterManager;

public class HudUpdateTick {

    private static final BitSet routersNeedingUpdate = new BitSet(4096);
    private static int firstRouter = -1;
    private static final int inventorySlotsToUpdatePerTick = 90;

    public HudUpdateTick() {}

    public static void clearUpdateFlags() {
        HudUpdateTick.routersNeedingUpdate.clear();
    }

    public static void add(IRouter run) {
        int index = run.getSimpleID();
        if (index < 0) {
            return;
        }
        HudUpdateTick.routersNeedingUpdate.set(index); // expands the bit-set when out of bounds.
        if (HudUpdateTick.firstRouter == -1) {
            HudUpdateTick.firstRouter = index;
        }
    }

    public static void tick() {
        if (HudUpdateTick.firstRouter == -1) {
            return;
        }
        IRouterManager rm = SimpleServiceLocator.routerManager;
        int slotSentCount = 0;
        // cork the compressor
        SimpleServiceLocator.serverBufferHandler.setPause(true);
        while (HudUpdateTick.firstRouter != -1 && slotSentCount < HudUpdateTick.inventorySlotsToUpdatePerTick) {
            HudUpdateTick.routersNeedingUpdate.clear(HudUpdateTick.firstRouter);
            IRouter currentRouter = rm.getRouterUnsafe(HudUpdateTick.firstRouter, false);
            if (currentRouter != null) {
                CoreRoutedPipe pipe = currentRouter.getCachedPipe();
                if (pipe != null) {
                    slotSentCount += pipe.sendQueueChanged(true);
                }
            }
            HudUpdateTick.firstRouter = HudUpdateTick.routersNeedingUpdate.nextSetBit(HudUpdateTick.firstRouter);
        }
        // and let it compress and send
        SimpleServiceLocator.serverBufferHandler.setPause(false);
    }
}
