package logisticspipes.routing.debug;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.Callable;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

import logisticspipes.commands.chathelper.LPChatListener;
import logisticspipes.interfaces.IRoutingDebugAdapter;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.gui.OpenChatGui;
import logisticspipes.network.packets.routingdebug.*;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.ExitRoute;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.PipeRoutingConnectionType;
import logisticspipes.routing.ServerRouter;
import logisticspipes.ticks.QueuedTasks;

public class DebugController implements IRoutingDebugAdapter {

    private static final HashMap<ICommandSender, DebugController> instances = new HashMap<>();
    public List<WeakReference<ExitRoute>> cachedRoutes = new LinkedList<>();

    private final ICommandSender sender;

    private DebugController(ICommandSender sender) {
        this.sender = sender;
    }

    public static DebugController instance(ICommandSender sender) {
        if (DebugController.instances.get(sender) == null) {
            DebugController.instances.put(sender, new DebugController(sender));
        }
        return DebugController.instances.get(sender);
    }

    private enum DebugWaitState {
        LOOP,
        CONTINUE,
        NOWAIT
    }

    private Thread oldThread = null;
    private DebugWaitState state;
    private ExitRoute prevNode = null;
    private ExitRoute nextNode = null;
    private boolean pipeHandled = false;
    private PriorityQueue<ExitRoute> candidatesCost = null;
    private ArrayList<EnumSet<PipeRoutingConnectionType>> closedSet = null;
    private ArrayList<EnumMap<PipeRoutingConnectionType, List<List<IFilter>>>> filterList = null;

    public void debug(final ServerRouter serverRouter) {
        QueuedTasks.queueTask((Callable<Object>) () -> {
            state = DebugWaitState.LOOP;
            Thread tmp = new Thread(() -> {
                while (LPChatListener.existTaskFor(sender.getCommandSenderName())) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                MainProxy.sendPacketToPlayer(PacketHandler.getPacket(OpenChatGui.class), (EntityPlayer) sender);
                if (oldThread != null) {
                    oldThread.stop();
                }
                oldThread = new RoutingTableDebugUpdateThread() {

                    @Override
                    public void run() {
                        serverRouter.CreateRouteTable(0, DebugController.this);
                        oldThread = null;
                    }
                };
                oldThread.setDaemon(true);
                oldThread.setName("RoutingTable update debug Thread");
                oldThread.start();
            });
            tmp.setDaemon(true);
            tmp.start();
            return null;
        });
    }

    private void sendMsg(String message) {
        sender.addChatMessage(new ChatComponentText(message));
    }

