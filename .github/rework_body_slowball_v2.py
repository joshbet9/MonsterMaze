from pathlib import Path
import re, subprocess

KIT=[Path('1.8/MonsterMazeStandalone/src/main/java/me/monstermaze/kit/KitManager.java'),Path('1.21/MonsterMazeStandalone/src/main/java/me/monstermaze/kit/KitManager.java')]
MOB=[Path('1.8/MonsterMazeStandalone/src/main/java/me/monstermaze/entity/MonsterManager.java'),Path('1.21/MonsterMazeStandalone/src/main/java/me/monstermaze/entity/MonsterManager.java')]

def once(s,a,b,label):
    n=s.count(a)
    if n!=1: raise RuntimeError(f'{label}: expected 1 match, got {n}')
    return s.replace(a,b)

BODY=r'''    // -------------------- Body Builder secondary: Body Rush --------------------
    // QOL-mode only. Two apples = two activations; each activation consumes one apple.
    // Body Rush lasts 10 seconds and each real mob contact removes exactly 2 seconds.

    @EventHandler
    public void onBodyRush(PlayerInteractEvent event) {
        if (game.getState() != GameState.LIVE) return;
        if (!game.qolEnabled()) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (getKit(player) != KitType.BODY_BUILDER) return;
        if (!game.getAlivePlayers().contains(player)) return;

        ItemStack hand = player.getItemInHand();
        if (hand == null || hand.getType() != Material.APPLE) return;
        long now = System.currentTimeMillis();
        Long until = bodyRushUntil.get(player.getUniqueId());
        if (until != null && until.longValue() > now) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (hand.getAmount() <= 1) player.setItemInHand(null);
        else { hand.setAmount(hand.getAmount()-1); player.setItemInHand(hand); }
        bodyRushUntil.put(player.getUniqueId(), now + BODY_RUSH_DURATION_MS);
        bodyRushHitFeedback.remove(player.getUniqueId());
        bodyRushVisual(player);
        TextUtil.actionBar(player, ChatColor.RED + ChatColor.BOLD.toString() + "BODY RUSH 10.0s");
        Bukkit.getPluginManager().callEvent(new AbilityUseEvent(player));
    }

    public boolean isBodyRushActive(Player player) {
        if (!game.qolEnabled()) return false;
        Long until=bodyRushUntil.get(player.getUniqueId());
        if (until==null) return false;
        if (until.longValue()<=System.currentTimeMillis()) { bodyRushUntil.remove(player.getUniqueId()); return false; }
        return true;
    }

    /** A real mob contact removes exactly 2 seconds from Body Rush. */
    public int consumeBodyRushUse(Player player) {
        UUID id=player.getUniqueId();
        long now=System.currentTimeMillis();
        Long until=bodyRushUntil.get(id);
        if (until==null || until.longValue()<=now) { bodyRushUntil.remove(id); return 0; }
        long remaining=until.longValue()-2000L;
        bodyRushHitFeedback.put(id,now+750L);
        bodyRushImpactVisual(player);
        if (remaining<=now) { bodyRushUntil.remove(id); return 0; }
        bodyRushUntil.put(id,remaining);
        return (int)Math.max(0L,remaining-now);
    }

    private static ItemStack bodyRushItem(int apples) {
        ItemStack item=new ItemStack(Material.APPLE,Math.max(1,apples));
        ItemMeta meta=item.getItemMeta();
        meta.setDisplayName(ChatColor.RED+""+ChatColor.BOLD+"Body Rush");
        meta.setLore(java.util.Arrays.asList(ChatColor.GRAY+"Right Click to activate.",ChatColor.GRAY+"10 seconds of mob-contact immunity.",ChatColor.GRAY+"Each contact removes 2 seconds.",ChatColor.RED+"Activations: "+apples));
        item.setItemMeta(meta);
        return item;
    }

    private void bodyRushVisual(Player player) { /* version-specific visual */ }
    private void bodyRushImpactVisual(Player player) { /* version-specific visual */ }

    private void tickAbilityBars() {
        if (game.getState()!=GameState.LIVE) return;
        long now=System.currentTimeMillis();
        for(Player p:game.getAlivePlayers()) {
            UUID id=p.getUniqueId();
            Long body=bodyRushUntil.get(id);
            if(body!=null && body.longValue()>now) {
                double secs=(body.longValue()-now)/1000.0;
                Long fb=bodyRushHitFeedback.get(id);
                String suffix=fb!=null && fb.longValue()>now ? "  "+ChatColor.YELLOW+"-2.0s" : "";
                TextUtil.actionBar(p,ChatColor.RED+ChatColor.BOLD.toString()+"BODY RUSH "+String.format(java.util.Locale.US,"%.1fs",secs)+suffix);
                continue;
            }
            if(body!=null) bodyRushUntil.remove(id);
            bodyRushHitFeedback.remove(id);
            if(getKit(p)!=KitType.SLOWBALL) continue;
            Long frozen=cryoFrozenUntil.get(id);
            if(frozen!=null && frozen.longValue()>now) {
                TextUtil.actionBar(p,ChatColor.AQUA+ChatColor.BOLD.toString()+"FROZEN "+String.format(java.util.Locale.US,"%.1fs",(frozen.longValue()-now)/1000.0));
                continue;
            }
            cryoFrozenUntil.remove(id);
            Long last=cryoCooldown.get(id);
            if(last==null) TextUtil.actionBar(p,ChatColor.AQUA+ChatColor.BOLD.toString()+"CRYO BLITZ READY");
            else {
                long left=(last.longValue()+CRYO_COOLDOWN_MS)-now;
                if(left<=0) { cryoCooldown.remove(id); TextUtil.actionBar(p,ChatColor.AQUA+ChatColor.BOLD.toString()+"CRYO BLITZ READY"); }
                else TextUtil.actionBar(p,ChatColor.AQUA+ChatColor.BOLD.toString()+"CRYO BLITZ "+String.format(java.util.Locale.US,"%.1fs",left/1000.0));
            }
        }
    }

    // -------------------- Slowballer secondary: Cryo Blitz --------------------'''

