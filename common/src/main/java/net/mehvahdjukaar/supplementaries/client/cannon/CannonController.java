package net.mehvahdjukaar.supplementaries.client.cannon;

import net.mehvahdjukaar.supplementaries.common.block.tiles.CannonBlockTile;
import net.mehvahdjukaar.supplementaries.common.network.ModNetwork;
import net.mehvahdjukaar.supplementaries.common.network.ServerBoundRequestOpenCannonGuiMessage;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;


public class CannonController {

    protected static CannonBlockTile cannon;

    private static CameraType lastCameraType;
    protected static HitResult hit;
    private static boolean firstTick = true;

    // values controlled by player mouse movement. Not actually what camera uses
    private static float yawIncrease;
    private static float pitchIncrease;

    private static boolean needsToUpdateServer;
    protected static ShootingMode shootingMode = ShootingMode.DOWN;

    @Nullable
    protected static CannonTrajectory trajectory;

    // lerp camera
    private static Vec3 lastCameraPos;
    private static float lastZoomOut = 0;
    private static float lastCameraYaw = 0;
    private static float lastCameraPitch = 0;

    protected static boolean showsTrajectory = true;

    public static void startControlling(CannonBlockTile tile) {
        cannon = tile;
        firstTick = true;
        shootingMode = cannon.getTrajectoryData().drag() != 0 ? ShootingMode.DOWN : ShootingMode.STRAIGHT;
        Minecraft mc = Minecraft.getInstance();
        lastCameraType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        mc.gui.setOverlayMessage(Component.translatable("message.supplementaries.cannon_maneuver",
                mc.options.keyShift.getTranslatedKeyMessage(),
                mc.options.keyAttack.getTranslatedKeyMessage()), false);
    }

    public static void stopControllingAndSync() {
        CannonBlockTile.sync(cannon, false, true);
        stopControlling();
    }

    public static void stopControlling() {
        cannon = null;
        lastCameraYaw = 0;
        lastCameraPitch = 0;
        lastZoomOut = 0;
        lastCameraPos = null;
        if (lastCameraType != null) {
            Minecraft.getInstance().options.setCameraType(lastCameraType);
        }
    }

    public static boolean isActive() {
        return cannon != null;
    }

    public static boolean setupCamera(Camera camera, BlockGetter level, Entity entity,
                                      boolean detached, boolean thirdPersonReverse, float partialTick) {

        if (isActive()) {
            Vec3 centerCannonPos = cannon.getBlockPos().getCenter();

            if (lastCameraPos == null) {
                lastCameraPos = camera.getPosition();
                lastCameraYaw = camera.getYRot();
                lastCameraPitch = camera.getXRot();
            }

            // lerp camera
            Vec3 targetCameraPos = centerCannonPos.add(0, 2, 0);
            float targetYRot = camera.getYRot() + yawIncrease;
            float targetXRot = Mth.clamp(camera.getXRot() + pitchIncrease, -90, 90);

            camera.setPosition(targetCameraPos);
            camera.setRotation(targetYRot, targetXRot);

            lastCameraPos = camera.getPosition();
            lastCameraYaw = camera.getYRot();
            lastCameraPitch = camera.getXRot();
            lastZoomOut = (float) camera.getMaxZoom(4);


            camera.move(-lastZoomOut, 0, -1);

            yawIncrease = 0;
            pitchIncrease = 0;


            // find hit result
            Vec3 lookDir2 = new Vec3(camera.getLookVector());
            float maxRange = 128;
            Vec3 actualCameraPos = camera.getPosition();
            Vec3 endPos = actualCameraPos.add(lookDir2.scale(maxRange));

            hit = level.clip(new ClipContext(actualCameraPos, endPos,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, entity));

            Vec3 targetVector = hit.getLocation().subtract(cannon.getBlockPos().getCenter());
            //rotate so we can work in 2d
            Vec2 target = new Vec2((float) Mth.length(targetVector.x, targetVector.z), (float) targetVector.y);
            target = target.add(target.normalized().scale(0.05f)); //so we hopefully hit the block we are looking at

            // calculate the yaw of target. no clue why its like this
            float wantedCannonYaw = Mth.PI + (float) Mth.atan2(-targetVector.x, targetVector.z);

            var restraints = cannon.getPitchAndYawRestrains();
            var ballistic = cannon.getTrajectoryData();
            trajectory = CannonTrajectory.findBest(target,
                    ballistic.gravity(), ballistic.drag(), cannon.getFirePower(), shootingMode,
                    restraints.minPitch(), restraints.maxPitch());

            if (trajectory != null) {
                float followSpeed = 0.25f;
                //TODO: improve
                cannon.setRestrainedPitch(Mth.rotLerp(1, cannon.getPitch(), trajectory.pitch() * Mth.RAD_TO_DEG));

                float yaw = wantedCannonYaw * Mth.RAD_TO_DEG;//  Mth.rotLerp(1, cannon.getYaw(1), wantedCannonYaw * Mth.RAD_TO_DEG);
                float prevYaw = cannon.getYaw(0);
                //overshoots since we are setting this every render tick. Calculates the next tick yaw
                float deltaYaw = Mth.wrapDegrees(yaw - prevYaw);
                yaw = prevYaw + deltaYaw / partialTick;
                cannon.setRestrainedYaw(yaw);
            }

            return true;
        }
        return false;
    }


    public static void onPlayerRotated(double yawAdd, double pitchAdd) {
        float scale = 0.2f;
        yawIncrease += (float) (yawAdd * scale);
        pitchIncrease += (float) (pitchAdd * scale);
        if (yawAdd != 0 || pitchAdd != 0) needsToUpdateServer = true;
    }


    public static void onKeyPressed(int key, int action, int modifiers) {
        if (action != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        Options options = mc.options;
        if (options.keyShift.matches(key, action)) {
            stopControllingAndSync();
        } else if (options.keyInventory.matches(key, action)) {
            ModNetwork.CHANNEL.sendToServer(new ServerBoundRequestOpenCannonGuiMessage(cannon.getBlockPos()));

            //Minecraft.getInstance().player.openMenu()
        } else if (options.keyJump.matches(key, action)) {
            if (trajectory != null && trajectory.gravity() != 0) {
                shootingMode = shootingMode.cycle();
                needsToUpdateServer = true;
            }
        }
    }

    public static void onMouseScrolled(double scrollDelta) {
        if (scrollDelta != 0) {
            cannon.changeFirePower((int) scrollDelta);
            needsToUpdateServer = true;
        }
    }


    public static void onPlayerAttack() {
        if (cannon != null && cannon.readyToFire()) {
            CannonBlockTile.sync(cannon, true, false);
        }
    }

    public static void onPlayerUse() {
        showsTrajectory = !showsTrajectory;
    }

    public static void onInputUpdate(Input input) {
        if (firstTick) {
            // resets input
            firstTick = false;
            input.down = false;
            input.jumping = false;
            input.up = false;
            input.left = false;
            input.right = false;
            input.shiftKeyDown = false;
            input.forwardImpulse = 0;
            input.leftImpulse = 0;
        }
    }

    public static void onClientTick(Minecraft mc) {
        if (!isActive()) return;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        Level level = player.level();
        BlockPos pos = cannon.getBlockPos();
        float maxDist = 7;
        if (level.getBlockEntity(pos) == cannon && !cannon.isRemoved() &&
                pos.distToCenterSqr(player.position()) < maxDist * maxDist) {
            if (needsToUpdateServer) {
                needsToUpdateServer = false;
                CannonBlockTile.sync(cannon, false, false);
            }
        } else {
            stopControllingAndSync();
        }
    }

}

