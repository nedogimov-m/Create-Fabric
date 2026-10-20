// Create1.21.1 registration callbacks, MIT; native26.3 Registry/Fabric event adaptation.
package com.simibubi.create.api.registrate;

import com.simibubi.create.impl.registrate.CreateRegistrateRegistrationCallbackImpl;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Callbacks for real native entry registration, retaining the original public Create API owner name.
 * A callback receives the actual value once, including when its entry already exists.
 * Native registries and java Consumers replace the former Registrate-specific types.
 */
public final class CreateRegistrateRegistrationCallback {
    public static <R, T extends R> void register(ResourceKey<? extends Registry<R>> registry, Identifier id, Consumer<? super T> callback) {
        CreateRegistrateRegistrationCallbackImpl.<R, T>register(registry, id, callback);
    }
    public static <R, T extends R> void register(Registry<R> registry, Identifier id, Consumer<? super T> callback) {
        CreateRegistrateRegistrationCallbackImpl.<R, T>register(registry, id, callback);
    }
    /**
     * Bind an actual native registry, including a registry not published in BuiltInRegistries.REGISTRY.
     * Repeating the same native instance is harmless; a different instance with the same key is rejected.
     * Built-in/root-published registries are discovered automatically for register-by-key calls.
     */
    public static <R> void provideRegistry(Registry<R> registry) {
        CreateRegistrateRegistrationCallbackImpl.provideRegistry(registry);
    }
    private CreateRegistrateRegistrationCallback() { throw new AssertionError("This class should not be instantiated"); }
}
