"""Tests for the neo4j-test-server Python package.

Requires NEO4J_TEST_SERVER_JAR pointing to a built fat JAR
(see .github/workflows/build.yml).
"""

import os

import pytest

from neo4j_test_server import Neo4jTestServer, ensure_jar

pytestmark = pytest.mark.skipif(
    not os.environ.get("NEO4J_TEST_SERVER_JAR"),
    reason="NEO4J_TEST_SERVER_JAR not set; skipping integration test",
)


def test_server_lifecycle() -> None:
    with Neo4jTestServer() as server:
        assert server.bolt_uri.startswith("bolt://")
        assert server.username
        assert server.password
        assert server.process.poll() is None


def test_ensure_jar_uses_env_override() -> None:
    path = ensure_jar()
    assert path.exists()
    assert path.name == "neo4j-test-server-all.jar"
