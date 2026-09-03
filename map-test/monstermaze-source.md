# Monster Maze Source Code

Source: `C:\mineplex-reborn-stable\mineplex-reborn-stable\Plugins\Nautilus.Game.Arcade\src\nautilus\game\arcade\game\games\monstermaze`
Generated: 08/28/2026 14:42:17



---

## File: `AbilityUseEvent.java`

```java
package nautilus.game.arcade.game.games.monstermaze.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class AbilityUseEvent extends PlayerEvent
{
	/**
	 * @author Mysticate
	 */
	
	private static final HandlerList _handlers = new HandlerList();
	
	private static HandlerList getHandlerList()
	{
		return _handlers;
	}
	
	@Override
	public HandlerList getHandlers()
	{
		return getHandlerList();
	}
	
	public AbilityUseEvent(Player player)
	{
		super(player);
	}
}
```


---

## File: `AbilityUseTracker.java`

```java
package nautilus.game.arcade.game.games.monstermaze.trackers;

import java.util.ArrayList;
import java.util.List;

import nautilus.game.arcade.events.GameStateChangeEvent;
import nautilus.game.arcade.game.Game.GameState;
import nautilus.game.arcade.game.games.monstermaze.MonsterMaze;
import nautilus.game.arcade.game.games.monstermaze.events.AbilityUseEvent;
import nautilus.game.arcade.stats.StatTracker;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public class AbilityUseTracker extends StatTracker<MonsterMaze>
{
	/**
	 * @author Mysticate
	 */
	
	private List<String> _out = new ArrayList<String>();
	
	public AbilityUseTracker(MonsterMaze game)
	{
		super(game);
	}

	@EventHandler
	public void onAbilityUse(AbilityUseEvent event)
	{
		if (!getGame().IsLive())
			return;
		
		if (isOut(event.getPlayer()))
			return;
		
		setOut(event.getPlayer());
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onGameEnd(GameStateChangeEvent event)
	{
		if (event.GetState() != GameState.End)
			return;
		
		if (getGame().getWinners() == null)
			return;
		
		for (Player player : getGame().getWinners())
		{
			if (isOut(player))
				continue;
			
			addStat(player);
		}
		
		_out.clear();
	}
	
	private boolean isOut(Player player)
	{
		for (String out : _out)
		{
			if (out.equalsIgnoreCase(player.getName()))
			{
				return true;
			}
		}
		return false;
	}
	
	private void setOut(Player player)
	{
		if (isOut(player))
			return;
		
		_out.add(player.getName());
	}
	
	private void addStat(Player player)
	{
		addStat(player, "Hard Mode", 1, true, false);
	}
}
```


---

## File: `EntityLaunchEvent.java`

```java
package nautilus.game.arcade.game.games.monstermaze.events;

import org.bukkit.entity.Entity;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;

public class EntityLaunchEvent extends EntityEvent
{
	/**
	 * @author Mysticate
	 */
	
	private static final HandlerList _handlers = new HandlerList();
	
	private static HandlerList getHandlerList()
	{
		return _handlers;
	}
	
	@Override
	public HandlerList getHandlers()
	{
		return getHandlerList();
	}
	
	public EntityLaunchEvent(Entity ent)
	{
		super(ent);
	}
}
```


---

## File: `events\AbilityUseEvent.java`

```java
package nautilus.game.arcade.game.games.monstermaze.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class AbilityUseEvent extends PlayerEvent
{
	/**
	 * @author Mysticate
	 */
	
	private static final HandlerList _handlers = new HandlerList();
	
	private static HandlerList getHandlerList()
	{
		return _handlers;
	}
	
	@Override
	public HandlerList getHandlers()
	{
		return getHandlerList();
	}
	
	public AbilityUseEvent(Player player)
	{
		super(player);
	}
}
```


---

## File: `events\EntityLaunchEvent.java`

```java
package nautilus.game.arcade.game.games.monstermaze.events;

import org.bukkit.entity.Entity;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;

public class EntityLaunchEvent extends EntityEvent
{
	/**
	 * @author Mysticate
	 */
	
	private static final HandlerList _handlers = new HandlerList();
	
	private static HandlerList getHandlerList()
	{
		return _handlers;
	}
	
	@Override
	public HandlerList getHandlers()
	{
		return getHandlerList();
	}
	
	public EntityLaunchEvent(Entity ent)
	{
		super(ent);
	}
}
```


---

## File: `events\FirstToSafepadEvent.java`

```java
package nautilus.game.arcade.game.games.monstermaze.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class FirstToSafepadEvent extends PlayerEvent
{
	/**
	 * @author Mysticate
	 */
	
	private static final HandlerList _handlers = new HandlerList();
	
	private static HandlerList getHandlerList()
	{
		return _handlers;
	}
	
	@Override
	public HandlerList getHandlers()
	{
		return getHandlerList();
	}
	
	public FirstToSafepadEvent(Player player)
	{
		super(player);
	}
}
```


---

## File: `events\MonsterBumpPlayerEvent.java`

```java
package nautilus.game.arcade.game.games.monstermaze.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class MonsterBumpPlayerEvent extends PlayerEvent
{
	/**
	 * @author Mysticate
	 */
	
	private static final HandlerList _handlers = new HandlerList();
	
	private static HandlerList getHandlerList()
	{
		return _handlers;
	}
	
	@Override
	public HandlerList getHandlers()
	{
		return getHandlerList();
	}
		
	public MonsterBumpPlayerEvent(Player player)
	{
		super(player);
	}
}
```


---

## File: `events\SafepadBuildEvent.java`

```java
package nautilus.game.arcade.game.games.monstermaze.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SafepadBuildEvent extends Event
{
	/**
	 * @author Mysticate
	 */
	
	private static final HandlerList _handlers = new HandlerList();
	
	private static HandlerList getHandlerList()
	{
		return _handlers;
	}
	
	@Override
	public HandlerList getHandlers()
	{
		return getHandlerList();
	}
}
```


---

## File: `FirstToSafepadEvent.java`

```java
package nautilus.game.arcade.game.games.monstermaze.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class FirstToSafepadEvent extends PlayerEvent
{
	/**
	 * @author Mysticate
	 */
	
	private static final HandlerList _handlers = new HandlerList();
	
	private static HandlerList getHandlerList()
	{
		return _handlers;
	}
	
	@Override
	public HandlerList getHandlers()
	{
		return getHandlerList();
	}
	
	public FirstToSafepadEvent(Player player)
	{
		super(player);
	}
}
```


---

## File: `FirstToSafepadTracker.java`

```java
package nautilus.game.arcade.game.games.monstermaze.trackers;

import nautilus.game.arcade.game.games.monstermaze.MonsterMaze;
import nautilus.game.arcade.game.games.monstermaze.events.FirstToSafepadEvent;
import nautilus.game.arcade.stats.StatTracker;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

public class FirstToSafepadTracker extends StatTracker<MonsterMaze>
{
	/**
	 * @author Mysticate
	 */
		
	public FirstToSafepadTracker(MonsterMaze game)
	{
		super(game);
	}

	@EventHandler
	public void onSafepadFirst(FirstToSafepadEvent event)
	{
		if (!getGame().IsLive())
			return;
		
		addStat(event.getPlayer());
	}
	
	private void addStat(Player player)
	{
		addStat(player, "Speed", 1, false, false);
	}
}
```


---

## File: `KitBodyBuilder.java`

```java
package nautilus.game.arcade.game.games.monstermaze.kits;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import mineplex.core.common.util.F;
import mineplex.core.game.kit.GameKit;
import mineplex.core.itemstack.ItemStackFactory;

import nautilus.game.arcade.ArcadeManager;
import nautilus.game.arcade.kit.Kit;
import nautilus.game.arcade.kit.Perk;
import nautilus.game.arcade.kit.perks.PerkDummy;

public class KitBodyBuilder extends Kit
{

	private static final Perk[] PERKS =
			{
					new PerkDummy("Body Builder", new String[]{"Your " + F.elem("Max Health") + " increases by " + F.skill("One Heart"), "when you are first to a Safe Pad.", "Maximum of 15 hearts."})
			};

	private static final ItemStack[] PLAYER_ITEMS =
			{
					ItemStackFactory.Instance.CreateStack(Material.COMPASS, (byte) 0, 1, F.item("Safe Pad Locator"))
			};

	public KitBodyBuilder(ArcadeManager manager)
	{
		super(manager, GameKit.MONSTER_MAZE_BODY_BUILDER, PERKS);
	}

	@Override
	public void GiveItems(Player player)
	{
		player.getInventory().setItem(4, PLAYER_ITEMS[0]);
	}
}
```


---

## File: `KitJumper.java`

```java
package nautilus.game.arcade.game.games.monstermaze.kits;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import mineplex.core.common.util.C;
import mineplex.core.common.util.F;
import mineplex.core.game.kit.GameKit;
import mineplex.core.itemstack.ItemStackFactory;

import nautilus.game.arcade.ArcadeManager;
import nautilus.game.arcade.game.games.monstermaze.kits.perks.PerkJumpsDisplay;
import nautilus.game.arcade.kit.Kit;
import nautilus.game.arcade.kit.Perk;
import nautilus.game.arcade.kit.perks.PerkDummy;

public class KitJumper extends Kit
{

	private static final Perk[] PERKS =
			{
					new PerkDummy("Jumper", new String[]{C.cGray + "You can jump " + C.cYellow + "5 Times" + C.cGray + "."}),
					new PerkJumpsDisplay()
			};

	private static final ItemStack[] PLAYER_ITEMS =
			{
					ItemStackFactory.Instance.CreateStack(Material.COMPASS, (byte) 0, 1, F.item("Safe Pad Locator")),
					ItemStackFactory.Instance.CreateStack(Material.FEATHER, (byte) 0, 5, C.cYellow + C.Bold + 5 + " Jumps Remaining")
			};

	public KitJumper(ArcadeManager manager)
	{
		super(manager, GameKit.MONSTER_MAZE_JUMPER, PERKS );
	}

	@Override
	public void GiveItems(Player player)
	{
		player.getInventory().setItem(4, PLAYER_ITEMS[0]);
		player.getInventory().setItem(8, PLAYER_ITEMS[1]);
	}
}
```


---

## File: `KitRepulsor.java`

```java
package nautilus.game.arcade.game.games.monstermaze.kits;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import mineplex.core.common.util.C;
import mineplex.core.common.util.F;
import mineplex.core.game.kit.GameKit;
import mineplex.core.itemstack.ItemStackFactory;

import nautilus.game.arcade.ArcadeManager;
import nautilus.game.arcade.game.games.monstermaze.kits.perks.PerkRepulsor;
import nautilus.game.arcade.kit.Kit;
import nautilus.game.arcade.kit.Perk;

public class KitRepulsor extends Kit
{
	private static final Perk[] PERKS =
			{
					new PerkRepulsor()
			};

	private static final ItemStack[] PLAYER_ITEMS =
			{
					ItemStackFactory.Instance.CreateStack(Material.COAL, (byte) 0, 3, C.cYellow + C.Bold + "Right Click" + C.cWhite + C.Bold + " - " + C.cGreen + C.Bold + "Repulse"),
					ItemStackFactory.Instance.CreateStack(Material.COMPASS, (byte) 0, 1, F.item("Safe Pad Locator"))
			};

	public KitRepulsor(ArcadeManager manager)
	{
		super(manager, GameKit.MONSTER_MAZE_REPULSOR, PERKS);
	}

	@Override
	public void GiveItems(Player player)
	{
		player.getInventory().setItem(0, PLAYER_ITEMS[0]);
		player.getInventory().setItem(4, PLAYER_ITEMS[1]);
	}
}
```


---

## File: `kits\KitBodyBuilder.java`

```java
package nautilus.game.arcade.game.games.monstermaze.kits;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import mineplex.core.common.util.F;
import mineplex.core.game.kit.GameKit;
import mineplex.core.itemstack.ItemStackFactory;

import nautilus.game.arcade.ArcadeManager;
import nautilus.game.arcade.kit.Kit;
import nautilus.game.arcade.kit.Perk;
import nautilus.game.arcade.kit.perks.PerkDummy;

public class KitBodyBuilder extends Kit
{

	private static final Perk[] PERKS =
			{
					new PerkDummy("Body Builder", new String[]{"Your " + F.elem("Max Health") + " increases by " + F.skill("One Heart"), "when you are first to a Safe Pad.", "Maximum of 15 hearts."})
			};

	private static final ItemStack[] PLAYER_ITEMS =
			{
					ItemStackFactory.Instance.CreateStack(Material.COMPASS, (byte) 0, 1, F.item("Safe Pad Locator"))
			};

	public KitBodyBuilder(ArcadeManager manager)
	{
		super(manager, GameKit.MONSTER_MAZE_BODY_BUILDER, PERKS);
	}

	@Override
	public void GiveItems(Player player)
	{
		player.getInventory().setItem(4, PLAYER_ITEMS[0]);
	}
}
```


---

## File: `kits\KitJumper.java`

```java
package nautilus.game.arcade.game.games.monstermaze.kits;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import mineplex.core.common.util.C;
import mineplex.core.common.util.F;
import mineplex.core.game.kit.GameKit;
import mineplex.core.itemstack.ItemStackFactory;

import nautilus.game.arcade.ArcadeManager;
import nautilus.game.arcade.game.games.monstermaze.kits.perks.PerkJumpsDisplay;
import nautilus.game.arcade.kit.Kit;
import nautilus.game.arcade.kit.Perk;
import nautilus.game.arcade.kit.perks.PerkDummy;

public class KitJumper extends Kit
{

	private static final Perk[] PERKS =
			{
					new PerkDummy("Jumper", new String[]{C.cGray + "You can jump " + C.cYellow + "5 Times" + C.cGray + "."}),
					new PerkJumpsDisplay()
			};

	private static final ItemStack[] PLAYER_ITEMS =
			{
					ItemStackFactory.Instance.CreateStack(Material.COMPASS, (byte) 0, 1, F.item("Safe Pad Locator")),
					ItemStackFactory.Instance.CreateStack(Material.FEATHER, (byte) 0, 5, C.cYellow + C.Bold + 5 + " Jumps Remaining")
			};

	public KitJumper(ArcadeManager manager)
	{
		super(manager, GameKit.MONSTER_MAZE_JUMPER, PERKS );
	}

	@Override
	public void GiveItems(Player player)
	{
		player.getInventory().setItem(4, PLAYER_ITEMS[0]);
		player.getInventory().setItem(8, PLAYER_ITEMS[1]);
	}
}
```


---

## File: `kits\KitRepulsor.java`

```java
package nautilus.game.arcade.game.games.monstermaze.kits;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import mineplex.core.common.util.C;
import mineplex.core.common.util.F;
import mineplex.core.game.kit.GameKit;
import mineplex.core.itemstack.ItemStackFactory;

import nautilus.game.arcade.ArcadeManager;
import nautilus.game.arcade.game.games.monstermaze.kits.perks.PerkRepulsor;
import nautilus.game.arcade.kit.Kit;
import nautilus.game.arcade.kit.Perk;

public class KitRepulsor extends Kit
{
	private static final Perk[] PERKS =
			{
					new PerkRepulsor()
			};

	private static final ItemStack[] PLAYER_ITEMS =
			{
					ItemStackFactory.Instance.CreateStack(Material.COAL, (byte) 0, 3, C.cYellow + C.Bold + "Right Click" + C.cWhite + C.Bold + " - " + C.cGreen + C.Bold + "Repulse"),
					ItemStackFactory.Instance.CreateStack(Material.COMPASS, (byte) 0, 1, F.item("Safe Pad Locator"))
			};

	public KitRepulsor(ArcadeManager manager)
	{
		super(manager, GameKit.MONSTER_MAZE_REPULSOR, PERKS);
	}

	@Override
	public void GiveItems(Player player)
	{
		player.getInventory().setItem(0, PLAYER_ITEMS[0]);
		player.getInventory().setItem(4, PLAYER_ITEMS[1]);
	}
}
```


---

## File: `kits\KitSlowball.java`

```java
package nautilus.game.arcade.game.games.monstermaze.kits;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;

import mineplex.core.common.util.F;
import mineplex.core.game.kit.GameKit;
import mineplex.core.itemstack.ItemStackFactory;
import mineplex.minecraft.game.core.damage.CustomDamageEvent;

import nautilus.game.arcade.ArcadeManager;
import nautilus.game.arcade.kit.Kit;
import nautilus.game.arcade.kit.Perk;
import nautilus.game.arcade.kit.perks.PerkConstructor;

public class KitSlowball extends Kit
{

	private static final Perk[] PERKS =
			{
					new PerkConstructor("Slowballer", 2, 16, Material.SNOW_BALL, "Slowball", true)
			};

	private static final ItemStack[] PLAYER_ITEMS =
			{
					ItemStackFactory.Instance.CreateStack(Material.COMPASS, (byte) 0, 1, F.item("Safe Pad Locator"))
			};

	public KitSlowball(ArcadeManager manager)
	{
		super(manager, GameKit.MONSTER_MAZE_SLOW_BALL, PERKS);
	}

	@Override
	public void GiveItems(Player player)
	{
		player.getInventory().setItem(4, PLAYER_ITEMS[0]);
	}

	@EventHandler
	public void SnowballHit(CustomDamageEvent event)
	{
		if (event.GetProjectile() == null)
			return;

		if (!(event.GetProjectile() instanceof Snowball))
			return;

		event.GetProjectile().remove();

		Manager.GetCondition().Factory().Slow("Snowball Slow", event.GetDamageeEntity(), (LivingEntity) event.GetProjectile().getShooter(), 2, 1, false, false, true, false);
	}
}
```


---

## File: `kits\PerkJumpsDisplay.java`

```java
package nautilus.game.arcade.game.games.monstermaze.kits.perks;

import mineplex.core.common.util.C;
import mineplex.core.common.util.F;
import mineplex.core.common.util.UtilTextBottom;
import mineplex.core.updater.UpdateType;
import mineplex.core.updater.event.UpdateEvent;
import nautilus.game.arcade.kit.Perk;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

public class PerkJumpsDisplay extends Perk
{
	public PerkJumpsDisplay()
	{
		super("Display", new String[0], false);
	}

	@EventHandler
	public void onShow(UpdateEvent event)
	{
		if (event.getType() != UpdateType.FAST)
			return;
		
		for (Player player : Manager.GetGame().GetPlayers(true))
		{
			if (!Kit.HasKit(player))
				continue;
			
			if (player.getInventory().getItem(8) == null || player.getInventory().getItem(8).getAmount() <= 0)
			{
				UtilTextBottom.display(C.cWhite + "No jumps left", player);
			}
			else
			{
				UtilTextBottom.display(F.elem(player.getInventory().getItem(8).getAmount() + "") + C.cWhite + " jumps left", player);
			}
		}
	}
}
```


---

## File: `kits\PerkRepulsor.java`

```java
package nautilus.game.arcade.game.games.monstermaze.kits.perks;

import java.util.HashMap;

import mineplex.core.common.util.F;
import mineplex.core.common.util.UtilAction;
import mineplex.core.common.util.UtilAlg;
import mineplex.core.common.util.UtilBlock;
import mineplex.core.common.util.UtilEnt;
import mineplex.core.common.util.UtilEvent;
import mineplex.core.common.util.UtilEvent.ActionType;
import mineplex.core.common.util.UtilFirework;
import mineplex.core.common.util.UtilInv;
import mineplex.core.common.util.UtilTime;
import mineplex.core.updater.event.UpdateEvent;
import nautilus.game.arcade.game.games.monstermaze.events.AbilityUseEvent;
import nautilus.game.arcade.game.games.monstermaze.events.EntityLaunchEvent;
import nautilus.game.arcade.kit.Perk;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.EntityEffect;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;

public class PerkRepulsor extends Perk
{
	private HashMap<Entity, Long> _launched = new HashMap<Entity, Long>();
	
	public PerkRepulsor()
	{
		super("Repulsor", new String[]
				{
				F.elem("Click") + " with Coal to use " + F.skill("Repulse") + ".",
				});
	}

	@EventHandler
	public void onRepulse(PlayerInteractEvent event)
	{	
		if (!Manager.GetGame().IsLive())
			return;
		
		if (!UtilEvent.isAction(event, ActionType.R)) 
			return;
		
		if (!UtilInv.IsItem(event.getItem(), Material.COAL, (byte) 0))
			return;
		
		Player player = event.getPlayer();
		
		if (!Kit.HasKit(player)) 
			return;
		
		event.setCancelled(true);
		
		UtilInv.remove(player, Material.COAL, (byte)0, 1);
		UtilInv.Update(player);
		
		UtilFirework.playFirework(player.getLocation(), Type.BALL_LARGE, Color.AQUA, false, false);
		
		for (Entity ent : UtilEnt.getInRadius(player.getLocation(), 6).keySet())
		{
			if (ent instanceof Player || !(ent instanceof LivingEntity))
				continue;
				
			ent.playEffect(EntityEffect.HURT);
			UtilAction.velocity(ent, UtilAlg.getTrajectory2d(player, ent), 1, true, 0, 0.8, 2, true);

			_launched.put(ent, System.currentTimeMillis());
			
			Bukkit.getPluginManager().callEvent(new EntityLaunchEvent(ent));
		}
		
		Bukkit.getPluginManager().callEvent(new AbilityUseEvent(player));
	}
	
	@EventHandler
	public void onUpdate(UpdateEvent event)
	{
		if (!Manager.GetGame().IsLive())
			return;
		
		HashMap<Entity, Long> copy = new HashMap<Entity, Long>();
		copy.putAll(_launched);
		
		for (Entity en : copy.keySet())
		{
			if (en == null || !en.isValid())
			{
				remove(en);
				continue;
			}
			
			if (en.isOnGround() && UtilTime.elapsed(copy.get(en), 500))
			{
				remove(en);
				continue;
			}
			
			//If there are blocks surrounding the block it's on top of (if it's on the side of a block)
			if (!UtilBlock.getInBoundingBox(en.getLocation().clone().add(1, -1, 1), en.getLocation().clone().subtract(1, 1, 1), true).isEmpty() && UtilTime.elapsed(copy.get(en), 500))
			{
				remove(en);
				continue;
			}
			
			if (UtilTime.elapsed(copy.get(en), 1500))
			{
				remove(en);
				continue;
			}
		}
	}
	
	private void remove(Entity en)
	{
		_launched.remove(en);
		en.remove();
		
		UtilFirework.playFirework(en.getLocation(), Type.BALL, Color.BLACK, false, false);
	}
}
```


---

## File: `kits\perks\PerkJumpsDisplay.java`

```java
package nautilus.game.arcade.game.games.monstermaze.kits.perks;

import mineplex.core.common.util.C;
import mineplex.core.common.util.F;
import mineplex.core.common.util.UtilTextBottom;
import mineplex.core.updater.UpdateType;
import mineplex.core.updater.event.UpdateEvent;
import nautilus.game.arcade.kit.Perk;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

public class PerkJumpsDisplay extends Perk
{
	public PerkJumpsDisplay()
	{
		super("Display", new String[0], false);
	}

	@EventHandler
	public void onShow(UpdateEvent event)
	{
		if (event.getType() != UpdateType.FAST)
			return;
		
		for (Player player : Manager.GetGame().GetPlayers(true))
		{
			if (!Kit.HasKit(player))
				continue;
			
			if (player.getInventory().getItem(8) == null || player.getInventory().getItem(8).getAmount() <= 0)
			{
				UtilTextBottom.display(C.cWhite + "No jumps left", player);
			}
			else
			{
				UtilTextBottom.display(F.elem(player.getInventory().getItem(8).getAmount() + "") + C.cWhite + " jumps left", player);
			}
		}
	}
}
```


---

## File: `kits\perks\PerkRepulsor.java`

