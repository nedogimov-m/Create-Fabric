package com.simibubi.create.content.contraptions;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Leading block cells for the original directional terrain checks. */
public abstract class TranslatingContraption extends Contraption {
    public net.minecraft.world.phys.Vec3 positionAtProgress(float progress){throw new UnsupportedOperationException("This translating structure has no stationary linear actuator progress");}
    public StructureTransform restorationAtProgress(float progress){return new StructureTransform(BlockPos.containing(positionAtProgress(Math.round(progress))),0,0,0);}
    private Set<BlockPos> cachedColliders;
    private Direction cachedDirection;
    public Set<BlockPos> getOrCreateColliders(Level level,Direction direction){
        if(cachedColliders==null||cachedDirection!=direction){cachedDirection=direction;cachedColliders=Set.copyOf(createColliders(level,direction));}
        return cachedColliders;
    }
    public Set<BlockPos> createColliders(Level level,Direction direction){
        var result=new HashSet<BlockPos>();var view=getCollisionLevel();
        for(var info:blocks.values()){
            if(info.state().getCollisionShape(view,info.pos()).isEmpty())continue;
            var next=info.pos().relative(direction);var other=blocks.get(next);
            if(other!=null&&!other.state().getCollisionShape(view,next).isEmpty())continue;
            result.add(info.pos().immutable());
        }return result;
    }
    protected void invalidateColliders(){cachedColliders=null;cachedDirection=null;}
    @Override public boolean canBeStabilized(Direction facing,BlockPos localPos){return ContraptionSettings.stabiliseStableContraptions;}
}
