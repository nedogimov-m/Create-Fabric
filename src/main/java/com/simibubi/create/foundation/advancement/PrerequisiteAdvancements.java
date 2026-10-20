package com.simibubi.create.foundation.advancement;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import com.simibubi.create.foundation.advancement.SimpleCreateTrigger;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.*;

/** Original prerequisite criteria, emitted exclusively by actual device/menu source events. */
public final class PrerequisiteAdvancements {
    private static final Set<String> NEW_CRITERIA=Set.of("pulley_maxed","windmill_maxed","steam_engine_maxed","hose_pulley_lava","foods","packager","frogport","factory_gauge","stock_ticker","table_cloth_shop");
    public static void register(){for(var id:NEW_CRITERIA){var key=Create.asResource(id+"_builtin");if(!BuiltInRegistries.TRIGGER_TYPES.containsKey(key))com.simibubi.create.foundation.advancement.AllTriggers.registerSimple(key);}}
    public static boolean completed(ServerPlayer player,String id){var advancement=player.level().getServer().getAdvancements().get(Create.asResource(id));return advancement==null||player.getAdvancements().getOrStartProgress(advancement).isDone();}
    public static void award(Player player,String id){
        if(!(player instanceof ServerPlayer actual)||player instanceof net.fabricmc.fabric.api.entity.FakePlayer||completed(actual,id))return;
        var criterion=BuiltInRegistries.TRIGGER_TYPES.getValue(Create.asResource(id+"_builtin"));
        if(!(criterion instanceof SimpleCreateTrigger trigger))throw new IllegalStateException("Missing genuine prerequisite criterion: "+id);
        trigger.trigger(actual);
    }
    /** The native Owner field belongs to this exact loaded BE, never to a nearby observer or replacement. */
    public static final class Owner implements AdvancementBehaviour.OwnerStorage {
        private final BlockEntity device;
        private final Set<String> criteria;
        private UUID owner;
        private boolean initialized;
        public Owner(BlockEntity device,String... criteria){this.device=device;this.criteria=Set.of(criteria);}
        public UUID uuid(){return owner;}
        @Override public UUID getPlayerId(){return owner;}
        @Override public void setPlayerId(UUID id){owner=id;initialized=false;}
        @Override public Collection<String> advancementIds(){return criteria;}
        @Override public boolean canClear(Player player){return player instanceof ServerPlayer actual&&criteria.stream().allMatch(id->completed(actual,id));}
        private boolean live(){var level=device.getLevel();return level instanceof ServerLevel&&!device.isRemoved()&&LoadedLevelAccess.blockEntity(level,device.getBlockPos())==device&&LoadedLevelAccess.blockState(level,device.getBlockPos()).is(device.getBlockState().getBlock());}
        private ServerPlayer player(){if(owner==null||!live())return null;return device.getLevel().getPlayerByUUID(owner) instanceof ServerPlayer player&&player.level()==device.getLevel()&&!(player instanceof net.fabricmc.fabric.api.entity.FakePlayer)?player:null;}
        public void place(LivingEntity placer){if(!(placer instanceof ServerPlayer player)||player instanceof net.fabricmc.fabric.api.entity.FakePlayer||!live()||placer.level()!=device.getLevel())return;owner=placer.getUUID();initialized=false;device.setChanged();initialize();}
        public void initialize(){if(initialized||!live())return;var player=player();if(owner==null){initialized=true;return;}if(player==null)return;initialized=true;removeCompleted(player);}
        private void removeCompleted(ServerPlayer player){if(criteria.stream().allMatch(id->completed(player,id))&&owner!=null&&(!(device instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity smart)||!AdvancementBehaviour.hasPendingFor(smart,owner))){owner=null;device.setChanged();}}
        public void award(String id){if(!criteria.contains(id))return;var player=player();if(player==null)return;PrerequisiteAdvancements.award(player,id);removeCompleted(player);}
        public void write(ValueOutput output){if(owner!=null)output.store("Owner",UUIDUtil.CODEC,owner);}
        public void read(ValueInput input){owner=input.read("Owner",UUIDUtil.CODEC).orElse(null);initialized=false;}
    }
    private PrerequisiteAdvancements(){}
}
