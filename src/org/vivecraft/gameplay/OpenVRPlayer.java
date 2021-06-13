package org.vivecraft.gameplay;

import java.util.ArrayList;
import java.util.Random;

import net.minecraft.util.math.vector.Vector2f;
import org.vivecraft.api.NetworkHelper;
import org.vivecraft.api.VRData;
import org.vivecraft.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.gameplay.screenhandlers.RadialHandler;
import org.vivecraft.gameplay.trackers.BowTracker;
import org.vivecraft.gameplay.trackers.FlightTracker;
import org.vivecraft.gameplay.trackers.Tracker;
import org.vivecraft.gameplay.trackers.VehicleTracker;
import org.vivecraft.provider.MCOpenVR;
import org.vivecraft.render.RenderPass;
import org.vivecraft.settings.VRSettings;

import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.WinGameScreen;
import net.minecraft.client.particle.DiggingParticle;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.passive.horse.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.util.Util;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import org.vivecraft.utils.math.Angle;
import org.vivecraft.utils.math.Vector2;


// VIVE
public class OpenVRPlayer 
{
	Minecraft mc = Minecraft.getInstance();

	//loop start
	public VRData vrdata_room_pre; //just latest polling data, origin = 0,0,0, rotation = 0, scaleXZ = walkMultiplier
	//handle server messages
	public VRData vrdata_world_pre; //latest polling data but last tick's origin, rotation, scale
	//tick here
	public VRData vrdata_room_post; //recalc here in the odd case the walk multiplier changed
	public VRData vrdata_world_post; //this is used for rendering and the server. _render is interpolated between this and _pre.
	//interpolate here between post and pre
	public VRData vrdata_world_render; // using interpolated origin, scale, rotation
	//loop end
	
	private long errorPrintTime = Util.milliTime();

	ArrayList<Tracker> trackers=new ArrayList<>();
	public void registerTracker(Tracker tracker){
		trackers.add(tracker);
	}

	public OpenVRPlayer() {
		this.vrdata_room_pre = new VRData(new Vector3d(0, 0, 0), mc.vrSettings.walkMultiplier, 1, 0);
		this.vrdata_room_post = new VRData(new Vector3d(0, 0, 0), mc.vrSettings.walkMultiplier, 1, 0);
		this.vrdata_world_post = new VRData(new Vector3d(0, 0, 0), mc.vrSettings.walkMultiplier, 1, 0);
		this.vrdata_world_pre = new VRData(new Vector3d(0, 0, 0), mc.vrSettings.walkMultiplier, 1, 0);
	}
	
	public VRData getVRDataWorld(){
		if(vrdata_world_render!=null)
			return vrdata_world_render;
		else
			return vrdata_world_pre;
	}

	public float worldScale = Minecraft.getInstance().vrSettings.overrides.getSetting(VRSettings.VrOptions.WORLD_SCALE).getFloat();
	private boolean noTeleportClient = true;
	private boolean teleportOverride = false;
	public int teleportWarningTimer = -1;

	public Vector3d roomOrigin = new Vector3d(0,0,0);
	private boolean isFreeMoveCurrent = true; // based on a heuristic of which locomotion type was last used
	
	//for overriding the world scale settings with wonder foods.
	public double wfMode = 0;
	public int wfCount = 0;
	//

	public int roomScaleMovementDelay = 0;

	boolean initdone =false;
	
	public static OpenVRPlayer get()
	{
		return Minecraft.getInstance().vrPlayer;
	}

	public static Vector3d room_to_world_pos(Vector3d pos, VRData data){
		Vector3d out = new Vector3d(pos.x*data.worldScale, pos.y*data.worldScale, pos.z*data.worldScale);
		out =out.rotateYaw(data.rotation_radians);
		return out.add(data.origin.x, data.origin.y, data.origin.z);
	}

	public static Vector3d world_to_room_pos(Vector3d pos, VRData data){
		Vector3d out = pos.add(-data.origin.x, -data.origin.y, -data.origin.z);
		out = new Vector3d(out.x/data.worldScale, out.y/data.worldScale, out.z/data.worldScale);
		return out.rotateYaw(-data.rotation_radians);
	}

