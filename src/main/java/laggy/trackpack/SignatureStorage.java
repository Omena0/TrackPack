package laggy.trackpack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class SignatureStorage {
    private static final int CURRENT_VERSION = 1;
    private static final String SIGNATURES_FILE = "signatures.json";

    public static class SignatureData {
        public int version = CURRENT_VERSION;
        public Map<String, PlayerSignatures> signatures = new HashMap<>();
    }

    public static class PlayerSignatures {
        public UUID playerUuid;
        public String playerName;
        public Set<Long> fingerprints = new HashSet<>();
        public long lastUpdated;

        public PlayerSignatures(UUID playerUuid, String playerName) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.lastUpdated = System.currentTimeMillis();
        }
    }

    private final File dataFolder;
    private final Gson gson;
    private SignatureData data;

    public SignatureStorage(File dataFolder) {
        this.dataFolder = dataFolder;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadSignatures();
    }

    private void loadSignatures() {
        File sigFile = new File(dataFolder, SIGNATURES_FILE);
        
        if (!sigFile.exists()) {
            data = new SignatureData();
            return;
        }

        try (FileReader reader = new FileReader(sigFile)) {
            SignatureData loaded = gson.fromJson(reader, SignatureData.class);
            if (loaded != null && loaded.version == CURRENT_VERSION) {
                data = loaded;
            } else {
                // Version mismatch, reset
                data = new SignatureData();
            }
        } catch (IOException e) {
            data = new SignatureData();
        }
    }

    public void saveSignatures() {
        try {
            File sigFile = new File(dataFolder, SIGNATURES_FILE);
            String json = gson.toJson(data);
            Files.writeString(sigFile.toPath(), json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addSignature(UUID playerUuid, String playerName, long fingerprint) {
        String key = playerUuid.toString();
        
        if (!data.signatures.containsKey(key)) {
            data.signatures.put(key, new PlayerSignatures(playerUuid, playerName));
        }

        PlayerSignatures ps = data.signatures.get(key);
        ps.playerName = playerName; // Update name in case of rename
        ps.fingerprints.add(fingerprint);
        ps.lastUpdated = System.currentTimeMillis();
        
        saveSignatures();
    }

    public Set<Long> getSignatures(UUID playerUuid) {
        PlayerSignatures ps = data.signatures.get(playerUuid.toString());
        if (ps != null) {
            return new HashSet<>(ps.fingerprints);
        }
        return new HashSet<>();
    }

    public Set<UUID> getAllPlayersWithSignature(long fingerprint) {
        Set<UUID> players = new HashSet<>();
        for (PlayerSignatures ps : data.signatures.values()) {
            if (ps.fingerprints.contains(fingerprint)) {
                players.add(ps.playerUuid);
            }
        }
        return players;
    }

    public Set<UUID> getAllAltsForPlayer(UUID playerUuid) {
        Set<UUID> alts = new HashSet<>();
        Set<Long> playerSignatures = getSignatures(playerUuid);
        
        // Find all players that have any of the same signatures
        for (long sig : playerSignatures) {
            alts.addAll(getAllPlayersWithSignature(sig));
        }
        
        alts.remove(playerUuid); // Remove self
        return alts;
    }

    public String getPlayerName(UUID playerUuid) {
        PlayerSignatures ps = data.signatures.get(playerUuid.toString());
        if (ps != null) {
            return ps.playerName;
        }
        return playerUuid.toString();
    }
}
