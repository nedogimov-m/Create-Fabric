package com.simibubi.create.content.decoration.copycat;

import com.simibubi.create.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.Shapes;

/** Source material admission; the actual consumed stack belongs to the native block entity. */
public abstract class CopycatBlock extends Block implements EntityBlock {
    public static final TagKey<Block> COPYCAT_ALLOW=TagKey.create(Registries.BLOCK,Create.asResource("copycat_allow")),COPYCAT_DENY=TagKey.create(Registries.BLOCK,Create.asResource("copycat_deny"));
    /** Native light-engine input replaces the former NeoForge auxiliary-light attachment. */
    public static final IntegerProperty LIGHT= IntegerProperty.create("light",0,15);
    protected CopycatBlock(Properties properties){super(properties.lightLevel(state->state.getValue(LIGHT)));registerDefaultState(defaultBlockState().setValue(LIGHT,0));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(LIGHT);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new CopycatBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return !level.isClientSide()&&type==AllBlockEntityTypes.COPYCAT?(world,pos,block,entity)->CopycatBlockEntity.tick(world,pos,block,(CopycatBlockEntity)entity):null;}
    public static BlockState getMaterial(BlockGetter level,BlockPos pos){return level.getBlockEntityRenderData(pos) instanceof CopycatBlockEntity.RenderData data?data.material():Blocks.AIR.defaultBlockState();}
    @Override public BlockState getAppearance(BlockState state,BlockAndLightGetter level,BlockPos pos,Direction side,BlockState queryState,BlockPos queryPos){return isIgnoredConnectivitySide(level,state,side,pos,queryPos)?state:getMaterial(level,pos);}