for p in KIT:
    s=p.read_text()
    s=once(s,'private final Map<UUID, Integer> bodyRushUses = new HashMap<UUID, Integer>();','private final Map<UUID, Long> bodyRushUntil = new HashMap<UUID, Long>();\n    private final Map<UUID, Long> bodyRushHitFeedback = new HashMap<UUID, Long>();\n    private final Map<UUID, Long> cryoFrozenUntil = new HashMap<UUID, Long>();',f'{p} state')
    s=s.replace('bodyRushUses','bodyRushUntil')
    s=s.replace('private static final int BODY_RUSH_MAX_USES = 5;','private static final long BODY_RUSH_DURATION_MS = 10000L;')
    s=s.replace('CRYO_COOLDOWN_MS = 60000','CRYO_COOLDOWN_MS = 30000')
    s=s.replace('CRYO_RADIUS = 5','CRYO_RADIUS = 6')
    s=once(s,'                jumpEvent();\n                repulseCleanup();','                jumpEvent();\n                repulseCleanup();\n                tickAbilityBars();',f'{p} ticker')
    s,n=re.subn(r'    // -------------------- Body Builder secondary: Body Rush --------------------.*?    // -------------------- Slowballer secondary: Cryo Blitz --------------------',BODY,s,count=1,flags=re.S)
    if n!=1: raise RuntimeError(f'{p} Body Rush block')
    s=s.replace('player.getInventory().setItem(0, bodyRushItem(BODY_RUSH_MAX_USES));','player.getInventory().setItem(0, bodyRushItem(2));')
    s=s.replace('bodyRushUntil.clear();','bodyRushUntil.clear();\n        bodyRushHitFeedback.clear();\n        cryoFrozenUntil.clear();')
    s=s.replace('bodyRushUntil.remove(player.getUniqueId());\n        cryoCooldown.remove(player.getUniqueId());','bodyRushUntil.remove(player.getUniqueId());\n        bodyRushHitFeedback.remove(player.getUniqueId());\n        cryoFrozenUntil.remove(player.getUniqueId());\n        cryoCooldown.remove(player.getUniqueId());')
    if '1.21/' in str(p):
        s=s.replace('player.getItemInHand()','player.getInventory().getItemInMainHand()').replace('player.setItemInHand(null)','player.getInventory().setItemInMainHand(null)').replace('player.setItemInHand(hand)','player.getInventory().setItemInMainHand(hand)')
        s=s.replace('private void bodyRushVisual(Player player) { /* version-specific visual */ }','private void bodyRushVisual(Player player) { player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.25f); player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0,1,0), 24, .35,.55,.35,.03); }')
        s=s.replace('private void bodyRushImpactVisual(Player player) { /* version-specific visual */ }','private void bodyRushImpactVisual(Player player) { player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 1.0f, 1.35f); player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0,1,0), 12, .25,.35,.25,.05); }')
        cryo='private void cryoVisual(Player player) { player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, .7f); player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0,1,0), 80, 3,1,3,.04); }'
    else:
        s=s.replace('private void bodyRushVisual(Player player) { /* version-specific visual */ }','private void bodyRushVisual(Player player) { player.playSound(player.getLocation(), Sound.SUCCESSFUL_HIT, 1.0f, 1.25f); player.getWorld().playEffect(player.getLocation(), Effect.MOBSPAWNER_FLAMES, 0); }')
        s=s.replace('private void bodyRushImpactVisual(Player player) { /* version-specific visual */ }','private void bodyRushImpactVisual(Player player) { player.playSound(player.getLocation(), Sound.HURT_FLESH, 1.0f, 1.35f); player.getWorld().playEffect(player.getLocation(), Effect.MOBSPAWNER_FLAMES, 0); }')
        cryo='private void cryoVisual(Player player) { player.playSound(player.getLocation(), Sound.GLASS_BREAK, 1.0f, .7f); player.getWorld().playEffect(player.getLocation(), Effect.SNOWBALL_BREAK, 0); }'
    s=s.replace('    private ItemStack slowballItem(UUID playerId, int amount) {','    '+cryo+'\n\n    private ItemStack slowballItem(UUID playerId, int amount) {',1)
    marker='        TextUtil.title(player, "", ChatColor.AQUA + "" + ChatColor.BOLD + "Cryo Blitz!"\n                        + ChatColor.WHITE + " " + frozenCount + " mob(s) frozen", 5, 30, 5);'
    if marker not in s: raise RuntimeError(f'{p} Cryo marker')
    s=s.replace(marker,marker+'\n        if (frozenCount > 0) cryoFrozenUntil.put(player.getUniqueId(), now + CRYO_FREEZE_MS);\n        cryoVisual(player);',1)
    p.write_text(s)