```java
package nautilus.game.arcade.game.games.monstermaze.kits.perks;

import java.util.HashMap;

import mineplex.core.common.util.F;
import mineplex.core.common.util.UtilAction;
import mineplex.core.common.util.UtilAlg;
import mineplex.core.common.util.UtilBlock;
import mineplex.core.common.util.UtilEnt;
import mineplex.core.common.util.UtilEvent;
import mineplex.core.common.util.UtilEvent.ActionType;
import mineplex.core.common.util.UtilFirework;
import mineplex.core.common.util.UtilInv;
import mineplex.core.common.util.UtilTime;
import mineplex.core.updater.event.UpdateEvent;
import nautilus.game.arcade.game.games.monstermaze.events.AbilityUseEvent;
import nautilus.game.arcade.game.games.monstermaze.events.EntityLaunchEvent;
import nautilus.game.arcade.kit.Perk;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.EntityEffect;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;

public class PerkRepulsor extends Perk
{
	private HashMap<Entity, Long> _launched = new HashMap<Entity, Long>();
	
	public PerkRepulsor()
	{
		super("Repulsor", new String[]
				{
				F.elem("Click") + " with Coal to use " + F.skill("Repulse") + ".",
				});
	}

	@EventHandler
	public void onRepulse(PlayerInteractEvent event)
	{	
		if (!Manager.GetGame().IsLive())
			return;
		
		if (!UtilEvent.isAction(event, ActionType.R)) 
			return;
		
		if (!UtilInv.IsItem(event.getItem(), Material.COAL, (byte) 0))
			return;
		
		Player player = event.getPlayer();
		
		if (!Kit.HasKit(player)) 
			return;
		
		event.setCancelled(true);
		
		UtilInv.remove(player, Material.COAL, (byte)0, 1);
		UtilInv.Update(player);
		
		UtilFirework.playFirework(player.getLocation(), Type.BALL_LARGE, Color.AQUA, false, false);
		
		for (Entity ent : UtilEnt.getInRadius(player.getLocation(), 6).keySet())
		{
			if (ent instanceof Player || !(ent instanceof LivingEntity))
				continue;
				
			ent.playEffect(EntityEffect.HURT);
			UtilAction.velocity(ent, UtilAlg.getTrajectory2d(player, ent), 1, true, 0, 0.8, 2, true);

			_launched.put(ent, System.currentTimeMillis());
			
			Bukkit.getPluginManager().callEvent(new EntityLaunchEvent(ent));
		}
		
		Bukkit.getPluginManager().callEvent(new AbilityUseEvent(player));
	}
	
	@EventHandler
	public void onUpdate(UpdateEvent event)
	{
		if (!Manager.GetGame().IsLive())
			return;
		
		HashMap<Entity, Long> copy = new HashMap<Entity, Long>();
		copy.putAll(_launched);
		
		for (Entity en : copy.keySet())
		{
			if (en == null || !en.isValid())
			{
				remove(en);
				continue;
			}
			
			if (en.isOnGround() && UtilTime.elapsed(copy.get(en), 500))
			{
				remove(en);
				continue;
			}
			
			//If there are blocks surrounding the block it's on top of (if it's on the side of a block)
			if (!UtilBlock.getInBoundingBox(en.getLocation().clone().add(1, -1, 1), en.getLocation().clone().subtract(1, 1, 1), true).isEmpty() && UtilTime.elapsed(copy.get(en), 500))
			{
				remove(en);
				continue;
			}
			
			if (UtilTime.elapsed(copy.get(en), 1500))
			{
				remove(en);
				continue;
			}
		}
	}
	
	private void remove(Entity en)
	{
		_launched.remove(en);
		en.remove();
		
		UtilFirework.playFirework(en.getLocation(), Type.BALL, Color.BLACK, false, false);
	}
}
```


---

## File: `KitSlowball.java`

```java
package nautilus.game.arcade.game.games.monstermaze.kits;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;

import mineplex.core.common.util.F;
import mineplex.core.game.kit.GameKit;
import mineplex.core.itemstack.ItemStackFactory;
import mineplex.minecraft.game.core.damage.CustomDamageEvent;

import nautilus.game.arcade.ArcadeManager;
import nautilus.game.arcade.kit.Kit;
import nautilus.game.arcade.kit.Perk;
import nautilus.game.arcade.kit.perks.PerkConstructor;

public class KitSlowball extends Kit
{

	private static final Perk[] PERKS =
			{
					new PerkConstructor("Slowballer", 2, 16, Material.SNOW_BALL, "Slowball", true)
			};

	private static final ItemStack[] PLAYER_ITEMS =
			{
					ItemStackFactory.Instance.CreateStack(Material.COMPASS, (byte) 0, 1, F.item("Safe Pad Locator"))
			};

	public KitSlowball(ArcadeManager manager)
	{
		super(manager, GameKit.MONSTER_MAZE_SLOW_BALL, PERKS);
	}

	@Override
	public void GiveItems(Player player)
	{
		player.getInventory().setItem(4, PLAYER_ITEMS[0]);
	}

	@EventHandler
	public void SnowballHit(CustomDamageEvent event)
	{
		if (event.GetProjectile() == null)
			return;

		if (!(event.GetProjectile() instanceof Snowball))
			return;

		event.GetProjectile().remove();

		Manager.GetCondition().Factory().Slow("Snowball Slow", event.GetDamageeEntity(), (LivingEntity) event.GetProjectile().getShooter(), 2, 1, false, false, true, false);
	}
}
```


---

## File: `Maze.java`

```java
package nautilus.game.arcade.game.games.monstermaze;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Set;

import mineplex.core.common.util.C;
import mineplex.core.common.util.F;
import mineplex.core.common.util.UtilAction;
import mineplex.core.common.util.UtilAlg;
import mineplex.core.common.util.UtilBlock;
import mineplex.core.common.util.UtilEnt;
import mineplex.core.common.util.UtilMath;
import mineplex.core.common.util.UtilParticle;
import mineplex.core.common.util.UtilPlayer;
import mineplex.core.common.util.UtilServer;
import mineplex.core.common.util.UtilTextMiddle;
import mineplex.core.common.util.UtilTime;
import mineplex.core.common.util.UtilParticle.ParticleType;
import mineplex.core.common.util.UtilParticle.ViewDist;
import mineplex.core.disguise.DisguiseFactory;
import mineplex.core.disguise.disguises.DisguiseBase;
import mineplex.core.disguise.disguises.DisguiseMagmaCube;
import mineplex.core.disguise.disguises.DisguiseSlime;
import mineplex.core.disguise.disguises.DisguiseSnowman;
import mineplex.core.recharge.Recharge;
import mineplex.core.updater.UpdateType;
import mineplex.core.updater.event.UpdateEvent;
import nautilus.game.arcade.events.GameStateChangeEvent;
import nautilus.game.arcade.game.Game.GameState;
import nautilus.game.arcade.game.games.monstermaze.MMMazes.MazePreset;
import nautilus.game.arcade.game.games.monstermaze.MazeMobWaypoint.CardinalDirection;
import nautilus.game.arcade.game.games.monstermaze.events.EntityLaunchEvent;
import nautilus.game.arcade.game.games.monstermaze.events.FirstToSafepadEvent;
import nautilus.game.arcade.game.games.monstermaze.events.MonsterBumpPlayerEvent;
import nautilus.game.arcade.game.games.monstermaze.events.SafepadBuildEvent;
import nautilus.game.arcade.game.games.monstermaze.kits.KitBodyBuilder;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutAnimation;
import net.minecraft.server.v1_8_R3.PacketPlayOutBlockBreakAnimation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

public class Maze implements Listener
{
	private MonsterMaze _host;
	private MazePreset _preset;
	
	private Location _location;
	
	private ArrayList<Player> _playersOnPad = new ArrayList<Player>();
	private HashMap<LivingEntity, MazeMobWaypoint> _ents = new HashMap<LivingEntity, MazeMobWaypoint>();

	private HashSet<Block> _movementWaypoints = new HashSet<Block>();
	private HashSet<Block> _movementWaypointsDisabled = new HashSet<Block>();
	private ArrayList<Location> _playerContainmentGlass = new ArrayList<Location>();

	private int _centerSafeZoneDecay = 11;
//	private NautHashMap<Location, Long> _centerSafeZoneDecay = new NautHashMap<Location, Long>();
	
	private SafePad _nextSafePad;
	private SafePad _safePad = null;
	private LinkedList<SafePad> _oldSafePads = new LinkedList<SafePad>();
	private int _curSafe = 1;

	private int _phaseTimer = 60;
	private int _phaseTimerStart = 60;
			
	@SuppressWarnings("deprecation")
	public Maze(MonsterMaze host, MazePreset maze)
	{
		_host = host;
		_preset = maze;
		
		for (Location loc : getPreset().getMaze())
		{
			_movementWaypoints.add(loc.getBlock());
		}
		
		for (Location loc : maze.getGlassBounds())
		{
			_playerContainmentGlass.add(loc.clone().subtract(0, 1, 0));
			_playerContainmentGlass.add(loc.clone().subtract(0, 2, 0));
		}
		
		for (Location loc : _playerContainmentGlass)
		{
			loc.getBlock().setType(Material.STAINED_GLASS);
			loc.getBlock().setData((byte) 5);
		}
		
		for(Location loc : _preset.getCenterSafeZonePaths())
		{
			_movementWaypointsDisabled.add(loc.getBlock());
		}
		
		_location = maze.getCenter();		
		
		spawnSafePad();
		_safePad = _nextSafePad;
		_nextSafePad = null;
		
		Bukkit.getPluginManager().registerEvents(this, _host.Manager.getPlugin());
	}
	
	public SafePad getSafePad()
	{
		return _safePad;
	}
	
	public MazePreset getPreset()
	{
		return _preset;
	}
	
	public Location getLocation()
	{
		return _location;
	}
	
	public int getCurrentSafePadCount()
	{
		return _curSafe;
	}
	
	public boolean isOnPad(Player player, boolean checkNotActive )
	{
		if (_safePad == null)
			return false;
		
		if (_safePad.isOn(player))
			return true;
		
		if (checkNotActive)
		{
			for (SafePad pad : _oldSafePads)
			{
				if (pad.isOn(player))
				{
					return true;
				}
			}
			
			if (_nextSafePad != null)
			{
				if (_nextSafePad.isOn(player))
				{
					return true;
				}
			}
			
			return false;
		}
		
		return false;
	}
	
	@EventHandler
	public void setCompassTarget(UpdateEvent event)
	{
		if (event.getType() != UpdateType.FASTER)
			return;
		
		if (_safePad == null)
			return;
		
		for (Player player : _host.GetPlayers(true))
		{
			player.setCompassTarget(_safePad.getLocation());
		}
	}
	
	@EventHandler
	public void updateTick(UpdateEvent event)
	{
		if (event.getType() != UpdateType.TICK)
			return;
		
		if (!_host.IsLive())
			return;
		
		//Updates
		checkPlayersOnSafePad();
		move();
		bump();
//		removeMobsOnSafePad();
	}

	@EventHandler
	public void updateSecond(UpdateEvent event)
	{
		if (event.getType() != UpdateType.SEC)
			return;
		
		if (!_host.IsLive())
			return;
		
		decrementSafePadTime();
		decrementPhaseTime();
		
		if (UtilTime.elapsed(_host.getGameLiveTime(), 20000))
		{
			deteriorateCenter();
		}
	}
	
	@EventHandler
	public void updateExperience(UpdateEvent event)
	{
		if (event.getType() != UpdateType.TICK)
			return;
		
		if (!_host.IsLive())
			return;
		
//		if (UtilTime.elapsed(Host.getGameLiveTime(), 15000))
//		{
//			deteriorateCenter();
//		}
		
		if (_safePad == null)
		{
			for (Player p : _host.GetPlayers(true))
			{
				p.setExp(0F);
			}
			return;
		}
		
		if (_playersOnPad.isEmpty()) //Nobody has gotten to it yet
		{
			float percent = (float) Math.min(Math.max(_phaseTimer * (1 / _phaseTimerStart), 0), .999);
			
			for(Player p : _host.GetPlayers(true))
			{
				p.setExp(percent);
			}
			return;
		}
		
		float percentage = (float) Math.min(Math.max(_phaseTimer / Math.max(6, 16 - (_curSafe - 1)), 0), 1);
		for (Player p : _host.GetPlayers(true))
		{
			p.setExp(percentage);
		}
	}

//	private void removeMobsOnSafePad()
//	{
//		Iterator<LivingEntity> it = _ents.keySet().iterator();
//		while (it.hasNext()) 
//		{
//			LivingEntity e = it.next();
//			if(_safePad != null)
//			{
//				if(_safePad.isOn(e))
//				{
//					System.out.println("entity on safepad removed");
//					it.remove();
//					e.remove();
//					continue;
//				}
//			}
//			
//			for (SafePad oldSafePad : _oldSafePads)
//			{
//				if(oldSafePad.isOn(e))
//				{
//					System.out.println("entity on old safepad removed");
//					it.remove();
//					e.remove();
//				}
//			}
//		}
//	}

	private void bump()
	{
		//Hit Players
		for (Player player : _host.GetPlayers(true))
		{
			if (!Recharge.Instance.usable(player, "Monster Hit"))
				continue;
			
			if (isOnPad(player, true))
				continue;

			//Hit Snowman
			for (LivingEntity ent : _ents.keySet())
			{
				if (UtilMath.offset(player, ent) >= 1)
					continue;

				Recharge.Instance.useForce(player, "Monster Hit", 1000);

				//Velocity
				//UtilAction.velocity(player, new Vector(1,0,0), 4, false, 0, 1.2, 1.2, true);
				UtilAction.velocity(player, UtilAlg.getTrajectory(ent, player), 1, false, 0, 0.75, 1.2, true);

				//Damage Event
				_host.Manager.GetDamage().NewDamageEvent(player, null, null, 
						null, 4, false, false, false,
						"Monster", "Monster Attack");

				PacketPlayOutAnimation animation = new PacketPlayOutAnimation();
				animation.a = ent.getEntityId();
				animation.b = 0;

				if (_host.Manager.GetDisguise().isDisguised(ent))
					animation.a = _host.Manager.GetDisguise().getDisguise(ent).getEntityId();
				
				for (Player cur : UtilServer.getPlayers())
				{
					for (int i = 0 ; i < 3 ; i++)
					{
						UtilPlayer.sendPacket(cur, animation);
					}
				}

				Bukkit.getPluginManager().callEvent(new MonsterBumpPlayerEvent(player));
			}
		}
	}

	private void move()
	{
		Iterator<Entry<LivingEntity, MazeMobWaypoint>> entIterator = _ents.entrySet().iterator();

		//Move & Die
		while (entIterator.hasNext())
		{
			Entry<LivingEntity, MazeMobWaypoint> data = entIterator.next();
			
			//New or Fallen
			if (data.getValue().Target == null || data.getKey().getLocation().getY() < data.getValue().Target.getBlockY())
			{
				Location loc = UtilAlg.findClosest(data.getKey().getLocation(), getPreset().getMaze());
				
				data.getKey().teleport(loc);
				data.getValue().Target = loc;
			}

			//New Waypoint
			if (UtilMath.offset2d(data.getKey().getLocation(), data.getValue().Target) < 0.4)
			{
				ArrayList<Block> nextBlock = new ArrayList<Block>();
				
				Block north = getTarget(data.getKey().getLocation().getBlock(), null, BlockFace.NORTH);
				Block south = getTarget(data.getKey().getLocation().getBlock(), null, BlockFace.SOUTH);
				Block east = getTarget(data.getKey().getLocation().getBlock(), null, BlockFace.EAST);
				Block west = getTarget(data.getKey().getLocation().getBlock(), null, BlockFace.WEST);
				
				if (north != null)		nextBlock.add(north);
				if (south != null)		nextBlock.add(south);
				if (east != null)		nextBlock.add(east);
				if (west != null)		nextBlock.add(west);
				
				if(nextBlock.isEmpty())
				{
					entIterator.remove();
					data.getKey().remove();
					continue;
				}
				
				if(nextBlock.size() > 1 && data.getValue().Direction != CardinalDirection.NULL) // they can do a uturn if they're stuck
				{
					if(data.getValue().Direction == CardinalDirection.NORTH)
					{
						nextBlock.remove(south);
					}
					else if(data.getValue().Direction == CardinalDirection.SOUTH)
					{
						nextBlock.remove(north);
					}
					else if(data.getValue().Direction == CardinalDirection.WEST)
					{
						nextBlock.remove(east);
					}
					else if(data.getValue().Direction == CardinalDirection.EAST)
					{
						nextBlock.remove(west);
					}
				}
				
				if (nextBlock.isEmpty())
				{
					entIterator.remove();
					data.getKey().remove();
					continue;
				}
				
				//Random Direction
				Location nextLoc = UtilAlg.Random(nextBlock).getLocation();
				data.getValue().Target = nextLoc.clone().add(0.5, 0, 0.5);
				if(north != null && nextLoc.equals(north.getLocation()))
				{
					data.getValue().Direction = CardinalDirection.NORTH;
				}
				else if(south != null && nextLoc.equals(south.getLocation()))
				{
					data.getValue().Direction = CardinalDirection.SOUTH;
				}
				else if(east != null && nextLoc.equals(east.getLocation()))
				{
					data.getValue().Direction = CardinalDirection.EAST;
				}
				else if(west != null && nextLoc.equals(west.getLocation()))
				{
					data.getValue().Direction = CardinalDirection.WEST;
				}
			}

			UtilEnt.CreatureMoveFast(data.getKey(), data.getValue().Target, 1.4f);
		}
	}
	
	private Block getTarget(Block start, Block cur, BlockFace face)
	{	
		if (cur == null)
			cur = start;
		
		while (_movementWaypoints.contains(cur.getRelative(face)) && !_movementWaypointsDisabled.contains(cur.getRelative(face)))
		{
			cur = cur.getRelative(face);
			
			//Stop at intersection
			int count = 0;
			
			if (face != BlockFace.NORTH && _movementWaypoints.contains(cur.getRelative(BlockFace.NORTH)) && !_movementWaypointsDisabled.contains(cur.getRelative(BlockFace.NORTH))) count++;
			if (face != BlockFace.SOUTH && _movementWaypoints.contains(cur.getRelative(BlockFace.SOUTH)) && !_movementWaypointsDisabled.contains(cur.getRelative(BlockFace.SOUTH))) count++;
			if (face != BlockFace.EAST && _movementWaypoints.contains(cur.getRelative(BlockFace.EAST)) && !_movementWaypointsDisabled.contains(cur.getRelative(BlockFace.EAST))) count++;
			if (face != BlockFace.WEST && _movementWaypoints.contains(cur.getRelative(BlockFace.WEST)) && !_movementWaypointsDisabled.contains(cur.getRelative(BlockFace.WEST))) count++;
			
			if (count > 1)
				break;
		}
		
		if (cur.equals(start))
			return null;
		
		return cur;
	}
	
//	@SuppressWarnings("deprecation")
//	public void addSafeZone()
//	{
//		if (_safeZones.isEmpty())
//			return;
//		
//		Location zone = _safeZones.remove(UtilMath.r(_safeZones.size()));
//		for (Block b : UtilBlock.getInBoundingBox(zone.clone().add(1, 0, 1), zone.clone().subtract(1, 0, 1), false))
//		{
//			for (int i = 0 ; i < 3 ; i++)
//			{
//				Block cur = b.getLocation().clone().subtract(0, i, 0).getBlock();
//				
//				cur.getRelative(BlockFace.DOWN).setType(Material.STAINED_CLAY);
//				cur.getRelative(BlockFace.DOWN).setData((byte) 13);
//			}
//			
//			_disabledWaypoints.add(b);
//
//			Iterator<Entity> it = _ents.keySet().iterator();
//			while (it.hasNext()) {
//				Entity e = it.next();
//				if (UtilMath.offset(e.getLocation(), b.getLocation()) < 2)
//				{
//					_ents.remove(_ents.get(e));
//					it.remove();
//					e.remove();
//				}
//			}
//		}
//	}
	
	public void fillSpawn(int numToSpawn)
	{
		System.out.println("spawning " + numToSpawn + " entities on map");
		
		int spawned = 0;
		
		while(spawned <= numToSpawn)
		{
			ArrayList<Location> validSpawns = new ArrayList<Location>(getPreset().getMaze());
			
			Iterator<Location> iter = validSpawns.iterator();
			while (iter.hasNext()) {
				Location b = iter.next();
				if(UtilMath.offset(b, _location) < 7.5)
				{
					iter.remove();
				}
			}
			Location loc = UtilAlg.Random(validSpawns);
			
			_host.CreatureAllowOverride = true;
			Snowman ent = loc.getWorld().spawn(loc, Snowman.class);
			
			DisguiseBase disguise = DisguiseFactory.createDisguise(ent, _host.getMonsterType());

			if (disguise instanceof DisguiseSlime)
			{
				((DisguiseSlime) disguise).SetSize(3);
			}

			if (disguise instanceof DisguiseMagmaCube)
			{
				((DisguiseMagmaCube) disguise).SetSize(3);
			}

			_host.CreatureAllowOverride = false;

			UtilEnt.vegetate(ent, true);
			UtilEnt.ghost(ent, true, false);
			_ents.put(ent, new MazeMobWaypoint(ent.getLocation()));

			if (disguise != null && !(disguise instanceof DisguiseSnowman))
			{
				_host.Manager.GetDisguise().disguise(disguise);
			}

			spawned++;
		}
	}
	
	private void spawn(int numToSpawn)
	{
		Location loc = UtilAlg.Random(getPreset().getSpawns());
		
		int spawned = 0;
		
		while (spawned <= numToSpawn)
		{
			_host.CreatureAllowOverride = true;
			Snowman ent = loc.getWorld().spawn(loc, Snowman.class);
						
			DisguiseBase disguise = DisguiseFactory.createDisguise(ent, _host.getMonsterType());
			
			if (disguise instanceof DisguiseSlime)
			{
				((DisguiseSlime) disguise).SetSize(3);
			}
			
			if (disguise instanceof DisguiseMagmaCube)
			{
				((DisguiseMagmaCube) disguise).SetSize(3);
			}
			
			_host.CreatureAllowOverride = false;
	
			UtilEnt.vegetate(ent, true);
			UtilEnt.ghost(ent, true, false);
			_ents.put(ent, new MazeMobWaypoint(ent.getLocation()));
			
			if (disguise != null && !(disguise instanceof DisguiseSnowman))
			{
				_host.Manager.GetDisguise().disguise(disguise);
			}
			
			spawned++;
		}
	}
	
	private Location pickNextLocForSafePad() // short method name
	{
		if (_safePad != null || !_oldSafePads.isEmpty())
		{
			ArrayList<Location> best = new ArrayList<Location>();
			ArrayList<Location> toAvoid = new ArrayList<Location>();			
			
			for (SafePad pad : _oldSafePads)
			{
				toAvoid.add(pad.getLocation());
			}
			
			if (_safePad != null)
			{
				toAvoid.add(_safePad.getLocation());
			}
			
			for (Location pos : getPreset().getValidSafePadSpawns())
			{
				boolean canAdd = true;
				for (Location avoid : toAvoid)
				{
					if (UtilMath.offset(pos, avoid) < 40)
					{
						canAdd = false;
					}
				}
				
				if (canAdd)
				{
					best.add(pos);
				}
			}
			
			if (best.isEmpty())
			{
				return UtilAlg.findFurthest(_location, getPreset().getValidSafePadSpawns());
			}
			else
			{
				return UtilAlg.Random(best);
			}
		}
		else
		{
			return UtilAlg.findFurthest(_location, getPreset().getValidSafePadSpawns());
		}
	}
	
	private void stopSafePad()
	{
		if (_safePad != null)
		{
			_safePad.turnOffBeacon();
			_oldSafePads.addLast(_safePad);
			_safePad = null;			
		}
	}
	
	public void spawnSafePad()
	{
		Location next = pickNextLocForSafePad();
		
		//Delay 
		_nextSafePad = new SafePad(_host, this, next.clone().subtract(0, 1, 0)); // maybe don't need to clone()
		_nextSafePad.build();
		
		for (Block cur : UtilBlock.getInBoundingBox(next.clone().add(-2, 0, -2), next.clone().add(2, 0, 2), false))
		{
			if (!_movementWaypointsDisabled.contains(cur))
				_movementWaypointsDisabled.add(cur);
		}
		
		for (Iterator<LivingEntity> it = _ents.keySet().iterator() ; it.hasNext() ;)
		{
			LivingEntity en = it.next();
			
			if (!_nextSafePad.isOn(en))
				continue;
			
			it.remove();
			en.remove();
		}
		
		Bukkit.getPluginManager().callEvent(new SafepadBuildEvent());
	}
	
	@SuppressWarnings("deprecation")
	public void deteriorateCenter()
	{
		if (getPreset().getCenterSafeZone().isEmpty() || _centerSafeZoneDecay == -1
//				&& _centerSafeZoneDecay.isEmpty()
				)
			return;
		
		_centerSafeZoneDecay--;
		
		ArrayList<Packet> toSend = new ArrayList<Packet>();
		
		int ind = 0;
		
		Iterator<Location> iterator = getPreset().getCenterSafeZone().iterator();
		while (iterator.hasNext())
		{
			Location cur = iterator.next();
			Location loc = cur.clone().subtract(0, 1, 0);

			if(_centerSafeZoneDecay == 10)
			{
				loc.getBlock().setTypeIdAndData(159, (byte) 5, true);
				toSend.add(getBreakPacket(loc, ind, 1));
			}
			else if(_centerSafeZoneDecay == 9)
			{
				toSend.add(getBreakPacket(loc, ind, 2));
			}
			else if(_centerSafeZoneDecay == 8)
			{
				loc.getBlock().setTypeIdAndData(159, (byte) 4, true);
				toSend.add(getBreakPacket(loc, ind, 3));
			}
			else if(_centerSafeZoneDecay == 7)
			{
				toSend.add(getBreakPacket(loc, ind, 4));
			}
			else if(_centerSafeZoneDecay == 6)
			{
				loc.getBlock().setTypeIdAndData(159, (byte) 1, true);
				toSend.add(getBreakPacket(loc, ind, 5));
			}
			else if(_centerSafeZoneDecay == 5)
			{
				toSend.add(getBreakPacket(loc, ind, 6));
			}
			else if(_centerSafeZoneDecay == 4)
			{
				loc.getBlock().setTypeIdAndData(159, (byte) 14, true);
				toSend.add(getBreakPacket(loc, ind, 7));
			}
			else if(_centerSafeZoneDecay == 3)
			{
				toSend.add(getBreakPacket(loc, ind, 8));
			}
			else if(_centerSafeZoneDecay == 2)
			{
				toSend.add(getBreakPacket(loc, ind, 9));
			}
			else if(_centerSafeZoneDecay == 1)
			{
				toSend.add(getBreakPacket(loc, ind, -1));
				iterator.remove();

				if (getPreset().getCenterSafeZonePaths().contains(cur))
				{
					getPreset().getCenterSafeZonePaths().remove(cur);
					getPreset().buildMazeBlockAt(cur);
					_movementWaypointsDisabled.remove(cur.getBlock());
				}
				else
				{
					loc.getBlock().setTypeIdAndData(0, (byte) 0, true);
				}

				getPreset().getMaze().add(cur);				
			}
			
			ind++;
		}

		for (Player p : UtilServer.getPlayers())
		{
			UtilPlayer.sendPacket(p, toSend.toArray(new Packet[toSend.size()]));
		}
		
		if (_centerSafeZoneDecay == 1)
		{
			_centerSafeZoneDecay = -1;
		}
		
//		if (!_centerSafeZone.isEmpty())
//		{
//			Location toBreak = _centerSafeZone.remove(UtilMath.r(_centerSafeZone.size()));
//			
//			if (_centerSafeZonePaths.contains(toBreak))
//			{
//				if (!_waypoints.contains(toBreak.getBlock()))
//					_waypoints.add(toBreak.getBlock());
//				
//				if (!_map.contains(toBreak))
//					_map.add(toBreak);
//				
//				_centerSafeZonePaths.remove(toBreak);
//				_preset.buildMazeBlockAt(toBreak);
//				
//				for (int i = 0 ; i < 3 ; i++)
//				{
//					Location cur = toBreak.clone().subtract(0, i, 0);
//					cur.getWorld().playEffect(cur, Effect.STEP_SOUND, cur.getBlock().getTypeId());
//				}
//			}
//			else
//			{
//				_centerSafeZoneDecay.put(toBreak, System.currentTimeMillis());
//			}
//		}
//
//		if (!_centerSafeZoneDecay.isEmpty())
//		{
//			NautHashMap<Location, Long> copy = new NautHashMap<Location, Long>(_centerSafeZoneDecay);
//
//			for (Entry<Location, Long> entry : copy.entrySet())
//			{
//				if (UtilTime.elapsed(entry.getValue(), 1250)) //break
//				{
//					for (int i = 1 ; i < 4 ; i++)
//					{
//						Location cur = entry.getKey().clone().subtract(0, i, 0);
//						cur.getWorld().playEffect(cur, Effect.STEP_SOUND, 152);
//						cur.getBlock().setTypeIdAndData(0, (byte) 0, true);
//					}
//
//					_centerSafeZonePaths.remove(entry.getKey());
//					_centerSafeZoneDecay.remove(entry.getKey());
//					continue;
//				}
//				else if (UtilTime.elapsed(entry.getValue(), 1000)) //red
//				{
//					for (int i = 1 ; i < 4 ; i++)
//					{
//						Location cur = entry.getKey().clone().subtract(0, i, 0);
//						cur.getBlock().setTypeIdAndData(159, (byte) 14, true);
//					}
//					continue;
//				}
//				else if (UtilTime.elapsed(entry.getValue(), 750)) //orange
//				{
//					for (int i = 1 ; i < 3 ; i++)
//					{
//						Location cur = entry.getKey().clone().subtract(0, i, 0);
//						cur.getBlock().setTypeIdAndData(159, (byte) 1, true);
//					}
//					continue;
//				}
//				else if (UtilTime.elapsed(entry.getValue(), 500)) //yellow
//				{
//					for (int i = 1 ; i < 4 ; i++)
//					{
//						Location cur = entry.getKey().clone().subtract(0, i, 0);
//						cur.getBlock().setTypeIdAndData(159, (byte) 4, true);
//					}
//					continue;
//				}
//				else if (UtilTime.elapsed(entry.getValue(), 250)) //blue
//				{
//					for (int i = 1 ; i < 4 ; i++)
//					{
//						Location cur = entry.getKey().clone().subtract(0, i, 0);
//						cur.getBlock().setTypeIdAndData(159, (byte) 3, true);
//					}
//					continue;
//				}
//			}
//		}
	}
	
	private Packet getBreakPacket(Location location, int index, int progress)
	{
		return new PacketPlayOutBlockBreakAnimation(index, new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ()), progress);
	}

	public void decrementSafePadTime()
	{
		Iterator<SafePad> iterator = _oldSafePads.iterator();
		while (iterator.hasNext())
		{
			SafePad pad = iterator.next();

			if (!pad.decay())
				continue;

			for (Block block : UtilBlock.getInBoundingBox(pad.getLocation().clone().add(-2, 1, -2), pad.getLocation().clone().add(2, 1, 2), false))
			{	
				if (_movementWaypointsDisabled.contains(block))
					_movementWaypointsDisabled.remove(block);				
			}

			iterator.remove();
		}
	}
	
	public void decrementPhaseTime()
	{
		if (_safePad == null)
			return;
		
		if(_phaseTimer == -1) return;
		
		_phaseTimer--;
		if(_phaseTimer == 20) // only gets to this by running out of time naturally, not by player
		{
			UtilTextMiddle.display("", C.cGreen + C.Bold + (int) _phaseTimer, 5, 40, 5);
		}
		
		if(_phaseTimer == 15 || _phaseTimer == 10)
		{
			UtilTextMiddle.display("", C.cGreen + C.Bold + (int) _phaseTimer, 5, 40, 5);
		}
			
		if(_phaseTimer == 5 || _phaseTimer == 4)
		{
			UtilTextMiddle.display("", C.cGreen + C.Bold + (int) _phaseTimer, 5, 40, 5);
		}
		
		if(_phaseTimer == 3)
		{
			UtilTextMiddle.display("", C.cYellow + C.Bold + (int) _phaseTimer, 5, 40, 5);
		}
		
		if(_phaseTimer == 2)
		{
			UtilTextMiddle.display("", C.cGold + C.Bold + (int) _phaseTimer, 5, 40, 5);		
			spawnSafePad();
		}
		
		if(_phaseTimer == 1)
		{
			UtilTextMiddle.display("", C.cRed + C.Bold + (int) _phaseTimer, 5, 40, 5);
		}

		if(_phaseTimer == 0)
		{
			for (Player p : _host.GetPlayers(true))
			{
				if(_safePad.isOn(p))
				{
					UtilTextMiddle.display("", C.cYellow + C.Bold + "Get to the Next Safe Pad!", 5, 40, 5, p);
					// maybe send them a happy message? =)
					//					UtilPlayer.message(p, F.main("Game", "Since you were on the Safe Pad, you didn't die!"));
				}
				else
				{
					_host.Manager.GetDamage().NewDamageEvent(p, null, null, 
							DamageCause.CUSTOM, 500, false, false, false,
							"Game", "Map damage");
					UtilTextMiddle.display("", C.cRed + C.Bold + "You weren't on the Safe Pad!", 5, 40, 5, p);
					UtilPlayer.message(p, F.main("Game", "You weren't on the Safe Pad!"));
				}
			}

			spawn(15);

			stopSafePad();
						
			_playersOnPad.clear();

			_phaseTimerStart = Math.max(15, 60 - ((_curSafe - 1) * 2));
			_phaseTimer = _phaseTimerStart;
			
			if (_nextSafePad == null)
			{
				spawnSafePad();
			}

			Iterator<LivingEntity> it = _ents.keySet().iterator();
			while (it.hasNext()) 
			{
				Entity e = it.next();
				if (_nextSafePad.isOn(e))
				{
					_ents.remove(_ents.get(e));
					it.remove();
					e.remove();
				}
			}
			
			_curSafe++;
			
			_safePad = _nextSafePad;
			_nextSafePad = null;
			
			
		}
	}
	
	private void checkPlayersOnSafePad()
	{
		if (_safePad == null) 
			return;

		boolean allOn = true;
		for (Player p : _host.GetPlayers(true))
		{
			if (!_safePad.isOn(p))
			{
				allOn = false;
				
				if (_playersOnPad.contains(p))
				{
					UtilTextMiddle.display("", C.cRed + C.Bold + "Get back to the Safe Pad!", 0, 5, 0, p);
				}
				continue;
			}

			if (_playersOnPad.contains(p))
				continue;

			UtilPlayer.message(p, F.main("Game", "You made it to the Safe Pad!"));
			_playersOnPad.add(p);

			_host.AddGems(p, 2, "Safe Pads Reached", true, true);

			if (_playersOnPad.size() == 1) // first player
			{	
				_host.Announce(F.main("Game", F.name(p.getName()) + " made it to the Safe Pad first!"));

				UtilTextMiddle.display("", C.cYellow + C.Bold + "You got to the Safe Pad first!", 5, 40, 5, p);
				_host.AddGems(p, 7.5, "First Safe Pads", true, true);
				p.playSound(p.getLocation(), Sound.SUCCESSFUL_HIT, 1.0f, 1.0f);

				//2 hearts
				UtilPlayer.health(p, 4.0);
				
				if (_host.GetKit(p) instanceof KitBodyBuilder)
				{
					p.setMaxHealth(Math.min(p.getMaxHealth() + 2, 30));
					UtilParticle.PlayParticle(ParticleType.HEART, p.getEyeLocation().clone().add(0, .5, 0), 0F, 0F, 0F, 0, 3, ViewDist.NORMAL, UtilServer.getPlayers());
				}
				
				int decreased = Math.max(6, 16 - (_curSafe - 1));
				_phaseTimer = Math.min(decreased, _phaseTimer);

				for (Player player : _host.GetPlayers(true))
				{
					if (player == p)
						continue;

					UtilPlayer.message(player, F.main("Game", "You have " + F.time(decreased + " Seconds") + " to make it to the Safe Pad!"));
				}
				
				Bukkit.getPluginManager().callEvent(new FirstToSafepadEvent(p));
			} 
			else // not the first
			{
				p.playSound(p.getLocation(), Sound.SUCCESSFUL_HIT, 1.0f, 1.0f);
				UtilTextMiddle.display("", C.cYellow + C.Bold + "You got to the Safe Pad!", 5, 40, 5, p);
				
				UtilPlayer.health(p, 2.0);
			}
		}
		
		if (allOn)
		{
			_phaseTimer = Math.min(4, _phaseTimer);
		}
	}
	
	public void removePlayerContainmentUnit()
	{
		for(Location loc : _playerContainmentGlass)
		{
			loc.getBlock().setType(Material.AIR);
		}
	}
	
	public Set<LivingEntity> getMonsters()
	{
		return _ents.keySet();
	}
	
	public int getPhaseTimer()
	{
		return _phaseTimer;
	}
	
	@EventHandler
	public void onGameStateDead(GameStateChangeEvent event)
	{
		if (event.GetState() != GameState.Dead)
			return;
		
		HandlerList.unregisterAll(this);
	}
	
	@EventHandler
    public void onSnowForm(EntityBlockFormEvent event)
    {
		event.setCancelled(true);
    }
	
	@EventHandler
	public void onEntityCombust(EntityCombustEvent event)
	{
		event.setCancelled(true);
	}
	
	@EventHandler
	public void onEntityLaunch(EntityLaunchEvent event)
	{
		if (!_ents.containsKey(event.getEntity()))
			return;
		
		_ents.remove(event.getEntity());
	}
}
```


