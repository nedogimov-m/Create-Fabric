// Create1.21.1 SimpleBuilder associations, MIT; native26.3 direct registration lifecycle.
package com.simibubi.create.api.registry.registrate;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.api.registry.SimpleRegistry.Provider;
import com.simibubi.create.impl.registry.TagProviderImpl;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import com.simibubi.create.api.registrate.CreateRegistrateRegistrationCallback;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;

/** Native registry builder; source associations publish only after the real native entry event. */
public class SimpleBuilder<R, T extends R, P> implements Supplier<T> {
    private final Registry<R> registry;
    private final P parent;
    private final Identifier id;
    private final Supplier<T> value;
    private final List<Consumer<? super T>> callbacks = new ArrayList<>();
    private enum Stage { NEW, CREATING, PUBLISHING, PUBLISHED, FAILED }
    private Stage stage = Stage.NEW;
    private T created, registered;

	private SimpleRegistryAccess<Block, R> byBlock;
	private SimpleRegistryAccess<BlockEntityType<?>, R> byBlockEntity;
	private SimpleRegistryAccess<EntityType<?>, R> byEntity;
	private SimpleRegistryAccess<Fluid, R> byFluid;

    public SimpleBuilder(Registry<R> registry, P parent, Identifier id, Supplier<T> value) {
        this.registry = Objects.requireNonNull(registry, "registry"); this.parent = parent;
        this.id = Objects.requireNonNull(id, "id"); this.value = Objects.requireNonNull(value, "value");
    }
    public static <R, T extends R> SimpleBuilder<R, T, Registry<R>> create(Registry<R> registry, Identifier id, Supplier<T> value) {
        return new SimpleBuilder<>(registry, registry, id, value);
    }
    protected T createEntry() { return value.get(); }
    public synchronized SimpleBuilder<R, T, P> onRegister(Consumer<? super T> callback) {
        Objects.requireNonNull(callback, "callback");
        if (stage == Stage.PUBLISHED) CreateRegistrateRegistrationCallback.<R, T>register(registry, id, callback);
        else {
            if (stage == Stage.FAILED) throw new IllegalStateException("Native builder registration failed: " + id);
            callbacks.add(callback);
        }
        return this;
    }
    public synchronized T register() {
        if (stage != Stage.NEW) throw new IllegalStateException("Native builder already attempted registration: " + id);
        if (registry.containsKey(id)) throw new IllegalArgumentException("Native registry entry already exists: " + id);
        stage = Stage.CREATING;
        try {
            created = Objects.requireNonNull(createEntry(), "Native entry factory returned null: " + id);
            if (registry.containsKey(id)) throw new IllegalArgumentException("Native registry entry appeared during factory: " + id);
            stage = Stage.PUBLISHING;
            CreateRegistrateRegistrationCallback.<R, T>register(registry, id, this::publish);
            Registry.register(registry, id, created);
            if (stage != Stage.PUBLISHED || registered != created || registry.getValue(id) != registered)
                throw new IllegalStateException("Native registry did not publish the builder's actual entry: " + id);
            return registered;
        } catch (RuntimeException | Error failure) {
            if (stage != Stage.PUBLISHED) stage = Stage.FAILED;
            throw failure;
        }
    }
    private synchronized void publish(T entry) {
        if (stage != Stage.PUBLISHING || entry != created || registry.getValue(id) != entry)
            throw new IllegalStateException("Unexpected native builder publication: " + id);
        registered = entry; stage = Stage.PUBLISHED;
        var publishedCallbacks = List.copyOf(callbacks); callbacks.clear();
        for (var callback : publishedCallbacks) callback.accept(entry);
    }
    @Override public synchronized T get() {
        if (stage != Stage.PUBLISHED) throw new IllegalStateException("Native builder entry has not been published: " + id);
        return registered;
    }
    public P build() { register(); return parent; }
    public Registry<R> getRegistry() { return registry; }
    public Identifier getId() { return id; }

	// for setup

	@SuppressWarnings("deprecation")
	public SimpleBuilder<R, T, P> byBlock(SimpleRegistry<Block, R> registry) {
		this.byBlock = SimpleRegistryAccess.of(registry, Block::builtInRegistryHolder);
		return this;
	}

	@SuppressWarnings("deprecation")
	public SimpleBuilder<R, T, P> byBlock(SimpleRegistry.Multi<Block, R> registry) {
		this.byBlock = SimpleRegistryAccess.of(registry, Block::builtInRegistryHolder);
		return this;
	}

