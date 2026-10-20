package com.simibubi.create.foundation.block.connected;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.block.v1.FabricBlockState;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Pinned source eight-neighbor context/orientation and exact8x8 sheet tile selection. */
public final class OmnidirectionalCT {
    public record Basis(Direction right,Direction up){}
    public record Context(boolean up,boolean down,boolean left,boolean right,boolean topLeft,boolean topRight,boolean bottomLeft,boolean bottomRight){
        public int textureIndex(){
            int x=(up?1:0)+(down?2:0),y=(left?1:0)+(right?2:0),borders=(up?0:1)+(down?0:1)+(left?0:1)+(right?0:1);
            if(borders==0){x+=(topRight?1:0)+(topLeft?2:0);y+=(bottomRight?2:0)+(bottomLeft?1:0);}
            if(borders==1){if(!right&&(topLeft||bottomLeft)){y=4;x=-1+(bottomLeft?1:0)+(topLeft?2:0);}if(!left&&(topRight||bottomRight)){y=5;x=-1+(bottomRight?1:0)+(topRight?2:0);}if(!down&&(topLeft||topRight)){y=6;x=-1+(topLeft?1:0)+(topRight?2:0);}if(!up&&(bottomLeft||bottomRight)){y=7;x=-1+(bottomLeft?1:0)+(bottomRight?2:0);}}
            if(borders==2&&(up&&left&&topLeft||down&&left&&bottomLeft||up&&right&&topRight||down&&right&&bottomRight))x+=3;
            return x+8*y;
        }
    }
    public static Basis basis(Direction face){
        var horizontal=face.getAxis()==Direction.Axis.X?Direction.SOUTH:Direction.WEST;var vertical=face.getAxis().isHorizontal()?Direction.UP:Direction.NORTH;
        if(face.getAxisDirection()==Direction.AxisDirection.POSITIVE)horizontal=horizontal.getOpposite();if(face==Direction.DOWN){vertical=vertical.getOpposite();horizontal=horizontal.getOpposite();}return new Basis(horizontal,vertical);
    }
    public static BlockState appearance(BlockGetter reader,BlockState reference,Direction face,BlockPos from,BlockPos target){
        var state=LoadedLevelAccess.blockState(reader,target);return reader instanceof BlockAndLightGetter lit?((FabricBlockState)(Object)state).getAppearance(lit,target,face,reference,from):state;
    }
    public static boolean connects(BlockGetter reader,BlockPos pos,BlockState state,BlockPos target,Direction face){
        var reference=LoadedLevelAccess.blockState(reader,pos);var other=appearance(reader,reference,face,pos,target);if(other.getBlock()!=state.getBlock())return false;
        var blockingPos=target.relative(face);var blocking=LoadedLevelAccess.blockState(reader,blockingPos);
        if(!Block.isFaceFull(blocking.getShape(reader,blockingPos),face.getOpposite())||face.getAxis().choose(pos.getX(),pos.getY(),pos.getZ())!=face.getAxis().choose(target.getX(),target.getY(),target.getZ()))return true;
        // Source's recursive blocking test leaves the face plane here, so it reduces to the actual
        // appearance of that one full neighboring block, not every arbitrary opaque obstruction.
        return appearance(reader,reference,face.getOpposite(),pos.relative(face),blockingPos).getBlock()!=state.getBlock();
    }
    public static Context build(BlockGetter reader,BlockPos pos,BlockState state,Direction face){
        var basis=basis(face);var h=basis.right;var v=basis.up;
        boolean up=connects(reader,pos,state,pos.relative(v),face),down=connects(reader,pos,state,pos.relative(v.getOpposite()),face),left=connects(reader,pos,state,pos.relative(h.getOpposite()),face),right=connects(reader,pos,state,pos.relative(h),face);
        return new Context(up,down,left,right,up&&left&&connects(reader,pos,state,pos.relative(v).relative(h.getOpposite()),face),up&&right&&connects(reader,pos,state,pos.relative(v).relative(h),face),down&&left&&connects(reader,pos,state,pos.relative(v.getOpposite()).relative(h.getOpposite()),face),down&&right&&connects(reader,pos,state,pos.relative(v.getOpposite()).relative(h),face));
    }
    private OmnidirectionalCT(){}
}