---

## File: `Maze1.java`

```java
package nautilus.game.arcade.game.games.monstermaze.mazes;

/**
 * Created by William (WilliamTiger).
 * 21/10/15
 */
public class Maze1
{

	public static final int[][] MAZE = {
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,1,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,2,0,0,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,0,0,2,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,1,1,1,0,1,0,0,0,1,0,1,1,1,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,1,1,1,1,1,1,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,1,1,1,1,1,1,1,0,1,0,0,1,0,1,0,0,1,0,1,1,1,1,1,1,1,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,1,1,1,1,0,1,0,1,0,1,1,1,1,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,0,0,0,0,0,0,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,0,0,1,0,1,1,1,1,0,1,0,0,1,0,0,0,1,0,1,0,1,1,1,1,1,0,0,1,0,1,0,1,0,1,0,0,1,1,1,1,1,0,1,0,1,0,0,0,1,0,0,1,0,1,1,1,1,0,1,0,0,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,1,0,1,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,1,0,1,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,1,1,0,0,0,1,0,0,0,1,1,1,1,0,1,0,1,0,1,1,1,1,0,0,0,1,0,0,0,1,1,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,2,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,1,1,1,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,0,0,1,0,0,0,0,1,1,1,1,0,0,0,0,0,1,1,1,1,0,0,0,0,1,0,0,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,1,1,1,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,0,1,0,0,1,1,1,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,1,1,1,0,0,1,0,1,1,1,1,1,1,1,2,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,0,1,1,1,0,1,1,1,0,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,0,1,0,1,0,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,0,1,1,1,0,1,0,0,1,1,1,1,1,1,1,0,0,1,0,1,1,1,0,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,2,1,1,1,1,1,0,0,0,1,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,0,1,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,1,0,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,1,0,0,0,1,1,1,1,1,2,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,0,1,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,1,0,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,1,0,1,0,0,0,1,0,1,0,0,1,1,1,1,0,0,1,1,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,1,1,0,0,1,1,1,1,0,0,1,0,1,0,0,0,1,0,1,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,1,0,1,0,1,1,1,0,1,1,1,1,0,0,1,0,0,0,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,0,0,0,1,0,0,1,1,1,1,0,1,1,1,0,1,0,1,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,2,1,1,1,1,1,1,0,1,1,1,1,0,0,1,0,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,0,1,0,0,1,1,1,1,0,1,1,1,1,1,1,2,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,1,1,1,0,0,0,1,1,1,1,1,0,1,1,0,1,1,1,1,1,1,1,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,1,1,1,1,1,1,1,0,1,1,0,1,1,1,1,1,0,0,0,1,1,1,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,0,1,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,0,1,0,0,0,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,0,0,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,0,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,1,1,0,0,1,1,1,1,1,0,1,0,1,1,1,1,1,1,0,1,0,0,0,1,0,0,0,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,0,0,0,1,0,0,0,1,0,1,1,1,1,1,1,0,1,0,1,1,1,1,1,0,0,1,1,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,1,1,1,0,0,0,1,1,1,1,1,0,1,0,0,1,1,1,1,1,1,1,1,0,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,0,1,1,1,1,1,1,1,1,0,0,1,0,1,1,1,1,1,0,0,0,1,1,1,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,0,0,1,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,1,0,0,1,1,1,1,1,1,1,2,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,0,0,1,0,1,0,1,1,1,1,0,0,1,0,1,1,0,1,1,1,1,1,0,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,0,1,0,0,1,1,1,1,1,0,1,1,0,1,0,0,1,1,1,1,0,1,0,1,0,0,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,0,0,1,1,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,1,1,0,0,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,0,0,0,1,1,1,1,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,0,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,1,1,1,1,0,0,0,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,0,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,2,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,1,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,1,0,1,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,1,1,1,1,1,1,1,0,1,0,1,0,0,0,1,1,1,1,1,2,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,1,0,1,1,1,1,1,0,1,0,1,0,0,0,1,0,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,0,1,0,0,0,1,0,1,0,1,1,1,1,1,0,1,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,1,0,0,1,0,0,1,0,1,1,1,0,0,0,1,0,0,1,0,1,1,1,1,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,1,1,1,1,0,1,0,0,1,0,0,0,1,1,1,0,1,0,0,1,0,0,1,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,1,0,0,1,0,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,0,1,0,0,1,0,0,0,0,0,0,0,0},
			{0,0,0,2,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,0,1,1,1,1,0,0,1,0,1,0,0,1,0,1,0,0,1,0,1,0,0,1,1,1,1,1,1,1,0,0,1,0,1,0,0,1,0,1,0,0,1,0,1,0,0,1,1,1,1,0,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,2,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,1,0,0,1,0,1,1,1,0,0,1,0,1,0,0,1,0,1,0,0,1,0,1,1,1,1,0,1,1,1,1,0,0,1,0,0,1,1,1,1,0,1,1,1,1,0,1,0,0,1,0,1,0,0,1,0,1,0,0,1,1,1,0,1,0,0,1,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,1,0,1,0,1,0,0,1,0,1,0,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,0,1,0,1,0,0,1,0,1,0,1,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,4,6,4,4,6,4,4,6,4,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,0,0,0,0,0},
			{0,0,2,1,1,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,1,1,0,0,0,0,1,0,0,1,0,0,0,4,5,3,3,5,3,3,5,4,0,0,0,1,0,0,1,0,0,0,0,1,1,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,1,1,2,0,0},
			{0,0,0,0,0,0,0,1,0,1,1,1,1,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,1,1,1,1,0,1,0,0,0,0,1,0,0,1,0,4,4,4,5,5,5,5,5,5,5,4,4,4,0,1,0,0,1,0,0,0,0,1,0,1,1,1,1,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,1,1,1,1,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,1,6,5,5,5,3,3,5,3,3,5,5,5,6,1,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,1,1,1,0,0,0,1,0,1,0,0,1,0,1,0,1,1,1,1,1,1,0,0,1,0,0,0,0,0,1,0,0,1,0,0,4,3,5,3,3,5,5,5,3,3,3,5,4,0,0,1,0,0,1,0,0,0,0,0,1,0,0,1,1,1,1,1,1,0,1,0,1,0,0,1,0,1,0,0,0,1,1,1,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,0,0,1,1,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,4,3,5,3,5,5,3,5,5,3,3,5,4,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,1,1,0,0,1,1,1,1,1,1,1,0,0,1,0,0,0,0,0,0,0},
			{2,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,1,0,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,1,1,1,1,1,1,6,5,5,5,5,3,3,3,5,5,5,5,6,1,1,1,1,1,1,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,0,1,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,2},
			{0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,0,0,1,1,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,4,3,5,3,5,5,3,5,5,3,3,5,4,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,1,1,0,0,1,1,1,1,1,1,1,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,1,1,1,0,0,0,1,0,1,0,0,1,0,1,0,1,1,1,1,1,1,0,0,1,0,0,0,0,0,1,0,0,1,0,0,4,3,5,3,3,5,5,5,3,3,3,5,4,0,0,1,0,0,1,0,0,0,0,0,1,0,0,1,1,1,1,1,1,0,1,0,1,0,0,1,0,1,0,0,0,1,1,1,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,1,6,5,5,5,3,3,5,3,3,5,5,5,6,1,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,1,1,1,1,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,1,1,1,1,0,1,0,0,0,0,1,0,0,1,0,4,4,4,5,5,5,5,5,5,5,4,4,4,0,1,0,0,1,0,0,0,0,1,0,1,1,1,1,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,1,1,1,1,0,1,0,0,0,0,0,0,0},
			{0,0,2,1,1,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,1,1,0,0,0,0,1,0,0,1,0,0,0,4,5,3,3,5,3,3,5,4,0,0,0,1,0,0,1,0,0,0,0,1,1,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,1,1,2,0,0},
			{0,0,0,0,0,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,4,6,4,4,6,4,4,6,4,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,1,0,1,0,1,0,0,1,0,1,0,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,0,1,0,1,0,0,1,0,1,0,1,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,1,0,0,1,0,1,1,1,0,0,1,0,1,0,0,1,0,1,0,0,1,0,1,1,1,1,0,1,1,1,1,0,0,1,0,0,1,1,1,1,0,1,1,1,1,0,1,0,0,1,0,1,0,0,1,0,1,0,0,1,1,1,0,1,0,0,1,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,2,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,0,1,1,1,1,0,0,1,0,1,0,0,1,0,1,0,0,1,0,1,0,0,1,1,1,1,1,1,1,0,0,1,0,1,0,0,1,0,1,0,0,1,0,1,0,0,1,1,1,1,0,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,2,0,0,0},
			{0,0,0,0,0,0,0,0,1,0,0,1,0,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,0,1,0,0,1,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,1,0,0,1,0,0,1,0,1,1,1,0,0,0,1,0,0,1,0,1,1,1,1,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,1,1,1,1,0,1,0,0,1,0,0,0,1,1,1,0,1,0,0,1,0,0,1,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,1,0,1,1,1,1,1,0,1,0,1,0,0,0,1,0,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,0,1,0,0,0,1,0,1,0,1,1,1,1,1,0,1,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,2,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,1,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,1,0,1,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,1,1,1,1,1,1,1,0,1,0,1,0,0,0,1,1,1,1,1,2,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,0,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,0,0,0,1,1,1,1,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,0,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,1,1,1,1,0,0,0,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,0,0,1,1,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,1,1,0,0,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,0,0,1,0,1,0,1,1,1,1,0,0,1,0,1,1,0,1,1,1,1,1,0,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,0,1,0,0,1,1,1,1,1,0,1,1,0,1,0,0,1,1,1,1,0,1,0,1,0,0,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,0,0,1,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,1,0,0,1,1,1,1,1,1,1,2,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,1,1,1,0,0,0,1,1,1,1,1,0,1,0,0,1,1,1,1,1,1,1,1,0,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,0,1,1,1,1,1,1,1,1,0,0,1,0,1,1,1,1,1,0,0,0,1,1,1,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,1,1,0,0,1,1,1,1,1,0,1,0,1,1,1,1,1,1,0,1,0,0,0,1,0,0,0,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,0,0,0,1,0,0,0,1,0,1,1,1,1,1,1,0,1,0,1,1,1,1,1,0,0,1,1,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,0,1,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,0,1,0,0,0,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,0,0,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,0,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,1,1,1,0,0,0,1,1,1,1,1,0,1,1,0,1,1,1,1,1,1,1,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,1,1,1,1,1,1,1,0,1,1,0,1,1,1,1,1,0,0,0,1,1,1,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,2,1,1,1,1,1,1,0,1,1,1,1,0,0,1,0,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,0,1,0,0,1,1,1,1,0,1,1,1,1,1,1,2,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,1,0,1,0,1,1,1,0,1,1,1,1,0,0,1,0,0,0,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,0,0,0,1,0,0,1,1,1,1,0,1,1,1,0,1,0,1,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,1,0,1,0,0,0,1,0,1,0,0,1,1,1,1,0,0,1,1,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,1,1,0,0,1,1,1,1,0,0,1,0,1,0,0,0,1,0,1,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,0,1,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,1,0,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,2,1,1,1,1,1,0,0,0,1,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,0,1,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,1,0,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,1,0,0,0,1,1,1,1,1,2,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,0,1,1,1,0,1,0,0,1,1,1,1,1,1,1,0,0,1,0,1,1,1,0,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,0,1,0,1,0,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,0,1,1,1,0,1,1,1,0,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,0,1,0,0,1,1,1,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,1,1,1,0,0,1,0,1,1,1,1,1,1,1,2,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,1,1,1,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,0,0,1,0,0,0,0,1,1,1,1,0,0,0,0,0,1,1,1,1,0,0,0,0,1,0,0,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,1,1,1,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,1,1,0,0,0,1,0,0,0,1,1,1,1,0,1,0,1,0,1,1,1,1,0,0,0,1,0,0,0,1,1,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,2,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,1,0,1,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,1,0,1,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,0,0,1,0,1,1,1,1,0,1,0,0,1,0,0,0,1,0,1,0,1,1,1,1,1,0,0,1,0,1,0,1,0,1,0,0,1,1,1,1,1,0,1,0,1,0,0,0,1,0,0,1,0,1,1,1,1,0,1,0,0,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,1,1,1,1,0,1,0,1,0,1,1,1,1,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,0,0,0,0,0,0,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,1,1,1,1,1,1,1,0,1,0,0,1,0,1,0,0,1,0,1,1,1,1,1,1,1,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,1,1,1,1,1,1,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,1,1,1,0,1,0,0,0,1,0,1,1,1,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,2,0,0,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,0,0,2,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,1,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
	};

}
```