	public SimpleBuilder<R, T, P> byBlockEntity(SimpleRegistry<BlockEntityType<?>, R> registry) {
		this.byBlockEntity = SimpleRegistryAccess.of(registry, TagProviderImpl::getBeHolder);
		return this;
	}

	public SimpleBuilder<R, T, P> byBlockEntity(SimpleRegistry.Multi<BlockEntityType<?>, R> registry) {
		this.byBlockEntity = SimpleRegistryAccess.of(registry, TagProviderImpl::getBeHolder);
		return this;
	}

	@SuppressWarnings("deprecation")
	public SimpleBuilder<R, T, P> byEntity(SimpleRegistry<EntityType<?>, R> registry) {
		this.byEntity = SimpleRegistryAccess.of(registry, EntityType::builtInRegistryHolder);
		return this;
	}

	@SuppressWarnings("deprecation")
	public SimpleBuilder<R, T, P> byEntity(SimpleRegistry.Multi<EntityType<?>, R> registry) {
		this.byEntity = SimpleRegistryAccess.of(registry, EntityType::builtInRegistryHolder);
		return this;
	}

	@SuppressWarnings("deprecation")
	public SimpleBuilder<R, T, P> byFluid(SimpleRegistry<Fluid, R> registry) {
		this.byFluid = SimpleRegistryAccess.of(registry, Fluid::builtInRegistryHolder);
		return this;
	}

	@SuppressWarnings("deprecation")
	public SimpleBuilder<R, T, P> byFluid(SimpleRegistry.Multi<Fluid, R> registry) {
		this.byFluid = SimpleRegistryAccess.of(registry, Fluid::builtInRegistryHolder);
		return this;
	}

	// association methods

	public SimpleBuilder<R, T, P> associate(Block block) {
		assertPresent(this.byBlock, "Block");
		this.onRegister(value -> this.byBlock.adder.accept(block, value));
		return this;
	}

	public SimpleBuilder<R, T, P> associateBlockTag(TagKey<Block> tag) {
		assertPresent(this.byBlock, "Block");
		this.onRegister(value -> this.byBlock.tagAdder.accept(tag, value));
		return this;
	}

	public SimpleBuilder<R, T, P> associate(BlockEntityType<?> type) {
		assertPresent(this.byBlockEntity, "BlockEntityType");
		this.onRegister(value -> this.byBlockEntity.adder.accept(type, value));
		return this;
	}

	public SimpleBuilder<R, T, P> associateBeTag(TagKey<BlockEntityType<?>> tag) {
		assertPresent(this.byBlockEntity, "BlockEntityType");
		this.onRegister(value -> this.byBlockEntity.tagAdder.accept(tag, value));
		return this;
	}

	public SimpleBuilder<R, T, P> associate(EntityType<?> type) {
		assertPresent(this.byEntity, "EntityType");
		this.onRegister(value -> this.byEntity.adder.accept(type, value));
		return this;
	}

	public SimpleBuilder<R, T, P> associateEntityTag(TagKey<EntityType<?>> tag) {
		assertPresent(this.byEntity, "EntityType");
		this.onRegister(value -> this.byEntity.tagAdder.accept(tag, value));
		return this;
	}

	public SimpleBuilder<R, T, P> associate(Fluid fluid) {
		assertPresent(this.byFluid, "Fluid");
		this.onRegister(value -> this.byFluid.adder.accept(fluid, value));
		return this;
	}

	public SimpleBuilder<R, T, P> associateFluidTag(TagKey<Fluid> tag) {
		assertPresent(this.byFluid, "Fluid");
		this.onRegister(value -> this.byFluid.tagAdder.accept(tag, value));
		return this;
	}

	private static void assertPresent(@Nullable Object object, String type) {
		if (object == null) {
			throw new IllegalStateException("This type does not support " + type + " associations");
		}
	}

	protected record SimpleRegistryAccess<K, V>(BiConsumer<K, V> adder, BiConsumer<TagKey<K>, V> tagAdder) {
		public static <K, V> SimpleRegistryAccess<K, V> of(SimpleRegistry<K, V> registry, Function<K, Holder<K>> holderGetter) {
			return new SimpleRegistryAccess<>(
				registry::register,
				(tag, value) -> registry.registerProvider(Provider.forTag(tag, holderGetter, value))
			);
		}

		public static <K, V> SimpleRegistryAccess<K, V> of(SimpleRegistry.Multi<K, V> registry, Function<K, Holder<K>> holderGetter) {
			return new SimpleRegistryAccess<>(
				registry::add,
				(tag, value) -> registry.addProvider(Provider.forTag(tag, holderGetter, value))
			);
		}
	}
}
