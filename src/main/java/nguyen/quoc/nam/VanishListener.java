package nguyen.quoc.nam;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public final class VanishListener implements Listener {

    private final EffectUtils plugin;

    public VanishListener(EffectUtils plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joiningPlayer = event.getPlayer();

        // Hide all currently vanished players from the joining player
        for (UUID vanishedId : plugin.getVanishedPlayers()) {
            Player vanishedPlayer = Bukkit.getPlayer(vanishedId);
            if (vanishedPlayer != null && vanishedPlayer.isOnline()) {
                joiningPlayer.hidePlayer(plugin, vanishedPlayer);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // If the quitting player is vanished, remove them to clean up the state
        if (plugin.isVanished(uuid)) {
            // We don't need to call showPlayer because the player is leaving the server anyway,
            // but we must clean up the in-memory set.
            plugin.setVanished(player, false);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        // If the deceased player is vanished, remove their vanish state
        if (plugin.isVanished(uuid)) {
            plugin.setVanished(player, false);
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        // Run in the next tick to ensure the world switch is fully completed on the server
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            // Case 1: The player who changed worlds is vanished.
            // We need to hide them from everyone in the new world.
            if (plugin.isVanished(player.getUniqueId())) {
                plugin.hidePlayerFromAll(player);
            }

            // Case 2: A normal player changed worlds.
            // We need to hide all vanished players from them.
            for (UUID vanishedId : plugin.getVanishedPlayers()) {
                Player vanishedPlayer = Bukkit.getPlayer(vanishedId);
                if (vanishedPlayer != null && vanishedPlayer.isOnline() && !vanishedPlayer.equals(player)) {
                    player.hidePlayer(plugin, vanishedPlayer);
                }
            }
        });
    }
}