---

## File: `Maze2.java`

```java
package nautilus.game.arcade.game.games.monstermaze.mazes;

/**
 * Created by William (WilliamTiger).
 * 21/10/15
 */
public class Maze2
{

	public static final int[][] MAZE = {
			{0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0},
			{0,0,0,2,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,2,0,0,0},
			{0,0,0,1,0,0,1,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,1,0,0,1,0,0,0},
			{0,2,1,1,1,1,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,1,2,0},
			{0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0},
			{0,0,0,1,0,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,1,0,0,0},
			{2,1,1,1,1,1,0,0,1,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,2,0,0,0,2,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,0,0,1,0,0,1,1,1,1,1,2},
			{0,0,0,1,0,1,0,1,1,1,1,1,1,1,0,0,1,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,0,1,0,1,0,0,0},
			{0,0,0,1,0,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,2,0,0,1,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,1,0,0,2,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,0,1,0,0,0},
			{0,2,1,1,1,1,0,1,0,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,2,0,0,0,0,1,0,0,0,0,2,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,2,0,0,0,0,1,0,0,0,0,2,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,0,1,0,1,1,1,1,2,0},
			{0,0,0,0,0,1,0,1,1,1,0,0,0,1,0,0,1,1,1,0,1,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,1,0,1,1,1,0,0,1,0,0,0,1,1,1,0,1,0,0,0,0,0},
			{0,0,0,0,0,1,0,1,0,1,0,0,0,1,1,1,1,0,1,1,1,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,1,1,1,0,1,1,1,1,0,0,0,1,0,1,0,1,0,0,0,0,0},
			{0,0,2,1,1,1,1,1,0,1,0,0,1,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,0,0,1,0,0,0,1,1,1,1,1,0,0,0,0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,0,1,1,1,1,1,0,0,0,1,0,0,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,1,0,0,1,0,1,1,1,1,1,2,0,0},
			{0,0,0,0,0,0,0,1,1,1,1,1,1,0,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,1,1,1,0,1,0,0,0,1,1,1,1,1,1,1,0,0,0,0,0,0,0,1,1,1,1,1,1,1,0,0,0,1,0,1,1,1,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,0,1,1,1,1,1,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,0,0,0,1,1,1,1,0,1,1,1,1,0,0,0,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,2,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,0,0,0,1,0,0,1,0,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,0,1,0,0,1,0,0,0,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,2,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,1,1,1,0,0,1,1,1,1,1,0,0,1,0,1,0,0,0,1,0,0,1,0,0,1,1,1,0,0,1,0,0,1,0,0,0,1,0,1,0,0,1,1,1,1,1,0,0,1,1,1,1,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,1,1,1,1,0,0,0,1,0,0,1,1,1,1,0,1,1,1,1,0,0,1,0,0,0,1,1,1,1,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,0,1,0,1,0,0,1,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,0,1,0,1,1,1,1,1,0,1,0,0,1,0,1,1,1,1,1,0,0,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,0,1,0,0,1,0,1,1,1,1,1,0,1,0,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,2,1,1,1,1,1,1,0,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,0,1,0,1,0,0,0,1,1,1,1,1,0,1,1,1,1,1,1,2,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,0,0,1,0,0,0,0,1,0,1,1,1,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,1,1,1,0,1,0,0,0,0,1,0,0,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,1,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,1,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,2,1,1,1,1,1,0,1,0,1,1,1,1,0,0,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,0,0,1,1,1,1,0,1,0,1,1,1,1,1,2,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,1,1,1,1,1,0,1,1,1,1,1,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,0,0,0,0,1,0,1,0,0,1,0,0,0,1,0,1,1,1,1,1,1,1,1,1,0,0,0,0,1,1,1,0,0,0,0,1,1,1,1,1,1,1,1,1,0,1,0,0,0,1,0,0,1,0,1,0,0,0,0,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,2,1,1,1,1,1,0,1,1,1,1,1,1,0,0,1,1,1,1,1,1,0,0,0,1,1,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,1,0,0,0,0,1,0,0,0,1,0,0,0,1,1,1,0,0,0,1,1,1,1,1,1,0,0,1,1,1,1,1,1,0,1,1,1,1,1,2,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,0,1,1,1,1,0,1,0,1,0,1,1,1,1,0,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,0,0,1,1,1,1,1,1,1,0,0,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,2,1,1,1,1,1,0,0,0,1,0,1,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,1,1,1,1,0,0,0,1,0,0,1,0,1,1,1,0,1,0,0,1,0,0,0,1,1,1,1,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,1,0,1,0,0,0,1,1,1,1,1,2,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,1,0,0,1,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,1,0,1,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,1,0,0,1,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,1,0,1,0,0,0,1,0,0,1,1,1,1,0,0,0,1,0,1,0,1,0,1,0,0,0,1,1,1,1,0,0,1,0,0,0,1,0,1,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,1,1,0,1,1,1,1,1,1,0,0,1,0,0,0,1,0,1,0,1,0,1,0,0,0,1,0,0,1,1,1,1,1,1,0,1,1,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,0,0,0,1,1,1,1,1,1,0,1,1,0,0,0,0,0,1,0,1,0,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,0,1,0,1,0,0,0,0,0,1,1,0,1,1,1,1,1,1,0,0,0,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,2,1,1,1,0,1,1,1,1,1,0,0,0,0,1,0,1,0,0,1,1,1,1,1,1,1,0,0,0,0,1,1,1,1,0,1,0,0,1,0,0,0,1,0,0,1,0,1,1,1,1,0,0,0,0,1,1,1,1,1,1,1,0,0,1,0,1,0,0,0,0,1,1,1,1,1,0,1,1,1,2,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,0,1,0,0,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,1,1,0,0,1,1,1,0,0,1,0,0,0,1,0,0,1,1,1,0,0,1,1,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,2,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,0,1,0,0,1,0,0,1,0,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,1,1,1,1,1,1,1,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,2,1,1,1,1,1,1,0,1,0,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,1,0,0,1,0,0,1,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,0,1,0,1,1,1,1,1,1,2,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,1,0,0,1,0,0,4,6,6,6,4,4,6,4,4,6,6,6,4,0,0,1,0,0,1,0,0,0,0,1,1,1,1,1,1,1,1,1,0,1,0,0,0,0,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,1,1,1,1,0,0,0,1,0,0,0,1,0,0,1,1,1,1,0,1,1,1,6,3,5,3,3,3,5,3,3,3,5,3,6,1,1,1,0,1,1,1,1,0,0,1,0,0,0,1,0,0,0,1,1,1,1,0,0,0,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,6,5,5,5,5,5,5,5,5,5,5,5,6,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,6,3,5,3,3,3,5,3,3,3,5,3,6,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,2,1,1,1,0,0,1,0,1,0,0,0,1,0,0,1,1,1,1,0,0,0,0,1,0,0,0,0,0,1,1,1,1,0,0,1,0,4,3,5,3,5,5,5,5,5,3,5,3,4,0,1,0,0,1,1,1,1,0,0,0,0,0,1,0,0,0,0,1,1,1,1,0,0,1,0,0,0,1,0,1,0,0,1,1,1,2,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,0,1,1,1,1,1,0,0,1,0,0,1,0,4,3,5,3,5,3,3,3,5,3,5,3,4,0,1,0,0,1,0,0,1,1,1,1,1,0,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,1,1,1,1,6,5,5,5,5,3,3,3,5,5,5,5,6,1,1,1,1,1,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,0,1,1,1,1,1,0,0,1,0,0,1,0,4,3,5,3,5,3,3,3,5,3,5,3,4,0,1,0,0,1,0,0,1,1,1,1,1,0,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,2,1,1,1,0,0,1,0,1,0,0,0,1,0,0,1,1,1,1,0,0,0,0,1,0,0,0,0,0,1,1,1,1,0,0,1,0,4,3,5,3,5,5,5,5,5,3,5,3,4,0,1,0,0,1,1,1,1,0,0,0,0,0,1,0,0,0,0,1,1,1,1,0,0,1,0,0,0,1,0,1,0,0,1,1,1,2,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,6,3,5,3,3,3,5,3,3,3,5,3,6,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,6,5,5,5,5,5,5,5,5,5,5,5,6,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,0,0,0,1,1,1,1,0,0,0,1,0,0,0,1,0,0,1,1,1,1,0,1,1,1,6,3,5,3,3,3,5,3,3,3,5,3,6,1,1,1,0,1,1,1,1,0,0,1,0,0,0,1,0,0,0,1,1,1,1,0,1,0,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,0,0,0,0,1,0,1,1,1,1,1,1,1,1,1,0,0,0,0,1,0,0,1,0,0,4,6,6,6,4,4,6,4,4,6,6,6,4,0,0,1,0,0,1,0,0,0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,2,1,1,1,1,1,1,0,1,0,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,1,0,0,1,0,0,1,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,0,1,0,1,1,1,1,1,1,2,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,1,1,1,1,1,1,1,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,0,1,0,0,1,0,0,1,0,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,2,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,0,1,0,0,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,1,1,0,0,1,1,1,0,0,1,0,0,0,1,0,0,1,1,1,0,0,1,1,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,2,1,1,1,0,1,1,1,1,1,0,0,0,0,1,0,1,0,0,1,1,1,1,1,1,1,0,0,0,0,1,1,1,1,0,1,0,0,1,0,0,0,1,0,0,1,0,1,1,1,1,0,0,0,0,1,1,1,1,1,1,1,0,0,1,0,1,0,0,0,0,1,1,1,1,1,0,1,1,1,2,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,0,0,0,1,1,1,1,1,1,0,1,1,0,0,0,0,0,1,0,1,0,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,0,1,0,1,0,0,0,0,0,1,1,0,1,1,1,1,1,1,0,0,0,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,1,1,0,1,1,1,1,1,1,0,0,1,0,0,0,1,0,1,0,1,0,1,0,0,0,1,0,0,1,1,1,1,1,1,0,1,1,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,1,0,1,0,0,0,1,0,0,1,1,1,1,0,0,0,1,0,1,0,1,0,1,0,0,0,1,1,1,1,0,0,1,0,0,0,1,0,1,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,1,0,0,1,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,1,0,1,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,1,0,0,1,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,2,1,1,1,1,1,0,0,0,1,0,1,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,1,1,1,1,0,0,0,1,0,0,1,0,1,1,1,0,1,0,0,1,0,0,0,1,1,1,1,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,1,0,1,0,0,0,1,1,1,1,1,2,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,0,0,1,1,1,1,1,1,1,0,0,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,0,1,1,1,1,0,1,0,1,0,1,1,1,1,0,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,2,1,1,1,1,1,0,1,1,1,1,1,1,0,0,1,1,1,1,1,1,0,0,0,1,1,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,1,0,0,0,0,1,0,0,0,1,0,0,0,1,1,1,0,0,0,1,1,1,1,1,1,0,0,1,1,1,1,1,1,0,1,1,1,1,1,2,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,0,0,0,0,1,0,1,0,0,1,0,0,0,1,0,1,1,1,1,1,1,1,1,1,0,0,0,0,1,1,1,0,0,0,0,1,1,1,1,1,1,1,1,1,0,1,0,0,0,1,0,0,1,0,1,0,0,0,0,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,1,1,1,1,1,0,1,1,1,1,1,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,2,1,1,1,1,1,0,1,0,1,1,1,1,0,0,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,0,0,1,1,1,1,0,1,0,1,1,1,1,1,2,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,1,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,1,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,0,0,1,0,0,0,0,1,0,1,1,1,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,1,1,1,0,1,0,0,0,0,1,0,0,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,2,1,1,1,1,1,1,0,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,0,1,0,1,0,0,0,1,1,1,1,1,0,1,1,1,1,1,1,2,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,0,1,0,1,1,1,1,1,0,1,0,0,1,0,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,0,0,1,1,1,1,1,0,1,0,0,1,0,1,1,1,1,1,0,1,0,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,1,1,1,1,0,0,0,1,0,0,1,1,1,1,0,1,1,1,1,0,0,1,0,0,0,1,1,1,1,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,0,1,0,1,0,0,1,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,1,1,1,0,0,1,1,1,1,1,0,0,1,0,1,0,0,0,1,0,0,1,0,0,1,1,1,0,0,1,0,0,1,0,0,0,1,0,1,0,0,1,1,1,1,1,0,0,1,1,1,1,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,2,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,0,0,0,1,0,0,1,0,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,0,1,0,0,1,0,0,0,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,2,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,0,0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,1,1,1,1,1,0,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,1,1,1,0,1,0,0,0,1,1,1,1,1,1,1,0,0,0,0,0,0,0,1,1,1,1,1,1,1,0,0,0,1,0,1,1,1,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,0,1,1,1,1,1,1,0,0,0,0,0,0,0},
			{0,0,2,1,1,1,1,1,0,1,0,0,1,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,0,0,1,0,0,0,1,1,1,1,1,0,0,0,0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,0,1,1,1,1,1,0,0,0,1,0,0,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,1,0,0,1,0,1,1,1,1,1,2,0,0},
			{0,0,0,0,0,1,0,1,0,1,0,0,0,1,1,1,1,0,1,1,1,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,1,1,1,0,1,1,1,1,0,0,0,1,0,1,0,1,0,0,0,0,0},
			{0,0,0,0,0,1,0,1,1,1,0,0,0,1,0,0,1,1,1,0,1,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,1,0,1,1,1,0,0,1,0,0,0,1,1,1,0,1,0,0,0,0,0},
			{0,2,1,1,1,1,0,1,0,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,2,0,0,0,0,1,0,0,0,0,2,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,2,0,0,0,0,1,0,0,0,0,2,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,0,1,0,1,1,1,1,2,0},
			{0,0,0,1,0,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,2,0,0,1,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,1,0,0,2,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,0,1,0,0,0},
			{0,0,0,1,0,1,0,1,1,1,1,1,1,1,0,0,1,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,0,1,0,1,0,0,0},
			{2,1,1,1,1,1,0,0,1,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,2,0,0,0,2,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,0,0,1,0,0,1,1,1,1,1,2},
			{0,0,0,1,0,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,1,0,0,0},
			{0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0},
			{0,2,1,1,1,1,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,1,2,0},
			{0,0,0,1,0,0,1,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,1,0,0,1,0,0,0},
			{0,0,0,2,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,2,0,0,0},
			{0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0},
	};

}
```


---

## File: `Maze3.java`

```java
package nautilus.game.arcade.game.games.monstermaze.mazes;

/**
 * Created by William (WilliamTiger).
 * 21/10/15
 */
public class Maze3
{

	public static final int[][] MAZE = {
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,1,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,2,0,1,0,2,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,1,0,0,0,1,0,1,0,1,0,0,0,1,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,0,0,2,0,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,1,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,0,2,0,0,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,2,0,0,1,0,0,0,1,0,1,0,1,0,0,0,1,0,0,2,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,0,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,1,0,0,0,1,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,0,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,1,1,0,0,1,0,0,1,1,1,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,1,1,1,0,0,1,0,0,1,1,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,1,1,0,0,0,1,1,1,1,0,0,0,0,0,1,0,0,0,1,1,1,1,0,0,0,1,0,0,0,1,0,0,0,1,1,1,1,0,0,0,1,0,0,0,0,0,1,1,1,1,0,0,0,1,1,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,1,1,0,0,1,0,0,0,0,0,1,1,1,1,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,1,1,1,1,0,0,0,0,0,1,0,0,1,1,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,0,1,0,0,1,1,1,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,1,1,1,0,0,1,0,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,1,1,1,1,1,0,0,1,1,1,1,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,1,1,1,1,1,0,0,1,1,1,1,1,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,2,1,1,1,1,1,1,0,0,1,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,1,1,0,0,0,0,1,1,1,0,0,1,1,1,1,0,0,0,1,1,1,1,0,0,1,1,1,0,0,0,0,1,1,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,1,0,0,1,1,1,1,1,1,2,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,1,0,1,0,0,0,1,1,0,1,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,1,0,1,1,0,0,0,1,0,1,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,1,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,1,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,1,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,0,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,0,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,0,1,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,2,1,1,1,1,1,1,0,0,0,0,1,0,0,0,0,1,0,1,1,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,1,0,0,1,1,1,1,1,1,1,1,1,1,1,0,0,1,1,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,1,1,0,1,0,0,0,0,1,0,0,0,0,1,1,1,1,1,1,2,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,1,1,1,0,0,1,1,1,1,0,0,1,1,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,1,1,0,0,1,1,1,1,0,0,1,1,1,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,0,1,1,1,1,0,0,1,1,1,1,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,1,1,1,1,0,0,1,1,1,1,0,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,1,1,1,1,1,1,0,0,0,1,1,1,1,0,0,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,0,0,1,1,1,1,0,0,0,1,1,1,1,1,1,2,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,0,1,0,0,1,1,0,0,0,1,1,0,0,0,1,0,0,0,1,1,0,0,0,1,1,0,0,1,0,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,2,1,1,1,1,1,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,1,0,0,1,1,1,0,0,1,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,1,1,1,1,1,2,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,0,0,1,0,1,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,1,1,1,1,0,1,1,1,1,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,1,0,1,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,1,1,1,0,0,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,1,1,1,0,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,0,1,1,1,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,0,0,1,1,1,1,1,1,1,1,1,1,2,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,0,0,1,0,0,0,1,1,1,1,0,1,1,1,0,0,1,0,0,1,0,1,1,1,1,1,0,1,0,0,1,0,0,1,1,1,0,1,1,1,1,0,0,0,1,0,0,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,0,0,0,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,1,0,0,0,1,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,1,1,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,1,1,0,0,0,0,0,0,0,0},
			{0,0,0,2,1,1,1,1,1,0,0,0,0,1,1,1,1,0,0,0,0,1,0,0,0,0,1,1,1,1,1,1,0,0,0,0,1,0,0,1,1,1,1,1,1,1,0,1,0,0,0,1,0,1,1,1,1,1,1,1,0,0,1,0,0,0,0,1,1,1,1,1,1,0,0,0,0,1,0,0,0,0,1,1,1,1,0,0,0,0,1,1,1,1,1,2,0,0,0},
			{0,0,0,0,0,0,0,0,1,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,1,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0},
			{0,0,0,0,2,1,1,1,1,1,1,1,0,1,1,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,1,1,0,1,1,1,1,1,1,1,2,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,1,1,1,0,1,0,1,1,1,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,0,0,0,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,0,0,0,0,0,1,0,0,1,4,4,4,6,4,4,6,4,4,6,4,4,4,1,0,0,1,0,0,0,0,0,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,0,0,0,0,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0},
			{2,1,1,1,1,1,1,1,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,0,0,1,1,0,0,0,1,1,1,1,1,6,5,5,5,5,3,5,3,5,5,5,5,6,1,1,1,1,1,0,0,0,1,1,0,0,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,1,1,1,1,1,1,1,2},
			{0,0,0,0,0,0,1,0,0,1,0,0,1,1,1,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,1,1,1,1,0,1,0,0,4,5,3,3,5,5,5,5,5,3,3,5,4,0,0,1,0,1,1,1,1,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,1,1,1,0,0,1,0,0,1,0,0,0,0,0,0},
			{0,0,0,0,0,0,1,1,1,1,0,0,1,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,6,5,5,5,5,3,5,3,5,5,5,5,6,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0,1,1,1,1,0,0,0,0,0,0},
			{0,0,0,0,0,1,1,0,0,1,1,1,1,0,1,1,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,6,3,3,5,3,3,5,3,3,5,3,3,6,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,1,1,0,1,1,1,1,0,0,1,1,0,0,0,0,0},
			{0,0,0,0,0,1,0,0,0,0,0,1,0,0,1,0,0,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,1,1,6,5,5,5,3,5,5,5,3,5,5,5,6,1,1,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,0,0,1,0,0,1,0,0,0,0,0,1,0,0,0,0,0},
			{0,2,1,1,1,1,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,0,6,3,3,5,5,5,3,5,5,5,3,3,6,0,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,1,1,1,1,2,0},
			{0,0,0,0,0,1,0,0,0,0,0,1,0,0,1,0,0,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,1,1,6,5,5,5,3,5,5,5,3,5,5,5,6,1,1,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,0,0,1,0,0,1,0,0,0,0,0,1,0,0,0,0,0},
			{0,0,0,0,0,1,1,0,0,1,1,1,1,0,1,1,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,6,3,3,5,3,3,5,3,3,5,3,3,6,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,1,1,0,1,1,1,1,0,0,1,1,0,0,0,0,0},
			{0,0,0,0,0,0,1,1,1,1,0,0,1,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,6,5,5,5,5,3,5,3,5,5,5,5,6,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0,1,1,1,1,0,0,0,0,0,0},
			{0,0,0,0,0,0,1,0,0,1,0,0,1,1,1,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,1,1,1,1,0,1,0,0,4,5,3,3,5,5,5,5,5,3,3,5,4,0,0,1,0,1,1,1,1,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,1,1,1,0,0,1,0,0,1,0,0,0,0,0,0},
			{2,1,1,1,1,1,1,1,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,0,0,1,1,0,0,0,1,1,1,1,1,6,5,5,5,5,3,5,3,5,5,5,5,6,1,1,1,1,1,0,0,0,1,1,0,0,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,1,1,1,1,1,1,1,2},
			{0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,0,0,0,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,0,0,0,0,0,1,0,0,1,4,4,4,6,4,4,6,4,4,6,4,4,4,1,0,0,1,0,0,0,0,0,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,0,0,0,0,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,1,1,1,0,1,0,1,1,1,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,2,1,1,1,1,1,1,1,0,1,1,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,1,1,0,1,1,1,1,1,1,1,2,0,0,0,0},
			{0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,1,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,1,0,0,0,0,0,0,0,0},
			{0,0,0,2,1,1,1,1,1,0,0,0,0,1,1,1,1,0,0,0,0,1,0,0,0,0,1,1,1,1,1,1,0,0,0,0,1,0,0,1,1,1,1,1,1,1,0,1,0,0,0,1,0,1,1,1,1,1,1,1,0,0,1,0,0,0,0,1,1,1,1,1,1,0,0,0,0,1,0,0,0,0,1,1,1,1,0,0,0,0,1,1,1,1,1,2,0,0,0},
			{0,0,0,0,0,0,0,0,1,1,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,1,1,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,1,0,0,0,1,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,0,0,0,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,0,0,1,0,0,0,1,1,1,1,0,1,1,1,0,0,1,0,0,1,0,1,1,1,1,1,0,1,0,0,1,0,0,1,1,1,0,1,1,1,1,0,0,0,1,0,0,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,1,1,1,0,0,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,1,1,1,0,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,0,1,1,1,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,0,0,1,1,1,1,1,1,1,1,1,1,2,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,0,0,1,0,1,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,1,1,1,1,0,1,1,1,1,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,1,0,1,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,2,1,1,1,1,1,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,1,0,0,1,1,1,0,0,1,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,1,1,1,1,1,2,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,0,1,0,0,1,1,0,0,0,1,1,0,0,0,1,0,0,0,1,1,0,0,0,1,1,0,0,1,0,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,2,1,1,1,1,1,1,0,0,0,1,1,1,1,0,0,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,0,0,1,1,1,1,0,0,0,1,1,1,1,1,1,2,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,0,1,1,1,1,0,0,1,1,1,1,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,1,1,1,1,0,0,1,1,1,1,0,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,1,1,1,0,0,1,1,1,1,0,0,1,1,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,1,1,0,0,1,1,1,1,0,0,1,1,1,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,2,1,1,1,1,1,1,0,0,0,0,1,0,0,0,0,1,0,1,1,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,1,0,0,1,1,1,1,1,1,1,1,1,1,1,0,0,1,1,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,1,1,0,1,0,0,0,0,1,0,0,0,0,1,1,1,1,1,1,2,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,1,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,0,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,0,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,0,1,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,1,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,1,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,1,0,1,0,0,0,1,1,0,1,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,1,0,1,1,0,0,0,1,0,1,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,2,1,1,1,1,1,1,0,0,1,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,1,1,0,0,0,0,1,1,1,0,0,1,1,1,1,0,0,0,1,1,1,1,0,0,1,1,1,0,0,0,0,1,1,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,1,0,0,1,1,1,1,1,1,2,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,1,1,1,1,1,0,0,1,1,1,1,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,1,1,1,1,1,0,0,1,1,1,1,1,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,0,1,0,0,1,1,1,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,1,1,1,0,0,1,0,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,1,1,0,0,1,0,0,0,0,0,1,1,1,1,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,1,1,1,1,0,0,0,0,0,1,0,0,1,1,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,1,1,0,0,0,1,1,1,1,0,0,0,0,0,1,0,0,0,1,1,1,1,0,0,0,1,0,0,0,1,0,0,0,1,1,1,1,0,0,0,1,0,0,0,0,0,1,1,1,1,0,0,0,1,1,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,1,1,0,0,1,0,0,1,1,1,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,1,1,1,0,0,1,0,0,1,1,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,0,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,1,0,0,0,1,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,0,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,2,0,0,1,0,0,0,1,0,1,0,1,0,0,0,1,0,0,2,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,0,0,2,0,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,1,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,0,2,0,0,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,1,0,0,0,1,0,1,0,1,0,0,0,1,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,2,0,1,0,2,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,1,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
	};

}
```


