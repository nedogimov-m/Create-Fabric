package com.simibubi.create.content.trains.track;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Original source outline/collision geometry, with native shape rotation. */
final class TrackShapes {
    private static final VoxelShape ORTHO=TrackVoxelShapes.orthogonal(),DIAG=TrackVoxelShapes.diagonal(),ASC=TrackVoxelShapes.ascending(),LONG=TrackVoxelShapes.longOrthogonalZOffset();
    static VoxelShape outline(TrackShape shape){return switch(shape){
        case XO->rotate(ORTHO,1);case ZO->ORTHO;
        case PD->rotate(DIAG,1);case ND->DIAG;
        case AS->ASC;case AW->rotate(ASC,1);case AN->rotate(ASC,2);case AE->rotate(ASC,3);
        case CR_O->Shapes.or(ORTHO,rotate(ORTHO,1));case CR_D->Shapes.or(DIAG,rotate(DIAG,1));
        case CR_NDX->Shapes.or(DIAG,rotate(ORTHO,1));case CR_NDZ->Shapes.or(DIAG,ORTHO);
        case CR_PDX->Shapes.or(rotate(DIAG,1),rotate(ORTHO,1));case CR_PDZ->Shapes.or(rotate(DIAG,1),ORTHO);
        case TS->LONG;case TW->rotate(LONG,1);case TN->rotate(LONG,2);case TE->rotate(LONG,3);
        case NONE->Block.box(0,0,0,16,4,16);
    };}
    static VoxelShape collision(TrackShape shape){return switch(shape){case AE,AW,AN,AS->Shapes.empty();default->Block.box(0,0,0,16,2,16);};}
    private static VoxelShape rotate(VoxelShape shape,int turns){
        var result=new VoxelShape[]{shape};for(int i=0;i<turns;i++){var next=new VoxelShape[]{Shapes.empty()};result[0].forAllBoxes((a,b,c,d,e,f)->next[0]=Shapes.or(next[0],Shapes.box(1-f,b,a,1-c,e,d)));result[0]=next[0];}return result[0].optimize();
    }
    private TrackShapes(){}
}
