package com.simibubi.create.content.kinetics.crafter;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import java.util.*;

public final class MechanicalCrafterBlockEntity extends KineticBlockEntity implements net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity {
    public final com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner sourceAdvancements=new com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner(this,"mechanical_crafter","crafter_lazy_000");
    public enum Phase { IDLE, ACCEPTING, ASSEMBLING, EXPORTING, WAITING, CRAFTING, INSERTING }
    public final CrafterInventory inventory = new CrafterInventory(this);
    public GroupedItems groupedItems = new GroupedItems(), groupedItemsBeforeCraft = new GroupedItems();
    public Phase phase = Phase.IDLE;
    public int countDown;
    public boolean covered;
    volatile Set<BlockPos> inputGroup;
    public record InputRenderData(Set<BlockPos> members) {}
    @Override public Object getRenderData() { return new InputRenderData(inputGroup); }
    private ItemStack scriptedResult = ItemStack.EMPTY;
    private boolean checkRequested;
    private boolean poweredCheckRequested;
    private boolean wasPoweredBefore = true;
    private UUID session;
    private Direction sessionTarget, sessionFacing;
    private List<BlockPos> participants = List.of();
    // Original3955 input data names real relative owners; it never supplies native session receipts.
    private BlockPos originalInputController;
    private Set<BlockPos> originalInputMembers = Set.of();
    private final Storage<ItemVariant> storage = ConnectedInputHandler.storage(this);
    private final OutputSnapshot outputSnapshot = new OutputSnapshot();
    private final class OutputSnapshot extends net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant<GroupedItems> {
        void snapshot(net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext tx) { updateSnapshots(tx); }
        @Override protected GroupedItems createSnapshot() { return groupedItems.copy(); }
        @Override protected void readSnapshot(GroupedItems snapshot) { groupedItems = snapshot.copy(); }
        @Override protected void onFinalCommit() { contentsChanged(); }
    }
    public MechanicalCrafterBlockEntity(BlockPos pos, BlockState state) { super(AllBlockEntityTypes.MECHANICAL_CRAFTER, pos, state); inputGroup = Set.of(pos.immutable()); }
    @Override public void onSpeedChanged(float previousSpeed){
        super.onSpeedChanged(previousSpeed);
        if(!net.minecraft.util.Mth.equal(getSpeed(),0)){sourceAdvancements.award("mechanical_crafter");if(Math.abs(getSpeed())<5)sourceAdvancements.award("crafter_lazy_000");}
    }
    public Direction getTargetDirection() { return MechanicalCrafterBlock.getTargetDirection(getBlockState()); }
    public int getCountDownSpeed() { return getSpeed() == 0 ? 0 : Math.clamp((int) Math.abs(getSpeed()), 4, 250); }
    public Storage<ItemVariant> storage() { return storage; }
    public void contentsChanged() { setChanged(); if (level instanceof ServerLevel) sync(); }
    void inputChanged() { checkRequested = true; contentsChanged(); }
    public void requestRecipeCheck(boolean powered){checkRequested=true;poweredCheckRequested|=powered;}
    public boolean checkCompletedRecipe(boolean poweredStart) {
        if (isVirtual()) return checkVirtualCompletedRecipe(poweredStart);
        if (originalInputController != null && !reconcileOriginalInput()) return false;
        if (!(level instanceof ServerLevel) || getSpeed() == 0 || phase != Phase.IDLE) return false;
        var chain = RecipeGridHandler.getAllCraftersOfChain(this);
        if (chain == null || chain.stream().anyMatch(c -> c.phase != Phase.IDLE) || chain.stream().allMatch(c -> c.inventory.isResourceBlank())) return false;
        if (!poweredStart && chain.stream().anyMatch(c -> c.inventory.isResourceBlank() && !c.covered)) return false;
        var stacks = chain.stream().map(c -> c.inventory.stack()).toList();
        try (Transaction tx = Transaction.openOuter()) { chain.forEach(c -> c.inventory.take(tx)); tx.commit(); }
        var token = UUID.randomUUID(); var locations = chain.stream().map(BlockEntity::getBlockPos).toList();
        for (int i = 0; i < chain.size(); i++) {
            var member = chain.get(i); member.groupedItems = new GroupedItems(stacks.get(i)); member.groupedItemsBeforeCraft = new GroupedItems();
            member.phase = RecipeGridHandler.getPrecedingCrafters(member).isEmpty() ? Phase.ASSEMBLING : Phase.ACCEPTING;
            member.countDown = 1; member.session = token; member.participants = locations; member.checkRequested = false;
            member.sessionTarget = member.getTargetDirection(); member.sessionFacing = member.getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING);
        }
        chain.forEach(MechanicalCrafterBlockEntity::contentsChanged); return true;
    }
    private boolean sessionLoaded() {
        if (session == null && originalInputController != null) {
            if (!resumeOriginalSession()) return false;
        }
        if (session == null) { ejectWholeGrid(); return false; }
        for (var pos : participants) {
            if (!level.isLoaded(pos)) return false;
            var other = CrafterHelper.getCrafter(level, pos);
            if (other == null || !session.equals(other.session)) { ejectWholeGrid(); return false; }
            if (other.getTargetDirection() != other.sessionTarget || other.getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING) != other.sessionFacing) { ejectWholeGrid(); return false; }
        }
        return true;
    }
    /** Reconcile only actual loaded virtual owners; ordinary client/server import paths remain separate. */
    public boolean reconcileVirtualInput() {
        if (!isVirtual() || level == null || isRemoved() || CrafterHelper.getCrafter(level, worldPosition) != this) return false;
        if (originalInputController == null) {
            var members = ConnectedInputHandler.getMembers(this);
            return !members.isEmpty() && members.stream().allMatch(member -> member.isVirtual() && member.level == level && !member.isRemoved());
        }
        var root = CrafterHelper.getCrafter(level, originalInputController);
        if (root == null) return false;
        var expected = root.originalInputController == null ? root.inputGroup : root.originalInputMembers;
        for (var pos : expected) {
            var member = CrafterHelper.getCrafter(level, pos);
            if (member == null || !member.isVirtual() || member.level != level || member.isRemoved()) return false;
        }
        return reconcileOriginalInput();
    }
    /** Reconcile genuine original controller/member references only against loaded reciprocal owners. */
    private boolean reconcileOriginalInput() {
        if (originalInputController == null) return true;
        if (level == null || !level.isLoaded(originalInputController)) return false;
        var root = CrafterHelper.getCrafter(level, originalInputController);
        if (root == null) return false;
        Set<BlockPos> expected = root.originalInputController == null ? root.inputGroup : root.originalInputMembers;
        if (expected.isEmpty() || !expected.contains(worldPosition) || !expected.contains(root.worldPosition)) return false;
        var facing = getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING);
        var actual = new ArrayList<MechanicalCrafterBlockEntity>();
        for (var pos : expected) {
            if (!level.isLoaded(pos)) return false;
            var member = CrafterHelper.getCrafter(level, pos);
            if (member == null || member.getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING) != facing
                    || member.originalInputController != null && !member.originalInputController.equals(root.worldPosition)
                    || member.originalInputController == null && !member.inputGroup.equals(expected)) return false;
            actual.add(member);
        }
        var reached = new HashSet<BlockPos>(); var frontier = new ArrayDeque<BlockPos>(); frontier.add(root.worldPosition);
        while (!frontier.isEmpty()) {
            var pos = frontier.removeFirst(); if (!reached.add(pos)) continue;
            for (var direction : Direction.values()) if (direction.getAxis() != facing.getAxis()) {
                var next = pos.relative(direction); if (expected.contains(next) && !reached.contains(next)) frontier.add(next);
            }
        }
        if (!reached.equals(expected)) return false;
        var group = Set.copyOf(expected);
        for (var member : actual) {
            boolean changed = !member.inputGroup.equals(group);
            member.inputGroup = group;
            // Active original operations retain their actual input evidence until session reconciliation.
            if (member.phase == Phase.IDLE) { member.originalInputController = null; member.originalInputMembers = Set.of(); changed = true; }
            if (changed) member.contentsChanged();
        }
        return true;
    }
    /** New native session joins only actual loaded members of the original active directional chain. */
    private boolean resumeOriginalSession() {
        var chain = RecipeGridHandler.getAllCraftersOfChain(this);
        if (chain == null || chain.isEmpty()) return false;
        for (var member : chain) {
            if (member.level != level || member.isRemoved() || CrafterHelper.getCrafter(level, member.worldPosition) != member
                    || member.session != null || member.originalInputController == null
                    || member.phase == Phase.IDLE || !member.reconcileOriginalInput()) return false;
            var target = RecipeGridHandler.getTargetingCrafter(member);
            if ((member.phase == Phase.CRAFTING || member.phase == Phase.INSERTING) && target != null) return false;
            if (member.phase == Phase.EXPORTING && target == null) return false;
        }
        var token = UUID.randomUUID();
        var locations = chain.stream().map(BlockEntity::getBlockPos).toList();
        for (var member : chain) {
            member.session = token; member.participants = locations;
            member.sessionTarget = member.getTargetDirection();
            member.sessionFacing = member.getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING);
            member.originalInputController = null; member.originalInputMembers = Set.of();
            member.contentsChanged();
        }
        return true;
    }
    private void continueIfAllPrecedingFinished() {
        if (RecipeGridHandler.getPrecedingCrafters(this).stream().anyMatch(c -> c.phase != Phase.WAITING)) return;
        phase = Phase.ASSEMBLING; countDown = 1; contentsChanged();
    }
    public void dropItem(ItemStack stack) {
        if (stack.isEmpty() || level == null || level.isClientSide() && !isVirtual()) return;
        var facing = getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING);
        var pos = Vec3.atCenterOf(worldPosition).add(facing.getStepX() * 0.75, 0, facing.getStepZ() * 0.75);
        var entity = new ItemEntity(level, pos.x, pos.y, pos.z, stack.copy()); entity.setDefaultPickUpDelay(); level.addFreshEntity(entity);
    }
    private void eject() {
        groupedItems.grid.values().forEach(this::dropItem); dropItem(inventory.remove());
        phase = Phase.IDLE; groupedItems = new GroupedItems(); groupedItemsBeforeCraft = new GroupedItems(); countDown = 0; session = null; participants = List.of(); originalInputController = null; originalInputMembers = Set.of(); contentsChanged();
    }
    public void ejectWholeGrid() {
        if (isVirtual()) { ejectVirtualGrid(); return; }
        if (level == null || level.isClientSide()) return;
        var token = session; var members = new LinkedHashSet<MechanicalCrafterBlockEntity>(); members.add(this);
        if (token != null) {
            for (var pos : participants) { var member = CrafterHelper.getCrafter(level, pos); if (member != null && token.equals(member.session)) members.add(member); }
        } else {
            var chain = RecipeGridHandler.getAllCraftersOfChain(this);
            if (chain != null) chain.stream().filter(c -> c.phase == Phase.IDLE).forEach(members::add);
        }
        members.forEach(MechanicalCrafterBlockEntity::eject);
    }
    public void tryInsert() {
        var target = worldPosition.relative(getTargetDirection()); if (!level.isLoaded(target)) return;
        var output = com.simibubi.create.foundation.item.LoadedItemStorage.find(level, target, getTargetDirection().getOpposite());
        if (output == null) { ejectWholeGrid(); return; }
        try (Transaction tx = Transaction.openOuter()) {
            for (var entry : new ArrayList<>(groupedItems.grid.entrySet())) {
                var stack = entry.getValue();
                if (stack.isEmpty()) { outputSnapshot.snapshot(tx); groupedItems.grid.remove(entry.getKey()); continue; }
                long moved = output.insert(ItemVariant.of(stack), stack.getCount(), tx);
                if (moved == 0) continue;
                outputSnapshot.snapshot(tx); stack.shrink((int) moved); if (stack.isEmpty()) groupedItems.grid.remove(entry.getKey());
            }
            tx.commit();
        }
        if (groupedItems.grid.isEmpty()) ejectWholeGrid(); else { phase = Phase.INSERTING; contentsChanged(); }
    }
    public static void tick(Level level, BlockPos pos, BlockState state, MechanicalCrafterBlockEntity crafter) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, crafter)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level, pos, state, crafter);
        if (crafter.isVirtual()) { crafter.tickVirtual(); return; }
        if (level.isClientSide()) {
            int previous = crafter.countDown;
            if (crafter.phase != Phase.IDLE && crafter.phase != Phase.ACCEPTING && crafter.phase != Phase.WAITING) crafter.countDown = Math.max(0, crafter.countDown - crafter.getCountDownSpeed());
            if (crafter.phase == Phase.CRAFTING && crafter.getSpeed() != 0) {
                var front = state.getValue(MechanicalCrafterBlock.HORIZONTAL_FACING);
                var center = Vec3.atCenterOf(pos).add(front.getStepX() * 0.65, 0, front.getStepZ() * 0.65);
                if (crafter.countDown > 1000) level.addParticle(net.minecraft.core.particles.ParticleTypes.CRIT, center.x, center.y, center.z, 0, 0, 0);
                var result = crafter.groupedItems.grid.getOrDefault(new Cell(0, 0), ItemStack.EMPTY);
                if (previous >= 1000 && crafter.countDown < 1000 && !result.isEmpty()) for (int i = 0; i < 10; i++) {
                    double x = front.getAxis() == Direction.Axis.X ? 0 : (level.getRandom().nextDouble() - 0.5) * 0.5;
                    double y = (level.getRandom().nextDouble() - 0.5) * 0.5, z = front.getAxis() == Direction.Axis.Z ? 0 : (level.getRandom().nextDouble() - 0.5) * 0.5;
                    level.addParticle(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(result)), center.x + x, center.y + y, center.z + z, x * 0.35, y * 0.35, z * 0.35);
                }
            }
            return;
        }
        if (crafter.originalInputController != null && !crafter.reconcileOriginalInput()) return;
        if (crafter.phase != Phase.ACCEPTING) {
            boolean signal = level.hasNeighborSignal(pos);
            if (signal && !crafter.wasPoweredBefore) crafter.checkCompletedRecipe(true);
            crafter.wasPoweredBefore = signal;
        }
        if (crafter.phase == Phase.IDLE) {
            if (level.getGameTime() % 20 == 0) ConnectedInputHandler.reconcile(crafter);
            if (crafter.checkRequested || level.getGameTime() % 20 == 0) { boolean powered=crafter.poweredCheckRequested;crafter.checkRequested=false;crafter.poweredCheckRequested=false;crafter.checkCompletedRecipe(powered); }
            return;
        }
        if (!crafter.sessionLoaded()) return;
        if (crafter.phase == Phase.ACCEPTING || crafter.phase == Phase.WAITING) return;
        if (crafter.phase == Phase.INSERTING) { if (level.getGameTime() % 20 == 0 || level.getBlockEntity(pos.relative(crafter.getTargetDirection())) instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity) crafter.tryInsert(); return; }
        crafter.countDown -= crafter.getCountDownSpeed(); crafter.setChanged();
        if (crafter.countDown >= 0) return;
        crafter.countDown = 0;
        if (crafter.phase == Phase.ASSEMBLING) {
            if (RecipeGridHandler.getTargetingCrafter(crafter) != null) { crafter.phase = Phase.EXPORTING; crafter.countDown = crafter.groupedItems.onlyEmptyItems() ? 0 : 1000; crafter.contentsChanged(); return; }
            var result = RecipeGridHandler.tryToApplyRecipe((ServerLevel) level, crafter.groupedItems);
            if (result == null || result.isEmpty()) { crafter.ejectWholeGrid(); return; }
            var containers = new ArrayList<ItemStack>();
            for (var stack : crafter.groupedItems.grid.values()) if (!stack.isEmpty() && stack.getItem().getCraftingRemainder() != null) containers.add(stack.getItem().getCraftingRemainder().create());
            crafter.groupedItemsBeforeCraft = crafter.groupedItems.copy(); crafter.groupedItems = new GroupedItems(result);
            for (int i = 0; i < containers.size(); i++) crafter.groupedItems.grid.put(new Cell(i + 1, 0), containers.get(i));
            crafter.phase = Phase.CRAFTING; crafter.countDown = 2000; crafter.contentsChanged(); return;
        }
        if (crafter.phase == Phase.EXPORTING) {
            var target = RecipeGridHandler.getTargetingCrafter(crafter);
            if (target == null || !Objects.equals(target.session, crafter.session)) { crafter.ejectWholeGrid(); return; }
            crafter.groupedItems.mergeOnto(target.groupedItems, state.getValue(MechanicalCrafterBlock.POINTING));
            crafter.groupedItems = new GroupedItems(); crafter.phase = Phase.WAITING;
            level.playSound(null, pos, AllSoundEvents.CRAFTER_CLICK, SoundSource.BLOCKS, 1, Math.min(2, target.groupedItems.grid.size() / 16f + 0.5f));
            crafter.contentsChanged(); target.continueIfAllPrecedingFinished(); target.contentsChanged(); return;
        }
        if (crafter.phase == Phase.CRAFTING) { level.playSound(null, pos, AllSoundEvents.CRAFTER_CRAFT, SoundSource.BLOCKS, 1, 1); crafter.tryInsert(); }
    
        }
    }
    public CrafterInventory getInventory() { return inventory; }

    @Override public void markVirtual() { super.markVirtual(); setLazyTickRate(20); }

    /** Source Ponder result; ordinary server crafting never reads this field. */
    public void setScriptedResult(ItemStack scriptedResult) {
        this.scriptedResult = Objects.requireNonNull(scriptedResult).copy();
    }

    @Override public void tick() {
        if (level == null) { super.tick(); return; }
        tick(level, worldPosition, getBlockState(), this);
    }

    @Override public void lazyTick() {
        super.lazyTick();
        if (!isVirtual() || level == null) return;
        if (phase == Phase.IDLE && !inventory.isResourceBlank()) checkCompletedRecipe(false);
        if (phase == Phase.INSERTING) tryInsert();
    }

    private boolean checkVirtualCompletedRecipe(boolean poweredStart) {
        if (originalInputController != null && !reconcileVirtualInput()) return false;
        if (level == null || getSpeed() == 0 || phase != Phase.IDLE) return false;
        var chain = RecipeGridHandler.getAllCraftersOfChain(this);
        if (chain == null || chain.stream().anyMatch(c -> !c.isVirtual() || c.level != level || c.phase != Phase.IDLE)) return false;
        // Source powered starts permit gaps; normal starts require an item or cover in every slot.
        boolean anyPresent = chain.stream().anyMatch(c -> !c.inventory.isResourceBlank() || !poweredStart && c.covered);
        if (!anyPresent || !poweredStart && chain.stream().anyMatch(c -> c.inventory.isResourceBlank() && !c.covered)) return false;
        var stacks = chain.stream().map(c -> c.inventory.stack()).toList();
        try (Transaction transaction = Transaction.openOuter()) {
            chain.forEach(c -> c.inventory.take(transaction));
            transaction.commit();
        }
        for (int i = 0; i < chain.size(); i++) {
            var member = chain.get(i);
            member.groupedItems = new GroupedItems(stacks.get(i));
            member.groupedItemsBeforeCraft = new GroupedItems();
            member.phase = Phase.ACCEPTING;
            member.countDown = 0;
            member.checkRequested = false;
            member.poweredCheckRequested = false;
            if (RecipeGridHandler.getPrecedingCrafters(member).isEmpty()) {
                member.phase = Phase.ASSEMBLING;
                member.countDown = 1;
            }
            member.contentsChanged();
        }
        return true;
    }

    /** Genuine source phases run against this virtual world's actual local owners and inventories. */
    private void tickVirtual() {
        if (!isVirtual() || level == null) return;
        if (originalInputController != null && !reconcileVirtualInput()) return;
        if (phase == Phase.ACCEPTING) return;
        boolean signal = level.hasNeighborSignal(worldPosition);
        if (signal && !wasPoweredBefore) checkCompletedRecipe(true);
        wasPoweredBefore = signal;
        if (phase == Phase.IDLE) {
            if (checkRequested) {
                boolean powered = poweredCheckRequested;
                checkRequested = false;
                poweredCheckRequested = false;
                checkCompletedRecipe(powered);
            }
            return;
        }
        if (phase == Phase.WAITING) return;
        if (phase == Phase.INSERTING) {
            if (level.getBlockEntity(worldPosition.relative(getTargetDirection())) instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity) tryInsert();
            return;
        }
        int previous = countDown;
        countDown -= getCountDownSpeed();
        if (phase == Phase.CRAFTING) {
            emitVirtualCraftingParticles(previous);
            if (countDown < 1000 && previous >= 1000) {
                level.playSound(null, worldPosition, AllSoundEvents.CRAFTER_CLICK, SoundSource.BLOCKS, 1, 2);
                level.playSound(null, worldPosition, AllSoundEvents.CRAFTER_CRAFT, SoundSource.BLOCKS, 1, 1);
            }
        }
        if (countDown >= 0) return;
        countDown = 0;
        if (phase == Phase.ASSEMBLING) {
            var target = RecipeGridHandler.getTargetingCrafter(this);
            if (target != null) {
                if (!target.isVirtual() || target.level != level) return;
                phase = Phase.EXPORTING;
                countDown = groupedItems.onlyEmptyItems() ? 0 : 1000;
                contentsChanged();
                return;
            }
            var containers = new ArrayList<ItemStack>();
            for (var stack : groupedItems.grid.values())
                if (!stack.isEmpty() && stack.getItem().getCraftingRemainder() != null)
                    containers.add(stack.getItem().getCraftingRemainder().create());
            groupedItemsBeforeCraft = groupedItems;
            groupedItems = new GroupedItems(scriptedResult);
            for (int i = 0; i < containers.size(); i++) groupedItems.grid.put(new Cell(i + 1, 0), containers.get(i));
            phase = Phase.CRAFTING;
            countDown = 2000;
            contentsChanged();
            return;
        }
        if (phase == Phase.EXPORTING) {
            var target = RecipeGridHandler.getTargetingCrafter(this);
            if (target == null) { ejectWholeGrid(); return; }
            if (!target.isVirtual() || target.level != level) return;
            boolean empty = groupedItems.onlyEmptyItems();
            groupedItems.mergeOnto(target.groupedItems, getBlockState().getValue(MechanicalCrafterBlock.POINTING));
            groupedItems = new GroupedItems();
            phase = Phase.WAITING;
            if (!empty) level.playSound(null, worldPosition, AllSoundEvents.CRAFTER_CLICK, SoundSource.BLOCKS, 1, target.groupedItems.grid.size() / 16f + .5f);
            contentsChanged();
            target.continueIfAllPrecedingFinished();
            target.contentsChanged();
            return;
        }
        if (phase == Phase.CRAFTING) tryInsert();
    }

    private void emitVirtualCraftingParticles(int previous) {
        if (!level.isClientSide() || getSpeed() == 0) return;
        var front = getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING);
        var center = Vec3.atCenterOf(worldPosition).add(front.getStepX() * .65, 0, front.getStepZ() * .65);
        if (countDown > 1000) level.addParticle(net.minecraft.core.particles.ParticleTypes.CRIT, center.x, center.y, center.z, 0, 0, 0);
        var result = groupedItems.grid.getOrDefault(new Cell(0, 0), ItemStack.EMPTY);
        if (previous >= 1000 && countDown < 1000 && !result.isEmpty()) {
            groupedItemsBeforeCraft = new GroupedItems();
            for (int i = 0; i < 10; i++) {
                double x = front.getAxis() == Direction.Axis.X ? 0 : (level.getRandom().nextDouble() - .5) * .5;
                double y = (level.getRandom().nextDouble() - .5) * .5, z = front.getAxis() == Direction.Axis.Z ? 0 : (level.getRandom().nextDouble() - .5) * .5;
                level.addParticle(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(result)), center.x + x, center.y + y, center.z + z, x * .35, y * .35, z * .35);
            }
        }
    }

    private void ejectVirtualGrid() {
        if (level == null) return;
        var chain = RecipeGridHandler.getAllCraftersOfChain(this);
        if (chain == null || chain.stream().anyMatch(c -> !c.isVirtual() || c.level != level)) return;
        chain.forEach(MechanicalCrafterBlockEntity::eject);
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel) { if (covered) dropItem(new ItemStack(AllItems.CRAFTER_SLOT_COVER)); ejectWholeGrid(); ConnectedInputHandler.onRemoved(this); }
        super.preRemoveSideEffects(pos, state);
    }
    @Override protected void write(ValueOutput out, boolean clientPacket) {
        super.write(out, clientPacket); inventory.save(out.child("Inventory"));
        out.store("GroupedItems", Entry.CODEC.listOf(), groupedItems.entries()); out.store("BeforeCraft", Entry.CODEC.listOf(), groupedItemsBeforeCraft.entries());
        out.putString("Phase", phase.name()); out.putInt("CountDown", countDown); out.putBoolean("Cover", covered);
        out.store("ConnectedInput", BlockPos.CODEC.listOf(), inputGroup.stream().map(p -> p.subtract(worldPosition)).toList());
        if (originalInputController != null) {
            var original = out.child("OriginalConnectedInput");
            boolean root = originalInputController.equals(worldPosition);
            original.putBoolean("Controller", root);
            original.store("Data", BlockPos.CODEC.listOf(), root
                ? originalInputMembers.stream().map(pos -> pos.subtract(worldPosition)).toList()
                : List.of(originalInputController.subtract(worldPosition)));
        }
        if (session != null) {
            out.store("Session", UUIDUtil.CODEC, session); out.store("Participants", BlockPos.CODEC.listOf(), participants);
            if (sessionTarget != null) out.store("SessionTarget", Direction.CODEC, sessionTarget);
            if (sessionFacing != null) out.store("SessionFacing", Direction.CODEC, sessionFacing);
        }
    }
    @Override protected void read(ValueInput in, boolean clientPacket) {
        var previousGroup = inputGroup;
         inventory.load(in.childOrEmpty("Inventory"));
        groupedItems = new GroupedItems(); groupedItems.load(in.read("GroupedItems", Entry.CODEC.listOf()).orElse(List.of()));
        groupedItemsBeforeCraft = new GroupedItems(); groupedItemsBeforeCraft.load(in.read("BeforeCraft", Entry.CODEC.listOf()).orElse(List.of()));
        try { phase = Phase.valueOf(in.getStringOr("Phase", "IDLE")); } catch (IllegalArgumentException ignored) { phase = Phase.IDLE; }
        countDown = Math.clamp(in.getIntOr("CountDown", 0), 0, 2000); covered = in.getBooleanOr("Cover", false);
        var group = new HashSet<BlockPos>(); in.read("ConnectedInput", BlockPos.CODEC.listOf()).orElse(List.of()).forEach(p -> group.add(worldPosition.offset(p))); group.add(worldPosition); inputGroup = Set.copyOf(group);
        originalInputController = null; originalInputMembers = Set.of();
        if (in.read("OriginalConnectedInput", com.mojang.serialization.Codec.PASSTHROUGH).isPresent()) {
            var original = in.childOrEmpty("OriginalConnectedInput");
            var offsets = original.read("Data", BlockPos.CODEC.listOf()).orElseThrow(() -> new IllegalArgumentException("Malformed original crafter input references"));
            boolean root = original.getBooleanOr("Controller", false);
            if (offsets.isEmpty() || root && !offsets.contains(BlockPos.ZERO) || !root && (offsets.size() != 1 || offsets.contains(BlockPos.ZERO))
                    || new HashSet<>(offsets).size() != offsets.size()) throw new IllegalArgumentException("Invalid original crafter input owner map");
            originalInputController = root ? worldPosition : worldPosition.offset(offsets.getFirst());
            if (root) originalInputMembers = Set.copyOf(offsets.stream().map(worldPosition::offset).toList());
            inputGroup = Set.of(); // No partial input group is exposed to external storage before reconciliation.
        }
        session = in.read("Session", UUIDUtil.CODEC).orElse(null); participants = in.read("Participants", BlockPos.CODEC.listOf()).orElse(List.of());
        if (session != null && originalInputController != null) throw new IllegalArgumentException("Mixed original and native crafter session ownership");
        sessionTarget = in.read("SessionTarget", Direction.CODEC).orElse(getTargetDirection()); sessionFacing = in.read("SessionFacing", Direction.CODEC).orElse(getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING));
        if (level != null && level.isClientSide() && !Objects.equals(previousGroup, inputGroup)) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
    
        super.read(in, clientPacket);
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("mechanical_crafter");
        ids.add("crafter_lazy_000");
    }
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return sourceAdvancements;}
}
