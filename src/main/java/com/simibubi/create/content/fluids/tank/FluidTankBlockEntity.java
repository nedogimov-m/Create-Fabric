package com.simibubi.create.content.fluids.tank;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.fluid.*;
import net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import java.util.*;

public class FluidTankBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements IMultiBlockEntityContainer.Fluid, RenderDataBlockEntity,com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public final com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner sourceAdvancements=new com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner(this,"steam_engine_maxed","pipe_organ");
    @Override public boolean addToGoggleTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip,boolean sneaking){var master=getControllerBE();if(master==null)return false;return master.boiler.addToGoggleTooltip(tooltip,sneaking,master.getTotalTankSize())||containedFluidTooltip(tooltip,sneaking,master.storage());}
    public static final long CAPACITY = 8 * FluidConstants.BUCKET;
    protected final SmartFluidTank tankInventory;
    protected BlockPos controller, lastKnownPos;
    protected int width = 1, height = 1, luminosity;
    private int capacityBlocks=1;
    protected boolean window = true, updateConnectivity = true;
    public final BoilerData boiler=new BoilerData();
    private boolean boilerNeedsEvaluation=true;
    private int syncCooldown;
    private boolean queuedSync;
    private float previousFill, visualFill;
    private float fluidChaseSpeed = .5f;
    private long fluidAnimationTick = Long.MIN_VALUE;
    private boolean initializeVisual = true;
    public FluidTankBlockEntity(BlockPos pos, BlockState state) { this(AllBlockEntityTypes.FLUID_TANK, pos, state); }
    protected FluidTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); tankInventory = createInventory(); }
    protected SmartFluidTank createInventory() { return new ConfiguredTankInventory(this::configuredCapacity, this::onFluidStackChanged); }
    public SmartFluidTank getTankInventory() { return tankInventory; }
    public static int getMaxSize() { return 3; }
    public static int getMaxHeight() { return com.simibubi.create.infrastructure.config.AllConfigs.server().fluids.fluidTankMaxHeight.get(); }
    public static long getCapacityMultiplier() { return com.simibubi.create.infrastructure.config.AllConfigs.server().fluids.fluidTankCapacity.get() * FluidConstants.BUCKET; }
    public static long capacityForBlocks(int blocks){long each=getCapacityMultiplier();return blocks>Long.MAX_VALUE/each?Long.MAX_VALUE:Math.max(1,blocks)*each;}
    private long configuredCapacity(){return capacityForBlocks(capacityBlocks);}
    /** Only configuration overflow is retained across a structural split; normal destruction semantics stay intact. */
    public long configurationOverflow(){return Math.max(0,tankInventory.getFluidAmount()-capacityForBlocks(getTotalTankSize()));}
    public void acceptOwnedFluid(FluidStack fluid){if(fluid.isEmpty())return;var current=tankInventory.getFluid();if(!current.isEmpty()&&!FluidStack.isSameFluidSameComponents(current,fluid))throw new IllegalArgumentException("Foreign fluid in actual tank ownership handoff");tankInventory.setFluid(new FluidStack(fluid.variant(),Math.addExact(current.getAmount(),fluid.getAmount())));}
    public float getFillState() { return tankInventory.getCapacity() == 0 ? 0 : (float) tankInventory.getFluidAmount() / tankInventory.getCapacity(); }
    public float getFluidLevel(float partialTick) { return previousFill + (visualFill - previousFill) * partialTick; }
    public void tickFluidLevel() {
        if (level == null || !level.isClientSide() || fluidAnimationTick == level.getGameTime()) return;
        fluidAnimationTick = level.getGameTime();
        previousFill = visualFill;
        visualFill += (getFillState() - visualFill) * fluidChaseSpeed;
    }
    public boolean hasWindows() { return window; }
    public int getTotalTankSize() { return width * width * height; }
    @Override public BlockPos getController() { return isController() ? worldPosition : controller; }
    @Override public boolean isController() { return controller == null || controller.equals(worldPosition); }
    @Override @SuppressWarnings("unchecked") public FluidTankBlockEntity getControllerBE() {
        if (isController()) return this;
        return level != null && level.isLoaded(controller) && com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,controller) instanceof FluidTankBlockEntity be && be.getType() == getType() && !be.isRemoved() ? be : null;
    }
    public boolean structureLoaded() {
        var master = getControllerBE(); if (master == null || master.level == null || !master.isController()) return false;
        for (int x = 0; x < master.width; x++) for (int y = 0; y < master.height; y++) for (int z = 0; z < master.width; z++) {
            var pos = master.worldPosition.offset(x, y, z);
            if (!level.isLoaded(pos) || !(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof FluidTankBlockEntity part) || part.getType() != getType() || !part.getController().equals(master.worldPosition)) return false;
        }
        return true;
    }
    private FluidTankBlockEntity activeController() { if (level instanceof ServerLevel server && TankRecoveryData.get(server).contains(worldPosition)) return null; return !isRemoved() && structureLoaded() ? getControllerBE() : null; }
    private SmartFluidTank activeTank() { var master=activeController();return master==null?null:master.tankInventory; }
    /** Views resolve the current controller on every call; cached handlers survive a merge or split. */
    private final Storage<FluidVariant> storage = new Storage<>() {
        @Override public long insert(FluidVariant resource, long amount, TransactionContext tx) { StoragePreconditions.notBlankNotNegative(resource, amount); var master=activeController();return master==null?0:master.boiler.isActive()?master.boiler.createHandler().insert(resource,amount,tx):master.tankInventory.insert(resource,amount,tx); }
        @Override public long extract(FluidVariant resource, long amount, TransactionContext tx) { StoragePreconditions.notBlankNotNegative(resource, amount); var master=activeController();return master==null||master.boiler.isActive()?0:master.tankInventory.extract(resource,amount,tx); }
        @Override public Iterator<StorageView<FluidVariant>> iterator() { return Collections.<StorageView<FluidVariant>>singleton(new StorageView<>() {
            @Override public boolean isResourceBlank() { return getResource().isBlank(); }
            @Override public FluidVariant getResource() { var master=activeController();return master==null||master.boiler.isActive()?FluidVariant.blank():master.tankInventory.getResource(); }
            @Override public long getAmount() { var master=activeController();return master==null||master.boiler.isActive()?0:master.tankInventory.getAmount(); }
            @Override public long getCapacity() { var master=activeController();return master==null?0:master.boiler.isActive()?10*FluidConstants.BUCKET:master.tankInventory.getCapacity(); }
            @Override public long extract(FluidVariant resource, long amount, TransactionContext tx) { return storage.extract(resource, amount, tx); }
        }).iterator(); }
    };
    public Storage<FluidVariant> storage() { return storage; }
    public void updateBoilerState() {
        var master=getControllerBE();if(master==null)return;master.boilerNeedsEvaluation=true;
        if(level==null||level.isClientSide()||!master.structureLoaded())return;
        boolean changed=master.boiler.evaluate(master);master.boilerNeedsEvaluation=false;
        if(master.boiler.isActive()&&master.window)master.setWindows(false);
        if(changed){master.setChanged();master.sync();master.boiler.checkPipeOrganAdvancement(master);}
    }
    public void updateBoilerTemperature(){var master=getControllerBE();if(master!=null)master.boiler.needsHeatLevelUpdate=true;}
    protected void onFluidStackChanged(FluidStack fluid) {
        if (!(level instanceof ServerLevel)) return;
        int emission = fluid.isEmpty() ? 0 : FluidVariantAttributes.getLuminance(fluid.variant());
        boolean gas = !fluid.isEmpty() && FluidVariantAttributes.isLighterThanAir(fluid.variant());
        int light = (int) (emission / 1.2f), filledHeight = (int) (getFillState() * height + 1);
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) for (int z = 0; z < width; z++) {
            var pos = worldPosition.offset(x, y, z); if (!level.isLoaded(pos)) continue;
            if (!(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof FluidTankBlockEntity part) || !part.getController().equals(worldPosition)) continue;
            part.luminosity = (gas ? height - y <= filledHeight : y < filledHeight) ? light : light > 0 ? 1 : 0;
            part.updateBlockLight(); level.updateNeighbourForOutputSignal(pos, part.getBlockState().getBlock());
        }
        setChanged(); sync();
    }
    private void updateBlockLight() {
        if (!(level instanceof ServerLevel) || !(getBlockState().getBlock() instanceof FluidTankBlock)) return;
        var master = getControllerBE(); int light = master != null && master.window ? luminosity : 0;
        if (getBlockState().getValue(FluidTankBlock.LIGHT) != light) level.setBlock(worldPosition, getBlockState().setValue(FluidTankBlock.LIGHT, light), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
    public void sync() {
        if (!(level instanceof ServerLevel)) return;
        if (syncCooldown > 0) { queuedSync = true; return; }
        queuedSync = false; syncCooldown = 8;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    public void sendDataImmediately() { syncCooldown = 0; sync(); }
    public void requestConnectivityUpdate() { updateConnectivity = true; setChanged(); }
    @Override public void preventConnectivityUpdate() { updateConnectivity = false; }
    @Override public void setController(BlockPos pos) { controller = pos.immutable();boilerNeedsEvaluation=true; setChanged(); sync(); }
    public void applyFluidTankSize(int blocks) { capacityBlocks=Math.max(1,blocks);long target=configuredCapacity();if(tankInventory.getCapacity()!=target)tankInventory.setCapacity(target); }
    @Override public void removeController(boolean keepFluids) {
        if (level == null || level.isClientSide()) return;
        updateConnectivity = true; if (!keepFluids) applyFluidTankSize(1);
        controller = null; width = height = 1;capacityBlocks=1;boiler.clear();boilerNeedsEvaluation=true; notifyMultiUpdated();
    }
    public void toggleWindows() { var master = getControllerBE(); if (master != null&&!master.boiler.isActive()) master.setWindows(!master.window); }
    public void setWindows(boolean windows) {
        window = windows;
        if (level == null || level.isClientSide()) return;
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) for (int z = 0; z < width; z++) {
            var pos = worldPosition.offset(x, y, z); if (!level.isLoaded(pos)) continue;
            if (!(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof FluidTankBlockEntity part) || !part.getController().equals(worldPosition)) continue;
            part.window = window; var shape = FluidTankBlock.Shape.PLAIN;
            if (window) {
                if (width == 1 || width == 3 && Math.abs(x - z) == 1) shape = FluidTankBlock.Shape.WINDOW;
                if (width == 2) shape = x == 0 ? z == 0 ? FluidTankBlock.Shape.WINDOW_NW : FluidTankBlock.Shape.WINDOW_SW : z == 0 ? FluidTankBlock.Shape.WINDOW_NE : FluidTankBlock.Shape.WINDOW_SE;
            }
            var state = part.getBlockState().setValue(FluidTankBlock.TOP, y == height - 1).setValue(FluidTankBlock.BOTTOM, y == 0).setValue(FluidTankBlock.SHAPE, shape);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE); part.updateBlockLight(); part.setChanged(); part.sync();
        }
        setChanged(); sync();
    }
    @Override public void notifyMultiUpdated() {
        boilerNeedsEvaluation=true;
        if (level == null || level.isClientSide()) return;
        var state = getBlockState(); if (!(state.getBlock() instanceof FluidTankBlock)) return;
        level.setBlock(worldPosition, state.setValue(FluidTankBlock.BOTTOM, getController().getY() == worldPosition.getY()).setValue(FluidTankBlock.TOP, getController().getY() + height - 1 == worldPosition.getY()), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        if (isController()) setWindows(window);
        onFluidStackChanged(tankInventory.getFluid()); setChanged(); sync();
    }
    public static void tick(Level level, BlockPos pos, BlockState state, FluidTankBlockEntity tank) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, tank)) {
            if (!createTick.shouldTick()) return;

        
        if (level.isClientSide()) {
            if(tank.isController())tank.boiler.tick(tank);
            tank.tickFluidLevel(); return;
        }
        if (tank.syncCooldown > 0 && --tank.syncCooldown == 0 && tank.queuedSync) tank.sync();
        if (tank.lastKnownPos != null && !tank.lastKnownPos.equals(pos)) tank.removeController(true);
        tank.lastKnownPos = pos;
        if (TankRecoveryData.get((ServerLevel) level).tryRestore((ServerLevel) level, pos)) return;
        if (tank.updateConnectivity && tank.isController() && tank.structureLoaded()) { tank.updateConnectivity = false; ConnectivityHandler.formMulti(tank); }
        if(tank.isController()&&tank.structureLoaded()){
            tank.applyFluidTankSize(tank.getTotalTankSize());
            if(tank.boilerNeedsEvaluation||level.getGameTime()%20==0)tank.updateBoilerState();
            tank.boiler.tick(tank);
        }
    
        }
    }
    void resetForRecovery(boolean windows) {
        controller=null; width=height=1; window=windows;boiler.clear();boilerNeedsEvaluation=true; tankInventory.clear();applyFluidTankSize(1); updateConnectivity=true; notifyMultiUpdated();
    }
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server) {
            var recovery = TankRecoveryData.get(server);
            if (recovery.contains(worldPosition) || !structureLoaded()) recovery.deferRemoval(this);
            else ConnectivityHandler.removePart(this);
        }
        super.preRemoveSideEffects(pos, state);
    }
    @Override protected void write(ValueOutput out, boolean clientPacket) {
        super.write(out, clientPacket); out.putBoolean("Uninitialized", updateConnectivity); out.putInt("Luminosity", luminosity);
        if (lastKnownPos != null) out.store("LastKnownPos", BlockPos.CODEC, lastKnownPos);
        if (!isController()) out.store("Controller", BlockPos.CODEC, controller);
        out.putBoolean("Window", window); out.putInt("Size", width); out.putInt("Height", height);
        if (isController()){tankInventory.save(out.child("TankContent"));boiler.save(out.child("Boiler"));}
    }
    @Override protected void read(ValueInput in, boolean clientPacket) {
        super.read(in, clientPacket); updateConnectivity = in.getBooleanOr("Uninitialized", true); luminosity = Math.clamp(in.getIntOr("Luminosity", 0), 0, 15);
        lastKnownPos = in.read("LastKnownPos", BlockPos.CODEC).orElse(null); controller = in.read("Controller", BlockPos.CODEC).orElse(null);
        window = in.getBooleanOr("Window", true); width = Math.clamp(in.getIntOr("Size", 1), 1, 3); height = Math.max(1,in.getIntOr("Height", 1));
        if (isController()) { applyFluidTankSize(getTotalTankSize()); tankInventory.load(in.childOrEmpty("TankContent")); }
        boiler.load(in.childOrEmpty("Boiler"),getTotalTankSize());boilerNeedsEvaluation=true;
        if (initializeVisual || clientPacket && in.getBooleanOr("ForceFluidLevel", false)) { visualFill = previousFill = getFillState(); initializeVisual = false; }
        if (clientPacket) fluidChaseSpeed = in.getBooleanOr("LazySync", false) ? .125f : .5f;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return super.getUpdateTag(registries); }
    public record TankRenderData(BlockPos controller, int width, int height) {}
    @Override public Object getRenderData() { return new TankRenderData(getController(), width, height); }
    @Override public BlockPos getLastKnownPos() { return lastKnownPos; }
    @Override public Object getExtraData() { return window; }
    @Override public void setExtraData(Object data) { if (data instanceof Boolean value) window = value; }
    @Override public Object modifyExtraData(Object data) { return data instanceof Boolean value ? value || window : data; }
    @Override public Direction.Axis getMainConnectionAxis() { return Direction.Axis.Y; }
    @Override public int getMaxLength(Direction.Axis axis, int width) { return axis.isVertical() ? getMaxHeight() : 3; }
    @Override public int getMaxWidth() { return 3; }
    @Override public int getHeight() { return height; }
    @Override public void setHeight(int height) { this.height = height; }
    @Override public int getWidth() { return width; }
    @Override public void setWidth(int width) { this.width = width; }
    @Override public boolean hasTank() { return true; }
    @Override public long getTankSize(int tank) { return getCapacityMultiplier(); }
    @Override public void setTankSize(int tank, int blocks) { applyFluidTankSize(blocks); }
    @Override public SmartFluidTank getTank(int tank) { return tankInventory; }
    @Override public FluidStack getFluid(int tank) { return tankInventory.getFluid(); }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        ids.clear();
        ids.add("steam_engine_maxed");
        ids.add("pipe_organ");
    }
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return sourceAdvancements;}
}
