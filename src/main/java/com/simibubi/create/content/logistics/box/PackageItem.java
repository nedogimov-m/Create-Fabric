package com.simibubi.create.content.logistics.box;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.*;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Consumer;
import net.createmod.catnip.data.Glob;
import net.minecraft.ChatFormatting;
import net.minecraft.core.*;
import net.minecraft.core.particles.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** Original package item semantics using native components and entity synchronization. */
public class PackageItem extends Item {
    public static final int SLOTS=9;
    public final PackageStyles.PackageStyle style;
    public PackageItem(Properties properties,PackageStyles.PackageStyle style){
        super(properties.overrideDescription("item.create."+(style.rare()?"rare_package":"package")));this.style=style;
        PackageStyles.ALL_BOXES.add(this);(style.rare()?PackageStyles.RARE_BOXES:PackageStyles.STANDARD_BOXES).add(this);
    }
    public static boolean isPackage(ItemStack stack){return stack.getItem() instanceof PackageItem;}
    public static ItemStack containingSlots(List<ItemStack> slots){var box=PackageStyles.getRandomBox();box.set(AllDataComponents.PACKAGE_CONTENTS,ItemContainerContents.fromItems(slots.stream().limit(SLOTS).map(ItemStack::copy).toList()));return box;}
    @Override public boolean canFitInsideContainerItems(){return false;}
    public static ItemStack containing(List<ItemStack> stacks){
        var contents=NonNullList.withSize(SLOTS,ItemStack.EMPTY);
        for(var input:stacks){var remaining=input.copy();
            for(int i=0;i<SLOTS&&!remaining.isEmpty();i++){var present=contents.get(i);if(present.isEmpty()||!ItemStack.isSameItemSameComponents(present,remaining))continue;int moved=Math.min(remaining.getCount(),Math.max(0,present.getMaxStackSize()-present.getCount()));present.grow(moved);remaining.shrink(moved);}
            for(int i=0;i<SLOTS&&!remaining.isEmpty();i++)if(contents.get(i).isEmpty()){int moved=Math.min(remaining.getCount(),remaining.getMaxStackSize());contents.set(i,remaining.copyWithCount(moved));remaining.shrink(moved);}
        }
        var box=PackageStyles.getRandomBox();box.set(AllDataComponents.PACKAGE_CONTENTS,ItemContainerContents.fromItems(contents));return box;
    }
    public static NonNullList<ItemStack> getContents(ItemStack box){var contents=NonNullList.withSize(SLOTS,ItemStack.EMPTY);var copies=box.getOrDefault(AllDataComponents.PACKAGE_CONTENTS,ItemContainerContents.EMPTY).itemCopies().limit(SLOTS).toList();for(int i=0;i<copies.size();i++)contents.set(i,copies.get(i));return contents;}
    public static void clearAddress(ItemStack box){box.remove(AllDataComponents.PACKAGE_ADDRESS);}
    public static void addAddress(ItemStack box,String address){box.set(AllDataComponents.PACKAGE_ADDRESS,address);}
    public static String getAddress(ItemStack box){return box.getOrDefault(AllDataComponents.PACKAGE_ADDRESS,"");}
    public static boolean matchAddress(ItemStack box,String address){return matchAddress(getAddress(box),address);}
    public static boolean matchAddress(String boxAddress,String address){
        if(address.isBlank())return boxAddress.isBlank();if(address.equals("*")||boxAddress.equals("*")||address.equals(boxAddress))return true;
        return address.matches(Glob.toRegexPattern(boxAddress,""))||boxAddress.matches(Glob.toRegexPattern(address,""));
    }
    public static float getWidth(ItemStack box){return box.getItem() instanceof PackageItem item?item.style.width()/16f:1;}
    public static float getHeight(ItemStack box){return box.getItem() instanceof PackageItem item?item.style.height()/16f:1;}
    public static float getHookDistance(ItemStack box){return box.getItem() instanceof PackageItem item?item.style.riggingOffset()/16f:1;}
    public static void setOrder(ItemStack box,int orderId,int linkIndex,boolean finalLink,int fragmentIndex,boolean isFinal,PackageOrderWithCrafts context){box.set(AllDataComponents.PACKAGE_ORDER_DATA,new PackageOrderData(orderId,linkIndex,finalLink,fragmentIndex,isFinal,context));}
    public static boolean hasOrderData(ItemStack box){return box.has(AllDataComponents.PACKAGE_ORDER_DATA);}
    public static int getOrderId(ItemStack box){var data=box.get(AllDataComponents.PACKAGE_ORDER_DATA);return data==null?-1:data.orderId();}
    public static int getIndex(ItemStack box){var data=box.get(AllDataComponents.PACKAGE_ORDER_DATA);return data==null?-1:data.fragmentIndex();}
    public static int getLinkIndex(ItemStack box){var data=box.get(AllDataComponents.PACKAGE_ORDER_DATA);return data==null?-1:data.linkIndex();}
    public static boolean isFinal(ItemStack box){var data=box.get(AllDataComponents.PACKAGE_ORDER_DATA);return data!=null&&data.isFinal();}
    public static boolean isFinalLink(ItemStack box){var data=box.get(AllDataComponents.PACKAGE_ORDER_DATA);return data!=null&&data.isFinalLink();}
    public static PackageOrderWithCrafts getOrderContext(ItemStack box){var data=box.get(AllDataComponents.PACKAGE_ORDER_DATA);return data!=null?data.orderContext():box.get(AllDataComponents.PACKAGE_ORDER_CONTEXT);}
    public static void addOrderContext(ItemStack box,PackageOrderWithCrafts context){box.set(AllDataComponents.PACKAGE_ORDER_CONTEXT,context);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> lines,TooltipFlag flag){
        if(stack.has(AllDataComponents.PACKAGE_ADDRESS))lines.accept(Component.literal("→ "+getAddress(stack)).withStyle(ChatFormatting.GOLD));
        int visible=0,skipped=0;for(var entry:getContents(stack)){if(entry.isEmpty()||entry.getItem() instanceof SpawnEggItem)continue;if(visible++>=3){skipped++;continue;}lines.accept(entry.getHoverName().copy().append(" x"+entry.getCount()).withStyle(ChatFormatting.GRAY));}
        if(skipped>0)lines.accept(Component.translatable("container.shulkerBox.more",skipped).withStyle(ChatFormatting.ITALIC));
    }
    @Override public int getUseDuration(ItemStack stack,LivingEntity entity){return 72000;}
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack){return ItemUseAnimation.BOW;}
    public InteractionResult open(Level level,Player player,InteractionHand hand){
        var box=player.getItemInHand(hand);var particle=box.copy();var contents=getContents(box);box.shrink(1);
        if(level instanceof ServerLevel server){
            for(var entry:contents){if(entry.isEmpty())continue;spawnEgg(server,entry,BlockPos.containing(player.position().add(player.getLookAngle().multiply(1,0,1).normalize())),player);player.getInventory().placeItemBackInInventory(entry.copy(),net.minecraft.util.Prediction.SERVER_ONLY);}
            pop(server,player.position().add(0,.5,0).add(player.getLookAngle().scale(.5)),particle);
        }
        return InteractionResult.SUCCESS;
    }
    public static void spawnEgg(ServerLevel level,ItemStack stack,BlockPos pos,LivingEntity owner){
        if(!(stack.getItem() instanceof SpawnEggItem))return;var type=SpawnEggItem.getType(stack);
        if(type!=null&&type.spawn(level,stack,owner,pos,EntitySpawnReason.SPAWN_ITEM_USE,false,false)!=null)stack.shrink(1);
    }
    public static void pop(ServerLevel level,Vec3 position,ItemStack box){
        level.playSound(null,BlockPos.containing(position),AllSoundEvents.PACKAGE_POP,SoundSource.NEUTRAL,1,1);
        if(!box.isEmpty())level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM,ItemStackTemplate.fromNonEmptyStack(box)),position.x,position.y,position.z,10,.25,.25,.25,.125);
    }
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();if(player==null)return InteractionResult.PASS;if(player.isShiftKeyDown())return open(context.getLevel(),player,context.getHand());
        var point=context.getClickLocation();float height=style.height()/16f,radius=style.width()/32f;
        if(context.getClickedFace()==Direction.DOWN)point=point.subtract(0,height+.25,0);else if(context.getClickedFace().getAxis().isHorizontal())point=point.add(context.getClickedFace().getUnitVec3().scale(radius));
        var scan=new AABB(point,point).inflate(radius,0,radius).expandTowards(0,height,0);var level=context.getLevel();
        if(!level.getEntities(AllEntityTypes.PACKAGE,scan,entity->true).isEmpty())return InteractionResult.PASS;
        if(level instanceof ServerLevel){var entity=PackageEntity.fromItemStack(level,point,context.getItemInHand());if(!level.addFreshEntity(entity))return InteractionResult.FAIL;context.getItemInHand().shrink(1);}
        return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){if(player.isShiftKeyDown())return open(level,player,hand);player.startUsingItem(hand);return InteractionResult.SUCCESS;}
    @Override public boolean releaseUsing(ItemStack stack,Level level,LivingEntity entity,int remaining){
        if(!(entity instanceof Player player))return false;int used=getUseDuration(stack,entity)-remaining;if(used<0)return false;float speed=getPackageVelocity(used);if(speed<.1||level.isClientSide())return false;
        var motion=entity.getLookAngle().scale(speed*2);var point=entity.position().add(0,entity.getBoundingBox().getYsize()/2,0).add(motion);var box=PackageEntity.fromItemStack(level,point,stack);
        box.setDeltaMovement(motion);box.tossedBy=new WeakReference<>(player);if(!level.addFreshEntity(box))return false;if(!player.getAbilities().instabuild)stack.shrink(1);
        level.playSound(null,player.getX(),player.getY(),player.getZ(),SoundEvents.SNOWBALL_THROW,SoundSource.NEUTRAL,.5f,.5f);return true;
    }
    public static float getPackageVelocity(int ticks){float value=ticks/20f;return Math.min(1,(value*value+value*2)/3);}
    public record PackageOrderData(int orderId,int linkIndex,boolean isFinalLink,int fragmentIndex,boolean isFinal,PackageOrderWithCrafts orderContext){
        public static final Codec<PackageOrderData> CODEC=RecordCodecBuilder.create(i->i.group(Codec.INT.fieldOf("order_id").forGetter(PackageOrderData::orderId),Codec.INT.fieldOf("link_index").forGetter(PackageOrderData::linkIndex),Codec.BOOL.fieldOf("is_final_link").forGetter(PackageOrderData::isFinalLink),Codec.INT.fieldOf("fragment_index").forGetter(PackageOrderData::fragmentIndex),Codec.BOOL.fieldOf("is_final").forGetter(PackageOrderData::isFinal),PackageOrderWithCrafts.CODEC.optionalFieldOf("order_context").forGetter(data->Optional.ofNullable(data.orderContext))).apply(i,(order,link,lastLink,index,last,context)->new PackageOrderData(order,link,lastLink,index,last,context.orElse(null))));
        public static final StreamCodec<RegistryFriendlyByteBuf,PackageOrderData> STREAM_CODEC=StreamCodec.composite(ByteBufCodecs.INT,PackageOrderData::orderId,ByteBufCodecs.INT,PackageOrderData::linkIndex,ByteBufCodecs.BOOL,PackageOrderData::isFinalLink,ByteBufCodecs.INT,PackageOrderData::fragmentIndex,ByteBufCodecs.BOOL,PackageOrderData::isFinal,ByteBufCodecs.optional(PackageOrderWithCrafts.STREAM_CODEC),data->Optional.ofNullable(data.orderContext),(order,link,lastLink,index,last,context)->new PackageOrderData(order,link,lastLink,index,last,context.orElse(null)));
    }
}