	public void postPoll(){
		this.vrdata_room_pre = new VRData(new Vector3d(0, 0, 0), mc.vrSettings.walkMultiplier, 1, 0);
		GuiHandler.processGui();
		KeyboardHandler.processGui();
		RadialHandler.processGui();
	}

	public void preTick(){
		this.onTick = true;
		this.vrdata_world_pre = new VRData(this.roomOrigin, mc.vrSettings.walkMultiplier, worldScale, (float) Math.toRadians(mc.vrSettings.vrWorldRotation));

		//adjust world scale
		float scaleSetting = mc.vrSettings.overrides.getSetting(VRSettings.VrOptions.WORLD_SCALE).getFloat();
		if (mc.gameRenderer.isInMenuRoom()) 
			this.worldScale = 1.0f;
		else if (this.wfCount > 0 && !mc.isGamePaused()) {
			if(this.wfCount < 40){		
				worldScale = (float) (worldScale - wfMode);
				if(wfMode > 0) { //go back.
					if(this.worldScale < scaleSetting)
						this.worldScale = scaleSetting;
				} else if (wfMode < 0) {
					if(this.worldScale >  scaleSetting)
						this.worldScale = scaleSetting;				
				}					
			} else { //shrink or grow
				worldScale = (float) (worldScale + wfMode);
				if(wfMode > 0) {
					if(this.worldScale >  20f) 
						this.worldScale = 20f;
				} else if (wfMode < 0) {
					if(this.worldScale <  0.1f) 
						this.worldScale = 0.1f;				
				}			
			}
			this.wfCount--;
		} else {
			this.worldScale = scaleSetting;
		}
		
		if(mc.vrSettings.seated && !mc.gameRenderer.isInMenuRoom())
			mc.vrSettings.vrWorldRotation = MCOpenVR.seatedRot;

	}
  
	public void postTick(){
		Minecraft mc = Minecraft.getInstance();

		//translational position change due only to scale. - move room to fix entity in place during scale changes.
		VRData tempps = new VRData(vrdata_world_pre.origin, mc.vrSettings.walkMultiplier, vrdata_world_pre.worldScale, vrdata_world_pre.rotation_radians);		
		VRData temps = new VRData(vrdata_world_pre.origin, mc.vrSettings.walkMultiplier, worldScale, vrdata_world_pre.rotation_radians);	
		Vector3d scaleOffset = temps.hmd.getPosition().subtract(tempps.hmd.getPosition());
	    this.roomOrigin = this.roomOrigin.subtract(scaleOffset);
		//
		
		//Handle all room translations up to this point and then rotate it around the hmd.
		VRData temp = new VRData(roomOrigin, mc.vrSettings.walkMultiplier, worldScale, vrdata_world_pre.rotation_radians);	
		float end = mc.vrSettings.vrWorldRotation;
		float start = (float) Math.toDegrees(vrdata_world_pre.rotation_radians);			
		rotateOriginAround(-end+start, temp.getHeadPivot());
		//

		this.vrdata_room_post = new VRData(new Vector3d(0, 0, 0), mc.vrSettings.walkMultiplier, 1, 0);
		this.vrdata_world_post = new VRData(this.roomOrigin, mc.vrSettings.walkMultiplier, worldScale, (float) Math.toRadians(mc.vrSettings.vrWorldRotation));

		//Vivecraft - setup the player entity with the correct view for the logic tick.
		doPermanantLookOverride(mc.player, vrdata_world_post);
		//

		NetworkHelper.sendVRPlayerPositions(this);
		this.onTick = false;
	}

