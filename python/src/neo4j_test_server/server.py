"""Bootstrap and lifecycle management for the Neo4j test server JAR.

The server is a self-contained fat JAR published as a GitHub Release
asset. On first use it is downloaded and cached locally; only a Java
21+ runtime is required.
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import IO, Optional

JAR_VERSION = os.environ.get("NEO4J_TEST_SERVER_JAR_VERSION", "v0.1.0")
JAR_NAME = "neo4j-test-server-all.jar"
RELEASE_BASE_URL = (
    "https://github.com/vshofar/neo4j-test-server/releases/download"
)

DEFAULT_CACHE_DIR = (
    Path(os.environ.get("NEO4J_TEST_SERVER_CACHE", ""))
    if os.environ.get("NEO4J_TEST_SERVER_CACHE")
    else Path.home() / ".cache" / "neo4j-test-server"
)

STARTUP_TIMEOUT_SECONDS = 180
SHUTDOWN_TIMEOUT_SECONDS = 60


@dataclass(frozen=True)
class ServerInfo:
    bolt_uri: str
    username: str
    password: str


def jar_path(version: str = JAR_VERSION, cache_dir: Optional[Path] = None) -> Path:
    """Return the cached location of the JAR for a given version."""
    return (cache_dir or DEFAULT_CACHE_DIR) / version / JAR_NAME


def release_url(version: str = JAR_VERSION) -> str:
    return f"{RELEASE_BASE_URL}/{version}/{JAR_NAME}"


def ensure_jar(
    version: str = JAR_VERSION,
    cache_dir: Optional[Path] = None,
    url: Optional[str] = None,
) -> Path:
    """Return a local path to the JAR, downloading it if necessary.

    Honors the ``NEO4J_TEST_SERVER_JAR`` environment variable, which
    points directly to a local JAR and skips downloading entirely.
    """
    override = os.environ.get("NEO4J_TEST_SERVER_JAR")
    if override:
        path = Path(override)
        if not path.exists():
            raise FileNotFoundError(f"NEO4J_TEST_SERVER_JAR not found: {path}")
        return path

    dest = jar_path(version, cache_dir)
    if dest.exists():
        return dest

    url = url or release_url(version)
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(".jar.part")

    print(f"Downloading {JAR_NAME} ({version}) from {url} ...", file=sys.stderr)
    try:
        with urllib.request.urlopen(url) as response, open(tmp, "wb") as out:
            shutil.copyfileobj(response, out, length=1024 * 1024)
        tmp.replace(dest)
    except Exception:
        tmp.unlink(missing_ok=True)
        raise

    print(f"Cached at {dest}", file=sys.stderr)
    return dest


class Neo4jTestServer:
    """Context manager that runs the Neo4j test server as a subprocess.

    Usage::

        with Neo4jTestServer() as server:
            driver = GraphDatabase.driver(
                server.bolt_uri,
                auth=(server.username, server.password),
            )
    """

    def __init__(
        self,
        jar: Optional[Path] = None,
        java: str = "java",
        working_dir: Optional[Path] = None,
        env: Optional[dict] = None,
    ) -> None:
        self._jar = Path(jar) if jar else None
        self._java = java
        self._working_dir = working_dir
        self._env = env
        self._process: Optional[subprocess.Popen] = None
        self._info: Optional[ServerInfo] = None

    @property
    def info(self) -> ServerInfo:
        if self._info is None:
            raise RuntimeError("server is not running")
        return self._info

    @property
    def bolt_uri(self) -> str:
        return self.info.bolt_uri

    @property
    def username(self) -> str:
        return self.info.username

    @property
    def password(self) -> str:
        return self.info.password

    @property
    def process(self) -> subprocess.Popen:
        if self._process is None:
            raise RuntimeError("server is not running")
        return self._process

    def start(self) -> ServerInfo:
        if self._process is not None:
            raise RuntimeError("server already running")

        jar = self._jar or ensure_jar()
        cmd = [self._java, "-jar", str(jar)]
        if self._working_dir is not None:
            cmd += ["--working-dir", str(self._working_dir)]

        self._process = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=None,
            text=True,
            env=self._env,
        )
        try:
            self._info = self._read_ready(self._process)
        except Exception:
            self._terminate()
            raise
        return self._info

    def stop(self) -> None:
        if self._process is None:
            return
        process = self._process
        self._process = None
        self._info = None

        if process.stdin:
            try:
                process.stdin.write("shutdown\n")
                process.stdin.flush()
            except (BrokenPipeError, OSError):
                pass
        try:
            process.wait(timeout=SHUTDOWN_TIMEOUT_SECONDS)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()

    def _read_ready(self, process: subprocess.Popen) -> ServerInfo:
        stdout: IO[str] = process.stdout  # type: ignore[assignment]
        deadline = _monotonic() + STARTUP_TIMEOUT_SECONDS
        for line in stdout:
            line = line.strip()
            if line.startswith("{"):
                data = json.loads(line)
                return ServerInfo(
                    bolt_uri=data["bolt_uri"],
                    username=data["username"],
                    password=data["password"],
                )
            if not process.poll() is None:
                raise RuntimeError(
                    f"server exited before readiness message (code {process.returncode})"
                )
            if _monotonic() > deadline:
                raise TimeoutError("timed out waiting for readiness message")
        raise RuntimeError("server closed stdout before readiness message")

    def _terminate(self) -> None:
        process = self._process
        self._process = None
        self._info = None
        if process is None:
            return
        if process.poll() is None:
            process.kill()
            process.wait()

    def __enter__(self) -> "Neo4jTestServer":
        self.start()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb) -> None:
        self.stop()


def _monotonic() -> float:
    import time

    return time.monotonic()


__all__ = [
    "JAR_VERSION",
    "Neo4jTestServer",
    "ServerInfo",
    "ensure_jar",
    "jar_path",
    "release_url",
]