    public BlockState getAcceptedBlockState(Level level,BlockPos pos,ItemStack stack,Direction face){
        if(stack.isEmpty()||!(stack.getItem() instanceof BlockItem item)||item.getBlock() instanceof CopycatBlock)return null;
        var block=item.getBlock();var material=block.defaultBlockState();
        if(!material.is(COPYCAT_ALLOW)&&!isAcceptedRegardless(material)){
            if(material.is(COPYCAT_DENY)||block instanceof EntityBlock||block instanceof StairBlock)return null;
            if(level!=null){var shape=material.getShape(level,pos);if(shape.isEmpty()||!shape.bounds().equals(Shapes.block().bounds())||material.getCollisionShape(level,pos).isEmpty())return null;}
        }
        if(face!=null){var axis=face.getAxis();if(material.hasProperty(BlockStateProperties.FACING))material=material.setValue(BlockStateProperties.FACING,face);if(material.hasProperty(BlockStateProperties.HORIZONTAL_FACING)&&axis!=Direction.Axis.Y)material=material.setValue(BlockStateProperties.HORIZONTAL_FACING,face);if(material.hasProperty(BlockStateProperties.AXIS))material=material.setValue(BlockStateProperties.AXIS,axis);if(material.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)&&axis!=Direction.Axis.Y)material=material.setValue(BlockStateProperties.HORIZONTAL_AXIS,axis);}
        return material;
    }
    public boolean isAcceptedRegardless(BlockState material){return false;}
    public BlockState prepareMaterial(Level level,BlockPos pos,BlockState state,Player player,InteractionHand hand,BlockHitResult hit,BlockState material){return material;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,pos))return InteractionResult.PASS;
        var material=getAcceptedBlockState(level,pos,stack,hit.getDirection());if(material!=null)material=prepareMaterial(level,pos,state,player,hand,hit,material);
        if(material==null||!(LoadedLevelAccess.blockEntity(level,pos) instanceof CopycatBlockEntity copycat))return InteractionResult.PASS;
        if(copycat.getMaterial().is(material.getBlock())){if(level.isClientSide())return copycat.canCycleMaterial()?InteractionResult.SUCCESS:InteractionResult.PASS;if(!copycat.cycleMaterial())return InteractionResult.PASS;level.playSound(null,pos,SoundEvents.ITEM_FRAME_ADD_ITEM,SoundSource.BLOCKS,.75f,.95f);return InteractionResult.SUCCESS;}
        if(copycat.hasCustomMaterial()||copycat.ownsConsumedItem())return InteractionResult.PASS;if(level.isClientSide())return InteractionResult.SUCCESS;
        // Real payment occurs before any native material/light/update callbacks. No component is stripped.
        var paid=player.isCreative()?stack.copyWithCount(1):stack.split(1);if(paid.isEmpty())return InteractionResult.FAIL;
        copycat.installPaidMaterial(material,paid);level.playSound(null,pos,material.getSoundType().getPlaceSound(),SoundSource.BLOCKS,1,.75f);return InteractionResult.SUCCESS;
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);if(placer==null||level.isClientSide()||!(LoadedLevelAccess.blockEntity(level,pos) instanceof CopycatBlockEntity copycat)||copycat.hasCustomMaterial()||copycat.ownsConsumedItem())return;
        var offhand=placer.getItemInHand(InteractionHand.OFF_HAND);var material=getAcceptedBlockState(level,pos,offhand,Direction.orderedByNearest(placer)[0]);if(material==null)return;
        var paid=placer instanceof Player player&&player.isCreative()?offhand.copyWithCount(1):offhand.split(1);if(!paid.isEmpty())copycat.installPaidMaterial(material,paid);
    }
    /** Native WrenchItem dispatch; protection precedes both material and wrapper transfers. */
    public InteractionResult onWrenched(BlockState state,UseOnContext context){
        return wrench(state,context,false);
    }
    public InteractionResult onSneakWrenched(BlockState state,UseOnContext context){return wrench(state,context,true);}
    private InteractionResult wrench(BlockState state,UseOnContext context,boolean removing){
        var level=context.getLevel();var pos=context.getClickedPos();var player=context.getPlayer();if(player==null||!player.mayBuild()||!level.mayInteract(player,pos))return InteractionResult.PASS;
        if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof CopycatBlockEntity copycat))return InteractionResult.PASS;
        if(!removing&&!copycat.hasCustomMaterial()&&!copycat.ownsConsumedItem())return InteractionResult.PASS;if(level.isClientSide())return InteractionResult.SUCCESS;
        if(!(level instanceof ServerLevel server))return InteractionResult.FAIL;
        if(removing&&!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level,player,pos,state,copycat))return InteractionResult.FAIL;
        var material=copycat.takeConsumedItem();if(!player.isCreative()&&!material.isEmpty())player.getInventory().placeItemBackInInventory(material,Prediction.SERVER_ONLY);
        copycat.setMaterial(AllBlocks.COPYCAT_BASE.defaultBlockState());level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK,pos,Block.getId(state));
        if(!removing)return InteractionResult.SUCCESS;
        if(LoadedLevelAccess.blockEntity(level,pos)!=copycat||LoadedLevelAccess.blockState(level,pos).getBlock()!=this)return InteractionResult.FAIL;
        if(!player.isCreative())Block.getDrops(state,server,pos,copycat,player,context.getItemInHand()).forEach(drop->player.getInventory().placeItemBackInInventory(drop,Prediction.SERVER_ONLY));
        if(!level.destroyBlock(pos,false))return InteractionResult.FAIL;PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level,player,pos,state,copycat);return InteractionResult.SUCCESS;
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){if(player.isCreative()&&LoadedLevelAccess.blockEntity(level,pos) instanceof CopycatBlockEntity copycat)copycat.takeConsumedItem();return super.playerWillDestroy(level,pos,state,player);}
    @Override protected float getDestroyProgress(BlockState state,Player player,BlockGetter level,BlockPos pos){var material=getMaterial(level,pos);return material.isAir()?super.getDestroyProgress(state,player,level,pos):material.getDestroyProgress(player,level,pos);}
    @Override protected ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state,boolean includeData){var material=getMaterial(level,pos);return material.isAir()||material.is(AllBlocks.COPYCAT_BASE)?new ItemStack(this):material.getCloneItemStack(level,pos,includeData);}
    @Override public float getProvidedEnchantmentPower(BlockState state,BlockGetter level,BlockPos pos){var material=getMaterial(level,pos);return material.isAir()?0:((net.fabricmc.fabric.api.block.v1.FabricBlock)material.getBlock()).getProvidedEnchantmentPower(material,level,pos);}
    @Override public void fallOn(Level level,BlockState state,BlockPos pos,Entity entity,double distance){var material=getMaterial(level,pos);if(material.isAir())super.fallOn(level,state,pos,entity,distance);else material.getBlock().fallOn(level,material,pos,entity,distance);}
    @Override public void spawnDestroyParticles(Level level,BlockPos pos,BlockState state){super.spawnDestroyParticles(level,pos,CopycatMaterialHooks.material(level,pos,state));}
    @Override public void spawnDestroyByEntityParticles(Level level,Entity entity,BlockPos pos,BlockState state){super.spawnDestroyByEntityParticles(level,entity,pos,CopycatMaterialHooks.material(level,pos,state));}
    public boolean isIgnoredConnectivitySide(BlockGetter level,BlockState state,Direction face,BlockPos from,BlockPos to){return false;}
    public abstract boolean canConnectTexturesToward(BlockGetter level,BlockPos from,BlockPos to,BlockState state);
    public boolean canFaceBeOccluded(BlockState state,Direction face){return false;}
    public boolean shouldFaceAlwaysRender(BlockState state,Direction face){return false;}
    public boolean hidesNeighborFace(BlockGetter level,BlockPos pos,BlockState state,BlockState neighbor,Direction direction){return false;}
}
