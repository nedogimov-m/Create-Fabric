package com.simibubi.create.test.mixin;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read actual native test lifecycle for scoped native-player cleanup, including test timeouts. */
@Mixin(GameTestHelper.class)
public interface GameTestHelperNativeInfoAccessor {
  @Accessor("testInfo")
  GameTestInfo create$getActualNativeTestInfo();
}