for p in MOB:
    s=p.read_text()
    if '1.21/' in str(p):
        s=once(s,'        for (Player player : players) {\n            if (!canBump(player) || game.isOnAnyPad(player)) continue;','        for (Player player : players) {\n            if (game.isOnAnyPad(player)) continue;\n            me.monstermaze.kit.KitManager km = game.getKitManager();\n            boolean bodyRush = km != null && km.isBodyRushActive(player);\n            if (!bodyRush && !canBump(player)) continue;',f'{p} outer')
        s=once(s,'                LivingEntity ent = mobs[i];\n                markBump(player);\n\n                me.monstermaze.kit.KitManager km = game.getKitManager();\n                if (km != null && km.isBodyRushActive(player)) {','                LivingEntity ent = mobs[i];\n\n                if (bodyRush) {',f'{p} contact')
    else:
        s=once(s,'        for (Player player : players) {\n            if (!canBump(player)) continue;\n            if (game.isOnAnyPad(player)) continue;','        for (Player player : players) {\n            if (game.isOnAnyPad(player)) continue;\n            me.monstermaze.kit.KitManager km = game.getKitManager();\n            boolean bodyRush = km != null && km.isBodyRushActive(player);\n            if (!bodyRush && !canBump(player)) continue;',f'{p} outer')
        s=once(s,'                LivingEntity ent = mobs[i];\n                markBump(player);\n\n                // Body Builder Body Rush (QOL only): a contacting mob is deflected away like a\n                // Repulsor launch — no knockback, no damage, full immunity — and consumes one use.\n                me.monstermaze.kit.KitManager km = game.getKitManager();\n                if (km != null && km.isBodyRushActive(player)) {','                LivingEntity ent = mobs[i];\n\n                if (bodyRush) {',f'{p} contact')
    s=once(s,'                // Anti-bonk, ping-independent: lift the player ABOVE the maze floor before applying','                // Normal mob contact retains the original one-second bump cooldown.\n                markBump(player);\n\n                // Anti-bonk, ping-independent: lift the player ABOVE the maze floor before applying',f'{p} normal mark')
    p.write_text(s)

for p in KIT:
    s=p.read_text()
    assert 'BODY_RUSH_MAX_USES' not in s and 'bodyRushUses' not in s

subprocess.run(['git','config','user.name','MonsterMaze Bot'],check=True)
subprocess.run(['git','config','user.email','monstermaze-bot@users.noreply.github.com'],check=True)
for f in ['.github/rework_body_slowball.py','.github/rework_body_slowball_v2.py','.github/workflows/apply-body-slowball-rework.yml','.github/workflows/run-body-slowball-rework.yml','.github/workflows/run-body-slowball-rework-v2.yml']:
    Path(f).unlink(missing_ok=True)
subprocess.run(['git','add']+[str(x) for x in KIT+MOB]+['.github'],check=True)
subprocess.run(['git','commit','-m','Apply Body Builder and Slowballer rework'],check=True)
subprocess.run(['git','push','origin','main'],check=True)