---

## File: `MazeBlockData.java`

```java
package nautilus.game.arcade.game.games.monstermaze;

import org.bukkit.Material;

public class MazeBlockData
{
	public static class MazeBlock
	{
		public final Material Type;
		public final byte Data;
		
		public MazeBlock(Material type)
		{
			this(type, (byte) 0);
		}
		
		public MazeBlock(Material type, byte data)
		{
			Type = type;
			Data = data;
		}
	}
	
	public final MazeBlock Top;
	public final MazeBlock Middle;
	public final MazeBlock Bottom;
	
	public MazeBlockData(MazeBlock top, MazeBlock middle, MazeBlock bottom)
	{
		Top = top;
		Middle = middle;
		Bottom = bottom;
	}
}
```


---

## File: `MazeMobWaypoint.java`

```java
package nautilus.game.arcade.game.games.monstermaze;

import org.bukkit.Location;

public class MazeMobWaypoint
{
	public Location Last;
	public Location Target;
	public CardinalDirection Direction = CardinalDirection.NULL;
	
	public MazeMobWaypoint(Location last)
	{
		Last = last;
		Target = null;
	}
	
	public enum CardinalDirection
	{
		NORTH, SOUTH, EAST, WEST, NULL // such order much not care
	}
}
```


---

## File: `mazes\Maze1.java`

```java
package nautilus.game.arcade.game.games.monstermaze.mazes;

/**
 * Created by William (WilliamTiger).
 * 21/10/15
 */
public class Maze1
{

	public static final int[][] MAZE = {
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,1,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,2,0,0,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,0,0,2,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,1,1,1,0,1,0,0,0,1,0,1,1,1,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,1,1,1,1,1,1,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,1,1,1,1,1,1,1,0,1,0,0,1,0,1,0,0,1,0,1,1,1,1,1,1,1,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,1,1,1,1,0,1,0,1,0,1,1,1,1,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,0,0,0,0,0,0,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,0,0,1,0,1,1,1,1,0,1,0,0,1,0,0,0,1,0,1,0,1,1,1,1,1,0,0,1,0,1,0,1,0,1,0,0,1,1,1,1,1,0,1,0,1,0,0,0,1,0,0,1,0,1,1,1,1,0,1,0,0,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,1,0,1,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,1,0,1,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,1,1,0,0,0,1,0,0,0,1,1,1,1,0,1,0,1,0,1,1,1,1,0,0,0,1,0,0,0,1,1,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,2,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,1,1,1,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,0,0,1,0,0,0,0,1,1,1,1,0,0,0,0,0,1,1,1,1,0,0,0,0,1,0,0,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,1,1,1,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,0,1,0,0,1,1,1,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,1,1,1,0,0,1,0,1,1,1,1,1,1,1,2,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,0,1,1,1,0,1,1,1,0,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,0,1,0,1,0,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,0,1,1,1,0,1,0,0,1,1,1,1,1,1,1,0,0,1,0,1,1,1,0,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,2,1,1,1,1,1,0,0,0,1,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,0,1,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,1,0,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,1,0,0,0,1,1,1,1,1,2,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,0,1,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,1,0,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,1,0,1,0,0,0,1,0,1,0,0,1,1,1,1,0,0,1,1,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,1,1,0,0,1,1,1,1,0,0,1,0,1,0,0,0,1,0,1,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,1,0,1,0,1,1,1,0,1,1,1,1,0,0,1,0,0,0,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,0,0,0,1,0,0,1,1,1,1,0,1,1,1,0,1,0,1,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,2,1,1,1,1,1,1,0,1,1,1,1,0,0,1,0,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,0,1,0,0,1,1,1,1,0,1,1,1,1,1,1,2,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,1,1,1,0,0,0,1,1,1,1,1,0,1,1,0,1,1,1,1,1,1,1,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,1,1,1,1,1,1,1,0,1,1,0,1,1,1,1,1,0,0,0,1,1,1,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,0,1,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,0,1,0,0,0,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,0,0,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,0,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,1,1,0,0,1,1,1,1,1,0,1,0,1,1,1,1,1,1,0,1,0,0,0,1,0,0,0,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,0,0,0,1,0,0,0,1,0,1,1,1,1,1,1,0,1,0,1,1,1,1,1,0,0,1,1,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,1,1,1,0,0,0,1,1,1,1,1,0,1,0,0,1,1,1,1,1,1,1,1,0,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,0,1,1,1,1,1,1,1,1,0,0,1,0,1,1,1,1,1,0,0,0,1,1,1,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,0,0,1,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,1,0,0,1,1,1,1,1,1,1,2,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,0,0,1,0,1,0,1,1,1,1,0,0,1,0,1,1,0,1,1,1,1,1,0,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,0,1,0,0,1,1,1,1,1,0,1,1,0,1,0,0,1,1,1,1,0,1,0,1,0,0,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,0,0,1,1,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,1,1,0,0,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,0,0,0,1,1,1,1,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,0,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,1,1,1,1,0,0,0,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,0,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,2,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,1,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,1,0,1,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,1,1,1,1,1,1,1,0,1,0,1,0,0,0,1,1,1,1,1,2,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,1,0,1,1,1,1,1,0,1,0,1,0,0,0,1,0,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,0,1,0,0,0,1,0,1,0,1,1,1,1,1,0,1,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,1,0,0,1,0,0,1,0,1,1,1,0,0,0,1,0,0,1,0,1,1,1,1,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,1,1,1,1,0,1,0,0,1,0,0,0,1,1,1,0,1,0,0,1,0,0,1,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,1,0,0,1,0,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,0,1,0,0,1,0,0,0,0,0,0,0,0},
			{0,0,0,2,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,0,1,1,1,1,0,0,1,0,1,0,0,1,0,1,0,0,1,0,1,0,0,1,1,1,1,1,1,1,0,0,1,0,1,0,0,1,0,1,0,0,1,0,1,0,0,1,1,1,1,0,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,2,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,1,0,0,1,0,1,1,1,0,0,1,0,1,0,0,1,0,1,0,0,1,0,1,1,1,1,0,1,1,1,1,0,0,1,0,0,1,1,1,1,0,1,1,1,1,0,1,0,0,1,0,1,0,0,1,0,1,0,0,1,1,1,0,1,0,0,1,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,1,0,1,0,1,0,0,1,0,1,0,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,0,1,0,1,0,0,1,0,1,0,1,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,4,6,4,4,6,4,4,6,4,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,0,0,0,0,0},
			{0,0,2,1,1,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,1,1,0,0,0,0,1,0,0,1,0,0,0,4,5,3,3,5,3,3,5,4,0,0,0,1,0,0,1,0,0,0,0,1,1,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,1,1,2,0,0},
			{0,0,0,0,0,0,0,1,0,1,1,1,1,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,1,1,1,1,0,1,0,0,0,0,1,0,0,1,0,4,4,4,5,5,5,5,5,5,5,4,4,4,0,1,0,0,1,0,0,0,0,1,0,1,1,1,1,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,1,1,1,1,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,1,6,5,5,5,3,3,5,3,3,5,5,5,6,1,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,1,1,1,0,0,0,1,0,1,0,0,1,0,1,0,1,1,1,1,1,1,0,0,1,0,0,0,0,0,1,0,0,1,0,0,4,3,5,3,3,5,5,5,3,3,3,5,4,0,0,1,0,0,1,0,0,0,0,0,1,0,0,1,1,1,1,1,1,0,1,0,1,0,0,1,0,1,0,0,0,1,1,1,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,0,0,1,1,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,4,3,5,3,5,5,3,5,5,3,3,5,4,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,1,1,0,0,1,1,1,1,1,1,1,0,0,1,0,0,0,0,0,0,0},
			{2,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,1,0,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,1,1,1,1,1,1,6,5,5,5,5,3,3,3,5,5,5,5,6,1,1,1,1,1,1,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,0,1,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,2},
			{0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,0,0,1,1,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,4,3,5,3,5,5,3,5,5,3,3,5,4,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,1,1,0,0,1,1,1,1,1,1,1,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,1,1,1,0,0,0,1,0,1,0,0,1,0,1,0,1,1,1,1,1,1,0,0,1,0,0,0,0,0,1,0,0,1,0,0,4,3,5,3,3,5,5,5,3,3,3,5,4,0,0,1,0,0,1,0,0,0,0,0,1,0,0,1,1,1,1,1,1,0,1,0,1,0,0,1,0,1,0,0,0,1,1,1,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,1,6,5,5,5,3,3,5,3,3,5,5,5,6,1,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,1,1,1,1,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,1,1,1,1,0,1,0,0,0,0,1,0,0,1,0,4,4,4,5,5,5,5,5,5,5,4,4,4,0,1,0,0,1,0,0,0,0,1,0,1,1,1,1,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,1,1,1,1,0,1,0,0,0,0,0,0,0},
			{0,0,2,1,1,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,1,1,0,0,0,0,1,0,0,1,0,0,0,4,5,3,3,5,3,3,5,4,0,0,0,1,0,0,1,0,0,0,0,1,1,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,1,1,2,0,0},
			{0,0,0,0,0,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,4,6,4,4,6,4,4,6,4,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,1,0,1,0,1,0,0,1,0,1,0,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,0,1,0,1,0,0,1,0,1,0,1,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,1,0,0,1,0,1,1,1,0,0,1,0,1,0,0,1,0,1,0,0,1,0,1,1,1,1,0,1,1,1,1,0,0,1,0,0,1,1,1,1,0,1,1,1,1,0,1,0,0,1,0,1,0,0,1,0,1,0,0,1,1,1,0,1,0,0,1,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,2,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,0,1,1,1,1,0,0,1,0,1,0,0,1,0,1,0,0,1,0,1,0,0,1,1,1,1,1,1,1,0,0,1,0,1,0,0,1,0,1,0,0,1,0,1,0,0,1,1,1,1,0,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,2,0,0,0},
			{0,0,0,0,0,0,0,0,1,0,0,1,0,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,0,1,0,0,1,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,1,0,0,1,0,0,1,0,1,1,1,0,0,0,1,0,0,1,0,1,1,1,1,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,1,1,1,1,0,1,0,0,1,0,0,0,1,1,1,0,1,0,0,1,0,0,1,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,1,0,1,1,1,1,1,0,1,0,1,0,0,0,1,0,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,0,1,0,0,0,1,0,1,0,1,1,1,1,1,0,1,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,2,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,1,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,1,0,1,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,1,1,1,1,1,1,1,0,1,0,1,0,0,0,1,1,1,1,1,2,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,0,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,0,0,0,1,1,1,1,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,0,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,1,1,1,1,0,0,0,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,0,0,1,1,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,1,1,0,0,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,0,0,1,0,1,0,1,1,1,1,0,0,1,0,1,1,0,1,1,1,1,1,0,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,0,1,0,0,1,1,1,1,1,0,1,1,0,1,0,0,1,1,1,1,0,1,0,1,0,0,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,0,0,1,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,1,0,0,1,1,1,1,1,1,1,2,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,1,1,1,0,0,0,1,1,1,1,1,0,1,0,0,1,1,1,1,1,1,1,1,0,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,0,1,1,1,1,1,1,1,1,0,0,1,0,1,1,1,1,1,0,0,0,1,1,1,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,1,1,0,0,1,1,1,1,1,0,1,0,1,1,1,1,1,1,0,1,0,0,0,1,0,0,0,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,0,0,0,1,0,0,0,1,0,1,1,1,1,1,1,0,1,0,1,1,1,1,1,0,0,1,1,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,0,1,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,0,1,0,0,0,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,0,0,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,0,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,1,1,1,0,0,0,1,1,1,1,1,0,1,1,0,1,1,1,1,1,1,1,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,1,1,1,1,1,1,1,0,1,1,0,1,1,1,1,1,0,0,0,1,1,1,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,2,1,1,1,1,1,1,0,1,1,1,1,0,0,1,0,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,0,1,0,0,1,1,1,1,0,1,1,1,1,1,1,2,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,1,0,1,0,1,1,1,0,1,1,1,1,0,0,1,0,0,0,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,0,0,0,1,0,0,1,1,1,1,0,1,1,1,0,1,0,1,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,1,0,1,0,0,0,1,0,1,0,0,1,1,1,1,0,0,1,1,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,1,1,0,0,1,1,1,1,0,0,1,0,1,0,0,0,1,0,1,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,0,1,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,1,0,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,2,1,1,1,1,1,0,0,0,1,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,0,1,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,1,0,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,1,0,0,0,1,1,1,1,1,2,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,0,1,1,1,0,1,0,0,1,1,1,1,1,1,1,0,0,1,0,1,1,1,0,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,0,1,0,1,0,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,0,1,1,1,0,1,1,1,0,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,0,1,0,0,1,1,1,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,1,1,1,0,0,1,0,1,1,1,1,1,1,1,2,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,1,1,1,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,0,0,1,0,0,0,0,1,1,1,1,0,0,0,0,0,1,1,1,1,0,0,0,0,1,0,0,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,1,1,1,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,1,1,0,0,0,1,0,0,0,1,1,1,1,0,1,0,1,0,1,1,1,1,0,0,0,1,0,0,0,1,1,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,2,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,1,0,1,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,1,0,1,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,0,0,1,0,1,1,1,1,0,1,0,0,1,0,0,0,1,0,1,0,1,1,1,1,1,0,0,1,0,1,0,1,0,1,0,0,1,1,1,1,1,0,1,0,1,0,0,0,1,0,0,1,0,1,1,1,1,0,1,0,0,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,1,1,1,1,0,1,0,1,0,1,1,1,1,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,0,0,0,0,0,0,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,1,1,1,1,1,1,1,0,1,0,0,1,0,1,0,0,1,0,1,1,1,1,1,1,1,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,1,1,1,1,1,1,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,1,1,1,0,1,0,0,0,1,0,1,1,1,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,2,0,0,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,0,0,2,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,1,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
	};

}
```


---

## File: `mazes\Maze2.java`

