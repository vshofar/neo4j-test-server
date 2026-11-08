#!/usr/bin/env python3
"""Example client for neo4j-test-server.

Launches the fat JAR as a subprocess, reads the readiness JSON from
stdout, connects via the Neo4j Python driver, runs a query, and shuts
the server down via stdin.

Prerequisite: build the JAR once with `./gradlew shadowJar` from the
project root. Only a Java 21+ runtime is needed to run it.
"""

import json
import subprocess
import sys
from pathlib import Path

from neo4j import GraphDatabase

PROJECT_ROOT = Path(__file__).resolve().parents[2]
JAR = PROJECT_ROOT / "build" / "libs" / "neo4j-test-server-all.jar"


def read_ready(process: subprocess.Popen) -> dict:
    for line in process.stdout:
        line = line.strip()
        if line.startswith("{"):
            return json.loads(line)
        print(f"[server] {line}", file=sys.stderr)
    raise RuntimeError("server closed stdout before readiness message")


def main() -> None:
    if not JAR.exists():
        sys.exit(f"JAR not found: {JAR}\nRun './gradlew shadowJar' first.")

    process = subprocess.Popen(
        ["java", "-jar", str(JAR)],
        cwd=PROJECT_ROOT,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=None,
        text=True,
    )

    try:
        ready = read_ready(process)
        print(f"Server ready: {ready['bolt_uri']}")

        driver = GraphDatabase.driver(
            ready["bolt_uri"],
            auth=(ready["username"], ready["password"]),
        )
        try:
            with driver.session() as session:
                session.run("CREATE (:PythonExample {name: 'hello'})")
                name = session.run(
                    "MATCH (n:PythonExample) RETURN n.name AS name"
                ).single()["name"]
                print(f"Read back: {name}")
        finally:
            driver.close()
    finally:
        if process.stdin:
            process.stdin.write("shutdown\n")
            process.stdin.flush()
        process.wait(timeout=60)
        print(f"Server exited with code {process.returncode}")


if __name__ == "__main__":
    main()
