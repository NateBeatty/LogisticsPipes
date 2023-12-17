package logisticspipes.network;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import net.minecraft.entity.player.EntityPlayer;

import org.apache.logging.log4j.Level;

import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.AttributeKey;
import logisticspipes.LPConstants;
import logisticspipes.LogisticsPipes;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.exception.DelayPacketException;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import lombok.SneakyThrows;

/*
 * Basically FML SimpleIndexedCodec, except with static registration of LP ModernPackets and short instead of byte
 * discriminator
 */
@Sharable
public class PacketHandler extends MessageToMessageCodec<FMLProxyPacket, ModernPacket> {

    public static List<ModernPacket> packetlist;
    public static Map<Class<? extends ModernPacket>, ModernPacket> packetmap;

    private static int packetDebugID = 1;
    public static final Map<Integer, StackTraceElement[]> debugMap = new HashMap<>();

    @SuppressWarnings("unchecked")
    // Suppressed because this cast should never fail.
    public static <T extends ModernPacket> T getPacket(Class<T> clazz) {
        T packet = (T) PacketHandler.packetmap.get(clazz).template();
        if (LPConstants.DEBUG && MainProxy.proxy.getSide().equals("Client")) {
            StackTraceElement[] trace = Thread.currentThread().getStackTrace();
            synchronized (PacketHandler.debugMap) { // Unique id
                int id = PacketHandler.packetDebugID++;
                PacketHandler.debugMap.put(id, trace);
                packet.setDebugId(id);
            }
        }
        return packet;
    }

    // horrible hack to carry the proper player for the side along...
    static class InboundModernPacketWrapper {

        final ModernPacket packet;
        final EntityPlayer player;

        InboundModernPacketWrapper(ModernPacket p, EntityPlayer e) {
            packet = p;
            player = e;
        }
    }

    /*
     * enumerates all ModernPackets, sets their IDs and populate packetlist/packetmap
     */
    @SuppressWarnings("unchecked")
    @SneakyThrows({ IOException.class, InvocationTargetException.class, IllegalAccessException.class,
            InstantiationException.class, IllegalArgumentException.class, NoSuchMethodException.class,
            SecurityException.class })
    // Suppression+sneakiness because these shouldn't ever fail, and if they do, it needs to fail.
    public static void initialize() {
        final List<ClassInfo> classes = new ArrayList<>(
                ClassPath.from(PacketHandler.class.getClassLoader())
                        .getTopLevelClassesRecursive("logisticspipes.network.packets"));
        classes.sort(Comparator.comparing(ClassInfo::getSimpleName));

        PacketHandler.packetlist = new ArrayList<>(classes.size());
        PacketHandler.packetmap = new HashMap<>(classes.size());

        int currentid = 0;

        for (ClassInfo c : classes) {
            final Class<?> cls = c.load();
            final ModernPacket instance = (ModernPacket) cls.getConstructor(int.class).newInstance(currentid);
            PacketHandler.packetlist.add(instance);
            PacketHandler.packetmap.put((Class<? extends ModernPacket>) cls, instance);
            currentid++;
        }
    }

    // TODO correct to work with WeakReference (See FML original)
    protected static final AttributeKey<ThreadLocal<FMLProxyPacket>> INBOUNDPACKETTRACKER = new AttributeKey<>(
            "lp:inboundpacket");

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        ctx.attr(PacketHandler.INBOUNDPACKETTRACKER).set(new ThreadLocal<>());
    }

    // Used to provide the Description packet
    public static FMLProxyPacket toFMLPacket(ModernPacket msg) throws Exception {
        return PacketHandler.toFMLPacket(msg, MainProxy.networkChannelName);
    }

    private static FMLProxyPacket toFMLPacket(ModernPacket msg, String channel) throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeShort(msg.getId());
        buffer.writeInt(msg.getDebugId());
        msg.writeData(new LPDataOutputStream(buffer));
        return new FMLProxyPacket(buffer.copy(), channel);
    }

    @Override
    protected final void encode(ChannelHandlerContext ctx, ModernPacket msg, List<Object> out) throws Exception {
        FMLProxyPacket proxy = PacketHandler.toFMLPacket(msg, ctx.channel().attr(NetworkRegistry.FML_CHANNEL).get());
        FMLProxyPacket old = ctx.attr(PacketHandler.INBOUNDPACKETTRACKER).get().get();
        if (old != null) {
            proxy.setDispatcher(old.getDispatcher());
        }
        out.add(proxy);
    }

    @Override
    protected final void decode(ChannelHandlerContext ctx, FMLProxyPacket msg, List<Object> out) throws Exception {
        ByteBuf payload = msg.payload();
        int packetID = payload.readShort();
        final ModernPacket packet = PacketHandler.packetlist.get(packetID).template();
        packet.setDebugId(payload.readInt());
        ctx.attr(PacketHandler.INBOUNDPACKETTRACKER).get().set(msg);
        packet.readData(new LPDataInputStream(payload.slice()));
        out.add(new InboundModernPacketWrapper(packet, MainProxy.proxy.getEntityPlayerFromNetHandler(msg.handler())));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        FMLLog.log(Level.ERROR, cause, "LogisticsPipes PacketHandler exception caught");
        super.exceptionCaught(ctx, cause);
    }

    // hacky callback to process packets coming from by the packetbufferhandler decompressors
    // TODO replace with proper netty implementation
    public static void onPacketData(final LPDataInputStream data, final EntityPlayer player) throws IOException {
        if (player == null) {
            return;
        }
        final int packetID = data.readShort();
        final ModernPacket packet = PacketHandler.packetlist.get(packetID).template();
        packet.setDebugId(data.readInt());
        packet.readData(data);
        PacketHandler.onPacketData(packet, player);
    }

    private static void onPacketData(ModernPacket packet, final EntityPlayer player) {
        try {
            packet.processPacket(player);
            if (LPConstants.DEBUG) {
                PacketHandler.debugMap.remove(packet.getDebugId());
            }
        } catch (DelayPacketException e) {
            if (packet.retry() && MainProxy.isClient(player.getEntityWorld())) {
                SimpleServiceLocator.clientBufferHandler.queueFailedPacket(packet, player);
            } else if (LPConstants.DEBUG) {
                LogisticsPipes.log.error(packet.getClass().getName());
                LogisticsPipes.log.error(packet.toString());
                e.printStackTrace();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
