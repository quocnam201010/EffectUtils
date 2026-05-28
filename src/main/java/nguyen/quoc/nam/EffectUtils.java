package nguyen.quoc.nam;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.bukkit.craftbukkit.entity.CraftPlayer;

public final class EffectUtils extends JavaPlugin {

    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> forgottenPlayers = ConcurrentHashMap.newKeySet();

    private File forgottenFile;
    private YamlConfiguration forgottenConfig;

    @Override
    public void onEnable() {
        // Load forgotten players from data file
        loadForgotten();

        // Ensure scoreboard team exists
        getForgetTeam();

        // Register commands and tab completers
        VanishCommand vanishCommand = new VanishCommand(this);
        if (getCommand("vanish") != null) {
            getCommand("vanish").setExecutor(vanishCommand);
            getCommand("vanish").setTabCompleter(vanishCommand);
        }

        ForgetCommand forgetCommand = new ForgetCommand(this);
        if (getCommand("forget") != null) {
            getCommand("forget").setExecutor(forgetCommand);
            getCommand("forget").setTabCompleter(forgetCommand);
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

        // Restore forgotten players back to the tab list (if they are online)
        for (UUID uuid : forgottenPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                showInTabListForEveryone(player);
                getForgetTeam().removeEntry(player.getName());
            }
        }
        forgottenPlayers.clear();

        getLogger().info("EffectUtils plugin has been disabled!");
    }

    // --- Vanish Feature Helpers ---

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

    // --- Forget Feature Helpers ---

    public Set<UUID> getForgottenPlayers() {
        return Collections.unmodifiableSet(forgottenPlayers);
    }

    public boolean isForgotten(UUID uuid) {
        return forgottenPlayers.contains(uuid);
    }

    public void setForgotten(Player player, boolean forget) {
        UUID uuid = player.getUniqueId();
        if (forget) {
            forgottenPlayers.add(uuid);
            saveForgotten();

            // Hide nameplate
            getForgetTeam().addEntry(player.getName());

            // Remove from tab list for everyone (using listed = false)
            hideFromTabListForEveryone(player);
            player.sendMessage(ChatColor.GREEN + "You are now forgotten!");
        } else {
            if (forgottenPlayers.remove(uuid)) {
                saveForgotten();

                // Show nameplate
                getForgetTeam().removeEntry(player.getName());

                // Add back to tab list for everyone (using listed = true)
                showInTabListForEveryone(player);
                player.sendMessage(ChatColor.GREEN + "You are no longer forgotten.");
            }
        }
    }

    public Team getForgetTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam("forget_tags");
        if (team == null) {
            team = scoreboard.registerNewTeam("forget_tags");
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        return team;
    }

    public void hideFromTabListForEveryone(Player player) {
        ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
        
        ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
            player.getUniqueId(),
            serverPlayer.getGameProfile(),
            false, // listed = false
            player.getPing(),
            getNmsGameType(player.getGameMode()),
            serverPlayer.getTabListDisplayName(),
            true, // showHat
            0, // listOrder
            null // chatSession
        );

        ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
            EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED),
            List.of(entry)
        );

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                ((CraftPlayer) other).getHandle().connection.send(packet);
            }
        }
    }

    public void showInTabListForEveryone(Player player) {
        ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
        
        ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
            player.getUniqueId(),
            serverPlayer.getGameProfile(),
            true, // listed = true
            player.getPing(),
            getNmsGameType(player.getGameMode()),
            serverPlayer.getTabListDisplayName(),
            true, // showHat
            0, // listOrder
            null // chatSession
        );

        ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
            EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED),
            List.of(entry)
        );

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                ((CraftPlayer) other).getHandle().connection.send(packet);
            }
        }
    }

    public GameType getNmsGameType(org.bukkit.GameMode mode) {
        switch (mode) {
            case SURVIVAL: return GameType.SURVIVAL;
            case CREATIVE: return GameType.CREATIVE;
            case ADVENTURE: return GameType.ADVENTURE;
            case SPECTATOR: return GameType.SPECTATOR;
            default: return GameType.SURVIVAL;
        }
    }

    public void loadForgotten() {
        forgottenFile = new File(getDataFolder(), "forgotten.yml");
        if (!forgottenFile.exists()) {
            getDataFolder().mkdirs();
            try {
                forgottenFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create forgotten.yml!");
            }
        }
        forgottenConfig = YamlConfiguration.loadConfiguration(forgottenFile);
        List<String> list = forgottenConfig.getStringList("forgotten");
        forgottenPlayers.clear();
        for (String s : list) {
            try {
                forgottenPlayers.add(UUID.fromString(s));
            } catch (IllegalArgumentException e) {
                // ignore invalid UUIDs
            }
        }
    }

    public void saveForgotten() {
        List<String> list = new ArrayList<>();
        for (UUID uuid : forgottenPlayers) {
            list.add(uuid.toString());
        }
        forgottenConfig.set("forgotten", list);
        try {
            forgottenConfig.save(forgottenFile);
        } catch (IOException e) {
            getLogger().severe("Could not save forgotten.yml!");
        }
    }
}
