package com.simibubi.create.content.decoration.slidingDoor;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import java.util.*;
import java.util.function.Function;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.*;

/** Native BE behaviour; train stations and elevator contacts attach it through their Provider. */
public class DoorControlBehaviour extends BlockEntityBehaviour {
    public static final BehaviourType<DoorControlBehaviour> TYPE=new BehaviourType<>("door_control");
    private static final List<Function<MovementContext,DoorControlBehaviour>> RESOLVERS=new ArrayList<>();
    public DoorControl mode=DoorControl.ALL;
    public DoorControlBehaviour(BlockEntity owner){super(owner);}
    public void set(DoorControl mode){Objects.requireNonNull(mode);if(this.mode==mode)return;this.mode=mode;changed();}
    @Override public void write(ValueOutput out){out.putString("DoorControl",mode.name());}
    @Override public void read(ValueInput in){var value=in.getStringOr("DoorControl","ALL");try{mode=DoorControl.valueOf(value);}catch(IllegalArgumentException invalid){mode=DoorControl.ALL;}}
    @Override public BehaviourType<?> getType(){return TYPE;}
    /** Integrations resolve their actual loaded station/contact; absence deliberately keeps ordinary contraptions closed. */
    public static void registerResolver(Function<MovementContext,DoorControlBehaviour> resolver){RESOLVERS.add(Objects.requireNonNull(resolver));}
    public static DoorControlBehaviour resolve(MovementContext context){for(var resolver:RESOLVERS){var value=resolver.apply(context);if(value!=null)return value;}return null;}
}
