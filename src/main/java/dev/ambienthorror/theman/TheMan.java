package dev.ambienthorror.theman;

import dev.ambienthorror.AmbientHorror;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TheMan {

    public enum Phase { PHASE_1, PHASE_2, PHASE_3 }

    private final AmbientHorror plugin;
    private final Player target;
    private Zombie baseEntity;
    private Object modeledEntity;
    private Object activeModel;
    private Phase currentPhase;
    private boolean active = false;
    private String currentAnim = "";
    
    // ✅ Track movement để escape stuck
    private Location lastLocation;
    private int stuckCounter = 0;

    private static final String MODEL_ID = "manfog";

    public TheMan(AmbientHorror plugin, Player target) {
        this.plugin = plugin;
        this.target = target;
    }

    public boolean spawn(Location location, Phase phase) {
        try {
            baseEntity = location.getWorld().spawn(location, Zombie.class, e -> {
                e.setInvisible(true);
                e.setSilent(true);
                e.setInvulnerable(true);
                e.setPersistent(false);
                e.setCustomNameVisible(false);
                e.setAI(false);  // ← TẮT AI, mình control manual
                e.setBaby(false);
                e.setRemoveWhenFarAway(false);
                e.setCanPickupItems(false);
                e.getEquipment().clear();
                e.setTarget(null);
                
                // ✅ Fix cháy nắng
                e.setFireTicks(0);
                e.setCollidable(false);  // ← Không collide block
                
                // ✅ Prevent knockback để smooth movement
                try {
                    e.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE)
                            .setBaseValue(1.0);  // Max knockback resistance
                } catch (Exception ignored) {}
            });
            
            this.lastLocation = location.clone();

            Class<?> apiClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Object apiInstance = null;
            try {
                Method getApi = apiClass.getMethod("api");
                apiInstance = getApi.invoke(null);
            } catch (Exception e1) {
                plugin.debug("[TheMan] api() failed: " + e1.getMessage());
            }

            if (apiInstance != null) {
                try {
                    Method getEntityFactory = apiInstance.getClass()
                            .getMethod("getModeledEntityFactory");
                    Object factory = getEntityFactory.invoke(apiInstance);
                    Method create = factory.getClass().getMethod("create",
                            org.bukkit.entity.Entity.class);
                    modeledEntity = create.invoke(factory, baseEntity);
                } catch (Exception e2) {
                    plugin.debug("[TheMan] factory failed: " + e2.getMessage());
                }
            }

            if (modeledEntity == null) {
                try {
                    Method createModeledEntity = apiClass.getMethod(
                            "createModeledEntity", org.bukkit.entity.Entity.class);
                    modeledEntity = createModeledEntity.invoke(null, baseEntity);
                } catch (Exception e3) {
                    plugin.debug("[TheMan] createModeledEntity failed: " + e3.getMessage());
                }
            }

            if (modeledEntity == null) {
                baseEntity.remove();
                plugin.log("[TheMan] Không thể tạo ModeledEntity!");
                return false;
            }

            try {
                if (apiInstance != null) {
                    Method getModelRegistry = apiInstance.getClass().getMethod("getModelRegistry");
                    Object registry = getModelRegistry.invoke(apiInstance);
                    Method getModel = registry.getClass().getMethod("getModel", String.class);
                    Object blueprint = getModel.invoke(registry, MODEL_ID);
                    if (blueprint != null) {
                        Method createModel = blueprint.getClass().getMethod("createActiveModel");
                        activeModel = createModel.invoke(blueprint);
                    }
                }
            } catch (Exception e4) {
                plugin.debug("[TheMan] registry failed: " + e4.getMessage());
            }

            if (activeModel == null) {
                try {
                    Method createActiveModel = apiClass.getMethod(
                            "createActiveModel", String.class);
                    activeModel = createActiveModel.invoke(null, MODEL_ID);
                } catch (Exception e5) {
                    plugin.debug("[TheMan] createActiveModel failed: " + e5.getMessage());
                }
            }

            if (activeModel == null) {
                baseEntity.remove();
                plugin.log("[TheMan] Không tìm thấy model: " + MODEL_ID);
                return false;
            }

            boolean added = false;
            for (Method m : modeledEntity.getClass().getMethods()) {
                if (m.getName().equals("addModel")) {
                    try {
                        Class<?>[] params = m.getParameterTypes();
                        if (params.length == 2) {
                            m.invoke(modeledEntity, activeModel, true);
                            added = true;
                            break;
                        } else if (params.length == 1) {
                            m.invoke(modeledEntity, activeModel);
                            added = true;
                            break;
                        }
                    } catch (Exception ex) {
                        plugin.debug("[TheMan] addModel failed: " + ex.getMessage());
                    }
                }
            }

            if (!added) {
                plugin.log("[TheMan] Không thể addModel!");
                baseEntity.remove();
                return false;
            }

            for (Method m : modeledEntity.getClass().getMethods()) {
                if (m.getName().contains("BaseEntity") && m.getName().contains("isible")) {
                    try { m.invoke(modeledEntity, false); break; }
                    catch (Exception ignored) {}
                }
            }

            playAnimation("idle");
            this.currentPhase = phase;
            this.active = true;
            plugin.debug("[TheMan] Spawned " + phase + " → " + target.getName());
            return true;

        } catch (ClassNotFoundException e) {
            plugin.log("[TheMan] ModelEngine không được cài!");
            if (baseEntity != null && !baseEntity.isDead()) baseEntity.remove();
            return false;
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
            if (modeledEntity != null) {
                for (Method m : modeledEntity.getClass().getMethods()) {
                    if (m.getName().equals("destroy") && m.getParameterCount() == 0) {
                        m.invoke(modeledEntity);
                        break;
                    }
                }
            }
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

        // ✅ Fix cháy nắng mỗi tick
        baseEntity.setFireTicks(0);
        
        // ✅ Fix nước
        if (baseEntity.isInWater()) {
            baseEntity.setVelocity(new Vector(0, 0.1, 0));
        }

        switch (currentPhase) {
            case PHASE_1 -> tickPhase1();
            case PHASE_2 -> tickPhase2();
            case PHASE_3 -> tickPhase3();
        }

        facePlayer();
        checkStuck();  // ✅ Check nếu stuck
        applyNearbyEffects();  // ✅ Apply effect khi gần
    }

    private void tickPhase1() {
        // Phase 1: Đứng yên từ xa, chỉ nhìn player
        baseEntity.setVelocity(new Vector(0, 0, 0));  // ← Dừng hẳn
        
        if (isPlayerLookingAt()) {
            playAnimation("death");
            plugin.getServer().getScheduler().runTaskLater(plugin, this::despawn, 10L);
            return;
        }
        if (!"idle".equals(currentAnim)) playAnimation("idle");
    }

    private void tickPhase2() {
        // Phase 2: Theo chân từ từ
        if (isPlayerLookingAt()) {
            baseEntity.setVelocity(new Vector(0, 0, 0));
            if (!"idle".equals(currentAnim)) playAnimation("idle");
            return;
        }
        
        // ✅ SMOOTH MOVEMENT - dùng velocity thay pathfinder
        moveTowardPlayer(0.35);  // ← Speed 0.35 (chậm từ từ)
        if (!"walk".equals(currentAnim)) playAnimation("walk");
        
        if (getDistanceToPlayer() <= 3.0) onReachPlayer();
    }

    private void tickPhase3() {
        // Phase 3: Lao vào nhanh
        if (isPlayerLookingAt()) {
            moveTowardPlayer(1.2);  // ← Speed 1.2 (rất nhanh, nhanh hơn player run)
        } else {
            moveTowardPlayer(1.5);  // ← Speed 1.5 (MAX nhanh)
        }
        
        if (!"walk".equals(currentAnim)) playAnimation("walk");
        if (getDistanceToPlayer() <= 3.0) onReachPlayer();
    }

    private void onReachPlayer() {
        baseEntity.setVelocity(new Vector(0, 0, 0));
        playAnimation("attack");
        plugin.getSanityManager().setSanity(target, 0);
        plugin.getSanityUI().onTierChange(target, 3);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::despawn, 40L);
        plugin.debug("[TheMan] Reached → " + target.getName());
    }

    /**
     * ✅ SMOOTH MOVEMENT - di chuyển mượt mà đến player
     */
    private void moveTowardPlayer(double speed) {
        if (baseEntity == null || target == null) return;
        
        Location from = baseEntity.getLocation();
        Location to = target.getLocation().clone();
        
        // Tính vector từ TheMan đến Player
        Vector direction = to.toVector().subtract(from.toVector());
        
        // Normalize + scale theo speed
        if (direction.length() > 0.1) {
            direction = direction.normalize().multiply(speed);
            baseEntity.setVelocity(direction);
        } else {
            baseEntity.setVelocity(new Vector(0, 0, 0));
        }
    }

    /**
     * ✅ CHECK STUCK - nếu bị kẹt block, teleport tiến
     */
    private void checkStuck() {
        if (lastLocation == null || baseEntity == null) return;
        
        double distance = baseEntity.getLocation().distance(lastLocation);
        
        // Nếu di chuyển < 0.1 block trong tick
        if (distance < 0.1) {
            stuckCounter++;
            if (stuckCounter >= 5) {  // ← Stuck 5 tick liên tiếp
                // Teleport tiến 1 block về phía player
                Location playerLoc = target.getLocation();
                Vector direction = playerLoc.toVector()
                        .subtract(baseEntity.getLocation().toVector())
                        .normalize()
                        .multiply(1.5);  // ← Teleport 1.5 block
                
                Location escapeLoc = baseEntity.getLocation().add(direction);
                baseEntity.teleport(escapeLoc);
                stuckCounter = 0;
                plugin.debug("[TheMan] Escape stuck → " + target.getName());
            }
        } else {
            stuckCounter = 0;
        }
        
        lastLocation = baseEntity.getLocation().clone();
    }

    /**
     * ✅ APPLY NEARBY EFFECTS - effect khi gần player
     */
    private void applyNearbyEffects() {
        if (target == null || !target.isOnline()) return;
        
        double distance = getDistanceToPlayer();
        
        // < 5 block: BLINDNESS (chóng mặt)
        if (distance <= 5.0) {
            target.addPotionEffect(
                    new PotionEffect(PotionEffectType.BLINDNESS, 20, 0, false, false),
                    true
            );
        }
        
        // < 8 block: SLOWNESS (chậm)
        if (distance <= 8.0) {
            target.addPotionEffect(
                    new PotionEffect(PotionEffectType.SLOWNESS, 20, 1, false, false),
                    true
            );
        }
        
        // < 6 block: Play scary sound
        if (distance <= 6.0 && Math.random() < 0.05) {  // ← 5% chance mỗi tick
            plugin.getSoundManager().play(target, "breathing_close");
        }
    }

    private void facePlayer() {
        if (baseEntity == null || baseEntity.isDead()) return;
        Location myLoc = baseEntity.getLocation();
        Vector dir = target.getLocation().toVector().subtract(myLoc.toVector());
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        Location facing = myLoc.clone();
        facing.setYaw(yaw);
        facing.setPitch(0);
        baseEntity.teleport(facing);
    }

    private boolean isPlayerLookingAt() {
        if (baseEntity == null) return false;
        Vector toMan = baseEntity.getLocation().toVector()
                .subtract(target.getEyeLocation().toVector()).normalize();
        return toMan.dot(target.getEyeLocation().getDirection().normalize()) > 0.7;
    }

    private double getDistanceToPlayer() {
        if (baseEntity == null) return 999;
        return baseEntity.getLocation().distance(target.getLocation());
    }

    private void playAnimation(String animName) {
        if (activeModel == null || animName.equals(currentAnim)) return;
        try {
            Method getHandler = activeModel.getClass().getMethod("getAnimationHandler");
            Object handler = getHandler.invoke(activeModel);
            for (Method m : handler.getClass().getMethods()) {
                if (m.getName().equals("playAnimation")) {
                    try {
                        Class<?>[] params = m.getParameterTypes();
                        if (params.length == 5) {
                            m.invoke(handler, animName, 0.1, 0.1, 1.0, true);
                        } else if (params.length == 1) {
                            m.invoke(handler, animName);
                        }
                        currentAnim = animName;
                        break;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            plugin.debug("[TheMan] Animation error (" + animName + "): " + e.getMessage());
        }
    }

    public boolean isActive()     { return active; }
    public Player getTarget()     { return target; }
    public Phase getPhase()       { return currentPhase; }
    public Entity getBaseEntity() { return baseEntity; }

    public void setPhase(Phase phase) {
        this.currentPhase = phase;
        plugin.debug("[TheMan] Phase → " + phase + " for " + target.getName());
    }
}
