package laggy.trackpack;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.google.gson.Gson;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
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

    private ProtocolManager protocolManager;

    public static PackInfo[] resourcePacks;
    private final HashMap<UUID, Status[]> playerPackQueue = new HashMap<>();
    public static final String BAD_URL = "http://127.0.0.1:0";

    @Override
    public void onEnable() {
        // Initialize ProtocolLib manager
        protocolManager = ProtocolLibrary.getProtocolManager();

        // Load resource packs from packs.json
        resourcePacks = readPacksFromConfig();
        if (resourcePacks == null) {
            getLogger().warning("Resource packs are not loaded. TrackPack will be disabled.");
            return;
        }

        // Register Bukkit event listeners
        getServer().getPluginManager().registerEvents(this, this);

        // Register ProtocolLib packet listener for resource pack status packets
        protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Client.RESOURCE_PACK_STATUS) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                onPackPacketReceived(event);
            }
        });
    }

    // Reads the packs.json config file and parses it into PackInfo[]
    private PackInfo[] readPacksFromConfig() {
        File packsFile = new File(getDataFolder(), "packs.json");
        if (!packsFile.exists()) {
            try {
                // noinspection ResultOfMethodCallIgnored
                packsFile.getParentFile().mkdirs();
                java.nio.file.Files.writeString(packsFile.toPath(), "[]");
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
        // Send the initial resource packs to the player
        sendInitialPacks(event.getPlayer());

        // Put the player in the queue
        Status[] packStatus = new Status[resourcePacks.length];
        Arrays.fill(packStatus, Status.WAITING);
        playerPackQueue.put(event.getPlayer().getUniqueId(), packStatus);
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        // Remove the player's pack status to prevent memory leaks if the player declines the packs
        playerPackQueue.remove(event.getPlayer().getUniqueId());
    }

    // Sends resource pack requests to the player with a fake URL (to see if the pack is cached already)
    private void sendInitialPacks(Player player) {
        // NOTE: the packet order can be randomized for obfuscation
        for (PackInfo pack : resourcePacks) {
            sendPack(player, pack.uuid, BAD_URL, pack.hash);
        }
    }

    // Handles incoming resource pack status packets from players
    private void onPackPacketReceived(PacketEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        UUID packId = event.getPacket().getUUIDs().read(0);
        EnumWrappers.ResourcePackStatus status = event.getPacket().getResourcePackStatus().read(0);

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
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ADD_RESOURCE_PACK);
        packet.getUUIDs().write(0, uuid);
        packet.getStrings().write(0, url);
        packet.getStrings().write(1, hash);
        packet.getBooleans().write(0, true);
        packet.getOptionalStructures().write(0, Optional.empty());
        protocolManager.sendServerPacket(player, packet);
    }
}
