package laggy.trackpack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class PackGenerator {
    private static final int NUM_PACKS = 14;

    public static void generateDefaultPacks(Path packDirectory) throws Exception {
        Files.createDirectories(packDirectory);

        for (int i = 0; i < NUM_PACKS; i++) {
            Path packPath = packDirectory.resolve(String.valueOf(i));
            if (!Files.exists(packPath)) {
                createPack(packPath, i);
            }
        }
    }

    private static void createPack(Path packPath, int index) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(packPath))) {
            ZipEntry entry = new ZipEntry("pack.mcmeta");
            zos.putNextEntry(entry);
            
            String packMeta = String.format(
                "{\"pack\":{\"pack_format\":22,\"supported_formats\":[22,1000],\"description\":\"pack %d\"}}",
                index
            );
            zos.write(packMeta.getBytes());
            zos.closeEntry();
        }
    }

    public static String calculateHash(Path filePath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] fileBytes = Files.readAllBytes(filePath);
        byte[] hashBytes = digest.digest(fileBytes);
        
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static TrackPack.PackInfo[] generatePacksJson(Path packDirectory, Path jsonPath) throws Exception {
        List<TrackPack.PackInfo> packs = new ArrayList<>();

        for (int i = 0; i < NUM_PACKS; i++) {
            Path packPath = packDirectory.resolve(String.valueOf(i));
            if (Files.exists(packPath)) {
                String hash = calculateHash(packPath);
                String url = String.format("http://omena0.txx.fi:20123/packs/%d", i);
                packs.add(new TrackPack.PackInfo(url, UUID.randomUUID(), hash));
            }
        }

        // Write to packs.json
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(jsonPath, gson.toJson(packs));

        return packs.toArray(new TrackPack.PackInfo[0]);
    }
}
