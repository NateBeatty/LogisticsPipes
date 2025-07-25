package logisticspipes.modules;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.DelayQueue;

import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.blocks.crafting.LogisticsCraftingTableTileEntity;
import logisticspipes.interfaces.*;
import logisticspipes.interfaces.routing.*;
import logisticspipes.items.ItemUpgrade;
import logisticspipes.logistics.LogisticsManager;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.modules.abstractmodules.LogisticsGuiModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractguis.ModuleCoordinatesGuiProvider;
import logisticspipes.network.abstractguis.ModuleInHandGuiProvider;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.guis.module.inhand.CraftingModuleInHand;
import logisticspipes.network.guis.module.inpipe.CraftingModuleSlot;
import logisticspipes.network.packets.block.CraftingPipeNextAdvancedSatellitePacket;
import logisticspipes.network.packets.block.CraftingPipePrevAdvancedSatellitePacket;
import logisticspipes.network.packets.cpipe.*;
import logisticspipes.network.packets.hud.HUDStartModuleWatchingPacket;
import logisticspipes.network.packets.hud.HUDStopModuleWatchingPacket;
import logisticspipes.network.packets.pipe.*;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.PipeFluidSatellite;
import logisticspipes.pipes.PipeItemsCraftingLogistics;
import logisticspipes.pipes.PipeItemsSatelliteLogistics;
import logisticspipes.pipes.PipeLogisticsChassi;
import logisticspipes.pipes.PipeLogisticsChassi.ChassiTargetInformation;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.CoreRoutedPipe.ItemSendMode;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.proxy.interfaces.ICraftingRecipeProvider;
import logisticspipes.proxy.interfaces.IFuzzyRecipeProvider;
import logisticspipes.request.*;
import logisticspipes.request.resources.DictResource;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.routing.*;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.utils.*;
import logisticspipes.utils.CacheHolder.CacheTypes;
import logisticspipes.utils.SinkReply.BufferMode;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.tuples.Pair;
import lombok.Getter;

public class ModuleCrafter extends LogisticsGuiModule implements ICraftItems, IHUDModuleHandler, IModuleWatchReciver {

    private PipeItemsCraftingLogistics _pipe;

    private IRequestItems _invRequester;
    // private ForgeDirection _sneakyDirection = ForgeDirection.UNKNOWN;

    public int satelliteId = 0;
    public int[] advancedSatelliteIdArray = new int[9];
    public DictResource[] fuzzyCraftingFlagArray = new DictResource[9];
    public DictResource outputFuzzyFlags = new DictResource(null, null);
    public int priority = 0;

    // from PipeItemsCraftingLogistics
    protected ItemIdentifierInventory _dummyInventory = new ItemIdentifierInventory(11, "Requested items", 127);
    protected ItemIdentifierInventory _liquidInventory = new ItemIdentifierInventory(
            ItemUpgrade.MAX_LIQUID_CRAFTER,
            "Fluid items",
            1,
            true);
    protected ItemIdentifierInventory _cleanupInventory = new ItemIdentifierInventory(
            ItemUpgrade.MAX_CRAFTING_CLEANUP * 3,
            "Cleanup Filer Items",
            1);

    protected int[] amount = new int[ItemUpgrade.MAX_LIQUID_CRAFTER];
    public int[] liquidSatelliteIdArray = new int[ItemUpgrade.MAX_LIQUID_CRAFTER];
    public int liquidSatelliteId = 0;

    public boolean[] craftingSigns = new boolean[6];
    public boolean waitingForCraft = false;

    private Map<ItemIdentifier, Integer> readyToRerequest = new HashMap();

    public int getReadyToRerequest(ItemIdentifier item) {
        return readyToRerequest.getOrDefault(item, 0);
    }

    public void setReadyToRerequest(ItemIdentifier item, int amount) {
        if (amount == 0 && readyToRerequest.containsKey(item)) {
            readyToRerequest.remove(item);
        } else {
            readyToRerequest.put(item, amount);
        }
    }

    private WeakReference<TileEntity> lastAccessedCrafter = new WeakReference<>(null);

    public boolean cleanupModeIsExclude = true;
    // for reliable transport
    protected final DelayQueue<DelayedGeneric<Pair<ItemIdentifierStack, IAdditionalTargetInformation>>> _lostItems = new DelayQueue<>();

    protected final PlayerCollectionList localModeWatchers = new PlayerCollectionList();

    public ModuleCrafter() {
        for (int i = 0; i < fuzzyCraftingFlagArray.length; i++) {
            fuzzyCraftingFlagArray[i] = new DictResource(null, null);
        }
    }

    public ModuleCrafter(PipeItemsCraftingLogistics parent) {
        _pipe = parent;
        _service = parent;
        _invRequester = parent;
        _world = parent;
        registerPosition(ModulePositionType.IN_PIPE, 0);
        for (int i = 0; i < fuzzyCraftingFlagArray.length; i++) {
            fuzzyCraftingFlagArray[i] = new DictResource(null, null);
        }
    }

    /**
     * assumes that the invProvider is also IRequest items.
     */
    @Override
    public void registerHandler(IWorldProvider world, IPipeServiceProvider service) {
        super.registerHandler(world, service);
        _invRequester = (IRequestItems) service;
    }

    protected SinkReply _sinkReply;

    @Override
    public void registerPosition(ModulePositionType slot, int positionInt) {
        super.registerPosition(slot, positionInt);
        _sinkReply = new SinkReply(
                FixedPriority.ItemSink,
                0,
                true,
                false,
                1,
                0,
                new ChassiTargetInformation(getPositionInt()));
    }

    public void requestPartial(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        RequestTree.requestPartial(item, (CoreRoutedPipe) _service, info);
    }

    @Override
    public SinkReply sinksItem(ItemIdentifier item, int bestPriority, int bestCustomPriority, boolean allowDefault,
            boolean includeInTransit) {
        if (bestPriority > _sinkReply.fixedPriority.ordinal() || (bestPriority == _sinkReply.fixedPriority.ordinal()
                && bestCustomPriority >= _sinkReply.customPriority)) {
            return null;
        }
        /*
         * _service.getRouter().getPipe() .UpdatePendingRouting(item, maxRequest(item),
         * _service.getRouter().getPipe().getRouterId());
         */

        return new SinkReply(
                _sinkReply,
                spaceFor(item, includeInTransit),
                areAllOrderesToBuffer() ? BufferMode.DESTINATION_BUFFERED : BufferMode.NONE);
    }

    public boolean networkHasItemsForCraft2(IOrderInfoProvider result) {
        IRouter r = SimpleServiceLocator.routerManager.getRouter(result.getRouterId());

        for (int i = 0; i < 9; i++) {

            ItemIdentifierStack itemStack = _dummyInventory.getIDStackInSlot(i);
            if (itemStack == null) {
                continue;
            }

            System.out.println(_dummyInventory.getIDStackInSlot(i).getFriendlyName());

            int currentlyHas = currentlyHas(itemStack.getItem());
            int storedInNetwork = SimpleServiceLocator.logisticsManager.getAvailableItems(r.getIRoutersByCost())
                    .getOrDefault(itemStack.getItem(), 0);

            if (currentlyHas + storedInNetwork < itemStack.getStackSize()) {
                return false;
            }
        }
        return true;
    }

    public boolean networkHasItemsForCraft() {
        IRouter r = getRouter();

        for (int i = 0; i < 9; i++) {
            ItemIdentifierStack itemStack = _dummyInventory.getIDStackInSlot(i);
            if (itemStack == null) {
                continue;
            }

            int storedInNetwork = SimpleServiceLocator.logisticsManager.getAvailableItems(r.getIRoutersByCost())
                    .getOrDefault(itemStack.getItem(), 0);

            if (storedInNetwork < itemStack.getStackSize()) {
                return false;
            }
        }
        return true;
    }

    public int getReadilyAvailiable(ItemIdentifier item) {
        IRouter r = getRouter();
        int storedInNetwork = SimpleServiceLocator.logisticsManager.getAvailableItems(r.getIRoutersByCost())
                .getOrDefault(item, 0);
        System.out.println("Network has: " + storedInNetwork);
        return storedInNetwork;
    }

    public int currentlyHas(ItemIdentifier item) {
        int inInventory = 0;

        WorldUtil wUtil = new WorldUtil(getWorld(), _service.getX(), _service.getY(), _service.getZ());
        TileEntity tile = wUtil.getAdjacentTileEntitie(_service.getRouter().getPipe().getPointedOrientation());

        IInventory base = (IInventory) tile;
        if (base instanceof net.minecraft.inventory.ISidedInventory) {
            base = new SidedInventoryMinecraftAdapter(
                    (net.minecraft.inventory.ISidedInventory) base,
                    _service.getRouter().getPipe().getPointedOrientation().getOpposite(),
                    false);
        }
        ForgeDirection dir = _service.getRouter().getPipe().getPointedOrientation();
        if (getUpgradeManager().hasSneakyUpgrade()) {
            dir = getUpgradeManager().getSneakyOrientation(); // Nx untested
        }
        IInventoryUtil inv = SimpleServiceLocator.inventoryUtilFactory.getInventoryUtil(base, dir);

        if (inv == null) {
            return -1;
        }
        Map<ItemIdentifier, Integer> inventoryMaterials = inv.getItemsAndCount();
        if (inventoryMaterials.containsKey(item)) {
            inInventory = inventoryMaterials.get(item);
        }
        return inInventory;
    }

