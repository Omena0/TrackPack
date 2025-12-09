package laggy.trackpack;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.text.SimpleDateFormat;
import java.util.*;

public class PackAltsCommand implements CommandExecutor, TabCompleter {
    private final AltTracker altTracker;

    public PackAltsCommand(AltTracker altTracker) {
        this.altTracker = altTracker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check permission - hide command from non-OP players
        if (!sender.hasPermission("trackpack.alts")) {
            return true; // Return true to hide "Unknown command" message
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /packalts <player_name>").color(NamedTextColor.RED));
            return true;
        }

        String targetPlayerName = args[0];
        Player onlinePlayer = Bukkit.getPlayer(targetPlayerName);
        UUID targetUuid = null;

        if (onlinePlayer != null) {
            targetUuid = onlinePlayer.getUniqueId();
        } else {
            // Try to get offline player
            try {
                org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetPlayerName);
                targetUuid = offlinePlayer.getUniqueId();
            } catch (Exception e) {
                sender.sendMessage(Component.text("Player " + targetPlayerName + " not found.").color(NamedTextColor.RED));
                return true;
            }
        }

        UUID originalAccount = altTracker.getOriginalAccount(targetUuid);
        List<AltTracker.AltAccount> alts = altTracker.getAlts(targetUuid);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        sender.sendMessage(Component.text("=== Alt Accounts for " + targetPlayerName + " ===").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Original Account: " + originalAccount).color(NamedTextColor.YELLOW));

        if (alts.isEmpty()) {
            sender.sendMessage(Component.text("No alt accounts found.").color(NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("Alt Accounts:").color(NamedTextColor.YELLOW));
            for (AltTracker.AltAccount alt : alts) {
                String lastLogin = dateFormat.format(new Date(alt.lastLogin));
                String message = String.format("  • %s (%s) - Last login: %s", alt.name, alt.uuid, lastLogin);
                sender.sendMessage(Component.text(message).color(NamedTextColor.WHITE));
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        // Only show tab completion for players with permission
        if (!sender.hasPermission("trackpack.alts")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> players = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    players.add(player.getName());
                }
            }
            return players;
        }
        return new ArrayList<>();
    }
}
