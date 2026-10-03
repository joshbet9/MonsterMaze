package me.monstermaze.cpu;

/**
 * Reusable semantic control buffer consumed by a CPU avatar.
 *
 * <p>The buffer is intentionally mutable so a production brain can reuse one
 * action instance per bot instead of allocating an object every tick.
 */
public final class CpuAvatarAction {
    public double forward;
    public double strafe;
    public float yawDelta;
    public boolean jump;
    public boolean sprint;

    public CpuAvatarAction() {
        clear();
    }

    public CpuAvatarAction(double forward, double strafe, float yawDelta,
                           boolean jump, boolean sprint) {
        set(forward, strafe, yawDelta, jump, sprint);
    }

    public CpuAvatarAction set(double forward, double strafe, float yawDelta,
                               boolean jump, boolean sprint) {
        this.forward = clamp(forward);
        this.strafe = clamp(strafe);
        this.yawDelta = clampYaw(yawDelta);
        this.jump = jump;
        this.sprint = sprint;
        return this;
    }

    public CpuAvatarAction clear() {
        forward = 0.0;
        strafe = 0.0;
        yawDelta = 0.0f;
        jump = false;
        sprint = false;
        return this;
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.max(-1.0, Math.min(1.0, value));
    }

    private static float clampYaw(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 0.0f;
        return Math.max(-30.0f, Math.min(30.0f, value));
    }
}
