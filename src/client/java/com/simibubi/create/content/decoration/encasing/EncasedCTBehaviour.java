package com.simibubi.create.content.decoration.encasing;

import com.simibubi.create.foundation.block.connected.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.block.v1.FabricBlockState;
import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class EncasedCTBehaviour {
    protected final CasingConnectivity connectivity;
    protected final CTSpriteShiftEntry shift;
    public EncasedCTBehaviour(CasingConnectivity connectivity,CTSpriteShiftEntry shift) { this.connectivity = connectivity; this.shift = shift; }
    public CTSpriteShiftEntry getShift(BlockState state,Direction face) { return shift; }
    protected BlockState appearance(BlockGetter reader,BlockState reference,Direction face,BlockPos from,BlockPos target) {
        var state = LoadedLevelAccess.blockState(reader,target);
        return reader instanceof BlockAndLightGetter light ? ((FabricBlockState)(Object)state).getAppearance(light,target,face,reference,from) : state;
    }
    protected boolean isBeingBlocked(BlockState state,BlockGetter reader,BlockPos pos,BlockPos target,Direction face) {
        var blockingPos = target.relative(face); var blocking = LoadedLevelAccess.blockState(reader,blockingPos);
        if (!Block.isFaceFull(blocking.getShape(reader,blockingPos),face.getOpposite())
                || face.getAxis().choose(pos.getX(),pos.getY(),pos.getZ()) != face.getAxis().choose(target.getX(),target.getY(),target.getZ())) return false;
        return connectsTo(state,appearance(reader,LoadedLevelAccess.blockState(reader,pos),face.getOpposite(),pos.relative(face),blockingPos),reader,pos,blockingPos,face);
    }
    public boolean connectsTo(BlockState state,BlockState other,BlockGetter reader,BlockPos pos,BlockPos target,Direction face) {
        if (isBeingBlocked(state,reader,pos,target,face)) return false;
        var entry = connectivity.get(state); var otherEntry = connectivity.get(other);
        return entry != null && otherEntry != null && entry.isSideValid(state,face) && otherEntry.isSideValid(other,face)
                && entry.getCasing() == otherEntry.getCasing();
    }
    protected boolean reverseUVs(BlockState state,Direction face) { return false; }
    protected boolean reverseUVsHorizontally(BlockState state,Direction face) { return reverseUVs(state,face); }
    protected boolean reverseUVsVertically(BlockState state,Direction face) { return reverseUVs(state,face); }
    private boolean test(BlockGetter reader,BlockPos pos,BlockState state,Direction face,Direction h,Direction v,int dh,int dv) {
        var target = pos.relative(h,dh).relative(v,dv);
        return connectsTo(state,appearance(reader,LoadedLevelAccess.blockState(reader,pos),face,pos,target),reader,pos,target,face);
    }
    public OmnidirectionalCT.Context buildContext(BlockGetter reader,BlockPos pos,BlockState state,Direction face) {
        var basis = OmnidirectionalCT.basis(face); var h = basis.right(); var v = basis.up();
        int sh = reverseUVsHorizontally(state,face) ? -1 : 1; int sv = reverseUVsVertically(state,face) ? -1 : 1;
        boolean up = test(reader,pos,state,face,h,v,0,sv),down = test(reader,pos,state,face,h,v,0,-sv),left = test(reader,pos,state,face,h,v,-sh,0),right = test(reader,pos,state,face,h,v,sh,0);
        return new OmnidirectionalCT.Context(up,down,left,right,up&&left&&test(reader,pos,state,face,h,v,-sh,sv),up&&right&&test(reader,pos,state,face,h,v,sh,sv),down&&left&&test(reader,pos,state,face,h,v,-sh,-sv),down&&right&&test(reader,pos,state,face,h,v,sh,-sv));
    }
}