```java
package nautilus.game.arcade.game.games.monstermaze.mazes;

/**
 * Created by William (WilliamTiger).
 * 21/10/15
 */
public class Maze2
{

	public static final int[][] MAZE = {
			{0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0},
			{0,0,0,2,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,2,0,0,0},
			{0,0,0,1,0,0,1,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,1,0,0,1,0,0,0},
			{0,2,1,1,1,1,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,1,2,0},
			{0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0},
			{0,0,0,1,0,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,1,0,0,0},
			{2,1,1,1,1,1,0,0,1,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,2,0,0,0,2,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,0,0,1,0,0,1,1,1,1,1,2},
			{0,0,0,1,0,1,0,1,1,1,1,1,1,1,0,0,1,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,0,1,0,1,0,0,0},
			{0,0,0,1,0,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,2,0,0,1,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,1,0,0,2,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,0,1,0,0,0},
			{0,2,1,1,1,1,0,1,0,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,2,0,0,0,0,1,0,0,0,0,2,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,2,0,0,0,0,1,0,0,0,0,2,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,0,1,0,1,1,1,1,2,0},
			{0,0,0,0,0,1,0,1,1,1,0,0,0,1,0,0,1,1,1,0,1,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,1,0,1,1,1,0,0,1,0,0,0,1,1,1,0,1,0,0,0,0,0},
			{0,0,0,0,0,1,0,1,0,1,0,0,0,1,1,1,1,0,1,1,1,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,1,1,1,0,1,1,1,1,0,0,0,1,0,1,0,1,0,0,0,0,0},
			{0,0,2,1,1,1,1,1,0,1,0,0,1,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,0,0,1,0,0,0,1,1,1,1,1,0,0,0,0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,0,1,1,1,1,1,0,0,0,1,0,0,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,1,0,0,1,0,1,1,1,1,1,2,0,0},
			{0,0,0,0,0,0,0,1,1,1,1,1,1,0,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,1,1,1,0,1,0,0,0,1,1,1,1,1,1,1,0,0,0,0,0,0,0,1,1,1,1,1,1,1,0,0,0,1,0,1,1,1,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,0,1,1,1,1,1,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,0,0,0,1,1,1,1,0,1,1,1,1,0,0,0,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,2,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,0,0,0,1,0,0,1,0,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,0,1,0,0,1,0,0,0,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,2,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,1,1,1,0,0,1,1,1,1,1,0,0,1,0,1,0,0,0,1,0,0,1,0,0,1,1,1,0,0,1,0,0,1,0,0,0,1,0,1,0,0,1,1,1,1,1,0,0,1,1,1,1,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,1,1,1,1,0,0,0,1,0,0,1,1,1,1,0,1,1,1,1,0,0,1,0,0,0,1,1,1,1,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,0,1,0,1,0,0,1,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,0,1,0,1,1,1,1,1,0,1,0,0,1,0,1,1,1,1,1,0,0,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,0,1,0,0,1,0,1,1,1,1,1,0,1,0,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,2,1,1,1,1,1,1,0,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,0,1,0,1,0,0,0,1,1,1,1,1,0,1,1,1,1,1,1,2,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,0,0,1,0,0,0,0,1,0,1,1,1,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,1,1,1,0,1,0,0,0,0,1,0,0,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,1,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,1,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,2,1,1,1,1,1,0,1,0,1,1,1,1,0,0,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,0,0,1,1,1,1,0,1,0,1,1,1,1,1,2,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,1,1,1,1,1,0,1,1,1,1,1,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,0,0,0,0,1,0,1,0,0,1,0,0,0,1,0,1,1,1,1,1,1,1,1,1,0,0,0,0,1,1,1,0,0,0,0,1,1,1,1,1,1,1,1,1,0,1,0,0,0,1,0,0,1,0,1,0,0,0,0,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,2,1,1,1,1,1,0,1,1,1,1,1,1,0,0,1,1,1,1,1,1,0,0,0,1,1,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,1,0,0,0,0,1,0,0,0,1,0,0,0,1,1,1,0,0,0,1,1,1,1,1,1,0,0,1,1,1,1,1,1,0,1,1,1,1,1,2,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,0,1,1,1,1,0,1,0,1,0,1,1,1,1,0,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,0,0,1,1,1,1,1,1,1,0,0,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,2,1,1,1,1,1,0,0,0,1,0,1,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,1,1,1,1,0,0,0,1,0,0,1,0,1,1,1,0,1,0,0,1,0,0,0,1,1,1,1,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,1,0,1,0,0,0,1,1,1,1,1,2,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,1,0,0,1,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,1,0,1,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,1,0,0,1,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,1,0,1,0,0,0,1,0,0,1,1,1,1,0,0,0,1,0,1,0,1,0,1,0,0,0,1,1,1,1,0,0,1,0,0,0,1,0,1,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,1,1,0,1,1,1,1,1,1,0,0,1,0,0,0,1,0,1,0,1,0,1,0,0,0,1,0,0,1,1,1,1,1,1,0,1,1,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,0,0,0,1,1,1,1,1,1,0,1,1,0,0,0,0,0,1,0,1,0,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,0,1,0,1,0,0,0,0,0,1,1,0,1,1,1,1,1,1,0,0,0,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,2,1,1,1,0,1,1,1,1,1,0,0,0,0,1,0,1,0,0,1,1,1,1,1,1,1,0,0,0,0,1,1,1,1,0,1,0,0,1,0,0,0,1,0,0,1,0,1,1,1,1,0,0,0,0,1,1,1,1,1,1,1,0,0,1,0,1,0,0,0,0,1,1,1,1,1,0,1,1,1,2,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,0,1,0,0,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,1,1,0,0,1,1,1,0,0,1,0,0,0,1,0,0,1,1,1,0,0,1,1,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,2,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,0,1,0,0,1,0,0,1,0,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,1,1,1,1,1,1,1,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,2,1,1,1,1,1,1,0,1,0,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,1,0,0,1,0,0,1,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,0,1,0,1,1,1,1,1,1,2,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,1,0,0,1,0,0,4,6,6,6,4,4,6,4,4,6,6,6,4,0,0,1,0,0,1,0,0,0,0,1,1,1,1,1,1,1,1,1,0,1,0,0,0,0,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,1,1,1,1,0,0,0,1,0,0,0,1,0,0,1,1,1,1,0,1,1,1,6,3,5,3,3,3,5,3,3,3,5,3,6,1,1,1,0,1,1,1,1,0,0,1,0,0,0,1,0,0,0,1,1,1,1,0,0,0,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,6,5,5,5,5,5,5,5,5,5,5,5,6,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,6,3,5,3,3,3,5,3,3,3,5,3,6,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,2,1,1,1,0,0,1,0,1,0,0,0,1,0,0,1,1,1,1,0,0,0,0,1,0,0,0,0,0,1,1,1,1,0,0,1,0,4,3,5,3,5,5,5,5,5,3,5,3,4,0,1,0,0,1,1,1,1,0,0,0,0,0,1,0,0,0,0,1,1,1,1,0,0,1,0,0,0,1,0,1,0,0,1,1,1,2,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,0,1,1,1,1,1,0,0,1,0,0,1,0,4,3,5,3,5,3,3,3,5,3,5,3,4,0,1,0,0,1,0,0,1,1,1,1,1,0,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,1,1,1,1,6,5,5,5,5,3,3,3,5,5,5,5,6,1,1,1,1,1,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,0,1,1,1,1,1,0,0,1,0,0,1,0,4,3,5,3,5,3,3,3,5,3,5,3,4,0,1,0,0,1,0,0,1,1,1,1,1,0,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,2,1,1,1,0,0,1,0,1,0,0,0,1,0,0,1,1,1,1,0,0,0,0,1,0,0,0,0,0,1,1,1,1,0,0,1,0,4,3,5,3,5,5,5,5,5,3,5,3,4,0,1,0,0,1,1,1,1,0,0,0,0,0,1,0,0,0,0,1,1,1,1,0,0,1,0,0,0,1,0,1,0,0,1,1,1,2,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,6,3,5,3,3,3,5,3,3,3,5,3,6,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,6,5,5,5,5,5,5,5,5,5,5,5,6,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,0,0,0,1,1,1,1,0,0,0,1,0,0,0,1,0,0,1,1,1,1,0,1,1,1,6,3,5,3,3,3,5,3,3,3,5,3,6,1,1,1,0,1,1,1,1,0,0,1,0,0,0,1,0,0,0,1,1,1,1,0,1,0,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,0,0,0,0,1,0,1,1,1,1,1,1,1,1,1,0,0,0,0,1,0,0,1,0,0,4,6,6,6,4,4,6,4,4,6,6,6,4,0,0,1,0,0,1,0,0,0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,2,1,1,1,1,1,1,0,1,0,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,1,0,0,1,0,0,1,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,0,1,0,1,1,1,1,1,1,2,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,1,1,1,1,1,1,1,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,0,1,0,0,1,0,0,1,0,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,2,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,0,1,0,0,0,0,1,0,1,0,0,1,0,0,1,0,0,1,0,0,1,1,1,0,0,1,1,1,0,0,1,0,0,0,1,0,0,1,1,1,0,0,1,1,1,0,0,1,0,0,1,0,0,1,0,0,1,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,2,1,1,1,0,1,1,1,1,1,0,0,0,0,1,0,1,0,0,1,1,1,1,1,1,1,0,0,0,0,1,1,1,1,0,1,0,0,1,0,0,0,1,0,0,1,0,1,1,1,1,0,0,0,0,1,1,1,1,1,1,1,0,0,1,0,1,0,0,0,0,1,1,1,1,1,0,1,1,1,2,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,0,0,0,1,1,1,1,1,1,0,1,1,0,0,0,0,0,1,0,1,0,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,0,1,0,1,0,0,0,0,0,1,1,0,1,1,1,1,1,1,0,0,0,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,1,1,0,1,1,1,1,1,1,0,0,1,0,0,0,1,0,1,0,1,0,1,0,0,0,1,0,0,1,1,1,1,1,1,0,1,1,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,1,0,1,0,0,0,1,0,0,1,1,1,1,0,0,0,1,0,1,0,1,0,1,0,0,0,1,1,1,1,0,0,1,0,0,0,1,0,1,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,1,0,0,1,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,1,0,1,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,1,0,0,1,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,2,1,1,1,1,1,0,0,0,1,0,1,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,1,1,1,1,0,0,0,1,0,0,1,0,1,1,1,0,1,0,0,1,0,0,0,1,1,1,1,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,1,0,1,0,0,0,1,1,1,1,1,2,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,0,0,1,1,1,1,1,1,1,0,0,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,0,1,1,1,1,0,1,0,1,0,1,1,1,1,0,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,2,1,1,1,1,1,0,1,1,1,1,1,1,0,0,1,1,1,1,1,1,0,0,0,1,1,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,1,0,0,0,0,1,0,0,0,1,0,0,0,1,1,1,0,0,0,1,1,1,1,1,1,0,0,1,1,1,1,1,1,0,1,1,1,1,1,2,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,0,0,0,0,1,0,1,0,0,1,0,0,0,1,0,1,1,1,1,1,1,1,1,1,0,0,0,0,1,1,1,0,0,0,0,1,1,1,1,1,1,1,1,1,0,1,0,0,0,1,0,0,1,0,1,0,0,0,0,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,1,1,1,1,1,0,1,1,1,1,1,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,2,1,1,1,1,1,0,1,0,1,1,1,1,0,0,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,0,0,1,1,1,1,0,1,0,1,1,1,1,1,2,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,1,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,1,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,1,0,0,1,0,0,0,0,1,0,1,1,1,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,1,1,1,0,1,0,0,0,0,1,0,0,1,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,2,1,1,1,1,1,1,0,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,0,1,0,1,0,0,0,1,1,1,1,1,0,1,1,1,1,1,1,2,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,0,1,0,1,1,1,1,1,0,1,0,0,1,0,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,0,0,1,1,1,1,1,0,1,0,0,1,0,1,1,1,1,1,0,1,0,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,1,1,1,1,0,0,0,1,0,0,1,1,1,1,0,1,1,1,1,0,0,1,0,0,0,1,1,1,1,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,0,1,0,1,0,0,1,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,1,1,1,0,0,1,1,1,1,1,0,0,1,0,1,0,0,0,1,0,0,1,0,0,1,1,1,0,0,1,0,0,1,0,0,0,1,0,1,0,0,1,1,1,1,1,0,0,1,1,1,1,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,2,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,1,1,1,1,0,0,0,1,0,0,1,0,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,0,1,0,0,1,0,0,0,1,1,1,1,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,2,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,0,0,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,0,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,0,0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,1,1,1,1,1,0,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,1,1,1,0,1,0,0,0,1,1,1,1,1,1,1,0,0,0,0,0,0,0,1,1,1,1,1,1,1,0,0,0,1,0,1,1,1,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,0,1,1,1,1,1,1,0,0,0,0,0,0,0},
			{0,0,2,1,1,1,1,1,0,1,0,0,1,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,0,0,1,0,0,0,1,1,1,1,1,0,0,0,0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,0,1,1,1,1,1,0,0,0,1,0,0,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,1,0,0,1,0,1,1,1,1,1,2,0,0},
			{0,0,0,0,0,1,0,1,0,1,0,0,0,1,1,1,1,0,1,1,1,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,1,1,1,0,1,1,1,1,0,0,0,1,0,1,0,1,0,0,0,0,0},
			{0,0,0,0,0,1,0,1,1,1,0,0,0,1,0,0,1,1,1,0,1,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,1,0,1,1,1,0,0,1,0,0,0,1,1,1,0,1,0,0,0,0,0},
			{0,2,1,1,1,1,0,1,0,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,2,0,0,0,0,1,0,0,0,0,2,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,2,0,0,0,0,1,0,0,0,0,2,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,0,1,0,1,1,1,1,2,0},
			{0,0,0,1,0,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,2,0,0,1,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,1,0,0,2,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,0,1,0,0,0},
			{0,0,0,1,0,1,0,1,1,1,1,1,1,1,0,0,1,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,0,1,0,1,0,0,0},
			{2,1,1,1,1,1,0,0,1,0,0,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,2,0,0,0,2,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,0,0,1,0,0,1,1,1,1,1,2},
			{0,0,0,1,0,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,1,0,0,0},
			{0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0},
			{0,2,1,1,1,1,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,1,2,0},
			{0,0,0,1,0,0,1,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,1,0,0,1,0,0,0},
			{0,0,0,2,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,2,0,0,0},
			{0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0},
	};

}
```


---

## File: `mazes\Maze3.java`

```java
package nautilus.game.arcade.game.games.monstermaze.mazes;

/**
 * Created by William (WilliamTiger).
 * 21/10/15
 */
public class Maze3
{

	public static final int[][] MAZE = {
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,1,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,2,0,1,0,2,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,1,0,0,0,1,0,1,0,1,0,0,0,1,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,0,0,2,0,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,1,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,0,2,0,0,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,2,0,0,1,0,0,0,1,0,1,0,1,0,0,0,1,0,0,2,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,0,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,1,0,0,0,1,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,0,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,1,1,0,0,1,0,0,1,1,1,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,1,1,1,0,0,1,0,0,1,1,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,1,1,0,0,0,1,1,1,1,0,0,0,0,0,1,0,0,0,1,1,1,1,0,0,0,1,0,0,0,1,0,0,0,1,1,1,1,0,0,0,1,0,0,0,0,0,1,1,1,1,0,0,0,1,1,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,1,1,0,0,1,0,0,0,0,0,1,1,1,1,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,1,1,1,1,0,0,0,0,0,1,0,0,1,1,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,0,1,0,0,1,1,1,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,1,1,1,0,0,1,0,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,1,1,1,1,1,0,0,1,1,1,1,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,1,1,1,1,1,0,0,1,1,1,1,1,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,2,1,1,1,1,1,1,0,0,1,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,1,1,0,0,0,0,1,1,1,0,0,1,1,1,1,0,0,0,1,1,1,1,0,0,1,1,1,0,0,0,0,1,1,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,1,0,0,1,1,1,1,1,1,2,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,1,0,1,0,0,0,1,1,0,1,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,1,0,1,1,0,0,0,1,0,1,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,1,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,1,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,1,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,0,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,0,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,0,1,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,2,1,1,1,1,1,1,0,0,0,0,1,0,0,0,0,1,0,1,1,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,1,0,0,1,1,1,1,1,1,1,1,1,1,1,0,0,1,1,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,1,1,0,1,0,0,0,0,1,0,0,0,0,1,1,1,1,1,1,2,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,1,1,1,0,0,1,1,1,1,0,0,1,1,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,1,1,0,0,1,1,1,1,0,0,1,1,1,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,0,1,1,1,1,0,0,1,1,1,1,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,1,1,1,1,0,0,1,1,1,1,0,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,1,1,1,1,1,1,0,0,0,1,1,1,1,0,0,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,0,0,1,1,1,1,0,0,0,1,1,1,1,1,1,2,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,0,1,0,0,1,1,0,0,0,1,1,0,0,0,1,0,0,0,1,1,0,0,0,1,1,0,0,1,0,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,2,1,1,1,1,1,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,1,0,0,1,1,1,0,0,1,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,1,1,1,1,1,2,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,0,0,1,0,1,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,1,1,1,1,0,1,1,1,1,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,1,0,1,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,1,1,1,0,0,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,1,1,1,0,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,0,1,1,1,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,0,0,1,1,1,1,1,1,1,1,1,1,2,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,0,0,1,0,0,0,1,1,1,1,0,1,1,1,0,0,1,0,0,1,0,1,1,1,1,1,0,1,0,0,1,0,0,1,1,1,0,1,1,1,1,0,0,0,1,0,0,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,0,0,0,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,1,0,0,0,1,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,1,1,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,1,1,0,0,0,0,0,0,0,0},
			{0,0,0,2,1,1,1,1,1,0,0,0,0,1,1,1,1,0,0,0,0,1,0,0,0,0,1,1,1,1,1,1,0,0,0,0,1,0,0,1,1,1,1,1,1,1,0,1,0,0,0,1,0,1,1,1,1,1,1,1,0,0,1,0,0,0,0,1,1,1,1,1,1,0,0,0,0,1,0,0,0,0,1,1,1,1,0,0,0,0,1,1,1,1,1,2,0,0,0},
			{0,0,0,0,0,0,0,0,1,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,1,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0},
			{0,0,0,0,2,1,1,1,1,1,1,1,0,1,1,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,1,1,0,1,1,1,1,1,1,1,2,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,1,1,1,0,1,0,1,1,1,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,0,0,0,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,0,0,0,0,0,1,0,0,1,4,4,4,6,4,4,6,4,4,6,4,4,4,1,0,0,1,0,0,0,0,0,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,0,0,0,0,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0},
			{2,1,1,1,1,1,1,1,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,0,0,1,1,0,0,0,1,1,1,1,1,6,5,5,5,5,3,5,3,5,5,5,5,6,1,1,1,1,1,0,0,0,1,1,0,0,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,1,1,1,1,1,1,1,2},
			{0,0,0,0,0,0,1,0,0,1,0,0,1,1,1,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,1,1,1,1,0,1,0,0,4,5,3,3,5,5,5,5,5,3,3,5,4,0,0,1,0,1,1,1,1,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,1,1,1,0,0,1,0,0,1,0,0,0,0,0,0},
			{0,0,0,0,0,0,1,1,1,1,0,0,1,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,6,5,5,5,5,3,5,3,5,5,5,5,6,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0,1,1,1,1,0,0,0,0,0,0},
			{0,0,0,0,0,1,1,0,0,1,1,1,1,0,1,1,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,6,3,3,5,3,3,5,3,3,5,3,3,6,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,1,1,0,1,1,1,1,0,0,1,1,0,0,0,0,0},
			{0,0,0,0,0,1,0,0,0,0,0,1,0,0,1,0,0,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,1,1,6,5,5,5,3,5,5,5,3,5,5,5,6,1,1,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,0,0,1,0,0,1,0,0,0,0,0,1,0,0,0,0,0},
			{0,2,1,1,1,1,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,0,6,3,3,5,5,5,3,5,5,5,3,3,6,0,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,1,1,1,1,2,0},
			{0,0,0,0,0,1,0,0,0,0,0,1,0,0,1,0,0,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,1,1,6,5,5,5,3,5,5,5,3,5,5,5,6,1,1,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,0,0,1,0,0,1,0,0,0,0,0,1,0,0,0,0,0},
			{0,0,0,0,0,1,1,0,0,1,1,1,1,0,1,1,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,6,3,3,5,3,3,5,3,3,5,3,3,6,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,0,0,1,0,0,1,1,0,1,1,1,1,0,0,1,1,0,0,0,0,0},
			{0,0,0,0,0,0,1,1,1,1,0,0,1,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,6,5,5,5,5,3,5,3,5,5,5,5,6,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0,1,1,1,1,0,0,0,0,0,0},
			{0,0,0,0,0,0,1,0,0,1,0,0,1,1,1,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,1,1,1,1,0,1,0,0,4,5,3,3,5,5,5,5,5,3,3,5,4,0,0,1,0,1,1,1,1,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,1,1,1,0,0,1,0,0,1,0,0,0,0,0,0},
			{2,1,1,1,1,1,1,1,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,0,0,1,1,0,0,0,1,1,1,1,1,6,5,5,5,5,3,5,3,5,5,5,5,6,1,1,1,1,1,0,0,0,1,1,0,0,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,1,1,1,1,1,1,1,2},
			{0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,0,0,0,0,1,1,1,1,1,1,1,0,0,1,1,1,1,1,1,0,0,0,0,0,1,0,0,1,4,4,4,6,4,4,6,4,4,6,4,4,4,1,0,0,1,0,0,0,0,0,1,1,1,1,1,1,0,0,1,1,1,1,1,1,1,0,0,0,0,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,1,1,1,0,1,0,1,1,1,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0},
			{0,0,0,0,2,1,1,1,1,1,1,1,0,1,1,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,1,1,0,1,1,1,1,1,1,1,2,0,0,0,0},
			{0,0,0,0,0,0,0,0,1,0,0,1,0,1,0,0,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,0,0,1,0,1,0,0,1,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,1,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,1,0,0,0,0,0,0,0,0},
			{0,0,0,2,1,1,1,1,1,0,0,0,0,1,1,1,1,0,0,0,0,1,0,0,0,0,1,1,1,1,1,1,0,0,0,0,1,0,0,1,1,1,1,1,1,1,0,1,0,0,0,1,0,1,1,1,1,1,1,1,0,0,1,0,0,0,0,1,1,1,1,1,1,0,0,0,0,1,0,0,0,0,1,1,1,1,0,0,0,0,1,1,1,1,1,2,0,0,0},
			{0,0,0,0,0,0,0,0,1,1,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,0,0,1,0,0,1,0,1,0,0,0,1,0,1,0,0,1,0,0,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,1,1,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,1,1,1,1,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,1,0,0,0,1,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,1,1,1,1,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,0,0,0,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,1,1,1,0,0,1,0,0,0,1,1,1,1,0,1,1,1,0,0,1,0,0,1,0,1,1,1,1,1,0,1,0,0,1,0,0,1,1,1,0,1,1,1,1,0,0,0,1,0,0,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,2,1,1,1,1,1,1,1,1,1,1,0,0,1,1,1,1,0,1,1,1,1,1,1,1,1,0,0,1,1,1,0,1,1,1,1,0,0,1,0,0,1,0,1,0,0,1,0,0,1,1,1,1,0,1,1,1,0,0,1,1,1,1,1,1,1,1,0,1,1,1,1,0,0,1,1,1,1,1,1,1,1,1,1,2,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,1,1,1,1,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,0,0,1,0,1,0,0,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,1,1,1,1,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,0,0,1,0,1,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,1,1,1,1,0,1,1,1,1,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,1,0,1,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,2,1,1,1,1,1,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,0,0,1,0,0,1,1,1,0,0,1,0,0,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,1,1,1,1,1,1,1,1,1,0,1,0,0,1,0,0,0,1,1,1,1,1,2,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,0,1,0,0,1,1,0,0,0,1,1,0,0,0,1,0,0,0,1,1,0,0,0,1,1,0,0,1,0,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,2,1,1,1,1,1,1,0,0,0,1,1,1,1,0,0,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,0,0,1,1,1,1,0,0,0,1,1,1,1,1,1,2,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,0,0,1,0,0,1,0,0,1,0,0,1,0,0,0,0,1,1,1,1,0,0,1,1,1,1,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,1,1,1,1,0,0,1,1,1,1,0,0,0,0,1,0,0,1,0,0,1,0,0,1,0,0,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,1,1,1,0,0,1,1,1,1,0,0,1,1,1,1,1,1,0,0,1,0,0,1,0,0,1,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,1,0,0,1,0,0,1,0,0,1,1,1,1,1,1,0,0,1,1,1,1,0,0,1,1,1,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,2,1,1,1,1,1,1,0,0,0,0,1,0,0,0,0,1,0,1,1,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,1,0,0,1,1,1,1,1,1,1,1,1,1,1,0,0,1,1,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,1,1,0,1,0,0,0,0,1,0,0,0,0,1,1,1,1,1,1,2,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,1,0,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,0,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,0,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,0,1,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,1,0,0,1,1,1,1,0,0,1,0,0,0,1,0,0,1,1,1,1,0,0,1,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,1,0,1,0,0,0,1,1,0,1,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,1,0,1,1,0,0,0,1,0,1,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,2,1,1,1,1,1,1,0,0,1,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,1,1,0,0,0,0,1,1,1,0,0,1,1,1,1,0,0,0,1,1,1,1,0,0,1,1,1,0,0,0,0,1,1,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,1,0,0,1,1,1,1,1,1,2,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,1,1,1,1,1,0,0,1,1,1,1,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,1,1,1,1,1,0,0,1,1,1,1,1,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,0,1,0,0,1,1,1,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,1,1,1,0,0,1,0,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,0,1,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,1,1,1,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,1,1,1,1,0,0,1,0,0,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,1,0,0,0,1,1,1,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,1,1,1,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,1,0,0,1,1,0,0,1,0,0,0,0,0,1,1,1,1,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,1,1,1,1,0,0,0,0,0,1,0,0,1,1,0,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,1,1,0,0,0,1,1,1,1,0,0,0,0,0,1,0,0,0,1,1,1,1,0,0,0,1,0,0,0,1,0,0,0,1,1,1,1,0,0,0,1,0,0,0,0,0,1,1,1,1,0,0,0,1,1,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,1,0,1,1,0,0,1,0,0,1,1,1,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,1,1,1,0,0,1,0,0,1,1,0,1,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,1,1,1,0,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,1,1,1,1,0,0,0,1,1,1,1,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,0,1,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,0,1,1,1,1,1,0,0,0,1,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,0,0,2,0,0,1,0,0,0,1,0,1,0,1,0,0,0,1,0,0,2,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,1,0,0,0,0,2,0,0,0,1,0,0,0,0,0,0,1,0,0,0,1,0,1,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,0,2,0,0,0,0,1,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,1,0,0,0,1,0,1,0,1,0,0,0,1,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,2,0,1,0,2,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,1,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
			{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
	};

}
```


---

## File: `MMMazes.java`

