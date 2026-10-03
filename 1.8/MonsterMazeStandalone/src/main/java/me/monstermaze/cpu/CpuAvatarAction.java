package me.monstermaze.cpu;

/**
 * Version-neutral control intent consumed by the CPU avatar.
 *
 * <p>Values are semantic inputs, not direct position/velocity writes.
 */
public final class CpuAvatarAction {
    public final double forward;
    public final double strafe;
    public final float yawDelta;
    public final boolean jump;
    public final boolean sprint;

    public CpuAvatarAction(double forward, double strafe, float yawDelta,
                           boolean jump, boolean sprint) {
        this.forward = clamp(forward);
        this.strafe = clamp(strafe);
        this.yawDelta = clampYaw(yawDelta);
        this.jump = jump;
        this.sprint = sprint;
    }

    public static CpuAvatarAction idle() {
        return new CpuAvatarAction(0.0, 0.0, 0.0f, false, false);
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
