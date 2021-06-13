package org.vivecraft.gameplay.trackers;

import com.bhaptics.haptic.models.PositionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.network.play.server.SEntityMetadataPacket;
import net.minecraft.network.play.server.SExplosionPacket;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.EffectType;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.TransformationMatrix;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.system.CallbackI;
import org.vivecraft.api.VRData;
import org.vivecraft.bodylink.Haptics;
import org.vivecraft.bodylink.RiggedBody;
import org.vivecraft.provider.MCOpenVR;
import org.vivecraft.utils.Debug;
import org.vivecraft.utils.math.Axis;
import org.vivecraft.utils.math.Quaternion;

import java.awt.*;
import java.util.ArrayList;
import java.util.Random;


public class HapticTracker extends Tracker{
    ArrayList<HapticsModule> modules = new ArrayList<>();

    float lastHealth;
    Random random = new Random();
    int hungerThreshold = 15;

    public HapticTracker(Minecraft mc) {
        super(mc);
        modules.add(new RainModule());
    }

    @Override
    public boolean isActive(ClientPlayerEntity player) {
        return Haptics.isConnected();
    }

    @Override
    public void doProcess(ClientPlayerEntity player) {
        //TODO Find better place for this
        RiggedBody.getInstance().updatePose(mc.vrPlayer.getVRDataWorld());

        float thresholdLowHealth = 5;
        float thresholdCriticalHealth = 2;

        Haptics.setLoopState(Haptics.Animations.fire, player.isBurning());
        Haptics.setLoopState(Haptics.Animations.potion_positive, hasPotionPositive(player));
        Haptics.setLoopState(Haptics.Animations.potion_negative, hasPotionNegative(player));
        Haptics.setLoopState(Haptics.Animations.low_health, player.getHealth() < thresholdLowHealth && !(player.getHealth() < thresholdCriticalHealth) );
        Haptics.setLoopState(Haptics.Animations.critical_health, player.getHealth() < thresholdCriticalHealth );
        Haptics.setLoopState(Haptics.Animations.rain, isInRain(player));

        for (HapticsModule h: modules) {
            if(h.enabled){
                h.tick(player);
            }
        }

        if(player.getHealth() != lastHealth){
            float damage = lastHealth - player.getHealth();
            if(damage > 0){
                handleHit(DamageSource.GENERIC,damage);
            }
            lastHealth = player.getHealth();
        }

        doHunger(player);

        Haptics.tick();
    }


    void doHunger(ClientPlayerEntity player){
        int food=player.getFoodStats().getFoodLevel();
        if(food < hungerThreshold){
            float foodPerc = (float) food / 20;
            if(random.nextInt(20 * 3 + (int)(foodPerc * 30 * 20) ) == 0){
                Haptics.getAnimation(Haptics.Animations.hunger).playSingle(false,null);
            }
        }
    }


    boolean hasPotionPositive(ClientPlayerEntity player){
        for( EffectInstance effect : player.getActivePotionEffects()){
            if( effect.getPotion().getEffectType() == EffectType.BENEFICIAL
                && !effect.isAmbient() ){
                return true;
            }
        }
        return false;
    }
    boolean hasPotionNegative(ClientPlayerEntity player){
        for( EffectInstance effect : player.getActivePotionEffects()){
            if( effect.getPotion().getEffectType() == EffectType.HARMFUL
                    && !effect.isAmbient() ){
                return true;
            }
        }
        return false;
    }

    boolean isInRain(ClientPlayerEntity player){
        BlockPos blockpos = player.getPosition();
        return player.world.isRainingAt(blockpos) || player.world.isRainingAt(new BlockPos((double)blockpos.getX(), player.getBoundingBox().maxY, (double)blockpos.getZ()));
    }

    public void handleExplode(SExplosionPacket packetIn) {
        double maxExplosionDist = 5;
        Vector3d exposionPos = new Vector3d(packetIn.getX(),packetIn.getY(),packetIn.getZ());
        double explosionDist = exposionPos.subtract(mc.player.getPositionVec()).length();
        if(explosionDist < maxExplosionDist){
            double distFactor = 1.0 - (explosionDist / maxExplosionDist);
            Haptics.getAnimation(Haptics.Animations.explosion).playSingle(true,null, distFactor);
        }
    }

    public void handleHit(DamageSource damageSrc, float damageAmount){
        //TODO Always generic. Need custom server packet.
        Vector3d dmgVec = new Vector3d(1,0,1).mul(mc.player.getMotion().scale(-1.0)).normalize();
        dmgVec=new Quaternion(Axis.YAW,mc.player.rotationYawHead +180).multiply(dmgVec);

        Haptics.getAnimation(Haptics.Animations.generic_hit).playSingle(true,dmgVec);
    }

    public void handleEat(ItemStack itemStack){
        if(itemStack.isFood()){
                if(itemStack.getItem().getFood().getEffects().isEmpty()) {
                    Haptics.getAnimation(Haptics.Animations.consume).playSingle(true,null);
                }else{
                    Haptics.getAnimation(Haptics.Animations.consume_effect).playSingle(true,null);
                }
        }
    }



    abstract static class HapticsModule{
        boolean enabled = false;
        abstract void tick(ClientPlayerEntity player);

    }

    class RainModule extends HapticsModule{
        // Range: 0 to 1
        double minAngle = 0;
        double dropChanceThreshold = 0.2;


        Random random = new Random();

        public RainModule() {
            super();
            enabled = false;
        }

        @Override
        void tick(ClientPlayerEntity player) {

            if (!player.world.isRaining())
                return;

            boolean isSnow = player.world.getBiome(player.getPosition()).doesSnowGenerate(player.world,player.getPosition());

            // Terminal Velocity of rain in m/s
            Vector3d rainFall = new Vector3d(0,-9,0);

            // Add inverse player motion for relative motion
            rainFall = rainFall.subtract(player.getMotion());

            Vector3d rainDir = rainFall.normalize();

            ArrayList<RiggedBody.HapticPoint> points = RiggedBody.getInstance().getHapticPoints(PositionType.All);

            Debug d = Debug.get("hapticsrain");

            for(RiggedBody.HapticPoint p : points){
                // Check Occlusion
                if(!player.world.isRainingAt(new BlockPos(p.getPosWorld(player)))){
                    continue;
                }

                Vector3d normal = p.getNormal(true);

                d.drawVector("vec:"+p.hashCode(),p.getPosWorld(player), normal, Color.red);

                double exposure = normal.dotProduct(rainDir.inverse());

                if( exposure < minAngle ){
                    // cull backface
                    exposure = 0;
                }

                double snowFactor = isSnow? 2.0 : 1.0;

                if(Math.abs(random.nextGaussian()) * exposure > dropChanceThreshold * snowFactor) {
                    int intensity = 10; // TODO Randomize
                    int duration = 10;
                    p.motor.dot(intensity, duration);
                }
            }
        }


    }
}
