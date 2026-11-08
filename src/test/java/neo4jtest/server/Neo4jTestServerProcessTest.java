package neo4jtest.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Neo4jTestServerProcessTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long STARTUP_TIMEOUT_SECONDS = 180;

    @Test
    void serverAnnouncesBoltEndpointAndShutsDown() throws Exception {
        Path workingDir = Files.createTempDirectory("neo4j-test-server-proc-");
        Process process = new ProcessBuilder(
                        javaBinary(),
                        "-Dfile.encoding=UTF-8",
                        "-cp", System.getProperty("java.class.path"),
                        "neo4jtest.server.Neo4jTestServer",
                        "--working-dir", workingDir.toString())
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();

        try {
            JsonNode ready = readReadyMessage(process);
            assertEquals("started", ready.get("status").asText());
            URI boltUri = URI.create(ready.get("bolt_uri").asText());
            assertTrue(boltUri.getHost().equals("127.0.0.1") || boltUri.getHost().equals("localhost"));

            String username = ready.get("username").asText();
            String password = ready.get("password").asText();

            try (Driver driver = GraphDatabase.driver(boltUri, AuthTokens.basic(username, password));
                 Session session = driver.session()) {
                session.run("CREATE (:ProcessTest {name: 'ok'})");
                String name = session.run("MATCH (n:ProcessTest) RETURN n.name AS name")
                        .single().get("name").asString();
                assertEquals("ok", name);
            }

            writeShutdown(process);
            boolean exited = process.waitFor(60, TimeUnit.SECONDS);
            assertTrue(exited, "server did not exit after shutdown request");
            assertEquals(0, process.exitValue());
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static JsonNode readReadyMessage(Process process) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STARTUP_TIMEOUT_SECONDS);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("{")) {
                    return MAPPER.readTree(line);
                }
                if (!process.isAlive()) {
                    throw new IllegalStateException("server exited before readiness message");
                }
                if (System.nanoTime() > deadline) {
                    throw new IllegalStateException("timed out waiting for readiness message");
                }
            }
        }
        throw new IllegalStateException("server closed stdout before readiness message");
    }

    private static void writeShutdown(Process process) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write("shutdown\n");
            writer.flush();
        }
    }

    private static String javaBinary() {
        String home = System.getProperty("java.home");
        String sep = System.getProperty("file.separator");
        return home + sep + "bin" + sep + "java";
    }
}
