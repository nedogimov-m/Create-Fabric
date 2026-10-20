package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.network.chat.*;

public final class PackageAddressDisplaySource extends SingleLineDisplaySource {
    @Override public boolean isAvailable(DisplayLinkContext context){return super.isAvailable(context)&&ObserverDisplayAccess.available(context);}

    @Override protected MutableComponent provideLine(DisplayLinkContext context,DisplayTargetStats stats){var observer=ObserverDisplayAccess.observer(context);if(observer==null)return EMPTY_LINE;for(var variant:ObserverDisplayAccess.items(context,false).keySet()){var stack=variant.toStack();if(PackageItem.isPackage(stack))return Component.literal(PackageItem.getAddress(stack));}var target=ObserverDisplayAccess.target(observer);if(LoadedLevelAccess.blockEntity(context.level(),target)instanceof ChainConveyorBlockEntity conveyor)for(var parcel:conveyor.getLoopingPackages())if(observer.filtering.test(parcel.item))return Component.literal(PackageItem.getAddress(parcel.item));return EMPTY_LINE;}
    @Override protected boolean allowsLabeling(DisplayLinkContext context){return true;}
    @Override protected String getTranslationKey(){return "read_package_address";}
}
