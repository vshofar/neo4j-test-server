# neo4j-test-server (Python)

Pacote Python para subir um servidor Neo4j descartável em testes de integração.

O pacote baixa automaticamente o fat JAR do
[GitHub Releases](https://github.com/vshofar/neo4j-test-server/releases) na
primeira utilização e o cacheia localmente. Em runtime, apenas **Java 21+** é
necessário — nada de Gradle ou Maven.

## Instalação

```bash
pip install neo4j-test-server
```

## Uso

### Context manager

```python
from neo4j_test_server import Neo4jTestServer
from neo4j import GraphDatabase

with Neo4jTestServer() as server:
    driver = GraphDatabase.driver(
        server.bolt_uri,
        auth=(server.username, server.password),
    )
    with driver.session() as session:
        session.run("CREATE (:Test {name: 'hello'})")
    driver.close()
# servidor encerrado automaticamente
```

### Fixture pytest

O pacote registra automaticamente uma fixture `neo4j_test_server`
(session-scoped) via entry point `pytest11`:

```python
def test_something(neo4j_test_server):
    driver = GraphDatabase.driver(
        neo4j_test_server.bolt_uri,
        auth=(neo4j_test_server.username, neo4j_test_server.password),
    )
    # ...
```

## Configuração

| Variável de ambiente | Descrição |
|---|---|
| `NEO4J_TEST_SERVER_JAR` | Caminho para um JAR local (pula download) |
| `NEO4J_TEST_SERVER_CACHE` | Diretório de cache (padrão: `~/.cache/neo4j-test-server`) |
| `NEO4J_TEST_SERVER_JAR_VERSION` | Versão do JAR a baixar (padrão: versão fixada no pacote) |
| `JAVA_HOME` / `java` no PATH | Runtime Java 21+ |

## Desenvolvimento local

Para testar o pacote contra um JAR buildado localmente:

```bash
# na raiz do monorepo
./gradlew shadowJar

# aponte o pacote para o JAR local
export NEO4J_TEST_SERVER_JAR=$PWD/build/libs/neo4j-test-server-all.jar

# instale o pacote em modo editável
pip install -e python/
```
