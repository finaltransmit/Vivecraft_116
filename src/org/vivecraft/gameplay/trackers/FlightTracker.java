package org.vivecraft.gameplay.trackers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import org.vivecraft.gameplay.OpenVRPlayer;
import org.vivecraft.provider.MCOpenVR;
import org.vivecraft.utils.Debug;
import org.vivecraft.utils.math.*;


public class FlightTracker extends Tracker{
    private Angle lookDir = new Angle();

    float armLevelTrigger = -0.2f; //Below chest

    boolean armsExtended;
    private float turnFactor = 90f;

    public FlightTracker(Minecraft mc) {
        super(mc);
    }

    @Override
    public boolean isActive(ClientPlayerEntity player) {
        return false;
        //TODO add to config

    }

    @Override
    public void doProcess(ClientPlayerEntity player) {
        Debug d= Debug.get("wings");
        Vector3d c0up = MCOpenVR.controllerUpHistory[0].averagePosition(0.5);
        Vector3d c1up = MCOpenVR.controllerUpHistory[1].averagePosition(0.5);

        Vector3d c0pos = MCOpenVR.controllerHistory[0].averagePosition(0.5) ;
        Vector3d c1pos = MCOpenVR.controllerHistory[1].averagePosition(0.5) ;

        Vector3d chestCenter =  MCOpenVR.hmdHistory.averagePosition(0.5)
                .add(new Vector3d(0,-0.3,0)); //TODO Do actual rigging instead of estimate


        Vector3d dir0 = c0pos.subtract(chestCenter);
        Vector3d dir1 = c1pos.subtract(chestCenter);

        Vector3d dir0Norm = dir0.normalize();
        Vector3d dir1Norm = dir1.normalize();

        //Handle arm extension
        armsExtended = ( dir0Norm.y > armLevelTrigger && dir1Norm.y > armLevelTrigger);

        if(!armsExtended)
            return;
        

        Vector3d combinedUp = c0up.add(c1up).scale(0.5f).normalize();

        // Base look is player forward
        float bodyYaw = mc.vrPlayer.getVRDataWorld().getBodyYaw();
        Quaternion quat = new Quaternion(Axis.YAW, bodyYaw);



        // Turn offset using wing roll
        Vector3d wingVec = c0pos.subtract(c1pos);
        float turnOffset = (float) wingVec.y * turnFactor;
        quat = quat.rotate(Axis.YAW, turnOffset,false);

        quat = quat.rotate(Axis.PITCH, (float) combinedUp.y * 90, true);

        // Average wing pitch is final pitch
        lookDir = quat.toEuler();



        for (int cId = 0; cId < 1; cId++) {
            Vector3d handFwd = MCOpenVR.controllerForwardHistory[cId].averagePosition(0.5);
            Vector3d handUp = MCOpenVR.controllerUpHistory[cId].averagePosition(0.5);

            Vector3d handLeft = handUp.normalize().crossProduct(handFwd.normalize());


        }
        //TODO
        //Calculate flow based on controller angles
        //Render Elytra on wings
    }

    /*public Quaternion getWingAngle(int c){

    }*/

    public Angle getLookDir() {
        return lookDir;
    }

    public boolean isLookOverridden(ClientPlayerEntity player){
        return (player.isElytraFlying() && wingsExtended(player));
    }

    public boolean wingsExtended(ClientPlayerEntity player){
        if(!isActive(player))
            return false;
        return armsExtended;
    }

}
