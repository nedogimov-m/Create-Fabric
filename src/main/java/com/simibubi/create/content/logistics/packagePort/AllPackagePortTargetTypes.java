package com.simibubi.create.content.logistics.packagePort;
import com.simibubi.create.Create;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import net.minecraft.core.Registry;
public final class AllPackagePortTargetTypes {
    public static final PackagePortTargetType CHAIN_CONVEYOR=Registry.register(CreateBuiltInRegistries.PACKAGE_PORT_TARGET_TYPE,Create.asResource("chain_conveyor"),new PackagePortTarget.ChainConveyorFrogportTarget.Type());
    public static final PackagePortTargetType TRAIN_STATION=Registry.register(CreateBuiltInRegistries.PACKAGE_PORT_TARGET_TYPE,Create.asResource("train_station"),new PackagePortTarget.TrainStationFrogportTarget.Type());
    public static void register(){}
    private AllPackagePortTargetTypes(){}
}
