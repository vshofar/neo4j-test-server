# neo4j-test-server

[![Build](https://github.com/vshofar/neo4j-test-server/actions/workflows/build.yml/badge.svg)](https://github.com/vshofar/neo4j-test-server/actions/workflows/build.yml)
[![PyPI](https://img.shields.io/pypi/v/neo4j-test-server)](https://pypi.org/project/neo4j-test-server/)
[![License: MIT](https://img.shields.io/pypi/l/neo4j-test-server)](LICENSE)

Servidor Neo4j descartável, em processo, projetado para testes de integração.

Ele sobe uma instância do Neo4j via [neo4j-harness](https://neo4j.com/docs/operations-manual/current/docker/neo4j-harness/) (in-process, sem Docker), imprime um JSON de readiness no `stdout` com a URI Bolt e credenciais geradas aleatoriamente, e permanece vivo até receber `shutdown` no `stdin`.

## Como funciona

```
┌─────────────┐   launch    ┌──────────────────┐   JSON (stdout)   ┌──────────────┐
│ Test Runner │ ──────────► │ Neo4jTestServer  │ ────────────────► │ bolt_uri +   │
│  (parent)   │             │  (Java process)  │                   │ credentials  │
│             │ ◄────────── │                  │ ◄──────────────── │              │
│             │  "shutdown" │                  │                   │              │
└─────────────┘  (stdin)    └──────────────────┘                   └──────────────┘
```

1. O processo é iniciado com `--working-dir <path>` (opcional; sem ele, usa um temp dir)
2. Uma senha aleatória de 24 caracteres é gerada e definida via `ALTER USER neo4j SET PASSWORD ... CHANGE NOT REQUIRED`
3. Bolt escuta em `127.0.0.1` com porta aleatória e **encryption desabilitado**
4. Uma linha JSON é impressa no `stdout`:

```json
{"status":"started","bolt_uri":"bolt://127.0.0.1:7687","username":"neo4j","password":"..."}
```

5. O servidor aguarda `shutdown` no `stdin` (ou EOF) para encerrar

## Stack

- Java 21 + Kotlin 2.2 (Kotlin DSL)
- `org.neo4j.test:neo4j-harness`
- Jackson (serialização do handshake)
- JUnit 5 + neo4j-java-driver (testes)

## Build e testes

```bash
./gradlew build       # compila e roda os testes
./gradlew test        # apenas os testes
./gradlew run         # sobe o servidor interativamente
./gradlew shadowJar   # gera o fat JAR autocontido
```

## Monorepo

Este repositório contém dois artefatos:

| Artefato | Local | Distribuição |
|---|---|---|
| Servidor Neo4j (Kotlin) | `src/` | Fat JAR via GitHub Releases |
| Pacote Python | `python/` | PyPI (`pip install neo4j-test-server`) |

O pacote Python baixa o JAR do GitHub Releases automaticamente na primeira
utilização e o cacheia localmente — em runtime, apenas Java 21+ é necessário.

## Uso em projetos Python (sem Gradle/Maven)

### Via pip (recomendado)

```bash
pip install neo4j-test-server
```

```python
from neo4j_test_server import Neo4jTestServer
from neo4j import GraphDatabase

with Neo4jTestServer() as server:
    driver = GraphDatabase.driver(
        server.bolt_uri,
        auth=(server.username, server.password),
    )
    # ...
```

Uma fixture pytest `neo4j_test_server` (session-scoped) também é registrada
automaticamente. Veja [`python/README.md`](python/README.md).

### Via JAR manual

Gere o fat JAR uma vez:

```bash
./gradlew shadowJar
# gera build/libs/neo4j-test-server-all.jar (~165 MB, autocontido)
```

O JAR embute todas as dependências (neo4j-harness, Kotlin, Jackson etc.). Em runtime, **apenas Java 21+ é necessário** — nada de Gradle ou Maven:

```python
import subprocess, json

process = subprocess.Popen(
    ["java", "-jar", "neo4j-test-server-all.jar"],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True,
)
ready = json.loads(next(l for l in process.stdout if l.startswith("{")))
# ready["bolt_uri"], ready["username"], ready["password"]
# ... use o driver neo4j ...
process.stdin.write("shutdown\n")  # encerra o servidor
```

Veja [`examples/python/example_client.py`](examples/python/example_client.py) para um exemplo completo.

## Uso como biblioteca de teste (classpath)

Para desenvolvimento dentro do próprio projeto Gradle:

```bash
java -cp "$(./gradlew -q printClasspath)" neo4jtest.server.Neo4jTestServer --working-dir /tmp/neo4j-data
```

A task `printClasspath` imprime o classpath completo do projeto para uso externo.

## Exemplo em Python

Veja [`examples/python/example_client.py`](examples/python/example_client.py) — um cliente completo que usa o pacote `neo4j-test-server` do PyPI:

```bash
cd examples/python
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python example_client.py
```

## Estrutura

```
src/main/kotlin/neo4jtest/server/
└── Neo4jTestServer.kt        # entry point: sobe Neo4j, handshake JSON, shutdown via stdin

src/test/kotlin/neo4jtest/server/
├── HarnessSmokeTest.kt           # smoke test do harness in-process
└── Neo4jTestServerProcessTest.kt # teste end-to-end do processo (startup → query → shutdown)

examples/python/
├── example_client.py        # cliente de exemplo que consome o servidor
└── requirements.txt

python/                        # pacote pip publicável
├── pyproject.toml
├── README.md
├── src/neo4j_test_server/     # bootstrap do JAR + context manager + fixture pytest
└── tests/

.github/workflows/
├── build.yml                  # CI: testes Kotlin + testes do pacote Python
└── release.yml                # tag v* → GitHub Release (JAR) + PyPI (wheel)
```

## Licença

[MIT](LICENSE) © 2026 vshofar