    private synchronized void wait(final String reson, boolean flag) {
        if (state == DebugWaitState.NOWAIT) {
            return;
        }
        state = DebugWaitState.LOOP;
        QueuedTasks.queueTask((Callable<Object>) () -> {
            sender.addChatMessage(new ChatComponentText(reson));
            LPChatListener.addTask(() -> {
                state = DebugWaitState.CONTINUE;
                MainProxy.sendPacketToPlayer(PacketHandler.getPacket(OpenChatGui.class), (EntityPlayer) sender);
                return true;
            }, sender);
            return null;
        });
        boolean exist = false;
        while (state == DebugWaitState.LOOP) {
            if (LPChatListener.existTaskFor(sender.getCommandSenderName())) {
                exist = true;
            } else {
                if (exist) {
                    state = DebugWaitState.NOWAIT;
                }
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void start(PriorityQueue<ExitRoute> candidatesCost, ArrayList<EnumSet<PipeRoutingConnectionType>> closedSet,
            ArrayList<EnumMap<PipeRoutingConnectionType, List<List<IFilter>>>> filterList) {
        this.candidatesCost = candidatesCost;
        this.closedSet = closedSet;
        this.filterList = filterList;
        ExitRoute[] e = candidatesCost.toArray(new ExitRoute[] {});
        MainProxy.sendPacketToPlayer(
                PacketHandler.getPacket(RoutingUpdateDebugCanidateList.class).setMsg(e),
                (EntityPlayer) sender);
        wait("Start?", true);
    }

    @Override
    public void nextPipe(ExitRoute lowestCostNode) {
        nextNode = lowestCostNode;
        if (!pipeHandled) {
            handledPipe(true);
        }
        pipeHandled = false;
        prevNode = lowestCostNode;
        MainProxy.sendPacketToPlayer(PacketHandler.getPacket(RoutingUpdateClearClient.class), (EntityPlayer) sender);
        MainProxy.sendPacketToPlayer(
                PacketHandler.getPacket(RoutingUpdateSourcePipe.class).setExitRoute(lowestCostNode),
                (EntityPlayer) sender);
    }

    @Override
    public void handledPipe() {
        handledPipe(false);
    }

    public void handledPipe(boolean flag) {
        for (int i = 0; i < closedSet.size(); i++) {
            EnumSet<PipeRoutingConnectionType> set = closedSet.get(i);
            if (set != null) {
                IRouter router = SimpleServiceLocator.routerManager.getRouter(i);
                if (router != null) {
                    MainProxy.sendPacketToPlayer(
                            PacketHandler.getPacket(RoutingUpdateDebugClosedSet.class).setPos(router.getLPPosition())
                                    .setSet(set),
                            (EntityPlayer) sender);
                }
            }
        }
        for (int i = 0; i < filterList.size(); i++) {
            EnumMap<PipeRoutingConnectionType, List<List<IFilter>>> filters = filterList.get(i);
            if (filters != null) {
                IRouter router = SimpleServiceLocator.routerManager.getRouter(i);
                if (router != null) {
                    MainProxy.sendPacketToPlayer(
                            PacketHandler.getPacket(RoutingUpdateDebugFilters.class).setPos(router.getLPPosition())
                                    .setFilters(filters),
                            (EntityPlayer) sender);
                }
            }
        }

        ExitRoute[] e = candidatesCost.toArray(new ExitRoute[] {});
        if (flag) {
            LinkedList<ExitRoute> list = new LinkedList<>();
            list.add(nextNode);
            list.addAll(Arrays.asList(e));
            e = list.toArray(new ExitRoute[] {});
        }
        MainProxy.sendPacketToPlayer(
                PacketHandler.getPacket(RoutingUpdateDebugCanidateList.class).setMsg(e),
                (EntityPlayer) sender);
        if (prevNode == null || prevNode.debug.isTraced) {
            // Display Information On Client Side

            wait("Continue with next pipe?", false);
        }
        pipeHandled = true;
    }

    @Override
    public void newCanidate(ExitRoute next) {
        next.debug.index = cachedRoutes.size();
        cachedRoutes.add(new WeakReference<>(next));
        MainProxy.sendPacketToPlayer(
                PacketHandler.getPacket(RoutingUpdateCanidatePipe.class).setExitRoute(next),
                (EntityPlayer) sender);
    }

    @Override
    public void stepOneDone() {
        sendMsg("Step One Finished");
    }

    @Override
    public void stepTwoDone() {
        sendMsg("Step Two Finished");
    }

    @Override
    public void done() {
        sendMsg("Update Done");
        MainProxy.sendPacketToPlayer(PacketHandler.getPacket(RoutingUpdateClearClient.class), (EntityPlayer) sender);
        MainProxy.sendPacketToPlayer(PacketHandler.getPacket(RoutingUpdateDoneDebug.class), (EntityPlayer) sender);
        cachedRoutes.clear();
    }

    @Override
    public void init() {
        sendMsg("Initialising variables");
        MainProxy.sendPacketToPlayer(PacketHandler.getPacket(RoutingUpdateInitDebug.class), (EntityPlayer) sender);
    }

    @Override
    public void newFlagsForPipe(EnumSet<PipeRoutingConnectionType> newFlags) {
        // TODO Auto-generated method stub

    }

    @Override
    public void filterList(EnumMap<PipeRoutingConnectionType, List<List<IFilter>>> filters) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean independent() {
        return true;
    }

    @Override
    public boolean isDebug() {
        return true;
    }

    public void untrace(int integer) {
        WeakReference<ExitRoute> ref = cachedRoutes.get(integer);
        if (ref != null && ref.get() != null) {
            ref.get().debug.isTraced = false;
            System.out.println("Did Untrack: " + ref.get().destination.getLPPosition());
        }
    }
}
