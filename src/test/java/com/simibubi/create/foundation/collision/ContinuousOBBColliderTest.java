package com.simibubi.create.foundation.collision;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;

class ContinuousOBBColliderTest {
    private static CollisionList box(double x,double y,double z,double dx,double dy,double dz){var list=new CollisionList();new CollisionList.Populate(list).append(x,y,z,dx,dy,dz);return list;}
    private static ContinuousOBBCollider.CollisionResponse collide(CollisionList blocks,OrientedBB entity,Vec3 motion){return ContinuousOBBCollider.collideMany(blocks,new CollisionList(),entity,motion,.6f,true);}
    private static void finite(Vec3 vector){assertTrue(Double.isFinite(vector.x)&&Double.isFinite(vector.y)&&Double.isFinite(vector.z));}
    @Test void emptySpaceDoesNotBecomeASolidContraptionBoundingBox(){var response=collide(new CollisionList(),new OrientedBB(new AABB(0,0,0,1,2,1)),new Vec3(0,-1,0));assertEquals(Vec3.ZERO,response.collisionResponse);assertFalse(response.surfaceCollision);assertEquals(1,response.temporalResponse);}
    @Test void fastMotionCrossingAnEntireBlockRetainsItsTimeOfImpact(){var entity=new OrientedBB(new AABB(-4,.1,.1,-3.4,1.9,.7));var response=collide(box(.5,.5,.5,.5,.5,.5),entity,new Vec3(10,0,0));assertTrue(response.temporalResponse>0&&response.temporalResponse<1,"Sweep must hit even when its final position is past the wall");assertEquals(0,-3.4+10*response.temporalResponse,.001);finite(response.normal);}
    @Test void aTallRotatedBodyDoesNotLoseItsEndsInTheDenseFilter(){var rotation=new Matrix3d().asXRotation((float)Math.toRadians(45));var entity=new OrientedBB(new Vec3(.5,.5,.5),new Vec3(.2,2,.2),rotation);var center=rotation.transform(new Vec3(0,1.8,0)).add(.5,.5,.5);var response=collide(box(center.x,center.y,center.z,.1,.1,.1),entity,Vec3.ZERO);assertTrue(response.surfaceCollision||response.collisionResponse.lengthSqr()>0);finite(response.collisionResponse);}
    @Test void perfectlyCoincidentCentersStillProduceAFiniteSeparation(){var response=collide(box(.5,.5,.5,.5,.5,.5),new OrientedBB(new AABB(0,0,0,1,1,1)),Vec3.ZERO);finite(response.collisionResponse);finite(response.normal);assertTrue(response.collisionResponse.lengthSqr()>0);}
    @Test void movingAwayFromAnUncontactedSurfaceDoesNotCreateGroundContact(){var response=collide(box(.5,.5,.5,.5,.5,.5),new OrientedBB(new AABB(.2,1.2,.2,.8,3,.8)),new Vec3(0,.5,0));assertFalse(response.surfaceCollision);assertEquals(Vec3.ZERO,response.collisionResponse);assertEquals(1,response.temporalResponse);}
    @Test void aRealGapBetweenTwoBarsRemainsTraversable(){var blocks=box(-1,.5,.5,.2,.5,.5);new CollisionList.Populate(blocks).append(2,.5,.5,.2,.5,.5);var response=collide(blocks,new OrientedBB(new AABB(.2,.1,-2,.8,1.9,-1.4)),new Vec3(0,0,4));assertFalse(response.surfaceCollision);assertEquals(1,response.temporalResponse);assertEquals(Vec3.ZERO,response.collisionResponse);}
    @Test void originalRotationMatricesRoundTripEveryAxis(){var point=new Vec3(3.25,-1.5,2.75);for(var matrix:new Matrix3d[]{new Matrix3d().asXRotation(.7f),new Matrix3d().asYRotation(-1.3f),new Matrix3d().asZRotation(.9f)})assertTrue(matrix.transformTransposed(matrix.transform(point)).distanceTo(point)<.00001);}
}
