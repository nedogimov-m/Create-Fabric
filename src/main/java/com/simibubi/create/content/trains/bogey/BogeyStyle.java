package com.simibubi.create.content.trains.bogey;

import com.simibubi.create.*;
import com.simibubi.create.content.trains.bogey.BogeySizes.BogeySize;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.particles.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Source style/size data without client-only render classes in the common class loader. */
public final class BogeyStyle {
    public final Identifier id,cycleGroup;
    public final Component displayName;
    public final Supplier<SoundEvent> soundEvent;
    public final ParticleOptions contactParticle,smokeParticle;
    public final CompoundTag defaultData;
    private final Map<BogeySize,Supplier<? extends AbstractBogeyBlock<?>>> sizes;
    private BogeyStyle(Builder builder){id=builder.id;cycleGroup=builder.cycleGroup;displayName=builder.displayName;soundEvent=builder.soundEvent;contactParticle=builder.contactParticle;smokeParticle=builder.smokeParticle;defaultData=builder.defaultData.copy();sizes=Collections.unmodifiableMap(new LinkedHashMap<>(builder.sizes));}
    public Map<Identifier,BogeyStyle> getCycleGroup(){return AllBogeyStyles.getCycleGroup(cycleGroup);}
    public Set<BogeySize> validSizes(){return sizes.keySet();}
    public AbstractBogeyBlock<?> getBlockForSize(BogeySize size){var supplier=sizes.get(size);if(supplier==null)throw new IllegalArgumentException("Style "+id+" has no size "+size.id());return Objects.requireNonNull(supplier.get());}
    public AbstractBogeyBlock<?> getNextBlock(BogeySize current){
        var size=current;for(int i=0;i<BogeySizes.all().size();i++){size=size.nextBySize();if(sizes.containsKey(size))return getBlockForSize(size);}throw new IllegalStateException("No registered size for "+id);
    }
    public static final class Builder {
        private final Identifier id,cycleGroup;
        private final Map<BogeySize,Supplier<? extends AbstractBogeyBlock<?>>> sizes=new LinkedHashMap<>();
        private Component displayName=Component.translatable("create.bogey.style.invalid");
        private Supplier<SoundEvent> soundEvent=()->AllSoundEvents.TRAIN2;
        private ParticleOptions contactParticle=ParticleTypes.CRIT,smokeParticle=ParticleTypes.POOF;
        private CompoundTag defaultData=new CompoundTag();
        public Builder(Identifier id,Identifier cycleGroup){this.id=Objects.requireNonNull(id);this.cycleGroup=Objects.requireNonNull(cycleGroup);}
        public Builder displayName(Component name){displayName=Objects.requireNonNull(name);return this;}
        public Builder soundEvent(Supplier<SoundEvent> sound){soundEvent=Objects.requireNonNull(sound);return this;}
        public Builder contactParticle(ParticleOptions particle){contactParticle=Objects.requireNonNull(particle);return this;}
        public Builder smokeParticle(ParticleOptions particle){smokeParticle=Objects.requireNonNull(particle);return this;}
        public Builder defaultData(CompoundTag data){defaultData=Objects.requireNonNull(data).copy();return this;}
        public Builder size(BogeySize size,Supplier<? extends AbstractBogeyBlock<?>> block){if(BogeySizes.all().get(size.id())!=size)throw new IllegalArgumentException("Unregistered size");if(sizes.putIfAbsent(size,Objects.requireNonNull(block))!=null)throw new IllegalArgumentException("Duplicate style size");return this;}
        public BogeyStyle build(){if(sizes.isEmpty())throw new IllegalStateException("Bogey style must contain a real size");var style=new BogeyStyle(this);AllBogeyStyles.register(style);return style;}
    }
}