```java
package nautilus.game.arcade.game.games.monstermaze;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import mineplex.core.common.util.UtilAlg;
import mineplex.core.common.util.UtilBlock;
import mineplex.core.common.util.UtilMath;
import nautilus.game.arcade.game.games.monstermaze.mazes.Maze1;
import nautilus.game.arcade.game.games.monstermaze.mazes.Maze2;
import nautilus.game.arcade.game.games.monstermaze.mazes.Maze3;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

public class MMMazes
{
	private static int[][] _lastSelected = null;
	
	public static MazePreset getRandomMapPreset(Location loc, MazeBlockData data)
	{
		return new MazePreset(getRandomMap(), loc, data);		
	}
	
	public static class MazePreset
	{
		private MazeBlockData _data;
		
		private boolean _built = false;
		
		private ArrayList<Location> _maze = new ArrayList<Location>();
		private ArrayList<Location> _validSafePadSpawns;
		private ArrayList<Location> _spawns = new ArrayList<Location>();
		
		private ArrayList<Location> _centerSafeZone = new ArrayList<Location>();
		private ArrayList<Location> _centerSafeZonePaths = new ArrayList<Location>();
		
		private ArrayList<Location> _glassBounds = new ArrayList<Location>();
		
		private ArrayList<Location> _safeZones = new ArrayList<Location>();
		
		private Location _center;
		
		public MazePreset(int[][] rawMaze, Location loc, MazeBlockData data)
		{
			int[][] maze = rawMaze;
			
			_data = data;
			
			_center = loc;
			
			Location cur = _center.clone().subtract(49, 0, 49);
			
			// x/y refers to array coordinates, not physical minecraft location
			
			//0 = Air
			//1 = Maze
			//2 = Spawn
			//3 = Center Safe Area
			//4 = Safe Barrier
			//5 = Paths through center safe area
			//6 = paths through safe barrier
			
			for (int y = 0; y < 99; y++)
			{
				for (int x = 0; x < 99; x++)
				{
					int i = maze[y][x];
					if(i == 1)
					{
						_maze.add(cur);
					}
					else if(i == 2)
					{
						_maze.add(cur);
						_spawns.add(cur);
					}
					else if(i == 3)
					{
						_centerSafeZone.add(cur);
					}
					else if(i == 4)
					{
						_centerSafeZone.add(cur);
						_glassBounds.add(cur.clone().add(0, 2, 0));
					}
					else if (i == 5)
					{
						_centerSafeZone.add(cur);
						_centerSafeZonePaths.add(cur);
						_maze.add(cur);
					}
					else if (i == 6)
					{
						_centerSafeZone.add(cur);
						_glassBounds.add(cur.clone().add(0, 2, 0));
						_centerSafeZonePaths.add(cur);
						_maze.add(cur);
					}
					cur = cur.clone().add(0, 0, 1);
				}
				cur.setZ(_center.clone().subtract(49, 0, 49).getZ());
				cur = cur.clone().add(1, 0, 0);
			}
			
			_validSafePadSpawns = new ArrayList<Location>(_maze);
			
			Iterator<Location> iter = _validSafePadSpawns.iterator();
			removeLoop: while(iter.hasNext())
			{
				Location l = iter.next();
				
				for(Location s : _spawns)
				{
					if(UtilMath.offset2d(l, s) < 10)
					{
						iter.remove();
						continue removeLoop;
					}
				}
				
				for(Location b : _glassBounds)
				{
					if(UtilMath.offset2d(l, b) < 7)
					{
						iter.remove();
						continue removeLoop;
					}
				}
			}

			ArrayList<Location> locsToPickFrom = new ArrayList<>(_validSafePadSpawns);

			int numberOfSafeZones = 8;
			for (int i = 0; i < numberOfSafeZones; i++)
			{
				Location toAdd = null;
				if (locsToPickFrom == null)
				{
					toAdd = UtilAlg.Random(locsToPickFrom);
				}
				else
				{
					ArrayList<Location> toBeAwayFrom = new ArrayList<Location>(_safeZones);
					toBeAwayFrom.add(_center);
					toAdd = UtilAlg.getLocationAwayFromOtherLocations(locsToPickFrom, toBeAwayFrom);
				}

				_safeZones.add(toAdd);
//				for(Block b : UtilBlock.getInBoundingBox(toAdd.clone().add(1, 0, 1), toAdd.clone().subtract(1, 0, 1), false))
//				{
//					_safeZones.add(b.getLocation());
//				}

				for (Block b : UtilBlock.getInRadius(toAdd.getBlock(), 6).keySet())
				{
					locsToPickFrom.remove(b.getLocation());
				}
			}
			System.out.println("_safeZones.size() = " + _safeZones.size());
			
			Iterator<Location> it = _maze.iterator();
			while(it.hasNext())
			{
				Location lo = it.next();
				if(_safeZones.contains(lo.getBlock().getLocation())) it.remove();
			}
			
			Iterator<Location> iter2 = _validSafePadSpawns.iterator();
			removeLoop: while(iter2.hasNext())
			{
				Location l = iter2.next();
				
				for(Location s : _safeZones)
				{
					if(UtilMath.offset2d(l, s) < 7)
					{
						iter2.remove();
						continue removeLoop;
					}
				}
			
			}
		}
		
		@SuppressWarnings("deprecation")
		public void build()
		{
			for (Location loc : _maze)
			{	
				buildMazeBlockAt(loc);
			}
			
			for (Location loc : _spawns)
			{
				loc.clone().subtract(0, 1, 0).getBlock().setType(Material.REDSTONE_BLOCK);
			}
			
			for (Location loc : _centerSafeZone)
			{
				loc.clone().subtract(0, 1, 0).getBlock().setTypeIdAndData(159, (byte) 5, true);
			}
			
			_built = true;
		}
		
		public ArrayList<Location> getSpawns()
		{
			return _spawns;
		}
		
		public ArrayList<Location> getSafeZones()
		{
			return _safeZones;
		}
		
		public boolean isBuilt()
		{
			return _built;
		}
		
		@SuppressWarnings("deprecation")
		public void buildMazeBlockAt(Location loc)
		{
			Location mod = loc.clone();
			
			mod.subtract(0, 1, 0).getBlock().setTypeIdAndData(_data.Top.Type.getId(), _data.Top.Data, true);
			mod.subtract(0, 1, 0).getBlock().setTypeIdAndData(_data.Middle.Type.getId(), _data.Middle.Data, true);
			mod.subtract(0, 1, 0).getBlock().setTypeIdAndData(_data.Bottom.Type.getId(), _data.Bottom.Data, true);
		}
		
		// anywhere a monster can walk
		public ArrayList<Location> getMaze()
		{
			return _maze;
		}
		
		public ArrayList<Location> getGlassBounds()
		{
			return _glassBounds;
		}
		
		public ArrayList<Location> getCenterSafeZone()
		{
			return _centerSafeZone;
		}

		public ArrayList<Location> getCenterSafeZonePaths()
		{
			return _centerSafeZonePaths;
		}
		
		public ArrayList<Location> getValidSafePadSpawns()
		{
			return _validSafePadSpawns;
		}

		public Location getCenter()
		{
			return _center;
		}
	}

	// PARSED MAZES
	
	private static final int[][] getRandomMap()
	{
		List<int[][]> maps = Arrays.asList(Maze1.MAZE, Maze2.MAZE, Maze3.MAZE);
		
		//Attempt 10 times to get a new random maze.
		int[][] selected = null;
		for (int i = 0 ; i < 10 && selected == null ; i++)
		{	
			int[][] possible = maps.get(UtilMath.r(maps.size()));
			
			if (_lastSelected != possible)
				selected = possible;
		}
		
		//If the random hit the last selected every time
		//Then just pick a random one.
		if (selected == null)
			selected = maps.get(UtilMath.r(maps.size()));

		//This makes it work between different games.
		_lastSelected = selected;
		return maps.get(UtilMath.r(maps.size()));
	}
	
	//0 = Air
	//1 = Maze
	//2 = Spawn
	//3 = Center Safe Area
	//4 = Safe Barrier
	//5 = Paths through center safe area
	//6 = paths through safe barrier
}
```


---

## File: `MonsterBumpPlayerEvent.java`

```java
package nautilus.game.arcade.game.games.monstermaze.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class MonsterBumpPlayerEvent extends PlayerEvent
{
	/**
	 * @author Mysticate
	 */
	
	private static final HandlerList _handlers = new HandlerList();
	
	private static HandlerList getHandlerList()
	{
		return _handlers;
	}
	
	@Override
	public HandlerList getHandlers()
	{
		return getHandlerList();
	}
		
	public MonsterBumpPlayerEvent(Player player)
	{
		super(player);
	}
}
```


---

## File: `MonsterMaze.java`

```java
package nautilus.game.arcade.game.games.monstermaze;

import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;

import mineplex.core.common.util.C;
import mineplex.core.common.util.UtilBlock;
import mineplex.core.common.util.UtilEnt;
import mineplex.core.common.util.UtilInv;
import mineplex.core.common.util.UtilTextMiddle;
import mineplex.core.common.util.UtilTime;
import mineplex.core.itemstack.ItemStackFactory;
import mineplex.core.recharge.Recharge;
import mineplex.core.updater.UpdateType;
import mineplex.core.updater.event.UpdateEvent;
import mineplex.minecraft.game.core.condition.Condition.ConditionType;
import nautilus.game.arcade.ArcadeManager;
import nautilus.game.arcade.GameType;
import nautilus.game.arcade.events.GameStateChangeEvent;
import nautilus.game.arcade.game.SoloGame;
import nautilus.game.arcade.game.games.monstermaze.MMMazes.MazePreset;
import nautilus.game.arcade.game.games.monstermaze.MazeBlockData.MazeBlock;
import nautilus.game.arcade.game.games.monstermaze.events.AbilityUseEvent;
import nautilus.game.arcade.game.games.monstermaze.events.MonsterBumpPlayerEvent;
import nautilus.game.arcade.game.games.monstermaze.kits.KitBodyBuilder;
import nautilus.game.arcade.game.games.monstermaze.kits.KitJumper;
import nautilus.game.arcade.game.games.monstermaze.kits.KitRepulsor;
import nautilus.game.arcade.game.games.monstermaze.kits.KitSlowball;
import nautilus.game.arcade.game.games.monstermaze.trackers.AbilityUseTracker;
import nautilus.game.arcade.game.games.monstermaze.trackers.FirstToSafepadTracker;
import nautilus.game.arcade.game.games.monstermaze.trackers.PilotTracker;
import nautilus.game.arcade.game.games.monstermaze.trackers.SnowmanHitTracker;
import nautilus.game.arcade.game.games.monstermaze.trackers.SurvivePast10thSafepadTracker;
import nautilus.game.arcade.game.modules.compass.CompassModule;
import nautilus.game.arcade.kit.Kit;
import nautilus.game.arcade.managers.chat.ChatStatData;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scoreboard.Team;

public class MonsterMaze extends SoloGame
{
	private Maze _maze;
	private EntityType _monsterType;
	
	private MazePreset _preset;
	
	private Location _center;
	
	private HashMap<Player, Long> _launched = new HashMap<Player, Long>();
	
	private static final int JUMP_POTION_AMPLIFIER = -10;
	private int _potionMult = JUMP_POTION_AMPLIFIER;
		
	@SuppressWarnings("unchecked")
	public MonsterMaze(ArcadeManager manager) 
	{
		super(manager, GameType.MonsterMaze,

				new Kit[]
						{
				new KitJumper(manager),
				new KitSlowball(manager),
				new KitBodyBuilder(manager),
				new KitRepulsor(manager)
						},

						new String[]
								{
				"Run over the maze and don't fall off,",
				"but make sure you avoid the monsters!",
				"Make it to a Safe Pad or be killed!"
								});

		DamagePvP = false;
		DamagePvE = false;
		
		DamageFall = false;

		HungerSet = 20;
		
		PrepareFreeze = false;
		
		HungerSet = 20;

		registerStatTrackers(
				new SnowmanHitTracker(this),
				new AbilityUseTracker(this),
				new FirstToSafepadTracker(this),
				new PilotTracker(this),
				new SurvivePast10thSafepadTracker(this)
				);
		//_maze = new SnowmanMaze(this, WorldData.GetDataLocs("GRAY")/*, WorldData.GetCustomLocs("103")*/);

		registerChatStats(//first to beacon
				new ChatStatData("Ninja", "Times Hit", true),
				new ChatStatData("Speed", "First to safe pad", true),
				BlankLine,
				new ChatStatData("kit", "Kit", true)
		);

		new CompassModule()
				.setGiveCompass(true)
				.setGiveCompassToSpecs(true)
				.setGiveCompassToAlive(false)
				.register(this);
	}
	
	public Maze getMaze()
	{
		return _maze;
	}

	@EventHandler
	public void GameStateChange(GameStateChangeEvent event)
	{
		if(event.GetState() == GameState.Live)
		{	
			_maze.removePlayerContainmentUnit();
			
			UtilTextMiddle.display("", C.cYellow + C.Bold + "Get to the Safe Pad!", 5, 40, 5);

			for (Team team : GetScoreboard().getScoreboard().getTeams())
				team.setCanSeeFriendlyInvisibles(true);
		}
		else if(event.GetState() == GameState.Recruit)
		{
			_monsterType = loadEntityType();
			_center = WorldData.GetDataLocs("ORANGE").get(0);
			_preset = MMMazes.getRandomMapPreset(_center, getMazeBlockData());
			_preset.build();
			_maze = new Maze(this, _preset);
			
			_maze.fillSpawn(150);
		}
	}
	
	private EntityType loadEntityType()
	{
		EntityType en = EntityType.SNOWMAN;
		
		for (String key : WorldData.GetAllCustomLocs().keySet())
		{
			try
			{
				if (key.startsWith("E"))
				{
					en = EntityType.valueOf(key.split(Pattern.quote("="))[1].toUpperCase());
				}
			}
			catch (Exception ex)
			{
				
			}
		}
		return en;
	}
	
	@SuppressWarnings("deprecation")
	private MazeBlockData getMazeBlockData()
	{
		MazeBlock top = null;
		MazeBlock mid = null;
		MazeBlock bottom = null;
		for (String key : WorldData.GetAllCustomLocs().keySet())
		{
			try
			{
				if (key.startsWith("B1"))
				{
					String[] typeData = key.split(Pattern.quote("="))[1].split(Pattern.quote(","));
					top = new MazeBlock(Material.getMaterial(Integer.valueOf(typeData[0])), Byte.valueOf(typeData[1]));
				}
				else if (key.startsWith("B2"))
				{
					String[] typeData = key.split(Pattern.quote("="))[1].split(Pattern.quote(","));
					mid = new MazeBlock(Material.getMaterial(Integer.valueOf(typeData[0])), Byte.valueOf(typeData[1]));
				}
				else if (key.startsWith("B3"))
				{
					String[] typeData = key.split(Pattern.quote("="))[1].split(Pattern.quote(","));
					bottom = new MazeBlock(Material.getMaterial(Integer.valueOf(typeData[0])), Byte.valueOf(typeData[1]));
				}
			}
			catch (Exception ex)
			{

			}
		}
		
		if (top != null && mid != null && bottom != null)
		{
			return new MazeBlockData(top, mid, bottom);
		}
		else
		{
			return new MazeBlockData(new MazeBlock(Material.QUARTZ_BLOCK), new MazeBlock(Material.QUARTZ_BLOCK, (byte) 2), new MazeBlock(Material.STEP, (byte) 15));
		}
	}
	
	private void setJumpsLeft(Player player, int jumps)
	{
		if (jumps <= 0)
		{
			player.getInventory().setItem(8, null);
		}
		else
		{
			player.getInventory().setItem(8, ItemStackFactory.Instance.CreateStack(Material.FEATHER, (byte)0, jumps, C.cYellow + C.Bold + jumps + " Jumps Remaining"));					
		}
	}
	
	@EventHandler
	public void jumpEvent(UpdateEvent event)
	{
		if(event.getType() != UpdateType.TICK) 
			return;
		
		if (!IsLive()) 
			return;
		
		for (Player p : GetPlayers(true))
		{
			if (!UtilInv.contains(p, "Jumps Remaining", Material.FEATHER, (byte) 0, 1) || p.getLocation().getY()-_center.getY() <= 0 || !Recharge.Instance.usable(p, "MM Player Jump") || isLaunched(p))
				continue;
			
			setJumpsLeft(p, p.getInventory().getItem(8).getAmount() - 1);
						
			p.playSound(p.getLocation(), Sound.CHICKEN_EGG_POP, 1.0f, 1.0f);
			
			Recharge.Instance.useForce(p, "MM Player Jump", 750);
			
			//Find blocks below a player
			for (int i = 0 ; i < 3 ; i++)
			{
				Block under = p.getLocation().clone().subtract(0, i, 0).getBlock();
				
				if (under.getType() == Material.AIR)
					continue;
								
				under.getWorld().playEffect(under.getLocation(), Effect.STEP_SOUND, UtilBlock.getStepSoundId(under));
				
				for (Block block : UtilBlock.getSurrounding(under, true))
				{
					if (block.getType() == Material.AIR)
						continue;
					
					block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, UtilBlock.getStepSoundId(block));
				}
				
				break;
			}
						
			Bukkit.getPluginManager().callEvent(new AbilityUseEvent(p));
		}
	}
	
	@EventHandler
	public void onDebug(PlayerCommandPreprocessEvent event)
	{
		if (!event.getPlayer().isOp())
			return;
		
		if (event.getMessage().toLowerCase().contains("/setmult "))
		{
			event.setCancelled(true);
			Integer mult = Integer.parseInt(event.getMessage().toLowerCase().replace("/setmult ", ""));
			_potionMult = mult;
			
			for (Player pl : GetPlayers(true))
			{
				Manager.GetCondition().Clean(pl);
			}
		}
	}
	
	@EventHandler
	public void onBreakJumper(InventoryClickEvent event)
	{
		if (!InProgress())
			return;
		if (!IsAlive(event.getWhoClicked()))
			return;
		
		event.setCancelled(true);
	}
	
	@EventHandler
	public void PotionEffects(UpdateEvent event)
	{
		if(event.getType() != UpdateType.TICK) return;
		if(!InProgress()) return;

		for(Player pl : GetPlayers(true))
		{
			if (IsLive() && (UtilInv.contains(pl, Material.FEATHER, (byte) 0, 1)))
			{
				while (Manager.GetCondition().GetActiveCondition(pl, ConditionType.JUMP) != null)
					Manager.GetCondition().GetActiveCondition(pl, ConditionType.JUMP).Expire();
			}
			else
			{
				if (!Manager.GetCondition().HasCondition(pl, ConditionType.JUMP, null))
					Manager.GetCondition().Factory().Jump("No jumping", pl, null, 9999999, _potionMult, true, false, false);
			}

//			if (!Manager.GetCondition().HasCondition(pl, ConditionType.INVISIBILITY, null))
//				Manager.GetCondition().Factory().Invisible("Hide players", pl, null, 9999999, 2, true, false, false);
		}
	}
	
	public EntityType getMonsterType()
	{
		return _monsterType;
	}
	
	@EventHandler
	public void onPlayerBump(MonsterBumpPlayerEvent event)
	{
		if (!IsLive())
			return;
		
		_launched.put(event.getPlayer(), System.currentTimeMillis());
	}
	
	@EventHandler
	public void tickBumps(UpdateEvent event)
	{
		if (event.getType() != UpdateType.TICK)
			return;
		
		if (!IsLive())
			return;
		
		HashMap<Player, Long> copy = new HashMap<Player, Long>();
		copy.putAll(_launched);
		
		for (Iterator<Player> iterator = copy.keySet().iterator() ; iterator.hasNext() ;)
		{
			Player player = iterator.next();
			
			if (player == null || !player.isOnline())
			{
				_launched.remove(player);
				continue;
			}
			
			if (UtilEnt.isGrounded(player) && UtilTime.elapsed(copy.get(player), 500))
			{
				_launched.remove(player);
				continue;
			}
			
			//If there are blocks surrounding the block it's on top of (if it's on the side of a block)
			if (player.getLocation().getY() == player.getLocation().getBlockY() && !UtilBlock.getInBoundingBox(player.getLocation().clone().add(1, -1, 1), player.getLocation().clone().subtract(1, 1, 1), true).isEmpty() && UtilTime.elapsed(copy.get(player), 500))
			{
				_launched.remove(player);
				continue;
			}
			
			//Time out
			if (UtilTime.elapsed(copy.get(player), 3000))
			{
				_launched.remove(player);
				continue;
			}
		}
	}
	
	public boolean isLaunched(Player player)
	{
		return _launched.containsKey(player);
	}
	
	@EventHandler
	public void onRegain(EntityRegainHealthEvent event)
	{
		if (event.getRegainReason() == RegainReason.SATIATED)
		{
			event.setCancelled(true);
		}
	}
	
	//Fix for eye of ender
	@EventHandler
	public void onDamage(EntityDamageEvent event)
	{
		if (event.getEntity() instanceof EnderCrystal)
		{
			event.setCancelled(true);
		}
	}
	
	@Override
	@EventHandler
	public void ScoreboardUpdate(UpdateEvent event)
	{
		if (event.getType() != UpdateType.FAST)
			return;

		if (GetTeamList().isEmpty())
			return;

		Scoreboard.reset();

		Scoreboard.writeNewLine();

		Scoreboard.write(C.cYellow + C.Bold + "Players");

		if (GetPlayers(true).size() > 5)
		{	
			Scoreboard.write(C.cWhite + GetPlayers(true).size() + " Alive");
		}
		else
		{
			for (Player p : GetPlayers(true))
			{
				Scoreboard.write(C.cWhite + p.getName());
			}
		}
		
		Scoreboard.writeNewLine();
		
		Scoreboard.write(C.cGreen + C.Bold + "Safe Pad");
		
		if (_maze.getSafePad() != null)
		{
			Scoreboard.write(C.cWhite + _maze.getPhaseTimer() + " Seconds");
		}
		else
		{
			Scoreboard.write("None");
		}
		
		Scoreboard.writeNewLine();
		
		Scoreboard.write(C.cGold + C.Bold + "Stage");
		
		Scoreboard.write(C.cWhite + getMaze().getCurrentSafePadCount());
		
		Scoreboard.draw();
	}
}
```


---

## File: `PerkJumpsDisplay.java`

```java
package nautilus.game.arcade.game.games.monstermaze.kits.perks;

import mineplex.core.common.util.C;
import mineplex.core.common.util.F;
import mineplex.core.common.util.UtilTextBottom;
import mineplex.core.updater.UpdateType;
import mineplex.core.updater.event.UpdateEvent;
import nautilus.game.arcade.kit.Perk;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

public class PerkJumpsDisplay extends Perk
{
	public PerkJumpsDisplay()
	{
		super("Display", new String[0], false);
	}

	@EventHandler
	public void onShow(UpdateEvent event)
	{
		if (event.getType() != UpdateType.FAST)
			return;
		
		for (Player player : Manager.GetGame().GetPlayers(true))
		{
			if (!Kit.HasKit(player))
				continue;
			
			if (player.getInventory().getItem(8) == null || player.getInventory().getItem(8).getAmount() <= 0)
			{
				UtilTextBottom.display(C.cWhite + "No jumps left", player);
			}
			else
			{
				UtilTextBottom.display(F.elem(player.getInventory().getItem(8).getAmount() + "") + C.cWhite + " jumps left", player);
			}
		}
	}
}
```


---

## File: `PerkRepulsor.java`

```java
package nautilus.game.arcade.game.games.monstermaze.kits.perks;

import java.util.HashMap;

import mineplex.core.common.util.F;
import mineplex.core.common.util.UtilAction;
import mineplex.core.common.util.UtilAlg;
import mineplex.core.common.util.UtilBlock;
import mineplex.core.common.util.UtilEnt;
import mineplex.core.common.util.UtilEvent;
import mineplex.core.common.util.UtilEvent.ActionType;
import mineplex.core.common.util.UtilFirework;
import mineplex.core.common.util.UtilInv;
import mineplex.core.common.util.UtilTime;
import mineplex.core.updater.event.UpdateEvent;
import nautilus.game.arcade.game.games.monstermaze.events.AbilityUseEvent;
import nautilus.game.arcade.game.games.monstermaze.events.EntityLaunchEvent;
import nautilus.game.arcade.kit.Perk;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.EntityEffect;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;

public class PerkRepulsor extends Perk
{
	private HashMap<Entity, Long> _launched = new HashMap<Entity, Long>();
	
	public PerkRepulsor()
	{
		super("Repulsor", new String[]
				{
				F.elem("Click") + " with Coal to use " + F.skill("Repulse") + ".",
				});
	}

	@EventHandler
	public void onRepulse(PlayerInteractEvent event)
	{	
		if (!Manager.GetGame().IsLive())
			return;
		
		if (!UtilEvent.isAction(event, ActionType.R)) 
			return;
		
		if (!UtilInv.IsItem(event.getItem(), Material.COAL, (byte) 0))
			return;
		
		Player player = event.getPlayer();
		
		if (!Kit.HasKit(player)) 
			return;
		
		event.setCancelled(true);
		
		UtilInv.remove(player, Material.COAL, (byte)0, 1);
		UtilInv.Update(player);
		
		UtilFirework.playFirework(player.getLocation(), Type.BALL_LARGE, Color.AQUA, false, false);
		
		for (Entity ent : UtilEnt.getInRadius(player.getLocation(), 6).keySet())
		{
			if (ent instanceof Player || !(ent instanceof LivingEntity))
				continue;
				
			ent.playEffect(EntityEffect.HURT);
			UtilAction.velocity(ent, UtilAlg.getTrajectory2d(player, ent), 1, true, 0, 0.8, 2, true);

			_launched.put(ent, System.currentTimeMillis());
			
			Bukkit.getPluginManager().callEvent(new EntityLaunchEvent(ent));
		}
		
		Bukkit.getPluginManager().callEvent(new AbilityUseEvent(player));
	}
	
	@EventHandler
	public void onUpdate(UpdateEvent event)
	{
		if (!Manager.GetGame().IsLive())
			return;
		
		HashMap<Entity, Long> copy = new HashMap<Entity, Long>();
		copy.putAll(_launched);
		
		for (Entity en : copy.keySet())
		{
			if (en == null || !en.isValid())
			{
				remove(en);
				continue;
			}
			
			if (en.isOnGround() && UtilTime.elapsed(copy.get(en), 500))
			{
				remove(en);
				continue;
			}
			
			//If there are blocks surrounding the block it's on top of (if it's on the side of a block)
			if (!UtilBlock.getInBoundingBox(en.getLocation().clone().add(1, -1, 1), en.getLocation().clone().subtract(1, 1, 1), true).isEmpty() && UtilTime.elapsed(copy.get(en), 500))
			{
				remove(en);
				continue;
			}
			
			if (UtilTime.elapsed(copy.get(en), 1500))
			{
				remove(en);
				continue;
			}
		}
	}
	
	private void remove(Entity en)
	{
		_launched.remove(en);
		en.remove();
		
		UtilFirework.playFirework(en.getLocation(), Type.BALL, Color.BLACK, false, false);
	}
}
```


