package com.simibubi.create.foundation.blockEntity.behaviour;

/** Identity keys deliberately distinguish different behaviours with the same optional name. */
public class BehaviourType<T extends BlockEntityBehaviour> {
    private final String name;
    private java.util.function.Function<net.minecraft.world.level.block.entity.BlockEntity,T> fallback=entity->null;
    public BehaviourType<T> withFallback(java.util.function.Function<net.minecraft.world.level.block.entity.BlockEntity,T> factory){fallback=factory;return this;}
    T resolve(net.minecraft.world.level.block.entity.BlockEntity entity){return entity==null?null:fallback.apply(entity); }
    public BehaviourType(){this("");}
    public BehaviourType(String name){this.name=name;}
    public String getName(){return name;}
    @Override public int hashCode(){return super.hashCode()*31*493286711;}
}
