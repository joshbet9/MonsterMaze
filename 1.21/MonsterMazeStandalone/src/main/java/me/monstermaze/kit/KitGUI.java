package me.monstermaze.kit;

import me.monstermaze.MonsterMazePlugin;
import me.monstermaze.game.GameManager;

/**
 * Backwards-compatible entry point for the unified lobby selector.
 * KitManager and existing /mm kit callers can continue using KitGUI.
 */
public class KitGUI extends LobbyGUI {
    public KitGUI(MonsterMazePlugin plugin, GameManager game, KitManager kits) {
        super(plugin, game, kits);
    }
}
