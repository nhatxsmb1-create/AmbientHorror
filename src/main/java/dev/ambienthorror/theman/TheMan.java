package dev.ambienthorror.theman;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import dev.ambienthorror.AmbientHorror;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class TheMan {

    public enum Phase { PHASE_1, PHASE_2, PHASE_3 }

    private final AmbientHorror plugin;
    private final Player target;
    private ArmorStand baseEntity;
    private ModeledEntity modeledEntity;
    private ActiveModel activeModel;
    private Phase currentPhase;
    private boolean active = false;

    private static final String MODEL_ID = "manfog";

    public TheMan(AmbientHorror plugin, Player target) {
        this.plugin = plugin;
        this.target = target;
    }

    public boolean spawn(Location location, Phase phase) {
        try {
            baseEntity = location.getWorld().spawn(location, ArmorStand.class, e -> {
                e.setInvisible(true);
                e.setSilent(true);
                e.setGravity(false);
                e.setInvulnerable(true);
                e.setPersistent(false);
                e.setCustomNameVisible(false);
            });

            modeledEntity = ModelEngineAPI.createModeledEntity(baseEntity);
            if (modeledEntity == null) {
                baseEntity.remove();
                plugin.log("[TheMan] ModelEngine không thể tạo ModeledEntity!");
                return false;
            }

            activeModel = ModelEngineAPI.createActiveModel(MODEL_ID);
            if (activeModel == null) {
                baseEntity.remove();
                plugin.log("[TheMan] Không tìm thấy model: " + MODEL_ID);
                return false;
            }

            modeledEntity.addModel(activeModel, true);
            modeledEntity.setBaseEntityVisible(false);
            playAnimation("idle");

            this.currentPhase = phase;
            this.active = true;

            plugin.debug("[TheMan] Spawned " + phase + " → " + target.getName());
            return true;

        } catch (Exception e) {
            plugin.log("[TheMan] Lỗi spawn: " + e.getMessage());
            if (baseEntity != null && !baseEntity.isDead()) baseEntity.remove();
            return false;
        }
    }

    public void despawn() {
        if (!active) return;
        active = false;
        try {
            if (modeledEntity != null) modeledEntity.destroy();
        } catch (Exception ignored) {}
        if (baseEntity != null && !baseEntity.isDead()) baseEntity.remove();
        plugin.debug("[TheMan] Despawned → " + target.getName());
    }

    public void tick() {
        if (!active || baseEntity == null || baseEntity.isDead()) {
            active = false;
            return;
        }
        if (!target.isOnline()) { despawn(); return; }

        switch (currentPhase) {
            case PHASE_1 -> tickPhase1();
            case PHASE_2 -> tickPhase2();
            case PHASE_3 -> tickPhase3();
        }
        facePlayer();
    }

    private void tickPhase1() {
        if (isPlayerLookingAt()) {
            playAnimation("death");
            plugin.getServer().getScheduler().runTaskLater(plugin, this::despawn, 10L);
        }
        if (!"idle".equals(getCurrentAnimation())) playAnimation("idle");
    }

    private void tickPhase2() {
        if (isPlayerLookingAt()) {
            if (!"idle".equals(getCurrentAnimation())) playAnimation("idle");
            return;
        }
        moveToward(0.12);
        if (!"walk".equals(getCurrentAnimation())) playAnimation("walk");
        if (getDistanceToPlayer() <= 3.0) onReachPlayer();
    }

    private void tickPhase3() {
        moveToward(0.22);
        if (!"walk".equals(getCurrentAnimation())) playAnimation("walk");
        if (getDistanceToPlayer() <= 3.0) onReachPlayer();
    }

    private void onReachPlayer() {
        playAnimation("attack");
        plugin.getSanityManager().setSanity(target, 0);
        plugin.getSanityUI().onTierChange(target, 3);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::despawn, 40L);
        plugin.debug("[TheMan] Reached player → " + target.getName());
    }

    private void moveToward(double speed) {
        if (baseEntity == null || baseEntity.isDead()) return;
        Location myLoc = baseEntity.getLocation();
        Vector direction = target.getLocation().toVector()
                .subtract(myLoc.toVector()).normalize().multiply(speed);
        Location newLoc = myLoc.clone().add(direction);
        newLoc.setYaw(myLoc.getYaw());
        newLoc.setPitch(myLoc.getPitch());
        baseEntity.teleport(newLoc);
    }

    private void facePlayer() {
        if (baseEntity == null || baseEntity.isDead()) return;
        Location myLoc = baseEntity.getLocation();
        Vector direction = target.getLocation().toVector().subtract(myLoc.toVector());
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        Location facing = myLoc.clone();
        facing.setYaw(yaw);
        baseEntity.teleport(facing);
    }

    private boolean isPlayerLookingAt() {
        if (baseEntity == null) return false;
        Vector toMan = baseEntity.getLocation().toVector()
                .subtract(target.getEyeLocation().toVector()).normalize();
        return toMan.dot(target.getEyeLocation().getDirection().normalize()) > 0.85;
    }

    private double getDistanceToPlayer() {
        if (baseEntity == null) return 999;
        return baseEntity.getLocation().distance(target.getLocation());
    }

    private void playAnimation(String animName) {
        if (activeModel == null) return;
        try {
            activeModel.getAnimationHandler().playAnimation(animName, 0.1, 0.1, 1.0, true);
        } catch (Exception e) {
            plugin.debug("[TheMan] Animation error: " + e.getMessage());
        }
    }

    private String getCurrentAnimation() {
        if (activeModel == null) return "";
        try {
            var playing = activeModel.getAnimationHandler().getPlayingAnimations();
            if (playing != null && !playing.isEmpty())
                return playing.keySet().iterator().next();
        } catch (Exception ignored) {}
        return "";
    }

    public boolean isActive()        { return active; }
    public Player getTarget()        { return target; }
    public Phase getPhase()          { return currentPhase; }
    public Entity getBaseEntity()    { return baseEntity; }
    public void setPhase(Phase phase) {
        this.currentPhase = phase;
        plugin.debug("[TheMan] Phase → " + phase + " for " + target.getName());
    }
              }
