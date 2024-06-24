package me.mykindos.betterpvp.champions.weapons.impl.legendaries;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.mykindos.betterpvp.champions.Champions;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.combat.weapon.types.ChannelWeapon;
import me.mykindos.betterpvp.core.combat.weapon.types.InteractWeapon;
import me.mykindos.betterpvp.core.combat.weapon.types.LegendaryWeapon;
import me.mykindos.betterpvp.core.energy.EnergyHandler;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.*;
import me.mykindos.betterpvp.core.utilities.model.ProgressBar;
import me.mykindos.betterpvp.core.utilities.model.display.PermanentComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.*;

@Singleton
@BPvPListener
public class LuminousEye extends ChannelWeapon implements InteractWeapon, LegendaryWeapon, Listener {

    private final WeakHashMap<Player, LuminousEyeData> cache = new WeakHashMap<>();
    private final EnergyHandler energyHandler;
    private final ClientManager clientManager;
    private final List<Egg> eggs = new ArrayList<>();

    private final PermanentComponent actionBar = new PermanentComponent(gmr -> {
        if (!gmr.isOnline() || !cache.containsKey(gmr.getPlayer())) {
            return null;
        }

        final double charge = cache.get(gmr.getPlayer()).getTicksCharged();
        final float percent = (float) (charge / 48);
        return new ProgressBar(percent, 24)
                .withProgressColor(NamedTextColor.GOLD)
                .withRemainingColor(NamedTextColor.WHITE)
                .build();
    });

    @Inject
    public LuminousEye(Champions champions,EnergyHandler energyHandler, ClientManager clientManager) {
        super(champions, "luminous_eye");
        this.clientManager = clientManager;
        this.energyHandler = energyHandler;
    }


    @Override
    public List<Component> getLore(ItemMeta meta) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Forged in the heart of a raging storm,", NamedTextColor.WHITE));
        lore.add(Component.text(""));
        lore.add(UtilMessage.deserialize("<white>Deals <yellow>%.1f Damage <white>per hit", baseDamage));

        return lore;
    }

    @UpdateEvent(priority = 100)
    public void doLuminousEye() {
        active.removeIf(uuid -> {
            Player player = Bukkit.getPlayer(uuid);

            if (player == null) return true;




            if ((player.getInventory().getItemInMainHand().getType() != Material.BLAZE_ROD)){

                clientManager.search().online(player).getGamer();
                return true;
            }


            activate(player);
            final LuminousEyeData data = cache.get(player);


            if(!canUse(player)) return true;

            if (data.getGamer().isHoldingRightClick() && !data.isHolding()) {
                data.setHolding(true);
                return false;
            }

            if (data.isHolding() && data.getTicksCharged() < 48){
                if(energyHandler.use(player, "Luminous Eye", 2, false)){
                    cache.get(player).setTicksCharged(data.getTicksCharged() + 1);
                }
            }

            if(!data.getGamer().isHoldingRightClick() && data.isHolding()){
                shoot(player, cache.get(player).getTicksCharged());
                data.getGamer().getActionBar().remove(actionBar);
                data.setHolding(false);
                cache.remove(player);
            }

            return false;
        });

    }

    @Override
    public void activate(Player player) {
        active.add(player.getUniqueId());
        cache.computeIfAbsent(player, key -> {
            final Gamer gamer = clientManager.search().online(player).getGamer();
            gamer.getActionBar().add(250, actionBar);
            return new LuminousEyeData(gamer, 0, false);
        });
    }

    public void shoot(Player player, int charge) {
        Egg egg = player.launchProjectile(Egg.class);
        egg.setVelocity(player.getEyeLocation().getDirection().multiply((Math.max(charge / 10.0, .25))));
        eggs.add(egg);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Egg egg) {
            eggs.remove(egg);
            if (egg.getShooter() instanceof Player damager && event.getHitEntity() instanceof LivingEntity damagee && eggs.contains(egg)) {
                if(damager.getUniqueId() == damagee.getUniqueId()) return;
                damagee.damage(5.0);
                Location damageeLocation = damagee.getLocation();
                World world = damagee.getWorld();
                world.strikeLightningEffect(damageeLocation);
                damagee.setFireTicks(50);
                UtilSound.playSound(damager.getWorld(), damager.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
            }
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event){
        if (event.getEntity().getType() == EntityType.CHICKEN){
            event.setCancelled(true);
        }
    }

    @Override
    public boolean canUse(Player player) {
        if (UtilBlock.isInWater(player)) {
            UtilMessage.simpleMessage(player, "Luminous Eye", "You cannot use <green>Luminous Eye <gray>in water.");
            return false;
        }
        return true;
    }

    public void displayTrail(Location location) {
        final Color color = Color.fromRGB(255,64,0);
        Particle.REDSTONE.builder()
                .location(location)
                .count(2)
                .extra(1)
                .data(new Particle.DustOptions(color, 1.5f))
                .receivers(60)
                .spawn();
    }


    @UpdateEvent
    public void updateParticle() {
        Iterator<Egg> it = eggs.iterator();
        while (it.hasNext()) {
            Egg next = it.next();
            if (next == null) {
                it.remove();
            } else if (next.isDead()) {
                it.remove();
            } else {
                Location loc = next.getLocation().add(new Vector(0, 0.25, 0));
                displayTrail(loc);
            }
        }
    }

    @Override
    public double getEnergy() {
        return 0;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    private static class LuminousEyeData {
        private Gamer gamer;
        private int ticksCharged;
        private boolean isHolding;
    }
}
