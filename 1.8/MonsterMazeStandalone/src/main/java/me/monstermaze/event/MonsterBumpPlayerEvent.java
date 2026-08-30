package me.monstermaze.event;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
public class MonsterBumpPlayerEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();
    public MonsterBumpPlayerEvent(Player player) { super(player); }
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
