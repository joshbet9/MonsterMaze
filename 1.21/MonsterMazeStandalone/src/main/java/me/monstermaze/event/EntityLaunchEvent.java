package me.monstermaze.event;
import org.bukkit.entity.Entity;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;
public class EntityLaunchEvent extends EntityEvent {
    private static final HandlerList handlers = new HandlerList();
    public EntityLaunchEvent(Entity ent) { super(ent); }
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
