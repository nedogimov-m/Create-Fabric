package com.simibubi.create.impl.contraption;

import com.simibubi.create.Create;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.api.contraption.BlockMovementChecks.*;
import com.simibubi.create.api.contraption.ContraptionMovementSetting;
import com.simibubi.create.content.contraptions.chassis.AbstractChassisBlock;
import com.simibubi.create.content.contraptions.chassis.StickerBlock;
import com.simibubi.create.content.decoration.steamWhistle.*;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.kinetics.crank.HandCrankBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.PushReaction;

/** Original ordered checks and vanilla fallbacks. Unported controller-specific checks are added with their controllers. */
public final class BlockMovementChecksImpl {
    private static final List<MovementNecessaryCheck> NECESSARY=new CopyOnWriteArrayList<>();
    private static final List<MovementAllowedCheck> ALLOWED=new CopyOnWriteArrayList<>();
    private static final List<BrittleCheck> BRITTLE=new CopyOnWriteArrayList<>();
    private static final List<AttachedCheck> ATTACHED=new CopyOnWriteArrayList<>();
    private static final List<NotSupportiveCheck> NOT_SUPPORTIVE=new CopyOnWriteArrayList<>();
    private static TagKey<Block> tag(String path){return TagKey.create(Registries.BLOCK,Create.asResource(path));}
    private static final TagKey<Block> MOVABLE_EMPTY=tag("movable_empty_collider"),NON_MOVABLE=tag("non_movable"),BRITTLE_TAG=tag("brittle");
    private static final TagKey<Block> NO_RELOCATION=TagKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath("c","relocation_not_supported"));

    public static void registerMovementNecessaryCheck(MovementNecessaryCheck check){NECESSARY.addFirst(Objects.requireNonNull(check));}
    public static void registerMovementAllowedCheck(MovementAllowedCheck check){ALLOWED.addFirst(Objects.requireNonNull(check));}
    public static void registerBrittleCheck(BrittleCheck check){BRITTLE.addFirst(Objects.requireNonNull(check));}
    public static void registerAttachedCheck(AttachedCheck check){ATTACHED.addFirst(Objects.requireNonNull(check));}
    public static void registerNotSupportiveCheck(NotSupportiveCheck check){NOT_SUPPORTIVE.addFirst(Objects.requireNonNull(check));}
    public static boolean isMovementNecessary(BlockState state,Level level,BlockPos pos){
        for(var check:NECESSARY){var result=check.isMovementNecessary(state,level,pos);if(result!=CheckResult.PASS)return result.toBoolean();}
        if(BlockMovementChecks.isBrittle(state)||state.is(MOVABLE_EMPTY))return true;
        return !state.getCollisionShape(level,pos).isEmpty()&&!state.canBeReplaced();
    }
    public static boolean isMovementAllowed(BlockState state,Level level,BlockPos pos){
        for(var check:ALLOWED){var result=check.isMovementAllowed(state,level,pos);if(result!=CheckResult.PASS)return result.toBoolean();}
        if(state.getBlock() instanceof AbstractChassisBlock)return true;
        var setting=ContraptionMovementSetting.get(state);
        if(state.getDestroySpeed(level,pos)==-1||state.is(NO_RELOCATION)||state.is(NON_MOVABLE)||setting==ContraptionMovementSetting.UNMOVABLE)return false;
        // Native 26.3 marks obsidian immoveable for pistons; Create's explicit setting must still enable it.
        if(setting!=null)return true;
        if(state.getBlock() instanceof com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock)return state.getValue(com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock.STATE)!=com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock.PistonState.MOVING;
        if(is(state,"belt")||state.getBlock() instanceof GrindstoneBlock)return true;
        return state.getPistonPushReaction()!=PushReaction.IMMOVEABLE;
    }
    public static boolean isBrittle(BlockState state){
        for(var check:BRITTLE){var result=check.isBrittle(state);if(result!=CheckResult.PASS)return result.toBoolean();}
        var block=state.getBlock();
        if(state.hasProperty(BlockStateProperties.HANGING))return true;
        if(block instanceof LadderBlock||block instanceof BaseTorchBlock||block instanceof SignBlock||block instanceof BasePressurePlateBlock)return true;
        if(block instanceof FaceAttachedHorizontalDirectionalBlock&&!(block instanceof GrindstoneBlock)&&!(block instanceof PackagerLinkBlock))return true;
        if(block instanceof BaseRailBlock||block instanceof DiodeBlock||block instanceof RedstoneWireBlock||block instanceof WoolCarpetBlock)return true;
        return block instanceof WhistleBlock||block instanceof WhistleExtenderBlock||block instanceof BeltFunnelBlock||state.is(BRITTLE_TAG);
    }
    public static boolean isBlockAttachedTowards(BlockState state,Level level,BlockPos pos,Direction direction){
        for(var check:ATTACHED){var result=check.isBlockAttachedTowards(state,level,pos,direction);if(result!=CheckResult.PASS)return result.toBoolean();}
        var block=state.getBlock();
        if(block instanceof com.simibubi.create.content.contraptions.actors.AttachedActorBlock)return direction==state.getValue(HorizontalDirectionalBlock.FACING).getOpposite();
        if(block instanceof LadderBlock)return state.getValue(LadderBlock.FACING)==direction.getOpposite();
        if(block instanceof WallTorchBlock)return state.getValue(WallTorchBlock.FACING)==direction.getOpposite();
        if(block instanceof WallSignBlock)return state.getValue(WallSignBlock.FACING)==direction.getOpposite();
        if(block instanceof StandingSignBlock||block instanceof BasePressurePlateBlock)return direction==Direction.DOWN;
        if(block instanceof DoorBlock)return state.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER&&direction==Direction.UP||direction==Direction.DOWN;
        if(block instanceof AbstractBedBlock){var facing=state.getValue(AbstractBedBlock.FACING);if(state.getValue(AbstractBedBlock.PART)==BedPart.HEAD)facing=facing.getOpposite();return direction==facing;}
        if(block instanceof FlowerPotBlock||block instanceof DiodeBlock||block instanceof RedstoneWireBlock||block instanceof WoolCarpetBlock)return direction==Direction.DOWN;
        if(block instanceof RedstoneWallTorchBlock)return state.getValue(RedstoneWallTorchBlock.FACING)==direction.getOpposite();
        if(block instanceof BaseTorchBlock)return direction==Direction.DOWN;
        if(block instanceof FaceAttachedHorizontalDirectionalBlock){var face=state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE);return switch(face){case CEILING->direction==Direction.UP;case FLOOR->direction==Direction.DOWN;case WALL->direction.getOpposite()==state.getValue(FaceAttachedHorizontalDirectionalBlock.FACING);};}
        if(state.hasProperty(BlockStateProperties.HANGING))return direction==(state.getValue(BlockStateProperties.HANGING)?Direction.UP:Direction.DOWN);
        if(block instanceof BaseRailBlock)return direction==Direction.DOWN;
        if(block instanceof HandCrankBlock)return direction==state.getValue(HandCrankBlock.FACING).getOpposite();
        if(block instanceof BellBlock){var attachment=state.getValue(BlockStateProperties.BELL_ATTACHMENT);return switch(attachment){case FLOOR->direction==Direction.DOWN;case CEILING->direction==Direction.UP;default->direction==state.getValue(HorizontalDirectionalBlock.FACING);};}
        if(block instanceof FluidTankBlock||block instanceof ItemVaultBlock){
            var other=pos.relative(direction);
            if(LoadedLevelAccess.blockEntity(level,pos)==null||LoadedLevelAccess.blockEntity(level,other)==null)return false;
            return ConnectivityHandler.isConnected(level,pos,other);
        }
        if(block instanceof WhistleBlock)return direction==(state.getValue(WhistleBlock.WALL)?state.getValue(WhistleBlock.FACING):Direction.DOWN);
        if(block instanceof WhistleExtenderBlock)return direction==Direction.DOWN;
        if(block instanceof StickerBlock&&state.getValue(StickerBlock.EXTENDED))return StickerBlock.attachedTowards(state,level,pos,direction);
        if(block instanceof com.simibubi.create.content.redstone.link.RedstoneLinkBlock)return direction.getOpposite()==state.getValue(com.simibubi.create.content.redstone.link.RedstoneLinkBlock.FACING);
        return false;
    }
    public static boolean isNotSupportive(BlockState state,Direction facing){
        for(var check:NOT_SUPPORTIVE){var result=check.isNotSupportive(state,facing);if(result!=CheckResult.PASS)return result.toBoolean();}
        if(state.getBlock() instanceof com.simibubi.create.content.contraptions.actors.AttachedActorBlock)return state.getValue(BlockStateProperties.HORIZONTAL_FACING)==facing;
        if(is(state,"mechanical_drill")||is(state,"mechanical_saw"))return state.getValue(BlockStateProperties.FACING)==facing;
        if(state.getBlock() instanceof WoolCarpetBlock)return facing==Direction.UP;
        if(state.getBlock() instanceof StickerBlock&&!state.getValue(StickerBlock.EXTENDED))return facing==state.getValue(StickerBlock.FACING);
        if(state.getBlock() instanceof com.simibubi.create.content.contraptions.piston.PistonExtensionPoleBlock && facing.getAxis()!=state.getValue(BlockStateProperties.FACING).getAxis())return true;
        return BlockMovementChecks.isBrittle(state);
    }
    private static boolean is(BlockState state,String path){return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(Create.asResource(path));}
    private BlockMovementChecksImpl(){}
}
