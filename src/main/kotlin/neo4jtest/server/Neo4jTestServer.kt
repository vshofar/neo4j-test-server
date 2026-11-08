package neo4jtest.server

import com.fasterxml.jackson.databind.ObjectMapper
import org.neo4j.configuration.GraphDatabaseSettings
import org.neo4j.configuration.connectors.BoltConnector
import org.neo4j.configuration.helpers.SocketAddress
import org.neo4j.harness.Neo4j
import org.neo4j.harness.Neo4jBuilders
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

object Neo4jTestServer {

    private const val USERNAME = "neo4j"
    private val MAPPER = ObjectMapper()
    private val RANDOM = SecureRandom()

    @JvmStatic
    fun main(args: Array<String>) {
        val workingDir = parseWorkingDir(args)
        val password = generatePassword()

        val neo4j = Neo4jBuilders.newInProcessBuilder(workingDir)
            .withDisabledServer()
            .withConfig(BoltConnector.listen_address, SocketAddress("127.0.0.1", 0))
            .withConfig(BoltConnector.encryption_level, BoltConnector.EncryptionLevel.DISABLED)
            .withConfig(GraphDatabaseSettings.auth_enabled, true)
            .build()

        Runtime.getRuntime().addShutdownHook(Thread({ closeQuietly(neo4j) }, "neo4j-shutdown"))

        try {
            setInitialPassword(neo4j, password)
        } catch (e: Exception) {
            System.err.println("Failed to set initial password: ${e.message}")
            closeQuietly(neo4j)
            exitProcess(2)
        }

        val boltUri = neo4j.boltURI()
        val ready = MAPPER.createObjectNode()
        ready.put("status", "started")
        ready.put("bolt_uri", boltUri.toString())
        ready.put("username", USERNAME)
        ready.put("password", password)
        println(MAPPER.writeValueAsString(ready))
        System.out.flush()

        watchStdin(neo4j)
    }

    private fun parseWorkingDir(args: Array<String>): Path {
        for (i in args.indices) {
            if (args[i] == "--working-dir" && i + 1 < args.size) {
                return Path.of(args[i + 1]).toAbsolutePath()
            }
        }
        return Files.createTempDirectory("neo4j-test-server-")
    }

    private fun generatePassword(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return buildString(24) {
            repeat(24) { append(alphabet[RANDOM.nextInt(alphabet.length)]) }
        }
    }

    private fun setInitialPassword(neo4j: Neo4j, password: String) {
        val system = neo4j.databaseManagementService().database("system")
        system.executeTransactionally(
            "ALTER USER neo4j SET PASSWORD '$password' CHANGE NOT REQUIRED",
            emptyMap<String, Any>()
        )
    }

    private fun watchStdin(neo4j: Neo4j) {
        val stopping = AtomicBoolean(false)
        val reader = Thread({
            try {
                BufferedReader(InputStreamReader(System.`in`, StandardCharsets.UTF_8)).use { input ->
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.trim().equals("shutdown", ignoreCase = true)) break
                    }
                }
            } catch (_: Exception) {
            }
            if (stopping.compareAndSet(false, true)) {
                System.err.println("Shutdown requested, stopping Neo4j...")
                closeQuietly(neo4j)
                exitProcess(0)
            }
        }, "stdin-watcher")
        reader.isDaemon = true
        reader.start()
        reader.join()
    }

    private fun closeQuietly(neo4j: Neo4j) {
        try {
            neo4j.close()
        } catch (e: Exception) {
            System.err.println("Error closing Neo4j: ${e.message}")
        }
    }
}
