package nguyen.quoc.nam;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class VanishCommand implements CommandExecutor, TabCompleter {

    private final EffectUtils plugin;

    public VanishCommand(EffectUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Permission check
        if (!sender.hasPermission("effectutils.vanish")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command!");
            return true;
        }

        // Must have exactly 2 arguments: <enable/disable> <player>
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /vanish <enable/disable> <player>");
            return true;
        }

        String subCommand = args[0].toLowerCase();
        boolean enable;
        if (subCommand.equals("enable")) {
            enable = true;
        } else if (subCommand.equals("disable")) {
            enable = false;
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /vanish <enable/disable> <player>");
            return true;
        }

        // Get target player
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player not found or not online!");
            return true;
        }

        boolean currentlyVanished = plugin.isVanished(target.getUniqueId());

        if (enable) {
            if (currentlyVanished) {
                sender.sendMessage(ChatColor.YELLOW + target.getName() + " is already vanished!");
            } else {
                plugin.setVanished(target, true);
                if (!sender.equals(target)) {
                    sender.sendMessage(ChatColor.GREEN + target.getName() + " is now vanished!");
                }
            }
        } else {
            if (!currentlyVanished) {
                sender.sendMessage(ChatColor.YELLOW + target.getName() + " is not currently vanished!");
            } else {
                plugin.setVanished(target, false);
                if (!sender.equals(target)) {
                    sender.sendMessage(ChatColor.GREEN + target.getName() + " is no longer vanished.");
                }
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("effectutils.vanish")) {
            return completions;
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("enable".startsWith(input)) {
                completions.add("enable");
            }
            if ("disable".startsWith(input)) {
                completions.add("disable");
            }
        } else if (args.length == 2) {
            String input = args[1].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(input)) {
                    completions.add(player.getName());
                }
            }
        }
        return completions;
    }
}