    public int maxRequest(ItemIdentifier item) {
        int x = 0;
        Map<ItemIdentifier, Integer> craftMaterials = _dummyInventory.getItemsAndCount();
        if (!craftMaterials.containsKey(item)) {
            return 0;
        }

        x = Math.max(item.getMaxStackSize(), craftMaterials.get(item));
        int a = currentlyHas(item);
        if (a == -1) {
            return 0;
        }

        int b = _service.countOnRoute(item);
        int c = _service.getRouter().getPipe().getPendingRouting(item);
        // int d = _service.getr
        /*
         * System.out.println( "Routing : " + x + " | " + a + " | " + b + " | " + c + " | " + (x - a - b - c) + " | " +
         * item.getFriendlyName());
         */
        return x - a - b - c;
    }

    protected int spaceFor(ItemIdentifier item, boolean includeInTransit) {
        Pair<String, ItemIdentifier> key = new Pair<>("spaceFor", item);
        Object cache = _service.getCacheHolder().getCacheFor(CacheTypes.Inventory, key);
        if (cache != null) {
            int count = (Integer) cache;
            if (includeInTransit) {
                count -= _service.countOnRoute(item);
            }
            // return count;
        }
        int count = 0;
        WorldUtil wUtil = new WorldUtil(getWorld(), _service.getX(), _service.getY(), _service.getZ());
        for (AdjacentTile tile : wUtil.getAdjacentTileEntities(true)) {
            if (!(tile.tile instanceof IInventory)) {
                continue;
            }
            IInventory base = (IInventory) tile.tile;
            if (base instanceof net.minecraft.inventory.ISidedInventory) {
                base = new SidedInventoryMinecraftAdapter(
                        (net.minecraft.inventory.ISidedInventory) base,
                        tile.orientation.getOpposite(),
                        false);
            }
            ForgeDirection dir = tile.orientation;
            if (getUpgradeManager().hasSneakyUpgrade()) {
                dir = getUpgradeManager().getSneakyOrientation();
            }
            IInventoryUtil inv = SimpleServiceLocator.inventoryUtilFactory.getInventoryUtil(base, dir);
            count += inv.roomForItem(item, 9999);
        }
        _service.getCacheHolder().setCache(CacheTypes.Inventory, key, count);
        if (includeInTransit) {
            count -= _service.countOnRoute(item);
        }
        // return count;
        return maxRequest(item);
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public LogisticsModule getSubModule(int slot) {
        return null;
    }

    public void onAllowedRemoval() {}

    public void Hello() {
        System.out.println("Hello");
    }

    @Override
    public void tick() {
        Map<ItemIdentifier, Integer> craftMaterials = _dummyInventory.getItemsAndCount();

        int counter = 0;
        for (ItemIdentifier key : craftMaterials.keySet()) {
            if (key.getFriendlyName().equals("Iron Ingot")) {
                // System.out.println("IRON!");
            }
            if (counter == 1) {
                // System.out.println(getCraftedItem().getItem() + " | " + key + " | " + _service.countOnRoute(key));
            }
            counter++;
        }

        if (!overflowedItems.isEmpty()) {
            // System.out.println("R|M: " + getRouter() + " | " + getPositionInt() + " is still requesting");
        }

        enabledUpdateEntity();

        /*
         * if (_lostItems.isEmpty()) { return; } DelayedGeneric<Pair<ItemIdentifierStack, IAdditionalTargetInformation>>
         * lostItem = _lostItems.poll(); int rerequested = 0; while (lostItem != null && rerequested < 100) {
         * Pair<ItemIdentifierStack, IAdditionalTargetInformation> pair = lostItem.get(); if
         * (_service.getItemOrderManager().hasOrders(ResourceType.CRAFTING)) { // System.out.println("En route: " +
         * _service.countOnRoute(pair.getValue1().getItem())); SinkReply reply = LogisticsManager .canSink(getRouter(),
         * null, true, pair.getValue1().getItem(), null, true, true); if (reply == null || reply.maxNumberOfItems < 1) {
         * _lostItems.add(new DelayedGeneric<>(pair, 9000 + (int) (Math.random() * 2000))); lostItem =
         * _lostItems.poll(); continue; } } int received = RequestTree.requestPartial(pair.getValue1(), (CoreRoutedPipe)
         * _service, pair.getValue2()); System.out.println("ITEM LOST"); //
         * getRouter().getPipe().UpdatePendingRouting(pair.getValue1().getItem(), -1 * //
         * pair.getValue1().getStackSize(), getRouter().getPipe().getRouterId()); rerequested++; if (received <
         * pair.getValue1().getStackSize()) { pair.getValue1().setStackSize(pair.getValue1().getStackSize() - received);
         * _lostItems.add(new DelayedGeneric<>(pair, 4500 + (int) (Math.random() * 1000))); } lostItem =
         * _lostItems.poll(); } if (lostItem != null) { Pair<ItemIdentifierStack, IAdditionalTargetInformation> pair =
         * lostItem.get(); _lostItems.add(new DelayedGeneric<>(pair, 9000 + (int) (Math.random() * 2000))); }
         */
    }

    // private List<Pair<ItemIdentifierStack, IAdditionalTargetInformation>> overflow = new ArrayList<>();

    // private Map<Pair<ItemIdentifier, IAdditionalTargetInformation>, Integer> overflowedItems = new HashMap();

    // private Map<ItemIdentifier, Pair<Integer, IAdditionalTargetInformation>> overflowedItems = new HashMap();

    private Map<ItemIdentifier, Integer> overflowedItems = new HashMap<>();

    private Map<ItemIdentifier, Integer> requestableItems = new HashMap<>();

    private Map<ItemIdentifier, Integer> requestableItems2 = new HashMap<>();

    public void makeOverflowedItemRequestable(ItemIdentifierStack stack) {
        System.out.println("making overflowed items requestable");
        UpdateOverflowedItems(stack.getItem(), -1 * stack.getStackSize());
        UpdateRequestableItems(stack.getItem(), stack.getStackSize());
    }

    public void setOverflowedAndRequestable(ItemIdentifier item, int stackSize) {
        int readilyAvailable = Math.min(getReadilyAvailiable(item), stackSize);
        int remainingStackSize = stackSize - readilyAvailable;
        System.out.println("Setting overflowed and requestable " + remainingStackSize + " | " + readilyAvailable);
        UpdateOverflowedItems(item, remainingStackSize);
        UpdateRequestableItems(item, readilyAvailable);
    }

    public boolean requestMoreItems() {
        System.out.println("Attempting to request more items");
        testfunction2();
        boolean thisModuleRequests = false;
        if (!networkHasItemsForCraft()) {
            return false;
        }
        IRouter r = getRouter();
        for (ItemIdentifier key : requestableItems.keySet()) {
            PipeLogisticsChassi pipe = ((PipeLogisticsChassi) (_service.getRouter().getPipe()));
            System.out.println("key: " + key.getFriendlyName() + " | value: " + requestableItems.get(key));

            int amount = Math.min(maxRequest(key), requestableItems.get(key));

            ItemIdentifierStack newRequest = new ItemIdentifierStack(key, amount);
            IAdditionalTargetInformation info = new ChassiTargetInformation(getPositionInt());

            SinkReply reply = LogisticsManager.canSink(getRouter(), null, true, key, null, true, true);

            if (reply == null || reply.maxNumberOfItems < 1) {
                System.out.println("Request error");
            } else {
                RequestTree.requestPartial(newRequest, (CoreRoutedPipe) _service, info);
                UpdateRequestableItems(key, -1 * amount);
                System.out.println("Requesting new batch: " + " | " + key.getFriendlyName() + " | " + amount);
                thisModuleRequests = true;
                pipe.currentBulkCraftingAmount += amount;
            }

        }
        return thisModuleRequests;
    }

    public void requestCraft() {
        for (int i = 0; i < 9; i++) {
            ItemIdentifierStack itemStack = _dummyInventory.getIDStackInSlot(i).clone();
            IAdditionalTargetInformation info = new ChassiTargetInformation(getPositionInt());
            RequestTree.requestPartial(itemStack, (CoreRoutedPipe) _service, info);
        }
    }

    public boolean requestMoreItems2() {
        boolean thisModuleRequests = false;
        if (requestableItems.isEmpty() || !networkHasItemsForCraft()) {
            return false;
        }
        requestCraft();
        return false;

    }

    public void sendItemToStorage(ItemIdentifierStack item) {
        List<Integer> jamList = new LinkedList<>();
        ItemIdentifier itemIdentifier = item.getItem();
        Pair<Integer, SinkReply> reply = _service.hasDestination(itemIdentifier, true, jamList);
        if (reply == null) {
            return;
        }
        ItemStack stackToSend = item.makeNormalStack();
        _service.sendStack(stackToSend, reply, ItemSendMode.Normal);
    }

    public void UpdateOverflowedItems(ItemIdentifierStack stack) {
        // System.out.println("Overflow Count: " + stack.getStackSize());
        UpdateOverflowedItems(stack.getItem(), stack.getStackSize());
    }

    public void UpdateOverflowedItems(ItemIdentifier stack, int stackSize) {
        int oldAmount = getOverflowedItemCount(stack);
        // System.out.println("Attempting to update: " + stack.getFriendlyName() + " | " + stackSize + " | ");
        setOverflowedItems(stack, oldAmount + stackSize);
    }

    private int getOverflowedItemCount(ItemIdentifier item) {
        return overflowedItems.getOrDefault(item, 0);
    }

    private void setOverflowedItems(ItemIdentifier item, int amount) {
        if (amount == 0 && overflowedItems.containsKey(item)) {
            overflowedItems.remove(item);
        } else {
            overflowedItems.put(item, amount);
        }
    }

    public void UpdateRequestableItems(ItemIdentifierStack stack) {
        UpdateRequestableItems(stack.getItem(), stack.getStackSize());
        loopFunction();
    }

    public void loopFunction() {
        System.out.println("Loop function running");
        for (ItemIdentifier key : requestableItems.keySet()) {
            System.out.println("key: " + key.getFriendlyName() + " | value: " + requestableItems.get(key));
        }
        for (Map.Entry<ItemIdentifier, Integer> entry : requestableItems.entrySet()) {
            ItemIdentifier key = entry.getKey();
            Integer value = entry.getValue();
            System.out.println("Key: " + key + ", Value: " + value);
        }
    }

    public void testfunc(ItemIdentifier stack, int stackSize) {
        requestableItems2.put(stack, stackSize);
        loopFunction();
    }

    public void UpdateRequestableItems(ItemIdentifier stack, int stackSize) {
        int oldAmount = getRequestableItemCount(stack);
        System.out.println("Stack: " + stack.getFriendlyName() + " | amount: " + stackSize);
        setRequestableItems(stack, oldAmount + stackSize);
    }

    private int getRequestableItemCount(ItemIdentifier item) {
        return requestableItems.getOrDefault(item, 0);
    }

    private void setRequestableItems(ItemIdentifier item, int amount) {
        if (amount == 0 && requestableItems.containsKey(item)) {
            requestableItems.remove(item);
        } else {
            requestableItems.put(item, amount);
        }
    }

    @Override
    public void itemArrived(ItemIdentifierStack item, IAdditionalTargetInformation info) {}

    @Override
    public void itemLost(ItemIdentifierStack item, IAdditionalTargetInformation info) {

        System.out.println("Tx36");

        // UpdateOverflowedItems(item);

        // System.out.println("itemLost: " + item.getFriendlyName());
        // _lostItems.add(new DelayedGeneric<>(new Pair<>(item, info), 5000));
    }

    public void crafterModulePing() {
        System.out.println(
                "PING PING PING PING PING PING PING PING PING PING PING PING PING PING PING PING PING PING PING");
        System.out.println("Router ID: " + getRouter().getPipe().getRouterId());
    }

    @Override
    public boolean hasGenericInterests() {
        return false;
    }

    @Override
    public Set<ItemIdentifier> getSpecificInterests() {
        List<ItemIdentifierStack> result = getCraftedItems();
        if (result == null) {
            return null;
        }
        Set<ItemIdentifier> l1 = new TreeSet<>();
        for (ItemIdentifierStack craftable : result) {
            l1.add(craftable.getItem());
        }
        /*
         * for(int i=0; i<9;i++) { ItemIdentifierStack stack = getMaterials(i); if(stack != null) {
         * l1.add(stack.getItem()); // needed to be interested in things for a chassi to report reliableDelivery
         * failure. } }
         */
        return l1;
    }

    @Override
    public boolean interestedInAttachedInventory() {
        return false;
        // when we are default we are interested in everything anyway, otherwise we're only interested in our filter.
    }

    @Override
    public boolean interestedInUndamagedID() {
        return false;
    }

    @Override

    public boolean recievePassive() {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/ModuleCrafter");
    }

    @Override
    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {
        if (!_service.getItemOrderManager().hasExtras() || tree.hasBeenQueried(_service.getItemOrderManager())) {
            return;
        }

        IResource requestedItem = tree.getRequestType();

        if (!canCraft(requestedItem)) {
            return;
        }

        for (IFilter filter : filters) {
            if (filter.isBlocked() == filter.isFilteredItem(requestedItem) || filter.blockProvider()) {
                return;
            }
        }
        int remaining = 0;
        for (LogisticsItemOrder extra : _service.getItemOrderManager()) {
            if (extra.getType() == ResourceType.EXTRA) {
                if (extra.getResource().getItem().equals(requestedItem)) {
                    remaining += extra.getResource().stack.getStackSize();
                }
            }
        }
        remaining -= root.getAllPromissesFor(this, getCraftedItem().getItem());
        if (remaining < 1) {
            return;
        }
        if (this.getUpgradeManager().isFuzzyUpgrade() && outputFuzzyFlags.getBitSet().nextSetBit(0) != -1) {
            DictResource dict = new DictResource(getCraftedItem(), null).loadFromBitSet(outputFuzzyFlags.getBitSet());
            LogisticsExtraDictPromise promise = new LogisticsExtraDictPromise(
                    dict,
                    Math.min(remaining, tree.getMissingAmount()),
                    this,
                    true);
            tree.addPromise(promise);
        } else {
            LogisticsExtraPromise promise = new LogisticsExtraPromise(
                    getCraftedItem().getItem(),
                    Math.min(remaining, tree.getMissingAmount()),
                    this,
                    true);
            tree.addPromise(promise);
        }
        tree.setQueried(_service.getItemOrderManager());
    }

    @Override
    public LogisticsItemOrder fullFill(LogisticsPromise promise, IRequestItems destination,
            IAdditionalTargetInformation info) {

        if (promise instanceof LogisticsExtraDictPromise) {
            _service.getItemOrderManager().removeExtras(((LogisticsExtraDictPromise) promise).getResource());
        }
        if (promise instanceof LogisticsExtraPromise) {
            _service.getItemOrderManager()
                    .removeExtras(new DictResource(new ItemIdentifierStack(promise.item, promise.numberOfItems), null));
        }
        if (promise instanceof LogisticsDictPromise) {
            _service.spawnParticle(Particles.WhiteParticle, 2);
            return _service.getItemOrderManager()
                    .addOrder(((LogisticsDictPromise) promise).getResource(), destination, ResourceType.CRAFTING, info);
        }
        _service.spawnParticle(Particles.WhiteParticle, 2);
        return _service.getItemOrderManager().addOrder(
                new ItemIdentifierStack(promise.item, promise.numberOfItems),
                destination,
                ResourceType.CRAFTING,
                info);
    }

    @Override
    public void getAllItems(Map<ItemIdentifier, Integer> list, List<IFilter> filter) {}

    @Override
    public IRouter getRouter() {
        return _service.getRouter();
    }

    @Override
    public void itemCouldNotBeSend(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        // System.out.println("Could not send");
        _invRequester.itemCouldNotBeSend(item, info);
    }

    @Override
    public int getID() {
        return _service.getRouter().getSimpleID();
    }

    @Override
    public int compareTo(IRequestItems value2) {
        return 0;
    }

    @Override
    public void registerExtras(IPromise promise) {
        if (promise instanceof LogisticsDictPromise) {
            _service.getItemOrderManager().addExtra(((LogisticsDictPromise) promise).getResource());
        } else {
            ItemIdentifierStack stack = new ItemIdentifierStack(promise.getItemType(), promise.getAmount());
            _service.getItemOrderManager().addExtra(new DictResource(stack, null));
        }
    }

    @Override
    public ICraftingTemplate addCrafting(IResource toCraft) {
        List<ItemIdentifierStack> stack = getCraftedItems();
        if (stack == null) {
            return null;
        }
        IReqCraftingTemplate template = null;
        if (this.getUpgradeManager().isFuzzyUpgrade() && outputFuzzyFlags.getBitSet().nextSetBit(0) != -1) {
            if (toCraft instanceof DictResource) {
                for (ItemIdentifierStack craftable : stack) {
                    DictResource dict = new DictResource(craftable, null);
                    dict.loadFromBitSet(outputFuzzyFlags.getBitSet());
                    if (toCraft.matches(craftable.getItem(), IResource.MatchSettings.NORMAL)
                            && dict.matches(((DictResource) toCraft).getItem(), IResource.MatchSettings.NORMAL)
                            && dict.getBitSet().equals(((DictResource) toCraft).getBitSet())) {
                        template = new DictCraftingTemplate(dict, this, priority);
                        break;
                    }
                }
            }
        } else {
            for (ItemIdentifierStack craftable : stack) {
                if (toCraft.matches(craftable.getItem(), IResource.MatchSettings.NORMAL)) {
                    template = new ItemCraftingTemplate(craftable, this, priority);
                    break;
                }
            }
        }
        if (template == null) {
            return null;
        }

        IRequestItems[] target = new IRequestItems[9];
        for (int i = 0; i < 9; i++) {
            target[i] = this;
        }

        boolean hasSatellite = isSatelliteConnected();
        if (!hasSatellite) {
            return null;
        }
        if (!getUpgradeManager().isAdvancedSatelliteCrafter()) {
            if (satelliteId != 0) {
                IRouter r = getSatelliteRouter(-1);
                if (r != null) {
                    IRequestItems sat = r.getPipe();
                    for (int i = 6; i < 9; i++) {
                        target[i] = sat;
                    }
                }
            }
        } else {
            for (int i = 0; i < 9; i++) {
                if (advancedSatelliteIdArray[i] != 0) {
                    IRouter r = getSatelliteRouter(i);
                    if (r != null) {
                        target[i] = r.getPipe();
                    }
                }
            }
        }

        // Check all materials
        for (int i = 0; i < 9; i++) {
            ItemIdentifierStack resourceStack = getMaterials(i);
            if (resourceStack == null || resourceStack.getStackSize() == 0) {
                continue;
            }
            IResource req;
            if (getUpgradeManager().isFuzzyUpgrade() && fuzzyCraftingFlagArray[i].getBitSet().nextSetBit(0) != -1) {
                DictResource dict;
                req = dict = new DictResource(resourceStack, target[i]);
                dict.loadFromBitSet(fuzzyCraftingFlagArray[i].getBitSet());
            } else {
                req = new ItemResource(resourceStack, target[i]);
            }
            int routerID = _service.getRouter().getPipe().getRouterId();
            template.addRequirement(req, new CraftingChassieInformation(routerID, i, getPositionInt()));
        }

        int liquidCrafter = getUpgradeManager().getFluidCrafter();
        IRequestFluid[] liquidTarget = new IRequestFluid[liquidCrafter];

        if (!getUpgradeManager().isAdvancedSatelliteCrafter()) {
            if (liquidSatelliteId != 0) {
                IRouter r = getFluidSatelliteRouter(-1);
                if (r != null) {
                    IRequestFluid sat = (IRequestFluid) r.getPipe();
                    for (int i = 0; i < liquidCrafter; i++) {
                        liquidTarget[i] = sat;
                    }
                }
            }
        } else {
            for (int i = 0; i < liquidCrafter; i++) {
                if (liquidSatelliteIdArray[i] != 0) {
                    IRouter r = getFluidSatelliteRouter(i);
                    if (r != null) {
                        liquidTarget[i] = (IRequestFluid) r.getPipe();
                    }
                }
            }
        }

        for (int i = 0; i < liquidCrafter; i++) {
            FluidIdentifier liquid = getFluidMaterial(i);
            int amount = getFluidAmount()[i];
            if (liquid == null || amount <= 0 || liquidTarget[i] == null) {
                continue;
            }
            template.addRequirement(new FluidResource(liquid, amount, liquidTarget[i]), null);
        }

        if (getUpgradeManager().hasByproductExtractor() && getByproductItem() != null) {
            template.addByproduct(getByproductItem());
        }
        // MinecraftServer.getServer().addChatMessage(new ChatComponentText("Apply recipe?"));
        // System.out.println("Tx9" + getRouter());
        // Prints very frequently
        return template;
    }

    protected ISlotUpgradeManager getUpgradeManager() {
        if (_service == null) {
            return null;
        }
        return _service.getUpgradeManager(slot, positionInt);
    }

    public boolean isSatelliteConnected() {
        final List<ExitRoute> routes = getRouter().getIRoutersByCost();
        if (!getUpgradeManager().isAdvancedSatelliteCrafter()) {
            if (satelliteId == 0) {
                return true;
            }
            for (final PipeItemsSatelliteLogistics satellite : PipeItemsSatelliteLogistics.AllSatellites) {
                if (satellite.satelliteId == satelliteId) {
                    if (satellite.stillNeedReplace() || satellite.getRouter() == null) {
                        continue;
                    }
                    IRouter satRouter = satellite.getRouter();
                    for (ExitRoute route : routes) {
                        if (route.destination == satRouter) {
                            return true;
                        }
                    }
                }
            }
        } else {
            boolean foundAll = true;
            for (int i = 0; i < 9; i++) {
                boolean foundOne = false;
                if (advancedSatelliteIdArray[i] == 0) {
                    continue;
                }
                for (final PipeItemsSatelliteLogistics satellite : PipeItemsSatelliteLogistics.AllSatellites) {
                    if (satellite.satelliteId == advancedSatelliteIdArray[i]) {
                        if (satellite.stillNeedReplace() || satellite.getRouter() == null) {
                            continue;
                        }
                        IRouter satRouter = satellite.getRouter();
                        for (ExitRoute route : routes) {
                            if (route.destination == satRouter) {
                                foundOne = true;
                                break;
                            }
                        }
                    }
                }
                foundAll &= foundOne;
            }
            return foundAll;
        }
        // TODO check for FluidCrafter
        return false;
    }

    @Override
    public boolean canCraft(IResource toCraft) {
        if (getCraftedItem() == null) {
            return false;
        }
        if (toCraft instanceof ItemResource || toCraft instanceof DictResource) {
            return toCraft.matches(getCraftedItem().getItem(), IResource.MatchSettings.NORMAL);
        }
        return false;
    }

    @Override
    public List<ItemIdentifierStack> getCraftedItems() {
        List<ItemIdentifierStack> list = new ArrayList<>(1);
        if (getCraftedItem() != null) {
            list.add(getCraftedItem());
        }
        return list;
    }

    public ItemIdentifierStack getCraftedItem() {
        return _dummyInventory.getIDStackInSlot(9);
    }

    @Override
    public int getTodo() {
        return _service.getItemOrderManager().totalAmountCountInAllOrders();
    }

    protected int getNextConnectSatelliteId(boolean prev, int x) {
        int closestIdFound = prev ? 0 : Integer.MAX_VALUE;
        if (_service == null) {
            return prev ? Math.max(0, satelliteId - 1) : satelliteId + 1;
        }
        for (final PipeItemsSatelliteLogistics satellite : PipeItemsSatelliteLogistics.AllSatellites) {
            if (satellite == null || satellite.stillNeedReplace()
                    || satellite.getRouter() == null
                    || satellite.isFluidPipe()) {
                continue;
            }
            IRouter satRouter = satellite.getRouter();
            List<ExitRoute> routes = getRouter().getDistanceTo(satRouter);
            if (routes != null && !routes.isEmpty()) {
                boolean filterFree = false;
                for (ExitRoute route : routes) {
                    if (route.filters.isEmpty()) {
                        filterFree = true;
                        break;
                    }
                }
                if (!filterFree) {
                    continue;
                }
                if (x == -1) {
                    if (!prev && satellite.satelliteId > satelliteId && satellite.satelliteId < closestIdFound) {
                        closestIdFound = satellite.satelliteId;
                    } else if (prev && satellite.satelliteId < satelliteId && satellite.satelliteId > closestIdFound) {
                        closestIdFound = satellite.satelliteId;
                    }
                } else {
                    if (!prev && satellite.satelliteId > advancedSatelliteIdArray[x]
                            && satellite.satelliteId < closestIdFound) {
                        closestIdFound = satellite.satelliteId;
                    } else if (prev && satellite.satelliteId < advancedSatelliteIdArray[x]
                            && satellite.satelliteId > closestIdFound) {
                                closestIdFound = satellite.satelliteId;
                            }
                }
            }
        }
        if (closestIdFound == Integer.MAX_VALUE) {
            if (x == -1) {
                return satelliteId;
            } else {
                return advancedSatelliteIdArray[x];
            }
        }
        return closestIdFound;
    }

    protected int getNextConnectFluidSatelliteId(boolean prev, int x) {
        int closestIdFound = prev ? 0 : Integer.MAX_VALUE;
        for (final PipeFluidSatellite satellite : PipeFluidSatellite.AllSatellites) {
            if (satellite == null || satellite.stillNeedReplace()
                    || satellite.getRouter() == null
                    || !((CoreRoutedPipe) satellite).isFluidPipe()) {
                continue;
            }
            IRouter satRouter = satellite.getRouter();
            List<ExitRoute> routes = getRouter().getDistanceTo(satRouter);
            if (routes != null && !routes.isEmpty()) {
                boolean filterFree = false;
                for (ExitRoute route : routes) {
                    if (route.filters.isEmpty()) {
                        filterFree = true;
                        break;
                    }
                }
                if (!filterFree) {
                    continue;
                }
                if (x == -1) {
                    if (!prev && satellite.satelliteId > liquidSatelliteId && satellite.satelliteId < closestIdFound) {
                        closestIdFound = satellite.satelliteId;
                    } else if (prev && satellite.satelliteId < liquidSatelliteId
                            && satellite.satelliteId > closestIdFound) {
                                closestIdFound = satellite.satelliteId;
                            }
                } else {
                    if (!prev && satellite.satelliteId > liquidSatelliteIdArray[x]
                            && satellite.satelliteId < closestIdFound) {
                        closestIdFound = satellite.satelliteId;
                    } else if (prev && satellite.satelliteId < liquidSatelliteIdArray[x]
                            && satellite.satelliteId > closestIdFound) {
                                closestIdFound = satellite.satelliteId;
                            }
                }
            }
        }
        if (closestIdFound == Integer.MAX_VALUE) {
            if (x == -1) {
                return liquidSatelliteId;
            } else {
                return liquidSatelliteIdArray[x];
            }
        }
        return closestIdFound;
    }

    public void setNextSatellite(EntityPlayer player) {
        if (MainProxy.isClient(player.worldObj)) {
            final CoordinatesPacket packet = PacketHandler.getPacket(CPipeNextSatellite.class).setModulePos(this);
            MainProxy.sendPacketToServer(packet);
        } else {
            satelliteId = getNextConnectSatelliteId(false, -1);
            final CoordinatesPacket packet = PacketHandler.getPacket(CPipeSatelliteId.class).setPipeId(satelliteId)
                    .setModulePos(this);
            MainProxy.sendPacketToPlayer(packet, player);
        }
    }

    // This is called by the packet PacketCraftingPipeSatelliteId
    public void setSatelliteId(int satelliteId, int x) {
        if (x == -1) {
            this.satelliteId = satelliteId;
        } else {
            advancedSatelliteIdArray[x] = satelliteId;
        }
    }

    public void setPrevSatellite(EntityPlayer player) {
        if (MainProxy.isClient(player.worldObj)) {
            final CoordinatesPacket packet = PacketHandler.getPacket(CPipePrevSatellite.class).setModulePos(this);
            MainProxy.sendPacketToServer(packet);
        } else {
            satelliteId = getNextConnectSatelliteId(true, -1);
            final CoordinatesPacket packet = PacketHandler.getPacket(CPipeSatelliteId.class).setPipeId(satelliteId)
                    .setModulePos(this);
            MainProxy.sendPacketToPlayer(packet, player);
        }
    }

    public IRouter getSatelliteRouter(int x) {
        if (x == -1) {
            for (final PipeItemsSatelliteLogistics satellite : PipeItemsSatelliteLogistics.AllSatellites) {
                if (satellite.satelliteId == satelliteId) {
                    if (satellite.stillNeedReplace() || satellite.getRouter() == null) {
                        continue;
                    }
                    return satellite.getRouter();
                }
            }
        } else {
            for (final PipeItemsSatelliteLogistics satellite : PipeItemsSatelliteLogistics.AllSatellites) {
                if (satellite.satelliteId == advancedSatelliteIdArray[x]) {
                    if (satellite.stillNeedReplace() || satellite.getRouter() == null) {
                        continue;
                    }
                    return satellite.getRouter();
                }
            }
        }
        return null;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        // super.readFromNBT(nbttagcompound);
        _dummyInventory.readFromNBT(nbttagcompound, "");
        _liquidInventory.readFromNBT(nbttagcompound, "FluidInv");
        _cleanupInventory.readFromNBT(nbttagcompound, "CleanupInv");
        satelliteId = nbttagcompound.getInteger("satelliteid");

        priority = nbttagcompound.getInteger("priority");
        for (int i = 0; i < 9; i++) {
            advancedSatelliteIdArray[i] = nbttagcompound.getInteger("advancedSatelliteId" + i);
        }
        if (nbttagcompound.hasKey("fuzzyCraftingFlag0")) {
            for (int i = 0; i < 9; i++) {
                int flags = nbttagcompound.getByte("fuzzyCraftingFlag" + i);
                DictResource dict = fuzzyCraftingFlagArray[i];
                if ((flags & 0x1) != 0) {
                    dict.use_od = true;
                }
                if ((flags & 0x2) != 0) {
                    dict.ignore_dmg = true;
                }
                if ((flags & 0x4) != 0) {
                    dict.ignore_nbt = true;
                }
                if ((flags & 0x8) != 0) {
                    dict.use_category = true;
                }
            }
        }
        if (nbttagcompound.hasKey("fuzzyFlags")) {
            NBTTagList lst = nbttagcompound.getTagList("fuzzyFlags", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < 9; i++) {
                NBTTagCompound comp = lst.getCompoundTagAt(i);
                fuzzyCraftingFlagArray[i].ignore_dmg = comp.getBoolean("ignore_dmg");
                fuzzyCraftingFlagArray[i].ignore_nbt = comp.getBoolean("ignore_nbt");
                fuzzyCraftingFlagArray[i].use_od = comp.getBoolean("use_od");
                fuzzyCraftingFlagArray[i].use_category = comp.getBoolean("use_category");
            }
        }
        if (nbttagcompound.hasKey("outputFuzzyFlags")) {
            NBTTagCompound comp = nbttagcompound.getCompoundTag("outputFuzzyFlags");
            outputFuzzyFlags.ignore_dmg = comp.getBoolean("ignore_dmg");
            outputFuzzyFlags.ignore_nbt = comp.getBoolean("ignore_nbt");
            outputFuzzyFlags.use_od = comp.getBoolean("use_od");
            outputFuzzyFlags.use_category = comp.getBoolean("use_category");
        }
        for (int i = 0; i < 6; i++) {
            craftingSigns[i] = nbttagcompound.getBoolean("craftingSigns" + i);
        }
        if (nbttagcompound.hasKey("FluidAmount")) {
            amount = nbttagcompound.getIntArray("FluidAmount");
        }
        if (amount.length < ItemUpgrade.MAX_LIQUID_CRAFTER) {
            amount = new int[ItemUpgrade.MAX_LIQUID_CRAFTER];
        }
        for (int i = 0; i < ItemUpgrade.MAX_LIQUID_CRAFTER; i++) {
            liquidSatelliteIdArray[i] = nbttagcompound.getInteger("liquidSatelliteIdArray" + i);
        }
        for (int i = 0; i < ItemUpgrade.MAX_LIQUID_CRAFTER; i++) {
            liquidSatelliteIdArray[i] = nbttagcompound.getInteger("liquidSatelliteIdArray" + i);
        }
        liquidSatelliteId = nbttagcompound.getInteger("liquidSatelliteId");
        cleanupModeIsExclude = nbttagcompound.getBoolean("cleanupModeIsExclude");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        // super.writeToNBT(nbttagcompound);
        _dummyInventory.writeToNBT(nbttagcompound, "");
        _liquidInventory.writeToNBT(nbttagcompound, "FluidInv");
        _cleanupInventory.writeToNBT(nbttagcompound, "CleanupInv");
        nbttagcompound.setInteger("satelliteid", satelliteId);

        nbttagcompound.setInteger("priority", priority);
        for (int i = 0; i < 9; i++) {
            nbttagcompound.setInteger("advancedSatelliteId" + i, advancedSatelliteIdArray[i]);
        }
        NBTTagList lst = new NBTTagList();
        for (int i = 0; i < 9; i++) {
            NBTTagCompound comp = new NBTTagCompound();
            comp.setBoolean("ignore_dmg", fuzzyCraftingFlagArray[i].ignore_dmg);
            comp.setBoolean("ignore_nbt", fuzzyCraftingFlagArray[i].ignore_nbt);
            comp.setBoolean("use_od", fuzzyCraftingFlagArray[i].use_od);
            comp.setBoolean("use_category", fuzzyCraftingFlagArray[i].use_category);
            lst.appendTag(comp);
        }
        nbttagcompound.setTag("fuzzyFlags", lst);
        {
            NBTTagCompound comp = new NBTTagCompound();
            comp.setBoolean("ignore_dmg", outputFuzzyFlags.ignore_dmg);
            comp.setBoolean("ignore_nbt", outputFuzzyFlags.ignore_nbt);
            comp.setBoolean("use_od", outputFuzzyFlags.use_od);
            comp.setBoolean("use_category", outputFuzzyFlags.use_category);
            nbttagcompound.setTag("outputFuzzyFlags", comp);
        }
        for (int i = 0; i < 6; i++) {
            nbttagcompound.setBoolean("craftingSigns" + i, craftingSigns[i]);
        }
        for (int i = 0; i < ItemUpgrade.MAX_LIQUID_CRAFTER; i++) {
            nbttagcompound.setInteger("liquidSatelliteIdArray" + i, liquidSatelliteIdArray[i]);
        }
        nbttagcompound.setIntArray("FluidAmount", amount);
        nbttagcompound.setInteger("liquidSatelliteId", liquidSatelliteId);
        nbttagcompound.setBoolean("cleanupModeIsExclude", cleanupModeIsExclude);
    }

    public ModernPacket getCPipePacket() {
        return PacketHandler.getPacket(CraftingPipeUpdatePacket.class).setAmount(amount)
                .setLiquidSatelliteIdArray(liquidSatelliteIdArray).setLiquidSatelliteId(liquidSatelliteId)
                .setSatelliteId(satelliteId).setAdvancedSatelliteIdArray(advancedSatelliteIdArray).setPriority(priority)
                .setModulePos(this);
    }

    public void handleCraftingUpdatePacket(CraftingPipeUpdatePacket packet) {
        amount = packet.getAmount();
        liquidSatelliteIdArray = packet.getLiquidSatelliteIdArray();
        liquidSatelliteId = packet.getLiquidSatelliteId();
        satelliteId = packet.getSatelliteId();
        advancedSatelliteIdArray = packet.getAdvancedSatelliteIdArray();
        priority = packet.getPriority();
    }

    @Override
    protected ModuleCoordinatesGuiProvider getPipeGuiProvider() {

        return NewGuiHandler.getGui(CraftingModuleSlot.class)
                .setAdvancedSat(getUpgradeManager().isAdvancedSatelliteCrafter())
                .setLiquidCrafter(getUpgradeManager().getFluidCrafter()).setAmount(amount)
                .setHasByproductExtractor(getUpgradeManager().hasByproductExtractor())
                .setFuzzy(getUpgradeManager().isFuzzyUpgrade()).setCleanupSize(getUpgradeManager().getCrafterCleanup())
                .setCleanupExclude(cleanupModeIsExclude);
    }

    @Override
    protected ModuleInHandGuiProvider getInHandGuiProvider() {
        return NewGuiHandler.getGui(CraftingModuleInHand.class).setAmount(amount)
                .setCleanupExclude(cleanupModeIsExclude);
    }

    /**
     * Simply get the dummy inventory
     *
     * @return the dummy inventory
     */
    public ItemIdentifierInventory getDummyInventory() {
        return _dummyInventory;
    }

    public ItemIdentifierInventory getFluidInventory() {
        return _liquidInventory;
    }

    public IInventory getCleanupInventory() {
        return _cleanupInventory;
    }

    public void setDummyInventorySlot(int slot, ItemStack itemstack) {
        _dummyInventory.setInventorySlotContents(slot, itemstack);
        // Tx8
    }

    public void importFromCraftingTable(EntityPlayer player) {
        if (MainProxy.isClient(getWorld())) {
            // Send packet asking for import
            final CoordinatesPacket packet = PacketHandler.getPacket(CPipeSatelliteImport.class).setModulePos(this);
            MainProxy.sendPacketToServer(packet);
        } else {
            final WorldUtil worldUtil = new WorldUtil(getWorld(), getX(), getY(), getZ());
            for (final AdjacentTile tile : worldUtil.getAdjacentTileEntities(true)) {
                for (ICraftingRecipeProvider provider : SimpleServiceLocator.craftingRecipeProviders) {
                    if (provider.importRecipe(tile.tile, _dummyInventory)) {
                        if (provider instanceof IFuzzyRecipeProvider) {
                            ((IFuzzyRecipeProvider) provider).importFuzzyFlags(
                                    tile.tile,
                                    _dummyInventory,
                                    fuzzyCraftingFlagArray,
                                    outputFuzzyFlags);
                        }
                        break;
                    }
                }
            }
            // Send inventory as packet
            final CoordinatesPacket packet = PacketHandler.getPacket(CPipeSatelliteImportBack.class)
                    .setInventory(_dummyInventory).setModulePos(this);
            if (player != null) {
                MainProxy.sendPacketToPlayer(packet, player);
            }
            MainProxy.sendPacketToAllWatchingChunk(getX(), getZ(), MainProxy.getDimensionForWorld(getWorld()), packet);
        }
    }

    protected World getWorld() {
        return _world.getWorld();
    }

    public void priorityUp(EntityPlayer player) {
        priority++;
        if (MainProxy.isClient(player.worldObj)) {
            MainProxy
                    .sendPacketToServer(PacketHandler.getPacket(CraftingPipePriorityUpPacket.class).setModulePos(this));
        } else if (MainProxy.isServer(player.worldObj)) {
            MainProxy.sendPacketToPlayer(
                    PacketHandler.getPacket(CraftingPriority.class).setInteger(priority).setModulePos(this),
                    player);
        }
    }

    public void priorityDown(EntityPlayer player) {
        priority--;
        if (MainProxy.isClient(player.worldObj)) {
            MainProxy.sendPacketToServer(
                    PacketHandler.getPacket(CraftingPipePriorityDownPacket.class).setModulePos(this));
        } else if (MainProxy.isServer(player.worldObj)) {
            MainProxy.sendPacketToPlayer(
                    PacketHandler.getPacket(CraftingPriority.class).setInteger(priority).setModulePos(this),
                    player);
        }
    }

    public void setPriority(int amount) {
        priority = amount;
    }

    public ItemIdentifierStack getByproductItem() {
        return _dummyInventory.getIDStackInSlot(10);
    }

    public ItemIdentifierStack getMaterials(int slotnr) {
        return _dummyInventory.getIDStackInSlot(slotnr);
    }

    public FluidIdentifier getFluidMaterial(int slotnr) {
        ItemIdentifierStack stack = _liquidInventory.getIDStackInSlot(slotnr);
        if (stack == null) {
            return null;
        }
        return FluidIdentifier.get(stack.getItem());
    }

    public void setNextSatellite(EntityPlayer player, int i) {
        if (MainProxy.isClient(player.worldObj)) {
            MainProxy.sendPacketToServer(
                    PacketHandler.getPacket(CraftingPipeNextAdvancedSatellitePacket.class).setInteger(i)
                            .setModulePos(this));
        } else {
            advancedSatelliteIdArray[i] = getNextConnectSatelliteId(false, i);
            MainProxy.sendPacketToPlayer(
                    PacketHandler.getPacket(CraftingAdvancedSatelliteId.class).setInteger2(i)
                            .setInteger(advancedSatelliteIdArray[i]).setModulePos(this),
                    player);
        }
    }

    public void setPrevSatellite(EntityPlayer player, int i) {
        if (MainProxy.isClient(player.worldObj)) {
            MainProxy.sendPacketToServer(
                    PacketHandler.getPacket(CraftingPipePrevAdvancedSatellitePacket.class).setInteger(i)
                            .setModulePos(this));
        } else {
            advancedSatelliteIdArray[i] = getNextConnectSatelliteId(true, i);
            MainProxy.sendPacketToPlayer(
                    PacketHandler.getPacket(CraftingAdvancedSatelliteId.class).setInteger2(i)
                            .setInteger(advancedSatelliteIdArray[i]).setModulePos(this),
                    player);
        }
    }

    public void changeFluidAmount(int change, int slot, EntityPlayer player) {
        if (MainProxy.isClient(player.worldObj)) {
            MainProxy.sendPacketToServer(
                    PacketHandler.getPacket(FluidCraftingAmount.class).setInteger2(slot).setInteger(change)
                            .setModulePos(this));
        } else {
            amount[slot] += change;
            if (amount[slot] <= 0) {
                amount[slot] = 0;
            }
            MainProxy.sendPacketToPlayer(
                    PacketHandler.getPacket(FluidCraftingAmount.class).setInteger2(slot).setInteger(amount[slot])
                            .setModulePos(this),
                    player);
        }
    }

    public void setPrevFluidSatellite(EntityPlayer player, int i) {
        if (MainProxy.isClient(player.worldObj)) {
            MainProxy.sendPacketToServer(
                    PacketHandler.getPacket(FluidCraftingPipeAdvancedSatellitePrevPacket.class).setInteger(i)
                            .setModulePos(this));
        } else {
            if (i == -1) {
                liquidSatelliteId = getNextConnectFluidSatelliteId(true, i);
                MainProxy.sendPacketToPlayer(
                        PacketHandler.getPacket(FluidCraftingAdvancedSatelliteId.class).setInteger2(i)
                                .setInteger(liquidSatelliteId).setModulePos(this),
                        player);
            } else {
                liquidSatelliteIdArray[i] = getNextConnectFluidSatelliteId(true, i);
                MainProxy.sendPacketToPlayer(
                        PacketHandler.getPacket(FluidCraftingAdvancedSatelliteId.class).setInteger2(i)
                                .setInteger(liquidSatelliteIdArray[i]).setModulePos(this),
                        player);
            }
        }
    }

    public void setNextFluidSatellite(EntityPlayer player, int i) {
        if (MainProxy.isClient(player.worldObj)) {
            MainProxy.sendPacketToServer(
                    PacketHandler.getPacket(FluidCraftingPipeAdvancedSatelliteNextPacket.class).setInteger(i)
                            .setModulePos(this));
        } else {
            if (i == -1) {
                liquidSatelliteId = getNextConnectFluidSatelliteId(false, i);
                MainProxy.sendPacketToPlayer(
                        PacketHandler.getPacket(FluidCraftingAdvancedSatelliteId.class).setInteger2(i)
                                .setInteger(liquidSatelliteId).setModulePos(this),
                        player);
            } else {
                liquidSatelliteIdArray[i] = getNextConnectFluidSatelliteId(false, i);
                MainProxy.sendPacketToPlayer(
                        PacketHandler.getPacket(FluidCraftingAdvancedSatelliteId.class).setInteger2(i)
                                .setInteger(liquidSatelliteIdArray[i]).setModulePos(this),
                        player);
            }
        }
    }

    public void setFluidAmount(int[] amount) {
        if (MainProxy.isClient(getWorld())) {
            this.amount = amount;
        }
    }

    public void defineFluidAmount(int integer, int slot) {
        if (MainProxy.isClient(getWorld())) {
            amount[slot] = integer;
        }
    }

    public int[] getFluidAmount() {
        return amount;
    }

    public void setFluidSatelliteId(int integer, int slot) {
        if (slot == -1) {
            liquidSatelliteId = integer;
        } else {
            liquidSatelliteIdArray[slot] = integer;
        }
    }

    public IRouter getFluidSatelliteRouter(int x) {
        if (x == -1) {
            for (final PipeFluidSatellite satellite : PipeFluidSatellite.AllSatellites) {
                if (satellite.satelliteId == liquidSatelliteId) {
                    if (satellite.stillNeedReplace() || satellite.getRouter() == null) {
                        continue;
                    }
                    return satellite.getRouter();
                }
            }
        } else {
            for (final PipeFluidSatellite satellite : PipeFluidSatellite.AllSatellites) {
                if (satellite.satelliteId == liquidSatelliteIdArray[x]) {
                    if (satellite.stillNeedReplace() || satellite.getRouter() == null) {
                        continue;
                    }
                    return satellite.getRouter();
                }
            }
        }
        return null;
    }

    // @SideOnly(Side.SERVER)
    public void openAttachedGui(EntityPlayer player) {
        // System.out.println("TEST [2]");
        if (MainProxy.isClient(player.worldObj)) {
            if (player instanceof EntityPlayerMP) {
                player.closeScreen();
            } else if (player instanceof EntityPlayerSP) {
                player.closeScreen();
            }
            MainProxy.sendPacketToServer(
                    PacketHandler.getPacket(CraftingPipeOpenConnectedGuiPacket.class).setModulePos(this));
            return;
        }

        // hack to avoid wrenching blocks
        int savedEquipped = player.inventory.currentItem;
        boolean foundSlot = false;
        // try to find a empty slot
        for (int i = 0; i < 9; i++) {
            if (player.inventory.getStackInSlot(i) == null) {
                foundSlot = true;
                player.inventory.currentItem = i;
                break;
            }
        }
        // okay, anything that's a block?
        if (!foundSlot) {
            for (int i = 0; i < 9; i++) {
                ItemStack is = player.inventory.getStackInSlot(i);
                if (is.getItem() instanceof ItemBlock) {
                    foundSlot = true;
                    player.inventory.currentItem = i;
                    break;
                }
            }
        }
        // give up and select whatever is right of the current slot
        if (!foundSlot) {
            player.inventory.currentItem = (player.inventory.currentItem + 1) % 9;
        }

        final WorldUtil worldUtil = new WorldUtil(getWorld(), getX(), getY(), getZ());
        boolean found = false;
        for (final AdjacentTile tile : worldUtil.getAdjacentTileEntities(true)) {
            for (ICraftingRecipeProvider provider : SimpleServiceLocator.craftingRecipeProviders) {
                if (provider.canOpenGui(tile.tile)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                found = (tile.tile instanceof IInventory);
            }

            if (found) {
                Block block = getWorld().getBlock(tile.tile.xCoord, tile.tile.yCoord, tile.tile.zCoord);
                if (block != null) {
                    if (block.onBlockActivated(
                            getWorld(),
                            tile.tile.xCoord,
                            tile.tile.yCoord,
                            tile.tile.zCoord,
                            player,
                            0,
                            0,
                            0,
                            0)) {
                        break;
                    }
                }
            }
        }
        player.inventory.currentItem = savedEquipped;
    }

    public void enabledUpdateEntity() {
        if (_service.getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
            if (_service.isNthTick(6)) {
                cacheAreAllOrderesToBuffer();
            }
            if (_service.getItemOrderManager().isFirstOrderWatched()) {
                TileEntity tile = lastAccessedCrafter.get();
                if (tile != null) {
                    _service.getItemOrderManager()
                            .setMachineProgress(SimpleServiceLocator.machineProgressProvider.getProgressForTile(tile));
                } else {
                    _service.getItemOrderManager().setMachineProgress((byte) 0);
                }
            }
        } else {
            cachedAreAllOrderesToBuffer = false;
        }

        if (!_service.isNthTick(6)) {
            return;
        }

        waitingForCraft = false;

        if ((!_service.getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA))) {
            if (getUpgradeManager().getCrafterCleanup() > 0) {
                List<AdjacentTile> crafters = locateCrafters();
                ItemStack extracted = null;
                for (AdjacentTile crafter : crafters) {
                    extracted = extractFiltered(
                            crafter,
                            _cleanupInventory,
                            cleanupModeIsExclude,
                            getUpgradeManager().getCrafterCleanup() * 3);
                    if (extracted != null && extracted.stackSize > 0) {
                        break;
                    }
                }
                if (extracted != null && extracted.stackSize > 0) {
                    _service.queueRoutedItem(
                            SimpleServiceLocator.routedItemHelper.createNewTravelItem(extracted),
                            ForgeDirection.UP);
                    _service.getCacheHolder().trigger(CacheTypes.Inventory);
                }
            }
            return;
        }

        waitingForCraft = true;

        List<AdjacentTile> crafters = locateCrafters();
        if (crafters.size() < 1) {
            if (_service.getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
                _service.getItemOrderManager().sendFailed();
            }
            return;
        }

        List<ItemIdentifierStack> wanteditem = getCraftedItems();
        if (wanteditem == null || wanteditem.isEmpty()) {
            return;
        }

        _service.spawnParticle(Particles.VioletParticle, 2);

        int itemsleft = itemsToExtract();
        int stacksleft = stacksToExtract();
        while (itemsleft > 0 && stacksleft > 0
                && (_service.getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA))) {
            LogisticsItemOrder nextOrder = _service.getItemOrderManager()
                    .peekAtTopRequest(ResourceType.CRAFTING, ResourceType.EXTRA); // fetch but not remove.
            int maxtosend = Math.min(itemsleft, nextOrder.getResource().stack.getStackSize());
            maxtosend = Math.min(nextOrder.getResource().getItem().getMaxStackSize(), maxtosend);

            // retrieve the new crafted items
            ItemStack extracted = null;
            AdjacentTile tile = null;
            for (AdjacentTile crafter : crafters) {
                tile = crafter;
                extracted = extract(tile, nextOrder.getResource(), maxtosend);
                if (extracted != null && extracted.stackSize > 0) {
                    break;
                }
            }
            if (extracted == null || extracted.stackSize == 0) {
                _service.getItemOrderManager().deferSend();
                break;
            }
            _service.getCacheHolder().trigger(CacheTypes.Inventory);
            lastAccessedCrafter = new WeakReference<>(tile.tile);
            // send the new crafted items to the destination
            ItemIdentifier extractedID = ItemIdentifier.get(extracted);
            while (extracted.stackSize > 0) {
                if (!doesExtractionMatch(nextOrder, extractedID)) {
                    LogisticsItemOrder startOrder = nextOrder;
                    if (_service.getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
                        do {
                            _service.getItemOrderManager().deferSend();
                            nextOrder = _service.getItemOrderManager()
                                    .peekAtTopRequest(ResourceType.CRAFTING, ResourceType.EXTRA);
                        } while (!doesExtractionMatch(nextOrder, extractedID) && startOrder != nextOrder);
                    }
                    if (startOrder == nextOrder) {
                        int numtosend = Math.min(extracted.stackSize, extractedID.getMaxStackSize());
                        if (numtosend == 0) {
                            break;
                        }
                        stacksleft -= 1;
                        itemsleft -= numtosend;
                        ItemStack stackToSend = extracted.splitStack(numtosend);
                        // Route the unhandled item
                        _service.sendStack(stackToSend, -1, ItemSendMode.Normal, null);

                        continue;
                    }
                }
                int numtosend = Math.min(extracted.stackSize, extractedID.getMaxStackSize());
                numtosend = Math.min(numtosend, nextOrder.getResource().stack.getStackSize());
                if (numtosend == 0) {
                    break;
                }
                stacksleft -= 1;
                itemsleft -= numtosend;
                ItemStack stackToSend = extracted.splitStack(numtosend);
                if (nextOrder.getDestination() != null) {
                    SinkReply reply = LogisticsManager.canSink(
                            nextOrder.getDestination().getRouter(),
                            null,
                            true,
                            ItemIdentifier.get(stackToSend),
                            null,
                            true,
                            false);
                    boolean defersend = reply == null || reply.bufferMode != BufferMode.NONE
                            || reply.maxNumberOfItems < 1;
                    IRoutedItem item = SimpleServiceLocator.routedItemHelper.createNewTravelItem(stackToSend);
                    item.setDestination(nextOrder.getDestination().getRouter().getSimpleID());
                    item.setTransportMode(TransportMode.Active);
                    item.setAdditionalTargetInformation(nextOrder.getInformation());
                    _service.queueRoutedItem(item, tile.orientation);
                    _service.getItemOrderManager().sendSuccessfull(stackToSend.stackSize, defersend, item);
                } else {
                    _service.sendStack(stackToSend, -1, ItemSendMode.Normal, nextOrder.getInformation());
                    _service.getItemOrderManager().sendSuccessfull(stackToSend.stackSize, false, null);
                }
                if (_service.getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
                    nextOrder = _service.getItemOrderManager()
                            .peekAtTopRequest(ResourceType.CRAFTING, ResourceType.EXTRA); // fetch but not remove.
                }
                PipeLogisticsChassi pipe = ((PipeLogisticsChassi) (_service.getRouter().getPipe()));

                pipe.currentCraftingAmount -= numtosend;
                pipe.currentBulkCraftingAmount -= numtosend;

                System.out.println("extracted size" + extracted.stackSize + " | " + numtosend);

                System.out.println("C amount: " + pipe.currentCraftingAmount + " | " + pipe.currentBulkCraftingAmount);
                System.out.println("Crafting module: " + pipe.usingCraftingModuleSlot);

                ModuleCrafter craftingModule = ((ModuleCrafter) pipe.getModules()
                        .getModule(pipe.usingCraftingModuleSlot));

                boolean shouldRequestWhenAvaliable = true; // True: request items whenever space is avaliable

                if (pipe.currentBulkCraftingAmount == 0 || shouldRequestWhenAvaliable) {
                    if (!craftingModule.requestMoreItems() && pipe.currentCraftingAmount == 0) {
                        pipe.requestModulesOverflowedItems();
                    }
                }
            }
        }
    }

