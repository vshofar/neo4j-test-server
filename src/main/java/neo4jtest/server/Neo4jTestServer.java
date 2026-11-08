package neo4jtest.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Neo4jTestServer {

    private static final String USERNAME = "neo4j";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Neo4jTestServer() {
    }

    public static void main(String[] args) throws Exception {
        Path workingDir = parseWorkingDir(args);
        String password = generatePassword();

        Neo4j neo4j = Neo4jBuilders.newInProcessBuilder(workingDir)
                .withDisabledServer()
                .withConfig(BoltConnector.listen_address, new SocketAddress("127.0.0.1", 0))
                .withConfig(BoltConnector.encryption_level, BoltConnector.EncryptionLevel.DISABLED)
                .withConfig(GraphDatabaseSettings.auth_enabled, true)
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> closeQuietly(neo4j), "neo4j-shutdown"));

        try {
            setInitialPassword(neo4j, password);
        } catch (Exception e) {
            System.err.println("Failed to set initial password: " + e.getMessage());
            closeQuietly(neo4j);
            System.exit(2);
            return;
        }

        URI boltUri = neo4j.boltURI();
        ObjectNode ready = MAPPER.createObjectNode();
        ready.put("status", "started");
        ready.put("bolt_uri", boltUri.toString());
        ready.put("username", USERNAME);
        ready.put("password", password);
        System.out.println(MAPPER.writeValueAsString(ready));
        System.out.flush();

        watchStdin(neo4j);
    }

    private static Path parseWorkingDir(String[] args) throws Exception {
        for (int i = 0; i < args.length; i++) {
            if ("--working-dir".equals(args[i]) && i + 1 < args.length) {
                return Path.of(args[++i]).toAbsolutePath();
            }
        }
        return Files.createTempDirectory("neo4j-test-server-");
    }

    private static String generatePassword() {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(24);
        for (int i = 0; i < 24; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static void setInitialPassword(Neo4j neo4j, String password) {
        GraphDatabaseService system = neo4j.databaseManagementService().database("system");
        system.executeTransactionally(
                "ALTER USER neo4j SET PASSWORD '" + password + "' CHANGE NOT REQUIRED",
                Map.of());
    }

    private static void watchStdin(Neo4j neo4j) throws Exception {
        AtomicBoolean stopping = new AtomicBoolean(false);
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    if ("shutdown".equalsIgnoreCase(line.trim())) {
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
            if (stopping.compareAndSet(false, true)) {
                System.err.println("Shutdown requested, stopping Neo4j...");
                closeQuietly(neo4j);
                System.exit(0);
            }
        }, "stdin-watcher");
        reader.setDaemon(true);
        reader.start();
        reader.join();
    }

    private static void closeQuietly(Neo4j neo4j) {
        try {
            neo4j.close();
        } catch (Exception e) {
            System.err.println("Error closing Neo4j: " + e.getMessage());
        }
    }
}
