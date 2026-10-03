package me.monstermaze.cpu;

/**
 * Direct policy boundary used by the live CPU opponent.
 *
 * <p>A production implementation receives one authoritative observation and
 * writes one semantic action. It never asks the server to simulate candidates.
 */
public interface CpuBrain {
    void reset(long seed);
    void decide(CpuObservation observation, CpuAvatarAction action);
}