    public void testfunction2() {
        System.out.println("Function 2 " + getCraftedItem().getFriendlyName());
    }

    private boolean doesExtractionMatch(LogisticsItemOrder nextOrder, ItemIdentifier extractedID) {
        return nextOrder.getResource().getItem().equals(extractedID)
                || (this.getUpgradeManager().isFuzzyUpgrade() && nextOrder.getResource().getBitSet().nextSetBit(0) != -1
                        && nextOrder.getResource().matches(extractedID, IResource.MatchSettings.NORMAL));
    }

    private boolean cachedAreAllOrderesToBuffer;

    public boolean areAllOrderesToBuffer() {
        return cachedAreAllOrderesToBuffer;
    }

    public void cacheAreAllOrderesToBuffer() {
        boolean result = true;
        for (LogisticsItemOrder order : _service.getItemOrderManager()) {
            if (order.getDestination() instanceof IItemSpaceControl) {
                SinkReply reply = LogisticsManager.canSink(
                        order.getDestination().getRouter(),
                        null,
                        true,
                        order.getResource().getItem(),
                        null,
                        true,
                        false);
                if (reply != null && reply.bufferMode == BufferMode.NONE && reply.maxNumberOfItems >= 1) {
                    result = false;
                    break;
                }
            } else { // No Space control
                result = false;
                break;
            }
        }
        cachedAreAllOrderesToBuffer = result;
    }

