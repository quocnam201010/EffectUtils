package nguyen.quoc.nam;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EffectUtils extends JavaPlugin {

    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        // Register command and tab completer
        VanishCommand vanishCommand = new VanishCommand(this);
        if (getCommand("vanish") != null) {
            getCommand("vanish").setExecutor(vanishCommand);
            getCommand("vanish").setTabCompleter(vanishCommand);
        }

        // Register event listener
        getServer().getPluginManager().registerEvents(new VanishListener(this), this);

        getLogger().info("EffectUtils plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        // Show all vanished players before disable/reload
        for (UUID uuid : vanishedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                showPlayerToAll(player);
            }
        }
        vanishedPlayers.clear();
        getLogger().info("EffectUtils plugin has been disabled!");
    }

    public Set<UUID> getVanishedPlayers() {
        return Collections.unmodifiableSet(vanishedPlayers);
    }

    public boolean isVanished(UUID uuid) {
        return vanishedPlayers.contains(uuid);
    }

    public void setVanished(Player player, boolean vanish) {
        UUID uuid = player.getUniqueId();
        if (vanish) {
            vanishedPlayers.add(uuid);
            hidePlayerFromAll(player);
            player.sendMessage(ChatColor.GREEN + "You are now vanished!");
        } else {
            if (vanishedPlayers.remove(uuid)) {
                showPlayerToAll(player);
                player.sendMessage(ChatColor.GREEN + "You are no longer vanished.");
            }
        }
    }

    public void hidePlayerFromAll(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.hidePlayer(this, player);
            }
        }
    }

    public void showPlayerToAll(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.showPlayer(this, player);
            }
        }
    }
}
