package logisticspipes.logistics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.LogisticsPipes;
import logisticspipes.interfaces.routing.IFluidSink;
import logisticspipes.interfaces.routing.IProvideFluids;
import logisticspipes.items.LogisticsFluidContainer;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.routing.ExitRoute;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.PipeRoutingConnectionType;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.tuples.Pair;

public class LogisticsFluidManager implements ILogisticsFluidManager {

    @Override
    public Pair<Integer, Integer> getBestReply(FluidStack stack, IRouter sourceRouter, List<Integer> jamList) {
        for (ExitRoute candidateRouter : sourceRouter.getIRoutersByCost()) {
            if (!candidateRouter.containsFlag(PipeRoutingConnectionType.canRouteTo)) {
                continue;
            }
            if (candidateRouter.destination.getSimpleID() == sourceRouter.getSimpleID()) {
                continue;
            }
            if (jamList.contains(candidateRouter.destination.getSimpleID())) {
                continue;
            }

            if (candidateRouter.destination.getPipe() == null || !candidateRouter.destination.getPipe().isEnabled()) {
                continue;
            }
            CoreRoutedPipe pipe = candidateRouter.destination.getPipe();

            if (!(pipe instanceof IFluidSink)) {
                continue;
            }

            int amount = ((IFluidSink) pipe).sinkAmount(stack);
            if (amount > 0) {
                return new Pair<>(candidateRouter.destination.getSimpleID(), amount);
            }
        }
        return new Pair<>(0, 0);
    }

    @Override
    public ItemIdentifierStack getFluidContainer(FluidStack stack) {
        ItemStack item = new ItemStack(LogisticsPipes.LogisticsFluidContainer, 1);
        NBTTagCompound nbt = new NBTTagCompound();
        stack.writeToNBT(nbt);
        item.setTagCompound(nbt);
        return ItemIdentifierStack.getFromStack(item);
    }

    @Override
    public FluidStack getFluidFromContainer(ItemIdentifierStack stack) {
        if (stack.makeNormalStack().getItem() instanceof LogisticsFluidContainer && stack.getItem().tag != null) {
            return FluidStack.loadFluidStackFromNBT(stack.getItem().tag);
        }
        return null;
    }

    @Override
    public TreeSet<ItemIdentifierStack> getAvailableFluid(List<ExitRoute> validDestinations) {
        Map<FluidIdentifier, Integer> allAvailableItems = new HashMap<>();
        for (ExitRoute r : validDestinations) {
            if (r == null) {
                continue;
            }
            if (!r.containsFlag(PipeRoutingConnectionType.canRequestFrom)) {
                continue;
            }
            if (!(r.destination.getPipe() instanceof IProvideFluids)) {
                continue;
            }

            IProvideFluids provider = (IProvideFluids) r.destination.getPipe();
            Map<FluidIdentifier, Integer> allItems = provider.getAvailableFluids();

            for (Entry<FluidIdentifier, Integer> liquid : allItems.entrySet()) {
                Integer amount = allAvailableItems.get(liquid.getKey());
                if (amount == null) {
                    allAvailableItems.put(liquid.getKey(), liquid.getValue());
                } else {
                    long addition = ((long) amount) + liquid.getValue();
                    if (addition > Integer.MAX_VALUE) {
                        addition = Integer.MAX_VALUE;
                    }
                    allAvailableItems.put(liquid.getKey(), (int) addition);
                }
            }
        }
        TreeSet<ItemIdentifierStack> itemIdentifierStackList = new TreeSet<>();
        for (Entry<FluidIdentifier, Integer> item : allAvailableItems.entrySet()) {
            itemIdentifierStackList.add(new ItemIdentifierStack(item.getKey().getItemIdentifier(), item.getValue()));
        }
        return itemIdentifierStackList;
    }
}
