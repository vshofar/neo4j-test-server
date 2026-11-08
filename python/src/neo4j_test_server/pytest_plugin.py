"""Pytest plugin exposing a session-scoped Neo4j test server fixture."""

from __future__ import annotations

from typing import Iterator

import pytest

from neo4j_test_server.server import Neo4jTestServer


@pytest.fixture(scope="session")
def neo4j_test_server() -> Iterator[Neo4jTestServer]:
    """Session-scoped fixture that starts the Neo4j test server once.

    The server is shared by all tests in the session and shut down
    automatically at the end.
    """
    with Neo4jTestServer() as server:
        yield server