    private ItemStack extract(AdjacentTile tile, IResource item, int amount) {
        if (tile.tile instanceof LogisticsCraftingTableTileEntity) {
            return extractFromLogisticsCraftingTable(
                    (LogisticsCraftingTableTileEntity) tile.tile,
                    item,
                    amount,
                    tile.orientation);
        } else if (tile.tile instanceof net.minecraft.inventory.ISidedInventory) {
            IInventory sidedadapter = new SidedInventoryMinecraftAdapter(
                    (net.minecraft.inventory.ISidedInventory) tile.tile,
                    ForgeDirection.UNKNOWN,
                    true);
            return extractFromIInventory(sidedadapter, item, amount, tile.orientation);

        } else if (tile.tile instanceof IInventory) {
            return extractFromIInventory((IInventory) tile.tile, item, amount, tile.orientation);
        }
        return null;
    }

    private ItemStack extractFiltered(AdjacentTile tile, ItemIdentifierInventory inv, boolean isExcluded,
            int filterInvLimit) {
        if (tile.tile instanceof net.minecraft.inventory.ISidedInventory) {
            IInventory sidedadapter = new SidedInventoryMinecraftAdapter(
                    (net.minecraft.inventory.ISidedInventory) tile.tile,
                    ForgeDirection.UNKNOWN,
                    true);
            return extractFromIInventoryFiltered(sidedadapter, inv, isExcluded, filterInvLimit, tile.orientation);
        } else if (tile.tile instanceof IInventory) {
            return extractFromIInventoryFiltered(
                    (IInventory) tile.tile,
                    inv,
                    isExcluded,
                    filterInvLimit,
                    tile.orientation);
        }
        return null;
    }

