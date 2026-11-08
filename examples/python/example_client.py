#!/usr/bin/env python3
"""Example client for neo4j-test-server.

Uses the `neo4j-test-server` package from PyPI, which downloads the
fat JAR from GitHub Releases on first use and caches it locally.
Only a Java 21+ runtime is required.
"""

import sys

from neo4j import GraphDatabase
from neo4j_test_server import Neo4jTestServer


def main() -> None:
    with Neo4jTestServer() as server:
        print(f"Server ready: {server.bolt_uri}")

        driver = GraphDatabase.driver(
            server.bolt_uri,
            auth=(server.username, server.password),
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

    print("Server stopped cleanly")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)
