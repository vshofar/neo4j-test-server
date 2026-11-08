#!/usr/bin/env python3
"""Example client for neo4j-test-server.

Launches the Java test server as a subprocess, reads the readiness
JSON from stdout, connects via the Neo4j Python driver, runs a
query, and shuts the server down via stdin.
"""

import json
import subprocess
import sys
from pathlib import Path

from neo4j import GraphDatabase

PROJECT_ROOT = Path(__file__).resolve().parents[2]
GRADLEW = PROJECT_ROOT / "gradlew"


def java_classpath() -> str:
    result = subprocess.run(
        [str(GRADLEW), "-q", "printClasspath"],
        cwd=PROJECT_ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def read_ready(process: subprocess.Popen) -> dict:
    for line in process.stdout:
        line = line.strip()
        if line.startswith("{"):
            return json.loads(line)
        print(f"[server] {line}", file=sys.stderr)
    raise RuntimeError("server closed stdout before readiness message")


def main() -> None:
    classpath = java_classpath()
    process = subprocess.Popen(
        [
            "java",
            "-Dfile.encoding=UTF-8",
            "-cp",
            classpath,
            "neo4jtest.server.Neo4jTestServer",
        ],
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
