# neo4j-test-server

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

- Java 21 + Gradle
- `org.neo4j.test:neo4j-harness`
- Jackson (serialização do handshake)
- JUnit 5 + neo4j-java-driver (testes)

## Build e testes

```bash
./gradlew build      # compila e roda os testes
./gradlew test       # apenas os testes
./gradlew run        # sobe o servidor interativamente
```

## Uso como biblioteca de teste

Qualquer linguagem pode consumir o servidor como sidecar:

```bash
java -cp "$(./gradlew -q printClasspath)" neo4jtest.server.Neo4jTestServer --working-dir /tmp/neo4j-data
```

A task `printClasspath` imprime o classpath completo do projeto para uso externo.

## Exemplo em Python

Veja [`examples/python/example_client.py`](examples/python/example_client.py):

```bash
cd examples/python
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python example_client.py
```

## Estrutura

```
src/main/java/neo4jtest/server/
└── Neo4jTestServer.java      # entry point: sobe Neo4j, handshake JSON, shutdown via stdin

src/test/java/neo4jtest/server/
├── HarnessSmokeTest.java           # smoke test do harness in-process
└── Neo4jTestServerProcessTest.java # teste end-to-end do processo (startup → query → shutdown)

examples/python/
├── example_client.py        # cliente de exemplo que consome o servidor
└── requirements.txt
```
