package laggy.trackpack;

import java.util.*;

public class AltTracker {
    public static class AltAccount {
        public UUID uuid;
        public String name;
        public long lastLogin;

        public AltAccount(UUID uuid, String name, long lastLogin) {
            this.uuid = uuid;
            this.name = name;
            this.lastLogin = lastLogin;
        }
    }

    private final SignatureStorage signatureStorage;
    private Map<UUID, Long> playerLastLogin = new HashMap<>();

    public AltTracker(SignatureStorage signatureStorage) {
        this.signatureStorage = signatureStorage;
    }

    public void recordPlayer(UUID playerUuid, long fingerprint, String playerName) {
        signatureStorage.addSignature(playerUuid, playerName, fingerprint);
        playerLastLogin.put(playerUuid, System.currentTimeMillis());
    }

    public void updateLastLogin(UUID playerUuid, String playerName) {
        playerLastLogin.put(playerUuid, System.currentTimeMillis());
    }

    public List<AltAccount> getAlts(UUID playerUuid) {
        Set<UUID> altUuids = signatureStorage.getAllAltsForPlayer(playerUuid);
        List<AltAccount> alts = new ArrayList<>();

        for (UUID altUuid : altUuids) {
            String name = signatureStorage.getPlayerName(altUuid);
            long lastLogin = playerLastLogin.getOrDefault(altUuid, 0L);
            alts.add(new AltAccount(altUuid, name, lastLogin));
        }

        // Sort by last login descending
        alts.sort((a, b) -> Long.compare(b.lastLogin, a.lastLogin));
        return alts;
    }

    public UUID getOriginalAccount(UUID playerUuid) {
        // Find the alt with the earliest creation (lowest UUID if tied)
        Set<UUID> alts = signatureStorage.getAllAltsForPlayer(playerUuid);
        alts.add(playerUuid); // Include self

        UUID earliest = playerUuid;
        for (UUID alt : alts) {
            if (alt.compareTo(earliest) < 0) {
                earliest = alt;
            }
        }
        return earliest;
    }
}