# `/pause` test plan

1. Solo 1.8: start a solo run, wait until LIVE, run `/pause`.
2. Confirm player cannot move, jump, interact, use items, take damage, lose hunger, or be moved by mob knockback.
3. Confirm stage/phase timer, safe-pad decay, center deterioration, mob movement and run timing remain frozen.
4. Run `/pause` again and confirm a 3-second countdown, then normal gameplay resumes.
5. Confirm the timer continues from the exact pre-pause value and the paused duration is excluded from PB/run elapsed time.
6. Repeat on 1.21 solo.
7. On a non-solo server, confirm a normal player is denied `/pause` and an OP/admin can pause/resume.
8. Confirm `/pause` cannot be used to pause a lobby or STARTING countdown before LIVE.
