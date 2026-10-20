package com.simibubi.create.content.trains.signal;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.EdgePointType;

/** Register the real complete signal point codec before railway SavedData is decoded. */
public final class SignalEdgePoints {
    public static final EdgePointType<SignalBoundary> SIGNAL = EdgePointType.register(
        Create.asResource("signal"), SignalBoundary::new, SignalBoundary.CODEC);
    public static void register() {}
    private SignalEdgePoints() {}
}
