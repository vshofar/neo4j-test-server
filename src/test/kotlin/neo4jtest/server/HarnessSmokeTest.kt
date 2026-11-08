package neo4jtest.server

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.neo4j.configuration.GraphDatabaseSettings
import org.neo4j.configuration.connectors.BoltConnector
import org.neo4j.configuration.helpers.SocketAddress
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.neo4j.harness.Neo4j
import org.neo4j.harness.Neo4jBuilders
import java.nio.file.Files
import java.nio.file.Path

class HarnessSmokeTest {

    @Test
    fun boltEndpointIsLoopback() {
        val uri = neo4j.boltURI().toString()
        assertTrue(uri.startsWith("bolt://127.0.0.1:"), "unexpected bolt uri: $uri")
    }

    @Test
    fun driverCanWriteAndRead() {
        GraphDatabase.driver(neo4j.boltURI(), AuthTokens.none()).use { driver: Driver ->
            driver.session().use { session ->
                session.run("CREATE (:Smoke {name: 'neo4j-test-server'})")
                val result = session.run("MATCH (n:Smoke) RETURN n.name AS name")
                assertEquals("neo4j-test-server", result.single()["name"].asString())
            }
        }
    }

    companion object {
        private lateinit var neo4j: Neo4j
        private lateinit var workingDir: Path

        @BeforeAll
        @JvmStatic
        fun startNeo4j() {
            workingDir = Files.createTempDirectory("harness-smoke-")
            neo4j = Neo4jBuilders.newInProcessBuilder(workingDir)
                .withDisabledServer()
                .withConfig(BoltConnector.listen_address, SocketAddress("127.0.0.1", 0))
                .withConfig(BoltConnector.encryption_level, BoltConnector.EncryptionLevel.DISABLED)
                .withConfig(GraphDatabaseSettings.auth_enabled, false)
                .build()
        }

        @AfterAll
        @JvmStatic
        fun stopNeo4j() {
            if (::neo4j.isInitialized) {
                neo4j.close()
            }
        }
    }
}
