package neo4jtest.server

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class Neo4jTestServerProcessTest {

    @Test
    fun serverAnnouncesBoltEndpointAndShutsDown() {
        val workingDir = Files.createTempDirectory("neo4j-test-server-proc-")
        val process = ProcessBuilder(
            javaBinary(),
            "-Dfile.encoding=UTF-8",
            "-cp", System.getProperty("java.class.path"),
            "neo4jtest.server.Neo4jTestServer",
            "--working-dir", workingDir.toString()
        )
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

        try {
            val ready = readReadyMessage(process)
            assertEquals("started", ready["status"].asText())
            val boltUri = URI.create(ready["bolt_uri"].asText())
            assertTrue(boltUri.host == "127.0.0.1" || boltUri.host == "localhost")

            val username = ready["username"].asText()
            val password = ready["password"].asText()

            GraphDatabase.driver(boltUri, AuthTokens.basic(username, password)).use { driver: Driver ->
                driver.session().use { session ->
                    session.run("CREATE (:ProcessTest {name: 'ok'})")
                    val name = session.run("MATCH (n:ProcessTest) RETURN n.name AS name")
                        .single()["name"].asString()
                    assertEquals("ok", name)
                }
            }

            writeShutdown(process)
            val exited = process.waitFor(60, TimeUnit.SECONDS)
            assertTrue(exited, "server did not exit after shutdown request")
            assertEquals(0, process.exitValue())
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }

    private fun readReadyMessage(process: Process): JsonNode {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STARTUP_TIMEOUT_SECONDS)
        BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                val trimmed = line.trim()
                if (trimmed.startsWith("{")) {
                    return MAPPER.readTree(trimmed)
                }
                if (!process.isAlive) {
                    throw IllegalStateException("server exited before readiness message")
                }
                if (System.nanoTime() > deadline) {
                    throw IllegalStateException("timed out waiting for readiness message")
                }
            }
        }
        throw IllegalStateException("server closed stdout before readiness message")
    }

    private fun writeShutdown(process: Process) {
        BufferedWriter(OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8)).use { writer ->
            writer.write("shutdown\n")
            writer.flush()
        }
    }

    private fun javaBinary(): String {
        val home = System.getProperty("java.home")
        val sep = System.getProperty("file.separator")
        return home + sep + "bin" + sep + "java"
    }

    companion object {
        private val MAPPER = ObjectMapper()
        private const val STARTUP_TIMEOUT_SECONDS = 180L
    }
}
