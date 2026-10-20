package com.simibubi.create.foundation.utility;

import com.mojang.serialization.Codec;
import com.simibubi.create.foundation.mixin.accessor.SavedDataStorageAccessor;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Genuine native disk/cache transitions; runtime UNRUN until executed by the root runner. */
public final class NativeSavedDataIdentityGameTests {
    private static final class CounterData extends SavedData {final int value;CounterData(int value){this.value=value;}}
    @GameTest public void actualCacheMissDoesNotLoadSavedJournalOrRunItsDecoder(GameTestHelper h){
        var level=h.getLevel();var storage=level.getDataStorage();var decoded=new AtomicInteger();
        Codec<CounterData> codec=Codec.INT.fieldOf("Value").codec().xmap(value->{decoded.incrementAndGet();return new CounterData(value);},data->data.value);
        var type=new SavedDataType<>(Identifier.fromNamespaceAndPath("create_test","canonical_cache_"+UUID.randomUUID()),()->new CounterData(0),codec,null);
        var first=new CounterData(41);var replacement=new CounterData(42);
        var cache=((SavedDataStorageAccessor)storage).create$getCache();
        try{
            storage.set(type,first);h.assertTrue(NativeSavedDataIdentity.isCurrent(level,type,first),"Actual raw native slot witnesses only its exact loaded object");
            storage.set(type,replacement);h.assertTrue(!NativeSavedDataIdentity.isCurrent(level,type,first)&&NativeSavedDataIdentity.isCurrent(level,type,replacement),"Actual native SavedDataStorage.set retires the original journal identity");
            storage.saveAndJoin();cache.remove(type);
            h.assertTrue(!NativeSavedDataIdentity.isCurrent(level,type,replacement)&&decoded.get()==0&&!cache.containsKey(type),"Missing actual cache slot refuses without disk load, decoder callback or cache mutation");
            var loaded=storage.get(type);
            h.assertTrue(loaded!=replacement&&loaded!=null&&loaded.value==42&&decoded.get()==1&&NativeSavedDataIdentity.isCurrent(level,type,loaded),"The real native load subsequently decodes the saved replacement exactly once, proving the dormant decoder was available");
            cache.put(type,Optional.empty());h.assertTrue(!NativeSavedDataIdentity.isCurrent(level,type,loaded)&&decoded.get()==1,"An actual native negative cache entry also refuses without decoding");
        }finally{cache.remove(type);}
        h.succeed();
    }
}
