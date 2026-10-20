package com.simibubi.create.content.redstone.displayLink.target;

import com.simibubi.create.api.behaviour.display.TextDisplayTarget;
import com.simibubi.create.content.trains.display.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Actual common board target kernel and real Display Link source/ownership adapter; direct native target API is retained. */
public final class DisplayBoardTarget extends com.simibubi.create.api.behaviour.display.DisplayTarget implements TextDisplayTarget {
    @FunctionalInterface public interface LayoutSource {void configure(FlapDisplayBlockEntity board,FlapDisplayLayout layout,int sourceRow);}
    private final Level level;
    private final BlockPos target;
    public DisplayBoardTarget(){this.level=null;this.target=BlockPos.ZERO;}
    public DisplayBoardTarget(Level level,BlockPos target){this.level=Objects.requireNonNull(level);this.target=target.immutable();}
    public FlapDisplayBlockEntity getController(){if(level==null)return null;return LoadedLevelAccess.blockEntity(level,target)instanceof FlapDisplayBlockEntity board?board.getController():null;}
    @Override public Stats stats(){var board=getController();return board==null?null:new Stats(board.ySize*2,board.getMaxCharCount(),board.getDisplayBounds());}
    @Override public Result acceptText(int firstLine,List<? extends Component> text){
        List<List<Component>> rows=text.stream().map(value->List.<Component>of(value)).toList();return acceptFlapText(firstLine,rows,(board,layout,row)->{if(!layout.isLayout("Default"))layout.loadDefault(board.getMaxCharCount());},false,line->false);
    }
    /** Source behavior: the explicitly selected first row can be overwritten; subsequent manual/reserved rows stop the write. */
    public Result acceptFlapText(int firstLine,List<? extends List<? extends Component>> text,LayoutSource source,boolean clearRemaining,IntPredicate reserved){
        var board=getController();if(board==null||level.isClientSide())return new Result(Status.UNAVAILABLE,0);if(!board.validLine(firstLine))return new Result(Status.INVALID_LINE,0);if(!board.isSpeedRequirementFulfilled())return new Result(Status.NOT_RUNNING,0);Objects.requireNonNull(source);Objects.requireNonNull(reserved);int changed=0;boolean blocked=false;
        for(int offset=0;firstLine+offset<board.ySize*2;offset++){int line=firstLine+offset;if(offset>0&&(board.manualLines[line]||reserved.test(line))){blocked=true;break;}if(offset>=text.size()){if(!clearRemaining)break;board.applyTextManually(line,null);changed++;continue;}var layout=board.getLines().get(line);source.configure(board,layout,offset);var sections=layout.getSections();var values=text.get(offset);for(int section=0;section<Math.min(values.size(),sections.size());section++)sections.get(section).setText(values.get(section),level.registryAccess());changed++;}
        if(changed>0)board.notifyUpdate();return new Result(blocked?(changed>0?Status.PARTIAL:Status.RESERVED):changed>0?Status.APPLIED:Status.UNCHANGED,changed);
    }

    private FlapDisplayBlockEntity getController(com.simibubi.create.content.redstone.displayLink.DisplayLinkContext context){return context.getTargetBlockEntity() instanceof FlapDisplayBlockEntity board?board.getController():null;}
    @Override public void acceptText(int line,List<net.minecraft.network.chat.MutableComponent> text,com.simibubi.create.content.redstone.displayLink.DisplayLinkContext context){acceptFlapText(line,text.stream().map(List::of).toList(),context);}
    public void acceptFlapText(int line,List<List<net.minecraft.network.chat.MutableComponent>> text,com.simibubi.create.content.redstone.displayLink.DisplayLinkContext context){
        var controller=getController(context);var source=context.blockEntity().activeSource;if(controller==null||source==null||!controller.isSpeedRequirementFulfilled()||!controller.validLine(line))return;
        reserve(line,controller,context);new DisplayBoardTarget(context.level(),context.getTargetPos()).acceptFlapText(line,text,(board,layout,row)->source.loadFlapDisplayLayout(context,board,layout,row),!(source instanceof com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource),row->isReserved(row,controller,context));
    }
    @Override public boolean isReserved(int line,net.minecraft.world.level.block.entity.BlockEntity target,com.simibubi.create.content.redstone.displayLink.DisplayLinkContext context){return super.isReserved(line,target,context)||target instanceof FlapDisplayBlockEntity board&&board.validLine(line)&&board.manualLines[line];}
    @Override public void release(com.simibubi.create.content.redstone.displayLink.DisplayLinkContext context){var controller=getController(context);if(controller!=null)((com.simibubi.create.content.redstone.displayLink.DisplayTargetReservations)(Object)controller).create$displayReservations().release(context.blockEntity().getBlockPos(),controller);}
    @Override public DisplayTargetStats provideStats(com.simibubi.create.content.redstone.displayLink.DisplayLinkContext context){var board=getController(context);return board==null?new DisplayTargetStats(0,0,this):new DisplayTargetStats(board.ySize*2,board.getMaxCharCount(),this);}
    @Override public net.minecraft.world.phys.AABB getMultiblockBounds(net.minecraft.world.level.LevelAccessor level,BlockPos pos){var entity=LoadedLevelAccess.blockEntity(level,pos);var board=entity instanceof FlapDisplayBlockEntity part?part.getController():null;if(board==null)return super.getMultiblockBounds(level,pos);var side=board.getDirection().getClockWise();return super.getMultiblockBounds(level,board.getBlockPos()).expandTowards(side.getStepX()*(board.xSize-1),1-board.ySize,side.getStepZ()*(board.xSize-1));}
}
