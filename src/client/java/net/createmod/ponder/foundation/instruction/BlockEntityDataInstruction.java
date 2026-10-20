// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.foundation.instruction;

import java.util.function.UnaryOperator;

import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

public class BlockEntityDataInstruction extends WorldModifyInstruction {

	private final boolean redraw;
	private final UnaryOperator<CompoundTag> data;
	private final Class<? extends BlockEntity> type;

	public BlockEntityDataInstruction(Selection selection, Class<? extends BlockEntity> type,
									  UnaryOperator<CompoundTag> data, boolean redraw) {
		super(selection);
		this.type = type;
		this.data = data;
		this.redraw = redraw;
	}

	@Override
	protected void runModification(Selection selection, PonderScene scene) {
		PonderLevel level = scene.getWorld();
		selection.forEach(pos -> {
			if (!level.getBounds()
				.isInside(pos))
				return;
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (!type.isInstance(blockEntity))
				return;
			CompoundTag apply = data.apply(blockEntity.saveWithFullMetadata(level.registryAccess()));
			//if (blockEntity instanceof SyncedBlockEntity) //TODO
			//	((SyncedBlockEntity) blockEntity).readClient(apply);
			blockEntity.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), apply));
		});
	}

	@Override
	protected boolean needsRedraw() {
		return redraw;
	}

}