	public void preRender(float par1){
		Minecraft mc = Minecraft.getInstance();

		//do some interpolatin'

		float interpolatedWorldScale = vrdata_world_post.worldScale*par1 + vrdata_world_pre.worldScale*(1-par1);

		float end = vrdata_world_post.rotation_radians;
		float start = vrdata_world_pre.rotation_radians;

		float difference = Math.abs(end - start);

		if (difference > Math.PI)
			if (end > start)
				start += 2*Math.PI;
			else
				end += 2*Math.PI;

		float interpolatedWorldRotation_Radians = (float) (end*par1 + start*(1-par1));
	
		Vector3d interPolatedRoomOrigin = new Vector3d(
				vrdata_world_pre.origin.x + (vrdata_world_post.origin.x - vrdata_world_pre.origin.x) * (double)par1,
				vrdata_world_pre.origin.y + (vrdata_world_post.origin.y - vrdata_world_pre.origin.y) * (double)par1,
				vrdata_world_pre.origin.z + (vrdata_world_post.origin.z - vrdata_world_pre.origin.z) * (double)par1
				);
		
	
//		System.out.println(vrdata_world_post.origin.x + " " + vrdata_world_pre.origin.x + " = " + interPolatedRoomOrigin.x);

		this.vrdata_world_render = new VRData(interPolatedRoomOrigin, mc.vrSettings.walkMultiplier, interpolatedWorldScale, interpolatedWorldRotation_Radians);

		//handle special items
		for (Tracker tracker : trackers) {
			if (tracker.getEntryPoint() == Tracker.EntryPoint.SPECIAL_ITEMS) {
				tracker.idleTick(mc.player);
				if (tracker.isActive(mc.player)){
					tracker.doProcess(mc.player);
				}else{
					tracker.reset(mc.player);
				}
			}
		}


	}

	public void postRender(float par1){
	}

	private boolean onTick; 
	public void setRoomOrigin(double x, double y, double z, boolean reset) { 

//		if(!reset && !onTick){
//			if (Util.milliTime() - errorPrintTime >= 1000) { // Only print once per second, since this might happen every frame
//				System.out.println("Vivecraft Warning: Room origin set wrongly! Printing call stack:");
//				Thread.dumpStack();
//				errorPrintTime = Util.milliTime();
//			}
//			return;
//		}

		if (reset){
			if(vrdata_world_pre!=null)
				vrdata_world_pre.origin = new Vector3d(x, y, z);
		}
		roomOrigin = new Vector3d(x, y, z);
	} 

	//set room 
	public void snapRoomOriginToPlayerEntity(ClientPlayerEntity player, boolean reset, boolean instant)
	{
		if (Thread.currentThread().getName().equals("Server thread"))
			return;
		
		if(player == null || player.getPositionVec() ==  Vector3d.ZERO) return;
		
		Minecraft mc = Minecraft.getInstance();
		
		if(mc.sneakTracker.sneakCounter > 0) return; //avoid relocating the view while roomscale dismounting.

		VRData temp = vrdata_world_pre;

		if(instant) temp = new VRData(roomOrigin, mc.vrSettings.walkMultiplier, this.worldScale, (float) Math.toRadians(mc.vrSettings.vrWorldRotation));

		Vector3d campos = temp.getHeadPivot().subtract(temp.origin);

		double x,y,z;

		x = player.getPosX() - campos.x;
		z = player.getPosZ() - campos.z;
		y = player.getPosY() + player.getRoomYOffsetFromPose();
				
		setRoomOrigin(x, y, z, reset);
	}


	public float rotDiff_Degrees(float start, float end){ //calculate shortest difference between 2 angles.

		double x = Math.toRadians(end);
		double y = Math.toRadians(start);
		
		return (float) Math.toDegrees((Math.atan2(Math.sin(x-y), Math.cos(x - y))));
	}

	public void rotateOriginAround(float degrees, Vector3d o){
		Vector3d pt = roomOrigin;

		float rads = (float) Math.toRadians(degrees); //reverse rotate.

		if(rads!=0)
			setRoomOrigin(
					Math.cos(rads) * (pt.x-o.x) - Math.sin(rads) * (pt.z-o.z) + o.x,
					pt.y,
					Math.sin(rads) * (pt.x-o.x) + Math.cos(rads) * (pt.z-o.z) + o.z
					,false);

	//	VRData test = new VRData(roomOrigin, mc.vrSettings.walkMultiplier, this.worldScale, (float) Math.toRadians(mc.vrSettings.vrWorldRotation));

	//	Vector3d b = vrdata_world_pre.hmd.getPosition();
	//	Vector3d a = test.hmd.getPosition();
	//	double dist = b.distanceTo(a); //should always be 0 (unless in a vehicle)   		
	}



