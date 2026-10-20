package com.simibubi.create.api.behaviour.display;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

/** Native direct text-target contract. A result describes actual target mutation, never transport/publication success. */
public interface TextDisplayTarget {
    record Stats(int maxRows,int maxColumns,AABB bounds){}
    enum Status {APPLIED,PARTIAL,UNCHANGED,UNAVAILABLE,NOT_RUNNING,INVALID_LINE,RESERVED}
    record Result(Status status,int changedRows){public boolean changed(){return changedRows>0;}}
    Stats stats();
    Result acceptText(int firstLine,List<? extends Component> text);
}
