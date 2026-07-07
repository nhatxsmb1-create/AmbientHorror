package dev.ambienthorror.theman;

import dev.ambienthorror.AmbientHorror;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;

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
                e.setAI(true);  // Bật AI để pathfinder hoạt động
                e.setBaby(false);
                e.setRemoveWhenFarAway(false);
                e.setCanPickupItems(false);
                e.getEquipment().clear();
                e.setTarget(null); // Không tự động attack
            });

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

        switch (currentPhase) {
            case PHASE_1 -> tickPhase1();
            case PHASE_2 -> tickPhase2();
            case PHASE_3 -> tickPhase3();
        }

        facePlayer();
    }

    private void tickPhase1() {
        // Đứng yên — nếu nhìn vào thì biến mất
        baseEntity.getPathfinder().stopPathfinding();
        if (isPlayerLookingAt()) {
            playAnimation("death");
            plugin.getServer().getScheduler().runTaskLater(plugin, this::despawn, 10L);
            return;
        }
        if (!"idle".equals(currentAnim)) playAnimation("idle");
    }

    private void tickPhase2() {
        if (isPlayerLookingAt()) {
            // Nhìn vào → dừng lại
            baseEntity.getPathfinder().stopPathfinding();
            if (!"idle".equals(currentAnim)) playAnimation("idle");
            return;
        }
        // Không nhìn → di chuyển chậm
        baseEntity.getPathfinder().moveTo(target, 0.6);
        if (!"walk".equals(currentAnim)) playAnimation("walk");
        if (getDistanceToPlayer() <= 3.0) onReachPlayer();
    }

    private void tickPhase3() {
        if (isPlayerLookingAt()) {
            // Nhìn vào → lao nhanh
            baseEntity.getPathfinder().moveTo(target, 2.0);
        } else {
            // Không nhìn → đi chậm và rình
            baseEntity.getPathfinder().moveTo(target, 0.4);
        }
        if (!"walk".equals(currentAnim)) playAnimation("walk");
        if (getDistanceToPlayer() <= 3.0) onReachPlayer();
    }

    private void onReachPlayer() {
        baseEntity.getPathfinder().stopPathfinding();
        playAnimation("attack");
        plugin.getSanityManager().setSanity(target, 0);
        plugin.getSanityUI().onTierChange(target, 3);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::despawn, 40L);
        plugin.debug("[TheMan] Reached → " + target.getName());
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