	public void tick(ClientPlayerEntity player, Minecraft mc, Random rand)
	{
		if(!player.initFromServer) return;

		if(!initdone){
			System.out.println("<Debug info start>");
			System.out.println("Room object: "+this);
			System.out.println("Room origin: " + vrdata_world_pre.origin);
			System.out.println("Hmd position room: " + vrdata_room_pre.hmd.getPosition());
			System.out.println("Hmd position world: " + vrdata_world_pre.hmd.getPosition());
			System.out.println("Hmd Projection Left: " + mc.stereoProvider.eyeproj[0]);
			System.out.println("Hmd Projection Right: " + mc.stereoProvider.eyeproj[1]);
			System.out.println("<Debug info end>");
			initdone =true;
		}

		doPlayerMoveInRoom(player);
		
		for (Tracker tracker : trackers) {
			if (tracker.getEntryPoint() == Tracker.EntryPoint.LIVING_UPDATE) {
				tracker.idleTick(mc.player);
				if (tracker.isActive(mc.player)){
					tracker.doProcess(mc.player);
				}else{
					tracker.reset(mc.player);
				}
			}
		}
	
		if(player.isPassenger()){
			Entity e = mc.player.getRidingEntity();		
			if (e instanceof AbstractHorseEntity) {
				AbstractHorseEntity el = (AbstractHorseEntity) e;
				if (el.canBeSteered() && el.isHorseSaddled() && !mc.horseTracker.isActive((ClientPlayerEntity)mc.player)){
					  el.renderYawOffset = vrdata_world_pre.getBodyYaw();
					  mc.vehicleTracker.rotationCooldown = 10;
				}
			}else if (e instanceof MobEntity) {
				MobEntity el = (MobEntity) e; //pigs and striders.
				if (el.canBeSteered()){
					el.renderYawOffset = vrdata_world_pre.getBodyYaw();
				    mc.vehicleTracker.rotationCooldown = 10;
				}
			}
		}
	}