---

## File: `PilotTracker.java`

```java
package nautilus.game.arcade.game.games.monstermaze.trackers;

import java.util.HashMap;

import mineplex.core.common.util.UtilEnt;
import mineplex.core.common.util.UtilTime;
import mineplex.core.updater.UpdateType;
import mineplex.core.updater.event.UpdateEvent;
import nautilus.game.arcade.events.GameStateChangeEvent;
import nautilus.game.arcade.game.Game.GameState;
import nautilus.game.arcade.game.games.monstermaze.MonsterMaze;
import nautilus.game.arcade.game.games.monstermaze.events.MonsterBumpPlayerEvent;
import nautilus.game.arcade.stats.StatTracker;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

public class PilotTracker extends StatTracker<MonsterMaze>
{
	/**
	 * @author Mysticate
	 */
	
	private HashMap<Player, Long> _launched = new HashMap<Player, Long>();
	
	public PilotTracker(MonsterMaze game)
	{
		super(game);
	}

	@EventHandler
	public void onSnowmanHit(MonsterBumpPlayerEvent event)
	{
		if (!getGame().IsLive())
			return;
		
		if (isLaunched(event.getPlayer()))
			return;
		
		setLaunched(event.getPlayer());
	}
	
	@EventHandler
	public void onUpdate(UpdateEvent event)
	{
		if (event.getType() != UpdateType.TICK)
			return;
		
		if (!getGame().IsLive())
			return;
		
		HashMap<Player, Long> copy = new HashMap<Player, Long>();
		copy.putAll(_launched);
		
		for (Player player : copy.keySet())
		{
			if (!isLaunched(player))
				continue;
			
			//Make sure the player isn't still on the ground after getting hit.
			if (!UtilTime.elapsed(_launched.get(player), 250))
					continue;
			
			if (player == null || !player.isOnline() || !getGame().IsAlive(player))
			{
				_launched.remove(player);				
				continue;
			}
			
			if (UtilEnt.isGrounded(player))
			{				
				_launched.remove(player);
								
				if (getGame().getMaze().isOnPad(player, false))
				{			
					addStat(player);
				}
				
				continue;
			}

			if (UtilTime.elapsed(_launched.get(player), 2000))
			{	
				_launched.remove(player);
				continue;
			}
		}
	}
	
	@EventHandler
	public void clearLaunched(GameStateChangeEvent event)
	{
		if (event.GetState() != GameState.End)
			return;
		
		_launched.clear();
	}
	
	private boolean isLaunched(Player player)
	{
		return _launched.containsKey(player);
	}
	
	private void setLaunched(Player player)
	{
		_launched.put(player, System.currentTimeMillis());
	}
	
	private void addStat(Player player)
	{
		addStat(player, "Pilot", 1, true, false);
	}
}
```


---

## File: `SafePad.java`

```java
package nautilus.game.arcade.game.games.monstermaze;

import java.util.ArrayList;
import java.util.Iterator;

import mineplex.core.common.util.MapUtil;
import mineplex.core.common.util.UtilAlg;
import mineplex.core.common.util.UtilPlayer;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutBlockBreakAnimation;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class SafePad
{
	private MonsterMaze Host;
//	private Maze _maze;
	
	private Location  _loc;
	
	private int _decayCount = 11;
	
	private ArrayList<SafePadBlock> _blocks = new ArrayList<SafePadBlock>();
	
	public SafePad(MonsterMaze host, Maze maze, Location loc)
	{
		Host = host;
//		_maze = maze;
		
		_loc = loc;
		
		//beacon surface
		for (int x = -2 ; x < 3 ; x++)
		{
			for (int z = -2 ; z < 3 ; z++)
			{
				if (x == 0 && z == 0)
					continue;
				
				_blocks.add(new SafePadBlock(loc.clone().add(x, 0, z), Material.STAINED_CLAY, (byte) 5));
			}
		}
		
		// beacon
		_blocks.add(new SafePadBlock(loc.clone().add(0, 0, 0), Material.BEACON, (byte)0));
		
		// beacon base
		for (int x = -1 ; x < 2 ; x++)
		{
			for (int z = -1 ; z < 2 ; z++)
			{
				_blocks.add(new SafePadBlock(loc.clone().add(x, -1, z), Material.IRON_BLOCK, (byte)0));
			}
			
			//Stairs
			_blocks.add(new SafePadBlock(loc.clone().add(x, -1, 2), Material.QUARTZ_STAIRS, (byte) 7));
			_blocks.add(new SafePadBlock(loc.clone().add(x, -1, -2), Material.QUARTZ_STAIRS, (byte) 6));
			_blocks.add(new SafePadBlock(loc.clone().add(2, -1, x), Material.QUARTZ_STAIRS, (byte) 5));
			_blocks.add(new SafePadBlock(loc.clone().add(-2, -1, x), Material.QUARTZ_STAIRS, (byte) 4));
		}
		
		// corner blocks
		_blocks.add(new SafePadBlock(loc.clone().add(2, -1, 2), Material.QUARTZ_BLOCK, (byte) 1));
		_blocks.add(new SafePadBlock(loc.clone().add(-2, -1, 2), Material.QUARTZ_BLOCK, (byte) 1));
		_blocks.add(new SafePadBlock(loc.clone().add(2, -1, -2), Material.QUARTZ_BLOCK, (byte) 1));
		_blocks.add(new SafePadBlock(loc.clone().add(-2, -1, -2), Material.QUARTZ_BLOCK, (byte) 1));
				
		// air slabs
		for (int x = -2 ; x < 3 ; x++)
		{
			for (int z = -2 ; z < 3 ; z++)
			{
				_blocks.add(new SafePadBlock(loc.clone().add(x, -2, z), Material.AIR, (byte) 0));
			}
		}
	}
	
	public class SafePadBlock
	{
		private Material _origM;
		private byte _origD;
		
		private Material _tempMat;
		private byte _tempData;
		
		Location _loc;
		
		@SuppressWarnings("deprecation")
		public SafePadBlock(Location loc, Material newMat, byte newData)
		{
			_origM = loc.getBlock().getType();
			_origD = loc.getBlock().getData();
			
			_tempMat = newMat;
			_tempData = newData;
			
			_loc = loc;
		}
		
		@SuppressWarnings("deprecation")
		public void setMaterial(Material m)
		{
			_tempMat = m;
			MapUtil.QuickChangeBlockAt(_loc, _tempMat.getId(), _tempData);
		}
		
		@SuppressWarnings("deprecation")
		public void setData(byte b) // for glass changing
		{
			_tempData = b;
			MapUtil.QuickChangeBlockAt(_loc, _tempMat.getId(), _tempData);
		}
		
		public Material getBlockMaterial()
		{
			return _loc.getBlock().getType();
		}
		
		@SuppressWarnings("deprecation")
		public byte getBlockData()
		{
			return _loc.getBlock().getData();
		}
		
		public Location getLocation()
		{
			return _loc;
		}
		
		@SuppressWarnings("deprecation")
		public void build()
		{
			MapUtil.QuickChangeBlockAt(_loc, _tempMat.getId(), _tempData);
//			_loc.getWorld().playEffect(_loc, Effect.STEP_SOUND, _loc.getBlock().getTypeId());
		}
		
		@SuppressWarnings("deprecation")
		public void restore()
		{
			MapUtil.QuickChangeBlockAt(_loc, _origM.getId(), _origD);
//			_loc.getWorld().playEffect(_loc, Effect.STEP_SOUND, _loc.getBlock().getTypeId());
		}
	}
	
	public void build()
	{
		for(SafePadBlock spb : _blocks)
		{
			spb.build();
		}
	}
	
	private void setBreakData(byte newData)
	{
		for (SafePadBlock spb : _blocks)
		{
			if (spb.getBlockMaterial() != Material.STAINED_CLAY) 
				continue;
			
			spb.setData(newData);
		}
	}
	
	public void turnOffBeacon()
	{
		for (SafePadBlock bl : _blocks)
		{
			if (bl.getBlockMaterial() == Material.IRON_BLOCK)
				bl.setMaterial(Material.QUARTZ_BLOCK);
			
			if (bl.getBlockMaterial() == Material.BEACON)
			{
				bl.setMaterial(Material.STAINED_CLAY);
				bl.setData((byte) 5);
			}
		}
	}

	public boolean decay()
	{
		if (_decayCount == -1)
			return true;

		_decayCount--;

		if(_decayCount == 10)
		{
			setBreakData((byte)5); // green
			setCrackedProgress(1);
		}
		else if(_decayCount == 9)
		{
			setCrackedProgress(2);
		}
		else if(_decayCount == 8)
		{
			setBreakData((byte)4); // yellow
			setCrackedProgress(3);
		}
		else if(_decayCount == 7)
		{
			setCrackedProgress(4);
		}
		else if(_decayCount == 6)
		{
			setBreakData((byte)1); // orange
			setCrackedProgress(5);
		}
		else if(_decayCount == 5)
		{
			setCrackedProgress(6);
		}
		else if(_decayCount == 4)
		{
			setBreakData((byte)14); // red
			setCrackedProgress(7);
		}
		else if(_decayCount == 3)
		{
			setCrackedProgress(8);
		}
		else if(_decayCount == 2)
		{
			setCrackedProgress(9);
		}
		else if(_decayCount == 1)
		{
			_decayCount = -1;
			
			setCrackedProgress(-1);

			destroySurface();
			destroyBase();

			return true;
		}
		return false;
	}

	private void setCrackedProgress(int progress)
	{
		int i = 0;
		Iterator<SafePadBlock> iter = _blocks.iterator();
		ArrayList<Packet> packets = new ArrayList<Packet>();
		while(iter.hasNext())
		{
			SafePadBlock spb = iter.next();
			if (!spb.getLocation().getBlock().getType().equals(Material.STAINED_CLAY)) 
				continue;
						
			i++;
			
			Packet packet = new PacketPlayOutBlockBreakAnimation(i, new BlockPosition(spb.getLocation().getBlockX(), spb.getLocation().getBlockY(), spb.getLocation().getBlockZ()), progress);
			packets.add(packet);
		}
		for(Player p : Host.GetPlayers(false))
		{
			Packet[] pcks = new Packet[packets.size()];
			packets.toArray(pcks);
			UtilPlayer.sendPacket(p, pcks);
		}
		/*for(SafePadBlock spb : _blocks)
		{
			if(!spb.getLocation().getBlock().getType().equals(Material.STAINED_GLASS)) continue;
			
			UtilParticle.PlayParticle(ParticleType.BLOCK_DUST.getParticle(Material.STAINED_GLASS, spb.getBlockData()), spb.getLocation(), 0.5f, 0.5f, 0.5f, 0.05f, 8, ViewDist.NORMAL, Host.GetPlayers(false).toArray(new Player[Host.GetPlayers(false).size()]));
			Packet packet = new PacketPlayOutBlockBreakAnimation(1, spb.getLocation().getBlockX(), spb.getLocation().getBlockY(), spb.getLocation().getBlockZ(), progress);
			for(Player p : Host.GetPlayers(false))
			{
				UtilPlayer.sendPacket(p, packet);
			}
		}*/
	}

	public void destroyBase()
	{
		for (final SafePadBlock bl : _blocks)
		{			
			if (bl.getBlockMaterial() == Material.QUARTZ_BLOCK || bl.getBlockMaterial() == Material.QUARTZ_STAIRS || bl.getBlockMaterial() == Material.AIR)
			{
				bl.restore();
			}
		}
	}
	
	public void destroySurface()
	{
		for (final SafePadBlock bl : _blocks)
		{			
			if (bl.getBlockMaterial() == Material.STAINED_CLAY)
			{		
				bl.restore();
			}
		}
	}

	public Location getLocation()
	{
		return _loc;
	}
	
	public boolean isOn(Entity e)
	{
		return UtilAlg.inBoundingBox(e.getLocation(), getLocation().clone().add(2.999, 5, 2), getLocation().clone().add(-2, 0, -2.999));
	}
}
```


---

## File: `SafepadBuildEvent.java`

```java
package nautilus.game.arcade.game.games.monstermaze.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SafepadBuildEvent extends Event
{
	/**
	 * @author Mysticate
	 */
	
	private static final HandlerList _handlers = new HandlerList();
	
	private static HandlerList getHandlerList()
	{
		return _handlers;
	}
	
	@Override
	public HandlerList getHandlers()
	{
		return getHandlerList();
	}
}
```


---

## File: `SnowmanHitTracker.java`

```java
package nautilus.game.arcade.game.games.monstermaze.trackers;

import java.util.ArrayList;
import java.util.List;

import nautilus.game.arcade.events.GameStateChangeEvent;
import nautilus.game.arcade.game.Game.GameState;
import nautilus.game.arcade.game.games.monstermaze.MonsterMaze;
import nautilus.game.arcade.game.games.monstermaze.events.MonsterBumpPlayerEvent;
import nautilus.game.arcade.stats.StatTracker;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public class SnowmanHitTracker extends StatTracker<MonsterMaze>
{
	/**
	 * @author Mysticate
	 */
	
	private List<String> _out = new ArrayList<String>();
	
	public SnowmanHitTracker(MonsterMaze game)
	{
		super(game);
	}

	@EventHandler
	public void onSnowmanHit(MonsterBumpPlayerEvent event)
	{
		if (!getGame().IsLive())
			return;
		
		if (isOut(event.getPlayer()))
			return;
		
		setOut(event.getPlayer());
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onGameEnd(GameStateChangeEvent event)
	{
		if (event.GetState() != GameState.End)
			return;
		
		if (getGame().getWinners() == null)
			return;
		
		for (Player player : getGame().getWinners())
		{
			if (isOut(player))
				continue;
			
			addStat(player);
		}
		
		_out.clear();
	}
	
	private boolean isOut(Player player)
	{
		for (String out : _out)
		{
			if (out.equalsIgnoreCase(player.getName()))
			{
				return true;
			}
		}
		return false;
	}
	
	private void setOut(Player player)
	{
		if (isOut(player))
			return;
		
		_out.add(player.getName());
	}
	
	private void addStat(Player player)
	{
		addStat(player, "Ninja", 1, true, false);
	}
}
```


---

## File: `SurvivePast10thSafepadTracker.java`

```java
package nautilus.game.arcade.game.games.monstermaze.trackers;

import nautilus.game.arcade.game.games.monstermaze.MonsterMaze;
import nautilus.game.arcade.game.games.monstermaze.events.SafepadBuildEvent;
import nautilus.game.arcade.stats.StatTracker;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

public class SurvivePast10thSafepadTracker extends StatTracker<MonsterMaze>
{
	/**
	 * @author Mysticate
	 */
			
	public SurvivePast10thSafepadTracker(MonsterMaze game)
	{
		super(game);
	}

	@EventHandler
	public void onSafepadBuild(SafepadBuildEvent event)
	{
		if (!getGame().IsLive())
			return;
			
		if (getGame().getMaze().getCurrentSafePadCount() > 10)
		{
			for (Player player : getGame().GetPlayers(true))
			{
				addStat(player);
			}
		}		
	}
	
	private void addStat(Player player)
	{
		addStat(player, "ToughCompetition", 1, true, false);
	}
}
```


---

## File: `trackers\AbilityUseTracker.java`

```java
package nautilus.game.arcade.game.games.monstermaze.trackers;

import java.util.ArrayList;
import java.util.List;

import nautilus.game.arcade.events.GameStateChangeEvent;
import nautilus.game.arcade.game.Game.GameState;
import nautilus.game.arcade.game.games.monstermaze.MonsterMaze;
import nautilus.game.arcade.game.games.monstermaze.events.AbilityUseEvent;
import nautilus.game.arcade.stats.StatTracker;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public class AbilityUseTracker extends StatTracker<MonsterMaze>
{
	/**
	 * @author Mysticate
	 */
	
	private List<String> _out = new ArrayList<String>();
	
	public AbilityUseTracker(MonsterMaze game)
	{
		super(game);
	}

	@EventHandler
	public void onAbilityUse(AbilityUseEvent event)
	{
		if (!getGame().IsLive())
			return;
		
		if (isOut(event.getPlayer()))
			return;
		
		setOut(event.getPlayer());
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onGameEnd(GameStateChangeEvent event)
	{
		if (event.GetState() != GameState.End)
			return;
		
		if (getGame().getWinners() == null)
			return;
		
		for (Player player : getGame().getWinners())
		{
			if (isOut(player))
				continue;
			
			addStat(player);
		}
		
		_out.clear();
	}
	
	private boolean isOut(Player player)
	{
		for (String out : _out)
		{
			if (out.equalsIgnoreCase(player.getName()))
			{
				return true;
			}
		}
		return false;
	}
	
	private void setOut(Player player)
	{
		if (isOut(player))
			return;
		
		_out.add(player.getName());
	}
	
	private void addStat(Player player)
	{
		addStat(player, "Hard Mode", 1, true, false);
	}
}
```


---

## File: `trackers\FirstToSafepadTracker.java`

```java
package nautilus.game.arcade.game.games.monstermaze.trackers;

import nautilus.game.arcade.game.games.monstermaze.MonsterMaze;
import nautilus.game.arcade.game.games.monstermaze.events.FirstToSafepadEvent;
import nautilus.game.arcade.stats.StatTracker;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

public class FirstToSafepadTracker extends StatTracker<MonsterMaze>
{
	/**
	 * @author Mysticate
	 */
		
	public FirstToSafepadTracker(MonsterMaze game)
	{
		super(game);
	}

	@EventHandler
	public void onSafepadFirst(FirstToSafepadEvent event)
	{
		if (!getGame().IsLive())
			return;
		
		addStat(event.getPlayer());
	}
	
	private void addStat(Player player)
	{
		addStat(player, "Speed", 1, false, false);
	}
}
```


---

## File: `trackers\PilotTracker.java`

```java
package nautilus.game.arcade.game.games.monstermaze.trackers;

import java.util.HashMap;

import mineplex.core.common.util.UtilEnt;
import mineplex.core.common.util.UtilTime;
import mineplex.core.updater.UpdateType;
import mineplex.core.updater.event.UpdateEvent;
import nautilus.game.arcade.events.GameStateChangeEvent;
import nautilus.game.arcade.game.Game.GameState;
import nautilus.game.arcade.game.games.monstermaze.MonsterMaze;
import nautilus.game.arcade.game.games.monstermaze.events.MonsterBumpPlayerEvent;
import nautilus.game.arcade.stats.StatTracker;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

public class PilotTracker extends StatTracker<MonsterMaze>
{
	/**
	 * @author Mysticate
	 */
	
	private HashMap<Player, Long> _launched = new HashMap<Player, Long>();
	
	public PilotTracker(MonsterMaze game)
	{
		super(game);
	}

	@EventHandler
	public void onSnowmanHit(MonsterBumpPlayerEvent event)
	{
		if (!getGame().IsLive())
			return;
		
		if (isLaunched(event.getPlayer()))
			return;
		
		setLaunched(event.getPlayer());
	}
	
	@EventHandler
	public void onUpdate(UpdateEvent event)
	{
		if (event.getType() != UpdateType.TICK)
			return;
		
		if (!getGame().IsLive())
			return;
		
		HashMap<Player, Long> copy = new HashMap<Player, Long>();
		copy.putAll(_launched);
		
		for (Player player : copy.keySet())
		{
			if (!isLaunched(player))
				continue;
			
			//Make sure the player isn't still on the ground after getting hit.
			if (!UtilTime.elapsed(_launched.get(player), 250))
					continue;
			
			if (player == null || !player.isOnline() || !getGame().IsAlive(player))
			{
				_launched.remove(player);				
				continue;
			}
			
			if (UtilEnt.isGrounded(player))
			{				
				_launched.remove(player);
								
				if (getGame().getMaze().isOnPad(player, false))
				{			
					addStat(player);
				}
				
				continue;
			}

			if (UtilTime.elapsed(_launched.get(player), 2000))
			{	
				_launched.remove(player);
				continue;
			}
		}
	}
	
	@EventHandler
	public void clearLaunched(GameStateChangeEvent event)
	{
		if (event.GetState() != GameState.End)
			return;
		
		_launched.clear();
	}
	
	private boolean isLaunched(Player player)
	{
		return _launched.containsKey(player);
	}
	
	private void setLaunched(Player player)
	{
		_launched.put(player, System.currentTimeMillis());
	}
	
	private void addStat(Player player)
	{
		addStat(player, "Pilot", 1, true, false);
	}
}
```


---

## File: `trackers\SnowmanHitTracker.java`

```java
package nautilus.game.arcade.game.games.monstermaze.trackers;

import java.util.ArrayList;
import java.util.List;

import nautilus.game.arcade.events.GameStateChangeEvent;
import nautilus.game.arcade.game.Game.GameState;
import nautilus.game.arcade.game.games.monstermaze.MonsterMaze;
import nautilus.game.arcade.game.games.monstermaze.events.MonsterBumpPlayerEvent;
import nautilus.game.arcade.stats.StatTracker;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public class SnowmanHitTracker extends StatTracker<MonsterMaze>
{
	/**
	 * @author Mysticate
	 */
	
	private List<String> _out = new ArrayList<String>();
	
	public SnowmanHitTracker(MonsterMaze game)
	{
		super(game);
	}

	@EventHandler
	public void onSnowmanHit(MonsterBumpPlayerEvent event)
	{
		if (!getGame().IsLive())
			return;
		
		if (isOut(event.getPlayer()))
			return;
		
		setOut(event.getPlayer());
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onGameEnd(GameStateChangeEvent event)
	{
		if (event.GetState() != GameState.End)
			return;
		
		if (getGame().getWinners() == null)
			return;
		
		for (Player player : getGame().getWinners())
		{
			if (isOut(player))
				continue;
			
			addStat(player);
		}
		
		_out.clear();
	}
	
	private boolean isOut(Player player)
	{
		for (String out : _out)
		{
			if (out.equalsIgnoreCase(player.getName()))
			{
				return true;
			}
		}
		return false;
	}
	
	private void setOut(Player player)
	{
		if (isOut(player))
			return;
		
		_out.add(player.getName());
	}
	
	private void addStat(Player player)
	{
		addStat(player, "Ninja", 1, true, false);
	}
}
```


---

## File: `trackers\SurvivePast10thSafepadTracker.java`

```java
package nautilus.game.arcade.game.games.monstermaze.trackers;

import nautilus.game.arcade.game.games.monstermaze.MonsterMaze;
import nautilus.game.arcade.game.games.monstermaze.events.SafepadBuildEvent;
import nautilus.game.arcade.stats.StatTracker;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

public class SurvivePast10thSafepadTracker extends StatTracker<MonsterMaze>
{
	/**
	 * @author Mysticate
	 */
			
	public SurvivePast10thSafepadTracker(MonsterMaze game)
	{
		super(game);
	}

	@EventHandler
	public void onSafepadBuild(SafepadBuildEvent event)
	{
		if (!getGame().IsLive())
			return;
			
		if (getGame().getMaze().getCurrentSafePadCount() > 10)
		{
			for (Player player : getGame().GetPlayers(true))
			{
				addStat(player);
			}
		}		
	}
	
	private void addStat(Player player)
	{
		addStat(player, "ToughCompetition", 1, true, false);
	}
}
```
