package com.simibubi.create.content.processing.recipe;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IngredientAllocationTest {
    @Test void reassignsGeneralIngredientSoSpecificOneCanMatch() {
        assertArrayEquals(new long[]{1, 1}, IngredientAllocation.allocate(new long[]{1, 1}, new long[]{1, 1},
                (ingredient, slot) -> ingredient == 0 || slot == 0));
    }
    @Test void multipleIngredientsUseOneStackWithoutDuplicatingIt() {
        assertArrayEquals(new long[]{9}, IngredientAllocation.allocate(new long[]{9}, new long[]{1, 1, 1, 1, 1, 1, 1, 1, 1}, (i, s) -> true));
        assertNull(IngredientAllocation.allocate(new long[]{8}, new long[]{1, 1, 1, 1, 1, 1, 1, 1, 1}, (i, s) -> true));
    }
    @Test void fluidTagsCanSplitConsumptionAcrossTanks() {
        assertArrayEquals(new long[]{20250, 60750}, IngredientAllocation.allocate(new long[]{20250, 81000}, new long[]{81000}, (i, s) -> true));
    }
    @Test void missingIngredientFailsEvenWithEnoughOtherItems() {
        assertNull(IngredientAllocation.allocate(new long[]{64, 64}, new long[]{1, 1}, (i, s) -> i == 0));
    }
    @Test void longAmountsDoNotWrapAtIntegerBoundary() {
        assertArrayEquals(new long[]{4000000000L}, IngredientAllocation.allocate(new long[]{5000000000L}, new long[]{4000000000L}, (i, s) -> true));
    }
    @Test void emptyDemandConsumesNothing() {
        assertArrayEquals(new long[]{0, 0}, IngredientAllocation.allocate(new long[]{1, 1}, new long[]{}, (i, s) -> true));
    }
}
