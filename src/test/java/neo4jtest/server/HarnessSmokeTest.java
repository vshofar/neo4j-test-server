package neo4jtest.server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Result;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarnessSmokeTest {

    private static Neo4j neo4j;
    private static Path workingDir;

    @BeforeAll
    static void startNeo4j() throws Exception {
        workingDir = Files.createTempDirectory("harness-smoke-");
        neo4j = Neo4jBuilders.newInProcessBuilder(workingDir)
                .withDisabledServer()
                .withConfig(BoltConnector.listen_address, new SocketAddress("127.0.0.1", 0))
                .withConfig(BoltConnector.encryption_level, BoltConnector.EncryptionLevel.DISABLED)
                .withConfig(GraphDatabaseSettings.auth_enabled, false)
                .build();
    }

    @AfterAll
    static void stopNeo4j() {
        if (neo4j != null) {
            neo4j.close();
        }
    }

    @Test
    void boltEndpointIsLoopback() {
        String uri = neo4j.boltURI().toString();
        assertTrue(uri.startsWith("bolt://127.0.0.1:"), "unexpected bolt uri: " + uri);
    }

    @Test
    void driverCanWriteAndRead() {
        try (Driver driver = GraphDatabase.driver(neo4j.boltURI(), AuthTokens.none());
             Session session = driver.session()) {
            session.run("CREATE (:Smoke {name: 'neo4j-test-server'})");
            Result result = session.run("MATCH (n:Smoke) RETURN n.name AS name");
            assertEquals("neo4j-test-server", result.single().get("name").asString());
        }
    }
}
