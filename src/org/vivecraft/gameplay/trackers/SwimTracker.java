package org.vivecraft.gameplay.trackers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.particles.IParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import org.vivecraft.api.VRData;
import org.vivecraft.gameplay.OpenVRPlayer;
import org.vivecraft.provider.MCOpenVR;
import org.vivecraft.utils.Debug;
import org.vivecraft.utils.math.Vector3;

import java.awt.*;

/**
 * Created by Hendrik on 02-Aug-16.
 */
public class SwimTracker extends Tracker {

	Vector3d motion = Vector3d.ZERO;
	double friction = 0.9f;

	double lastDist;

	final double riseSpeed = 0.005f;
	double swimspeed = 1f;

	public SwimTracker(Minecraft mc) {
		super(mc);
	}

	public boolean isActive(ClientPlayerEntity p) {
		if (mc.vrSettings.seated)
			return false;
		if (!mc.vrSettings.realisticSwimEnabled)
			return false;
		if (mc.currentScreen != null)
			return false;
		if (p == null || !p.isAlive())
			return false;
		if (mc.playerController == null) return false;
		if (!p.isInWater() && !p.isInLava())
			return false;
		if (p.moveForward > 0)
			return false;
		if (p.moveStrafing > 0)
			return false;
		return true;
	}

	boolean isHandInWater(VRData.VRDevicePose hand) {
		return mc.world.containsAnyLiquid(new AxisAlignedBB(new BlockPos(hand.getPosition())));
	}

	/**
	 * How much of the player is in water.
	 * 0 for completely dry, 1 for completely submerged
	 */
	public double getBodyInWaterAmount() {
		return 0;
	}

	static int flavor = 0;

	public void doProcess(ClientPlayerEntity player) {



//LEGACY:

//		{//float
//			//remove bouyancy for now.
//			Vector3d face = mc.vrPlayer.vrdata_world_pre.hmd.getPosition();
//			float height = (float) (mc.vrPlayer.vrdata_room_pre.hmd.getPosition().y * 0.9);
//			if(height > 1.6)height = 1.6f;
//			Vector3d feets = face.subtract(0,height, 0);
//			double waterLine=256;
//
//			BlockPos bp = new BlockPos(feets);
//			for (int i = 0; i < 4; i++) {
//				Material mat=player.world.getBlockState(bp).getMaterial();
//				if(!mat.isLiquid())
//				{
//					waterLine=bp.getY();
//					break;
//				}
//				bp = bp.up();
//			}
//
//			double percent = (waterLine - feets.y) / (face.y - feets.y);
//
//			if(percent < 0){
//				//how did u get here, drybones?
//				return;
//			}
//
//			if(percent < 0.5 && player.onGround){
//				return;
//				//no diving in the kiddie pool.
//			}
//
//			player.addVelocity(0, 0.018D , 0); //counteract most gravity.
//
//			double neutal = player.collidedHorizontally? 0.5 : 1;
//
//			if(percent > neutal && percent < 2){ //between halfway submerged and 1 body length under.
//				//rise!
//				double buoyancy = 2 - percent;
//				if(player.collidedHorizontally)  player.addVelocity(0, 00.03f, 0);	
//				player.addVelocity(0, 0.0015 + buoyancy/100 , 0);		
//			}

//		}
		if(flavor == 0){//swim

			Vector3d controllerR= mc.vrPlayer.vrdata_world_pre.getController(0).getPosition();
			Vector3d controllerL= mc.vrPlayer.vrdata_world_pre.getController(1).getPosition();




			Vector3d middle= controllerL.subtract(controllerR).scale(0.5).add(controllerR);

			Vector3d hmdPos=mc.vrPlayer.vrdata_world_pre.getHeadPivot().subtract(0,0.3,0);

			Vector3d movedir=middle.subtract(hmdPos).normalize().add(
					mc.vrPlayer.vrdata_world_pre.hmd.getDirection()).scale(0.5);

			Vector3d contollerDir= mc.vrPlayer.vrdata_world_pre.getController(0).getCustomVector(new Vector3d(0,0,-1)).add(
					mc.vrPlayer.vrdata_world_pre.getController(1).getCustomVector(new Vector3d(0,0,-1))).scale(0.5);
			double dirfactor=contollerDir.add(movedir).length()/2;

			double distance= hmdPos.distanceTo(middle);
			double distDelta=lastDist-distance;

			if(distDelta>0){
				Vector3d velo=movedir.scale(distDelta*swimspeed*dirfactor);
				motion=motion.add(velo.scale(0.15));
			}

			lastDist=distance;
			player.setSwimming(motion.length() > 0.3f);
			player.setSprinting(motion.length() > 1.0f);
			player.addVelocity(motion.x,motion.y,motion.z);
			motion=motion.scale(friction);
		}
		else if(flavor == 1){
			Debug d = Debug.get("swim");

			Vector3d[] appliedForces = new Vector3d[]{ Vector3d.ZERO, Vector3d.ZERO};

			VRData vrData = mc.vrPlayer.getVRDataWorld();
			//for each hand
			for (int i = 0; i < 2; i++) {
				if(!isHandInWater(vrData.getHand(i))){
					continue;
				}

				Vector3d handFwd = MCOpenVR.getHandRotation(i).transform(new Vector3(0, 0 ,-1)).toVector3d();
				Vector3d handVelocity = MCOpenVR.controllerHistory[i].getVelocity(0.5);

				double projectedSpeed = -handFwd.dotProduct(handVelocity);

				if(projectedSpeed<0){
					projectedSpeed = 0;
				}

				Vector3d projectedVel = handFwd.scale( projectedSpeed );

				appliedForces[i] = projectedVel.scale(swimspeed * 0.05);
			}


			Vector3d finalVelocity = appliedForces[0].add(appliedForces[1]);
			player.addVelocity(finalVelocity.x,finalVelocity.y,finalVelocity.z);

			//TODO Finish
		}


	}
}