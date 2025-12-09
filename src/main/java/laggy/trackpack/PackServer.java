package laggy.trackpack;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

public class PackServer {
    private HttpServer server;
    private static final int PORT = 5009;
    private static final String HOST = "0.0.0.0";
    private final Path packsDirectory;

    public PackServer(Path packsDirectory) {
        this.packsDirectory = packsDirectory;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(HOST, PORT), 0);
        server.createContext("/packs/", new PackHandler());
        server.createContext("/hide_errors.zip", new HideErrorsPackHandler());
        server.setExecutor(null);
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public String getPackUrl(int packIndex) {
        return String.format("http://omena0.txx.fi:20123/packs/%d", packIndex);
    }

    private class PackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            // Extract pack index from path (e.g., "/packs/5" -> "5")
            String[] parts = path.split("/");
            if (parts.length < 3) {
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
                return;
            }

            String packIndexStr = parts[parts.length - 1];
            try {
                Path packFile = packsDirectory.resolve(packIndexStr);
                if (!Files.exists(packFile)) {
                    exchange.sendResponseHeaders(404, 0);
                    exchange.close();
                    return;
                }

                byte[] packData = Files.readAllBytes(packFile);
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, packData.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(packData);
                }
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, 0);
                exchange.close();
            }
        }
    }

    private class HideErrorsPackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // Try to find hide_errors.zip in the packs directory (plugin data folder)
                File hideErrorsFile = new File(packsDirectory.toFile().getParent(), "hide_errors.zip");
                
                if (!hideErrorsFile.exists()) {
                    exchange.sendResponseHeaders(404, 0);
                    exchange.close();
                    return;
                }

                byte[] packData = Files.readAllBytes(hideErrorsFile.toPath());
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, packData.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(packData);
                }
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, 0);
                exchange.close();
            }
        }
    }
}
