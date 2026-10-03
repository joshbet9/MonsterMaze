package me.monstermaze.cpu;

/**
 * Deterministic bootstrap direct policy used only to validate the full live
 * brain -> avatar -> vanilla movement path before a trained artifact is loaded.
 *
 * <p>It is deliberately simple and is not the target trained opponent.
 */
public final class ReferencePadSeekBrain implements CpuBrain {
    @Override
    public void reset(long seed) {
        // Deterministic reference policy: no stochastic state required yet.
    }

    @Override
    public void decide(CpuObservation observation, CpuAvatarAction action) {
        float yawError = observation.get(14) * 180.0f;
        float turn = Math.max(-30.0f, Math.min(30.0f, yawError));

        // Stay inside the vanilla player input surface. The learned policy will
        // eventually replace this direct rule without changing the execution path.
        boolean needsTurn = Math.abs(yawError) > 3.0f;
        action.set(needsTurn ? 1.0 : 1.0, 0.0, turn, false, true);
    }
}
