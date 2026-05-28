package nguyen.quoc.nam;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.TabCompleteEvent;
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.bukkit.craftbukkit.entity.CraftPlayer;

public final class VanishListener implements Listener {

    private final EffectUtils plugin;

    public VanishListener(EffectUtils plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 1. Silence join message if player is forgotten
        if (plugin.isForgotten(player.getUniqueId())) {
            event.setJoinMessage(null);
        }

        // 2. Hide all currently vanished players from the joining player
        for (UUID vanishedId : plugin.getVanishedPlayers()) {
            Player vanishedPlayer = Bukkit.getPlayer(vanishedId);
            if (vanishedPlayer != null && vanishedPlayer.isOnline()) {
                player.hidePlayer(plugin, vanishedPlayer);
            }
        }

        // 3. Apply tab list removal logic for forgotten players after 2 ticks
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyTabListRemoval(player), 2L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // 1. Silence quit message if player is forgotten
        if (plugin.isForgotten(uuid)) {
            event.setQuitMessage(null);
        }

        // 2. If the quitting player is vanished, remove them to clean up the state
        if (plugin.isVanished(uuid)) {
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

        // 1. Vanished player world change handling (1 tick delay is standard for Bukkit's hidePlayer)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            if (plugin.isVanished(player.getUniqueId())) {
                plugin.hidePlayerFromAll(player);
            }

            for (UUID vanishedId : plugin.getVanishedPlayers()) {
                Player vanishedPlayer = Bukkit.getPlayer(vanishedId);
                if (vanishedPlayer != null && vanishedPlayer.isOnline() && !vanishedPlayer.equals(player)) {
                    player.hidePlayer(plugin, vanishedPlayer);
                }
            }
        });

        // 2. Forgotten player world change handling (2 ticks delay for NMS packet processing safety)
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyTabListRemoval(player), 2L);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Forgotten player respawn handling (2 ticks delay for NMS packet processing safety)
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyTabListRemoval(player), 2L);
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        List<String> completions = new ArrayList<>(event.getCompletions());
        boolean removed = completions.removeIf(completion -> {
            Player target = Bukkit.getPlayerExact(completion);
            return target != null && plugin.isForgotten(target.getUniqueId());
        });
        if (removed) {
            event.setCompletions(completions);
        }
    }

    @EventHandler
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        List<String> completions = new ArrayList<>(event.getCompletions());
        boolean removed = completions.removeIf(completion -> {
            Player target = Bukkit.getPlayerExact(completion);
            return target != null && plugin.isForgotten(target.getUniqueId());
        });
        if (removed) {
            event.setCompletions(completions);
        }
    }

    private void applyTabListRemoval(Player player) {
        if (!player.isOnline()) {
            return;
        }

        // Case 1: If the player themselves is forgotten, hide them from everyone else and enforce tag visibility
        if (plugin.isForgotten(player.getUniqueId())) {
            plugin.getForgetTeam().addEntry(player.getName());
            plugin.hideFromTabListForEveryone(player);
        }

        // Case 2: Hide all other online forgotten players from this player's tab list (using listed = false)
        List<ClientboundPlayerInfoUpdatePacket.Entry> entriesToRemove = new ArrayList<>();
        for (UUID uuid : plugin.getForgottenPlayers()) {
            Player forgotten = Bukkit.getPlayer(uuid);
            if (forgotten != null && forgotten.isOnline() && !forgotten.equals(player)) {
                ServerPlayer nmsForgotten = ((CraftPlayer) forgotten).getHandle();
                entriesToRemove.add(new ClientboundPlayerInfoUpdatePacket.Entry(
                    uuid,
                    nmsForgotten.getGameProfile(),
                    false, // listed = false
                    forgotten.getPing(),
                    plugin.getNmsGameType(forgotten.getGameMode()),
                    nmsForgotten.getTabListDisplayName(),
                    true, // showHat
                    0, // listOrder
                    null // chatSession
                ));
            }
        }

        if (!entriesToRemove.isEmpty()) {
            ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED),
                entriesToRemove
            );
            ((CraftPlayer) player).getHandle().connection.send(packet);
        }
    }
}
