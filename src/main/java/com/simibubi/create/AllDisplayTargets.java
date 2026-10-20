package com.simibubi.create;

import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.redstone.displayLink.target.*;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.entity.BlockEntityTypes;

/** Real native synced source-compatible target identifiers and actual block/type associations. */
public final class AllDisplayTargets {
    public static final SignDisplayTarget SIGN=register("sign",new SignDisplayTarget());
    public static final LecternDisplayTarget LECTERN=register("lectern",new LecternDisplayTarget());
    public static final DisplayBoardTarget DISPLAY_BOARD=register("display_board",new DisplayBoardTarget());
    public static final NixieTubeDisplayTarget NIXIE_TUBE=register("nixie_tube",new NixieTubeDisplayTarget());
    public static final Map<String,DisplayTarget> LEGACY_NAMES=Map.of("sign_display_target",SIGN,"lectern_display_target",LECTERN,"display_board_target",DISPLAY_BOARD,"nixie_tube_target",NIXIE_TUBE);
    private static <T extends DisplayTarget>T register(String name,T target){return Registry.register(CreateBuiltInRegistries.DISPLAY_TARGET,Create.asResource(name),target);}
    private static boolean associated;
    public static void register(){if(associated)return;associated=true;DisplayTarget.BY_BLOCK_ENTITY.register(BlockEntityTypes.SIGN,SIGN);DisplayTarget.BY_BLOCK_ENTITY.register(BlockEntityTypes.LECTERN,LECTERN);DisplayTarget.BY_BLOCK.register(AllBlocks.DISPLAY_BOARD,DISPLAY_BOARD);for(var block:AllBlocks.NIXIE_TUBES.values())DisplayTarget.BY_BLOCK.register(block,NIXIE_TUBE);}
}