	public void doPlayerMoveInRoom(ClientPlayerEntity player){

		if(roomScaleMovementDelay > 0){
			roomScaleMovementDelay--;
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if(player == null) return;
		if(player.isSneaking()) {return;} //jrbudda : prevent falling off things or walking up blocks while moving in room scale.
		if(player.isSleeping()) return; //
		if(mc.jumpTracker.isjumping()) return; //
		if(mc.climbTracker.isGrabbingLadder()) return; //
		if(!player.isAlive()) return; //
			
		VRData temp = new VRData(this.roomOrigin, mc.vrSettings.walkMultiplier, worldScale, this.vrdata_world_pre.rotation_radians);
		
		if(mc.vehicleTracker.canRoomscaleDismount(mc.player)) {
			Vector3d mountpos = mc.player.getRidingEntity().getPositionVec();
			Vector3d tp = temp.getHeadPivot();
			double dist = Math.sqrt((tp.x - mountpos.x) * (tp.x - mountpos.x) + (tp.z - mountpos.z) *(tp.z - mountpos.z));
			//TODO: The timing on this is wrong, I think.
			if (dist > 1.0) {
				mc.sneakTracker.sneakCounter = 5;
			}	
			return; 
		}
		//if(Math.abs(player.motionX) > 0.01) return;
		//if(Math.abs(player.motionZ) > 0.01) return;

		float playerHalfWidth = player.getWidth() / 2;
		float playerHeight = player.getHeight();

		// move player's X/Z coords as the HMD moves around the room

		//OK this is the first place I've found where we reallly need to update the VR data before doing this calculation.

		Vector3d eyePos = temp.getHeadPivot();

		double x = eyePos.x;
		double y = player.getPosY();
		double z = eyePos.z;

		// create bounding box at dest position
		AxisAlignedBB bb = new AxisAlignedBB(
				x - (double) playerHalfWidth,
				y,
				z - (double) playerHalfWidth,
				x + (double) playerHalfWidth,
				y + (double) playerHeight,
				z + (double) playerHalfWidth);

		Vector3d torso = null;

		// valid place to move player to?
		float var27 = 0.0625F;
		boolean emptySpot = mc.world.hasNoCollisions(player, bb);
		
		if (emptySpot)
		{
			// don't call setPosition style functions to avoid shifting room origin
			player.setRawPosition(x, !mc.vrSettings.simulateFalling ? y : player.getPosY(), z);
			
			player.setBoundingBox(new AxisAlignedBB(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY + playerHeight, bb.maxZ));
			player.fallDistance = 0.0F;

			torso = getEstimatedTorsoPosition(x, y, z);
		}

		//test for climbing up a block
		else if (((mc.vrSettings.walkUpBlocks && player.getMuhJumpFactor() == 1) || (mc.climbTracker.isGrabbingLadder() && mc.vrSettings.realisticClimbEnabled)) && player.fallDistance == 0)
		{
			if (torso == null)
			{
				torso = getEstimatedTorsoPosition(x, y, z);
			}

			// is the player significantly inside a block?
			float climbShrink = player.getSize(player.getPose()).width * 0.45f;
			double shrunkClimbHalfWidth = playerHalfWidth - climbShrink;
			AxisAlignedBB bbClimb = new AxisAlignedBB(
					torso.x - shrunkClimbHalfWidth,
					bb.minY,
					torso.z - shrunkClimbHalfWidth,
					torso.x + shrunkClimbHalfWidth,
					bb.maxY,
					torso.z + shrunkClimbHalfWidth);

			boolean iscollided = !mc.world.hasNoCollisions(player, bbClimb);

			if(iscollided){
				double xOffset = torso.x - x;
				double zOffset = torso.z - z;

				bb = bb.offset(xOffset, 0, zOffset);

				int extra = 0;
				if(player.isOnLadder() && mc.vrSettings.realisticClimbEnabled)
					extra = 6;

				for (int i = 0; i <=10+extra ; i++)
				{
					bb = bb.offset(0, .1, 0);

					emptySpot = mc.world.hasNoCollisions(player, bb);
					if (emptySpot)
					{
						x += xOffset;  	
						z += zOffset;
						y += 0.1f*i;

						player.setRawPosition(x, y, z);

						player.setBoundingBox(new AxisAlignedBB(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ));

						Vector3d dest  = roomOrigin.add(xOffset, 0.1f*i, zOffset);

						setRoomOrigin(dest.x, dest.y, dest.z, false);

						Vector3d look = player.getLookVec();
						Vector3d forward = new Vector3d(look.x,0,look.z).normalize();
						player.fallDistance = 0.0F;
						mc.player.stepSound(new BlockPos(player.getPositionVec()), player.getPositionVec());

						break;
					}
				}
			}
		}
	}




	// use simple neck modeling to estimate torso location
	public Vector3d getEstimatedTorsoPosition(double x, double y, double z)
	{
		Entity player = Minecraft.getInstance().player;
		Vector3d look = player.getLookVec();
		Vector3d forward = new Vector3d(look.x, 0, look.z).normalize();
		float factor = (float)look.y * 0.25f;
		Vector3d torso = new Vector3d(
				x + forward.x * factor,
				y + forward.y * factor,
				z + forward.z * factor);

		return torso;
	}




	public void blockDust(double x, double y, double z, int count, BlockPos bp, BlockState bs, float scale, float velscale){
		Random rand = new Random();
		Minecraft mc = Minecraft.getInstance();
		for (int i = 0; i < count; ++i)
		{

//			DiggingParticle p = new DiggingParticle(mc.world,
//					x + ((double)rand.nextFloat() - 0.5D) *.02f,
//					y + ((double)rand.nextFloat() - 0.5D) *.02f,
//					z + ((double)rand.nextFloat() - 0.5D) *.02f,
//					((double)rand.nextFloat()- 0.5D)*.001f,
//					((double)rand.nextFloat()- 0.5D)*.05f,
//					((double)rand.nextFloat()- 0.5D)*.001f,
//					bs);
			DiggingParticle p = new DiggingParticle(mc.world,x,y,z,
					0,0,0,
					bs);
			
			p.multiplyVelocity(velscale);

			mc.particles.addEffect(p.setBlockPos(bp).multiplyParticleScaleBy(scale));

		}
	}

	public void updateFreeMove() {
		if (mc.teleportTracker.isAiming())
			isFreeMoveCurrent = false;
		if (mc.player.movementInput.moveForward != 0 || mc.player.movementInput.moveStrafe != 0)
			isFreeMoveCurrent = true;
			updateTeleportKeys();
	}
	
	/**
	 * Again with the weird logic, see {@link #isTeleportEnabled()}
	 *
	 * @return
	 */
	public boolean getFreeMove() {
		if (mc.vrSettings.seated)
			return mc.vrSettings.seatedFreeMove || !isTeleportEnabled();
		else
			return isFreeMoveCurrent || mc.vrSettings.forceStandingFreeMove;
	}



	//	public Vector3d getWalkMultOffset(){
	//		ClientPlayerEntity player = Minecraft.getInstance().player;
	//		if(player==null || !player.initFromServer)
	//			return Vector3d.ZERO;
	//		float walkmult=Minecraft.getInstance().vrSettings.walkMultiplier;
	//		Vector3d pos=vecMult(MCOpenVR.getCenterEyePosition(),interpolatedWorldScale);
	//		return new Vector3d(pos.x*walkmult,pos.y,pos.z*walkmult).subtract(pos);
	//	}


	//	//leave these for now
	//	public FloatBuffer getHMDMatrix_World() {
	//		Matrix4f out = MCOpenVR.hmdRotation;
	//		Matrix4f rot;
	//		
	//		if(vrdata_world_render != null)
	//		 rot = Matrix4f.rotationY(vrdata_world_render.rotation);
	//		else
	//		 rot = Matrix4f.rotationY(vrdata_world_pre.rotation);
	//
	//		return Matrix4f.multiply(rot, out).toFloatBuffer();
	//	}	
	//	
	//	public FloatBuffer getControllerMatrix_World(int controller) {
	//		Matrix4f out = MCOpenVR.getAimRotation(controller);
	//		Matrix4f rot = Matrix4f.rotationY(vrdata_world_post.rotation);
	//		return Matrix4f.multiply(rot,out).toFloatBuffer();
	//	}
	//	
	//	public FloatBuffer getControllerMatrix_World_Transposed(int controller) {
	//		Matrix4f out = MCOpenVR.getAimRotation(controller);
	//		Matrix4f rot;
	//		
	//		if(vrdata_world_render != null)
	//		 rot = Matrix4f.rotationY(vrdata_world_render.rotation);
	//		else
	//		 rot = Matrix4f.rotationY(vrdata_world_pre.rotation);
	//		
	//		return Matrix4f.multiply(rot,out).transposed().toFloatBuffer();
	//	}



	@Override
	public String toString() {
		return "VRPlayer: " +
				"\r\n \t origin: " + this.roomOrigin +
				"\r\n \t rotation: " + String.format("%.3f", Minecraft.getInstance().vrSettings.vrWorldRotation) +
				"\r\n \t scale: " + String.format("%.3f", this.worldScale) + 
				"\r\n \t room_pre " + this.vrdata_room_pre + 
				"\r\n \t world_pre " + this.vrdata_world_pre + 
				"\r\n \t world_post " + this.vrdata_world_post + 
				"\r\n \t world_render " + this.vrdata_world_render ;	
	}

	public Vector3d getRightClickLookOverride(PlayerEntity entity, int c) {
		Vector3d out = entity.getLookVec();
		
		if(mc.gameRenderer.crossVec != null){
			out = entity.getEyePosition(1).subtract(mc.gameRenderer.crossVec).normalize().inverse(); //backwards
		}
		
		ItemStack i = c == 0 ? entity.getHeldItemMainhand() : entity.getHeldItemOffhand();

		if(i.getItem() instanceof SnowballItem ||
				i.getItem() instanceof EggItem ||
				i.getItem() instanceof SpawnEggItem ||
				i.getItem() instanceof PotionItem ||
				i.getItem() instanceof BowItem ||
				(i.getItem() instanceof CrossbowItem && ((CrossbowItem)i.getItem()).isCharged(i))
				) { //TODO: Check for others?
			//use r_hand aim
			VRData data = mc.vrPlayer.vrdata_world_pre;
			out = data.getController(c).getDirection();		
			
			Vector3d aim = mc.bowTracker.getAimVector(); //this is actually reversed
			if (mc.bowTracker.isNotched() && aim != null && aim.lengthSquared() > 0) {
				out = aim.inverse();
			}
		} 
		else if  (i.getItem() == Items.BUCKET){
			if(mc.interactTracker.bukkit[c])
				out = entity.getEyePosition(1).subtract(mc.vrPlayer.vrdata_world_pre.getController(c).getPosition()).normalize().inverse(); //backwards
		}

		return out;
	}
	
	public void doPermanantLookOverride(ClientPlayerEntity entity, VRData data){
		if(entity == null)return;
		//This is used for all sorts of things both client and server side.

		if(true){  //hmm, to use HMD? LETS TRY IT!
			//set model view direction to camera
			entity.rotationYawHead = entity.rotationYaw = data.hmd.getYaw();
			entity.rotationPitch = -data.hmd.getPitch();
		} else { //default to looking 'at' the crosshair position.

		}	

		if((entity.isSprinting() && entity.movementInput.jump) || entity.isElytraFlying() || (entity.isSwimming() && entity.moveForward > 0)){
			//needed for server side movement.
			if(mc.vrSettings.vrFreeMoveMode == mc.vrSettings.FREEMOVE_CONTROLLER ){
				entity.rotationYawHead = entity.rotationYaw = data.getController(1).getYaw();
				entity.rotationPitch = -data.getController(1).getPitch();
			}else{
				entity.rotationYawHead = entity.rotationYaw = data.hmd.getYaw();
				entity.rotationPitch = -data.hmd.getPitch();
			}
		}

		if(mc.flightTracker.isActive(entity)){
			if(mc.flightTracker.isLookOverridden(entity)) {
				Angle look = mc.flightTracker.getLookDir();

				entity.rotationPitch = look.getPitch();
				entity.rotationYawHead = look.getYaw();
			}
		}

		if(entity.isPassenger()) {
			//needed for server side movement.
			Vector3d dir = VehicleTracker.getSteeringDirection(entity);
			if(dir != null) {
				entity.rotationPitch = (float)Math.toDegrees(Math.asin(-dir.y/dir.length()));
				entity.rotationYawHead = entity.rotationYaw = (float)Math.toDegrees(Math.atan2(-dir.x,dir.z));
			}
		}
		
	}

	public Vector3d AimedPointAtDistance(VRData source, int controller, double distance) {
        Vector3d vec3d = source.getController(controller).getPosition();
        Vector3d vec3d1 = source.getController(controller).getDirection();
        return vec3d.add(vec3d1.x * distance, vec3d1.y * distance, vec3d1.z * distance);
	}
	
	public RayTraceResult rayTraceBlocksVR(VRData source, int controller, double blockReachDistance, boolean p_174822_4_)
	{
		Vector3d vec3d = source.getController(controller).getPosition();
		Vector3d vec3d2 = AimedPointAtDistance(source, controller, blockReachDistance);
		return mc.world.rayTraceBlocks(new RayTraceContext(vec3d, vec3d2, RayTraceContext.BlockMode.OUTLINE, p_174822_4_ ? RayTraceContext.FluidMode.ANY : RayTraceContext.FluidMode.NONE, mc.player));
	}
	
	public boolean isTeleportSupported() {
		return !noTeleportClient;
	}

	public boolean isTeleportOverridden() {
		return teleportOverride;
	}

	/**
	 * The logic here is a bit weird, because teleport is actually still enabled even in
	 * seated free move mode. You could use it by simply binding it in the vanilla controls.
	 * However, when free move is forced in standing mode, teleport is outright disabled.
	 *
	 * @return
	 */
	public boolean isTeleportEnabled() {
		boolean enabled = !noTeleportClient || teleportOverride;
		if (!mc.vrSettings.seated)
			return enabled && !mc.vrSettings.forceStandingFreeMove;
		else
			return enabled;
	}

	public void setTeleportSupported(boolean supported) {
		noTeleportClient = !supported;
		updateTeleportKeys();
	}

	public void setTeleportOverride(boolean override) {
		teleportOverride = override;
		updateTeleportKeys();
	}

    private void updateTeleportKeys() {
		MCOpenVR.getInputAction(MCOpenVR.keyTeleport).setEnabled(isTeleportEnabled());
		MCOpenVR.getInputAction(MCOpenVR.keyTeleportFallback).setEnabled(!isTeleportEnabled());
	}


}

