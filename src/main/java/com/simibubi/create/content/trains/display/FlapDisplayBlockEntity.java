package com.simibubi.create.content.trains.display;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.utility.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.*;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.AABB;

/** Real source controller, layouts and line data on the existing native kinetic network. */
public final class FlapDisplayBlockEntity extends KineticBlockEntity {
    @Override protected boolean isNoisy() { return false; }
    public static final int MIN_SPEED=30,MAX_WIDTH=32,MAX_HEIGHT=33;
    public List<FlapDisplayLayout> lines;
    public boolean isController,isRunning;
    public int xSize=1,ySize=1;
    public DyeColor[] colour=new DyeColor[2];
    public boolean[] glowingLines=new boolean[2],manualLines=new boolean[2];
    public FlapDisplayBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.FLAP_DISPLAY,pos,state);}
    @Override public float getMinimumRequiredSpeed(){return MIN_SPEED;}
    public boolean isSpeedRequirementFulfilled(){return Math.abs(getSpeed())>=MIN_SPEED&&!isOverstressed()&&!isConflicted();}
    @Override public void onSpeedChanged(float previous){isRunning=isSpeedRequirementFulfilled();}
    public static void tick(Level level,BlockPos pos,BlockState state,FlapDisplayBlockEntity entity){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, entity)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level,pos,state,entity);entity.isRunning=entity.isSpeedRequirementFulfilled();if(!level.isClientSide()){if(Math.floorMod(level.getGameTime()+pos.asLong(),10)==0)entity.updateControllerStatus();return;}
        if(!entity.isRunning||!entity.isController)return;int active=0;for(var layout:entity.getLines())for(var section:layout.getSections())active+=section.tick(Math.abs(entity.getSpeed())>128,level.getRandom());if(active==0)return;
        float volume=Math.clamp(active/20f,.25f,1.5f),background=Math.clamp(active/40f,.25f,1);var middle=pos.relative(entity.getDirection().getClockWise(),entity.xSize/2).below(entity.ySize/2);level.playLocalSound(middle.getX(),middle.getY(),middle.getZ(),AllSoundEvents.SCROLL_VALUE,SoundSource.BLOCKS,volume,.56f,false);level.playLocalSound(middle.getX(),middle.getY(),middle.getZ(),SoundEvents.CALCITE_HIT,SoundSource.BLOCKS,.35f*background,1.95f,false);
    
        }
    }
    /** Unknown loaded frontiers defer a decision rather than destroying a saved controller/line state. */
    public void updateControllerStatus(){
        if(level==null||level.isClientSide()||!(getBlockState().getBlock()instanceof FlapDisplayBlock))return;var state=getBlockState();var left=state.getValue(FlapDisplayBlock.HORIZONTAL_FACING).getClockWise();var leftPos=worldPosition.relative(left);if(!LoadedLevelAccess.isLoaded(level,leftPos))return;
        boolean controller=!state.getValue(FlapDisplayBlock.UP)&&LoadedLevelAccess.blockState(level,leftPos)!=state;int width=1,height=1;
        if(controller){for(int offset=1;offset<MAX_WIDTH;offset++){var pos=worldPosition.relative(left.getOpposite(),offset);if(!LoadedLevelAccess.isLoaded(level,pos))return;if(LoadedLevelAccess.blockState(level,pos)!=state)break;width++;}for(int offset=0;offset<32;offset++){var pos=worldPosition.below(offset);if(!LoadedLevelAccess.isLoaded(level,pos))return;if(!LoadedLevelAccess.blockState(level,pos).getOptionalValue(FlapDisplayBlock.DOWN).orElse(false))break;height++;}}
        if(isController==controller&&xSize==width&&ySize==height)return;isController=controller;xSize=width;ySize=height;colour=Arrays.copyOf(colour,ySize*2);glowingLines=Arrays.copyOf(glowingLines,ySize*2);manualLines=new boolean[ySize*2];lines=null;notifyUpdate();
    }
    public FlapDisplayBlockEntity getController(){
        if(isController)return this;if(level==null||!(getBlockState().getBlock()instanceof FlapDisplayBlock))return null;var origin=getBlockState();var left=origin.getValue(FlapDisplayBlock.HORIZONTAL_FACING).getClockWise();var pos=worldPosition;
        for(int i=0;i<64;i++){if(!LoadedLevelAccess.isLoaded(level,pos))return null;var state=LoadedLevelAccess.blockState(level,pos);if(!FlapDisplayBlock.canConnect(origin,state))return null;if(state.getValue(FlapDisplayBlock.UP)){pos=pos.above();continue;}var next=pos.relative(left);if(!LoadedLevelAccess.isLoaded(level,next))return null;var other=LoadedLevelAccess.blockState(level,next);if(FlapDisplayBlock.canConnect(origin,other)&&!other.getValue(FlapDisplayBlock.UP)){pos=next;continue;}return LoadedLevelAccess.blockEntity(level,pos)instanceof FlapDisplayBlockEntity flap&&flap.isController?flap:null;}return null;
    }
    public Direction getDirection(){return getBlockState().getValue(FlapDisplayBlock.HORIZONTAL_FACING).getOpposite();}
    public int getMaxCharCount(){return getMaxCharCount(0);}
    public int getMaxCharCount(int gaps){return Math.max(0,(int)((xSize*16f-2f-4f*Math.max(0,gaps))/3.5f));}
    public int getLineIndexAt(double yCoord){return (int)Math.clamp(Math.floor(2*(worldPosition.getY()-yCoord+1)),0,ySize*2);}
    public List<FlapDisplayLayout> getLines(){if(lines==null)initDefaultSections();return lines;}
    public void initDefaultSections(){lines=new ArrayList<>();for(int i=0;i<ySize*2;i++)lines.add(new FlapDisplayLayout(getMaxCharCount()));}
    public boolean validLine(int line){return line>=0&&line<ySize*2;}
    public void applyTextManually(int line,Component component){
        if(!validLine(line)||level==null||level.isClientSide())return;var resolved=component==null?Component.empty():DynamicComponent.parseCustomText(level,worldPosition,component);if(resolved==null)return;var layout=getLines().get(line);if(!layout.isLayout("Default"))layout.loadDefault(getMaxCharCount());manualLines[line]=component!=null;layout.getSections().getFirst().setText(resolved,level.registryAccess());notifyUpdate();
    }
    public void setColour(int line,DyeColor color){if(!validLine(line))return;colour[line]=color==DyeColor.WHITE?null:color;notifyUpdate();}
    public void setGlowing(int line){if(!validLine(line))return;glowingLines[line]=true;notifyUpdate();}
    public int getLineColor(int line){if(!validLine(line))return 0xFFD3C6BA;return colour[line]==null?0xFFD3C6BA:0xFF000000|DyeHelper.getDyeColors(colour[line]).first();}
    public boolean isLineGlowing(int line){return validLine(line)&&glowingLines[line];}
    public AABB getDisplayBounds(){var side=getDirection().getClockWise();return new AABB(worldPosition).expandTowards(side.getStepX()*(xSize-1),1-ySize,side.getStepZ()*(xSize-1));}
    public void notifyUpdate(){setChanged();if(level!=null&&!level.isClientSide())sync();}
    @Override protected void write(ValueOutput out, boolean clientPacket){
        super.write(out, clientPacket);out.putBoolean("Controller",isController);out.putInt("XSize",xSize);out.putInt("YSize",ySize);for(int i=0;i<ySize*2;i++){if(manualLines[i])out.putBoolean("CustomLine"+i,true);if(glowingLines[i])out.putBoolean("GlowingLine"+i,true);if(colour[i]!=null)out.putString("Dye"+i,colour[i].name());getLines().get(i).write(out.child("Display"+i));}
    }
    @Override protected void read(ValueInput in, boolean clientPacket){
        super.read(in, clientPacket);int oldWidth=xSize,oldHeight=ySize;boolean oldController=isController;isController=in.getBooleanOr("Controller",false);xSize=Math.clamp(in.getIntOr("XSize",1),1,MAX_WIDTH);ySize=Math.clamp(in.getIntOr("YSize",1),1,MAX_HEIGHT);manualLines=new boolean[ySize*2];glowingLines=new boolean[ySize*2];colour=new DyeColor[ySize*2];
        for(int i=0;i<ySize*2;i++){manualLines[i]=in.getBooleanOr("CustomLine"+i,false);glowingLines[i]=in.getBooleanOr("GlowingLine"+i,false);var name=in.getStringOr("Dye"+i,"");if(!name.isEmpty())try{colour[i]=DyeColor.valueOf(name.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException invalid){colour[i]=null;}}
        if(oldWidth!=xSize||oldHeight!=ySize||oldController!=isController)lines=null;for(int i=0;i<getLines().size();i++)getLines().get(i).read(in.childOrEmpty("Display"+i));isRunning=isSpeedRequirementFulfilled();
    }
}
