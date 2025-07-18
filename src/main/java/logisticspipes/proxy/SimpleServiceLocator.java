/*
 * Copyright (c) Krapht, 2011 "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public License 1.0,
 * or MMPL. Please check the contents of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package logisticspipes.proxy;

import java.util.LinkedList;

import logisticspipes.interfaces.ISecurityStationManager;
import logisticspipes.interfaces.routing.IDirectConnectionManager;
import logisticspipes.logistics.ILogisticsFluidManager;
import logisticspipes.logistics.ILogisticsManager;
import logisticspipes.proxy.interfaces.*;
import logisticspipes.proxy.progressprovider.MachineProgressProvider;
import logisticspipes.proxy.specialconnection.SpecialPipeConnection;
import logisticspipes.proxy.specialconnection.SpecialTileConnection;
import logisticspipes.proxy.specialtankhandler.SpecialTankHandler;
import logisticspipes.recipes.CraftingPermissionManager;
import logisticspipes.renderer.newpipe.GLRenderListHandler;
import logisticspipes.routing.IRouterManager;
import logisticspipes.routing.pathfinder.PipeInformationManager;
import logisticspipes.ticks.ClientPacketBufferHandlerThread;
import logisticspipes.ticks.ServerPacketBufferHandlerThread;
import logisticspipes.utils.InventoryUtilFactory;
import logisticspipes.utils.RoutedItemHelper;

public final class SimpleServiceLocator {

    private SimpleServiceLocator() {}

    public static IBCProxy buildCraftProxy = null;

    public static void setBuildCraftProxy(final IBCProxy bcProxy) {
        SimpleServiceLocator.buildCraftProxy = bcProxy;
    }

    public static IIC2Proxy IC2Proxy;

    public static void setElectricItemProxy(final IIC2Proxy ic2Proxy) {
        SimpleServiceLocator.IC2Proxy = ic2Proxy;
    }

    public static IForestryProxy forestryProxy;

    public static void setForestryProxy(final IForestryProxy fProxy) {
        SimpleServiceLocator.forestryProxy = fProxy;
    }

    public static ICCProxy ccProxy;

    public static void setCCProxy(final ICCProxy cProxy) {
        SimpleServiceLocator.ccProxy = cProxy;
    }

    public static IDirectConnectionManager connectionManager;

    public static void setDirectConnectionManager(final IDirectConnectionManager conMngr) {
        SimpleServiceLocator.connectionManager = conMngr;
    }

    public static ISecurityStationManager securityStationManager;

    public static void setSecurityStationManager(final ISecurityStationManager secStationMngr) {
        SimpleServiceLocator.securityStationManager = secStationMngr;
    }

    public static IRouterManager routerManager;

    public static void setRouterManager(final IRouterManager routerMngr) {
        SimpleServiceLocator.routerManager = routerMngr;
    }

    public static ILogisticsManager logisticsManager;

    public static void setLogisticsManager(final ILogisticsManager logisticsMngr) {
        SimpleServiceLocator.logisticsManager = logisticsMngr;
    }

    public static ILogisticsFluidManager logisticsFluidManager;

    public static void setLogisticsFluidManager(final ILogisticsFluidManager logisticsMngr) {
        SimpleServiceLocator.logisticsFluidManager = logisticsMngr;
    }

    public static InventoryUtilFactory inventoryUtilFactory;

    public static void setInventoryUtilFactory(final InventoryUtilFactory invUtilFactory) {
        SimpleServiceLocator.inventoryUtilFactory = invUtilFactory;
    }

    public static LinkedList<ICraftingRecipeProvider> craftingRecipeProviders = new LinkedList<>();

    public static void addCraftingRecipeProvider(ICraftingRecipeProvider provider) {
        SimpleServiceLocator.craftingRecipeProviders.add(provider);
    }

    public static SpecialPipeConnection specialpipeconnection;

    public static void setSpecialConnectionHandler(final SpecialPipeConnection special) {
        SimpleServiceLocator.specialpipeconnection = special;
    }

    public static SpecialTileConnection specialtileconnection;

    public static void setSpecialConnectionHandler(final SpecialTileConnection special) {
        SimpleServiceLocator.specialtileconnection = special;
    }

    public static IThaumCraftProxy thaumCraftProxy;

    public static void setThaumCraftProxy(IThaumCraftProxy proxy) {
        SimpleServiceLocator.thaumCraftProxy = proxy;
    }

    public static IThermalExpansionProxy thermalExpansionProxy;

    public static void setThermalExpansionProxy(IThermalExpansionProxy proxy) {
        SimpleServiceLocator.thermalExpansionProxy = proxy;
    }

    public static IBetterStorageProxy betterStorageProxy;

    public static void setBetterStorageProxy(IBetterStorageProxy proxy) {
        SimpleServiceLocator.betterStorageProxy = proxy;
    }

    public static SpecialTankHandler specialTankHandler;

    public static void setSpecialTankHandler(SpecialTankHandler proxy) {
        SimpleServiceLocator.specialTankHandler = proxy;
    }

    public static ClientPacketBufferHandlerThread clientBufferHandler;

    public static void setClientPacketBufferHandlerThread(ClientPacketBufferHandlerThread proxy) {
        SimpleServiceLocator.clientBufferHandler = proxy;
    }

    public static ServerPacketBufferHandlerThread serverBufferHandler;

    public static void setServerPacketBufferHandlerThread(ServerPacketBufferHandlerThread proxy) {
        SimpleServiceLocator.serverBufferHandler = proxy;
    }

    public static INEIProxy neiProxy;

    public static void setNEIProxy(INEIProxy proxy) {
        SimpleServiceLocator.neiProxy = proxy;
    }

    public static CraftingPermissionManager craftingPermissionManager;

    public static void setCraftingPermissionManager(CraftingPermissionManager manager) {
        SimpleServiceLocator.craftingPermissionManager = manager;
    }

    public static IFactorizationProxy factorizationProxy;

    public static void setFactorizationProxy(IFactorizationProxy proxy) {
        SimpleServiceLocator.factorizationProxy = proxy;
    }

    public static PipeInformationManager pipeInformationManager;

    public static void setPipeInformationManager(PipeInformationManager manager) {
        SimpleServiceLocator.pipeInformationManager = manager;
    }

    public static IEnderIOProxy enderIOProxy;

    public static void setEnderIOProxy(IEnderIOProxy proxy) {
        SimpleServiceLocator.enderIOProxy = proxy;
    }

    public static IIronChestProxy ironChestProxy;

    public static void setIronChestProxy(IIronChestProxy proxy) {
        SimpleServiceLocator.ironChestProxy = proxy;
    }

    public static IEnderStorageProxy enderStorageProxy;

    public static void setEnderStorageProxy(IEnderStorageProxy proxy) {
        SimpleServiceLocator.enderStorageProxy = proxy;
    }

    public static MachineProgressProvider machineProgressProvider;

    public static void setMachineProgressProvider(MachineProgressProvider provider) {
        SimpleServiceLocator.machineProgressProvider = provider;
    }

    public static RoutedItemHelper routedItemHelper;

    public static void setRoutedItemHelper(RoutedItemHelper helper) {
        SimpleServiceLocator.routedItemHelper = helper;
    }

    public static IOpenComputersProxy openComputersProxy;

    public static void setOpenComputersProxy(IOpenComputersProxy proxy) {
        SimpleServiceLocator.openComputersProxy = proxy;
    }

    public static IToolWrenchProxy toolWrenchHandler;

    public static void setToolWrenchProxy(IToolWrenchProxy handler) {
        SimpleServiceLocator.toolWrenchHandler = handler;
    }

    public static GLRenderListHandler renderListHandler;

    public static void setRenderListHandler(GLRenderListHandler handler) {
        SimpleServiceLocator.renderListHandler = handler;
    }

    public static IExtraCellsProxy extraCellsProxy;

    public static void setExtraCellsProxy(IExtraCellsProxy proxy) {
        SimpleServiceLocator.extraCellsProxy = proxy;
    }

    public static ICoFHPowerProxy cofhPowerProxy;

    public static void setCoFHPowerProxy(ICoFHPowerProxy proxy) {
        SimpleServiceLocator.cofhPowerProxy = proxy;
    }

    public static IBinnieProxy binnieProxy;

    public static void setBinnieProxy(IBinnieProxy proxy) {
        SimpleServiceLocator.binnieProxy = proxy;
    }

    public static ICCLProxy cclProxy;

    public static void setCCLProxy(ICCLProxy proxy) {
        SimpleServiceLocator.cclProxy = proxy;
    }
}