    private ItemStack extractFromIInventory(IInventory inv, IResource wanteditem, int count, ForgeDirection dir) {
        IInventoryUtil invUtil = SimpleServiceLocator.inventoryUtilFactory.getInventoryUtil(inv, dir);
        ItemIdentifier itemToExtract = null;
        if (wanteditem instanceof ItemResource) {
            itemToExtract = ((ItemResource) wanteditem).getItem();
        } else if (wanteditem instanceof DictResource) {
            int max = Integer.MIN_VALUE;
            ItemIdentifier toExtract = null;
            for (Map.Entry<ItemIdentifier, Integer> content : invUtil.getItemsAndCount().entrySet()) {
                if (wanteditem.matches(content.getKey(), IResource.MatchSettings.NORMAL)) {
                    if (content.getValue() > max) {
                        max = content.getValue();
                        toExtract = content.getKey();
                    }
                }
            }
            if (toExtract == null) {
                return null;
            }
            itemToExtract = toExtract;
        }

        int available = invUtil.itemCount(itemToExtract);
        if (available == 0) {
            return null;
        }
        if (!_service.useEnergy(neededEnergy() * Math.min(count, available))) {
            return null;
        }
        return invUtil.getMultipleItems(itemToExtract, Math.min(count, available));
    }

    private ItemStack extractFromIInventoryFiltered(IInventory inv, ItemIdentifierInventory filter, boolean isExcluded,
            int filterInvLimit, ForgeDirection dir) {
        IInventoryUtil invUtil = SimpleServiceLocator.inventoryUtilFactory.getInventoryUtil(inv, dir);
        ItemIdentifier wanteditem = null;
        for (ItemIdentifier item : invUtil.getItemsAndCount().keySet()) {
            if (isExcluded) {
                boolean found = false;
                for (int i = 0; i < filter.getSizeInventory() && i < filterInvLimit; i++) {
                    ItemIdentifierStack identStack = filter.getIDStackInSlot(i);
                    if (identStack == null) {
                        continue;
                    }
                    if (identStack.getItem().equalsWithoutNBT(item)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    wanteditem = item;
                }
            } else {
                boolean found = false;
                for (int i = 0; i < filter.getSizeInventory() && i < filterInvLimit; i++) {
                    ItemIdentifierStack identStack = filter.getIDStackInSlot(i);
                    if (identStack == null) {
                        continue;
                    }
                    if (identStack.getItem().equalsWithoutNBT(item)) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    wanteditem = item;
                }
            }
        }
        if (wanteditem == null) {
            return null;
        }
        int available = invUtil.itemCount(wanteditem);
        if (available == 0) {
            return null;
        }
        if (!_service.useEnergy(neededEnergy() * Math.min(64, available))) {
            return null;
        }
        return invUtil.getMultipleItems(wanteditem, Math.min(64, available));
    }

    private ItemStack extractFromLogisticsCraftingTable(LogisticsCraftingTableTileEntity tile, IResource wanteditem,
            int count, ForgeDirection dir) {
        ItemStack extracted = extractFromIInventory(tile, wanteditem, count, dir);
        if (extracted != null) {
            return extracted;
        }
        ItemStack retstack = null;
        while (count > 0) {
            ItemStack stack = tile.getOutput(wanteditem, _service);
            if (stack == null || stack.stackSize == 0) {
                break;
            }
            if (retstack == null) {
                if (!wanteditem.matches(
                        ItemIdentifier.get(stack),
                        wanteditem instanceof ItemResource ? IResource.MatchSettings.WITHOUT_NBT
                                : IResource.MatchSettings.NORMAL)) {
                    break;
                }
            } else {
                if (!retstack.isItemEqual(stack)) {
                    break;
                }
                if (!ItemStack.areItemStackTagsEqual(retstack, stack)) {
                    break;
                }
            }
            if (!_service.useEnergy(neededEnergy() * stack.stackSize)) {
                break;
            }

            if (retstack == null) {
                retstack = stack;
            } else {
                retstack.stackSize += stack.stackSize;
            }
            count -= stack.stackSize;
            if (getUpgradeManager().isFuzzyUpgrade()) {
                break;
            }
        }
        return retstack;
    }

    protected int neededEnergy() {
        return 10;
    }

    protected int itemsToExtract() {
        return 1;
    }

    protected int stacksToExtract() {
        return 1;
    }

    private List<AdjacentTile> _cachedCrafters = null;

    public List<AdjacentTile> locateCrafters() {
        if (_cachedCrafters != null) {
            return _cachedCrafters;
        }
        WorldUtil worldUtil = new WorldUtil(getWorld(), getX(), getY(), getZ());
        LinkedList<AdjacentTile> crafters = new LinkedList<>();
        for (AdjacentTile tile : worldUtil.getAdjacentTileEntities(true)) {
            if (!(tile.tile instanceof IInventory)) {
                continue;
            }
            crafters.add(tile);
        }
        _cachedCrafters = crafters;
        return _cachedCrafters;
    }

    public void clearCraftersCache() {
        _cachedCrafters = null;
    }

    @Override
    public void clearCache() {
        clearCraftersCache();
    }

    public void importCleanup() {
        for (int i = 0; i < 10; i++) {
            _cleanupInventory.setInventorySlotContents(i, _dummyInventory.getStackInSlot(i));
        }
        for (int i = 10; i < _cleanupInventory.getSizeInventory(); i++) {
            _cleanupInventory.setInventorySlotContents(i, (ItemStack) null);
        }
        _cleanupInventory.compact_first(10);
        _cleanupInventory.recheckStackLimit();
        cleanupModeIsExclude = false;
    }

    public void toogleCleaupMode() {
        cleanupModeIsExclude = !cleanupModeIsExclude;
    }

    @Override
    public void startHUDWatching() {
        MainProxy.sendPacketToServer(PacketHandler.getPacket(HUDStartModuleWatchingPacket.class).setModulePos(this));
    }

    @Override
    public void stopHUDWatching() {
        MainProxy.sendPacketToServer(PacketHandler.getPacket(HUDStopModuleWatchingPacket.class).setModulePos(this));
    }

    @Override
    public void startWatching(EntityPlayer player) {
        localModeWatchers.add(player);
    }

    @Override
    public void stopWatching(EntityPlayer player) {
        localModeWatchers.remove(player);
    }

    @Override
    public IHUDModuleRenderer getHUDRenderer() {
        // TODO Auto-generated method stub
        return null;
    }

    public static class CraftingChassieInformation extends ChassiTargetInformation {

        @Getter
        private final int craftingSlot;

        @Getter
        private final int routerID;

        public CraftingChassieInformation(int routerID, int craftingSlot, int moduleSlot) {
            super(moduleSlot);
            this.craftingSlot = craftingSlot;
            this.routerID = routerID;
        }
    }
}
