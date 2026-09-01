# Payment Service

Serviço REST para criação e consulta de pagamentos em Java, com foco em idempotência, concorrência e processamento assíncrono. O núcleo do desafio está implementado e validado com PostgreSQL, Flyway e testes automatizados.

## Stack

- Java 21
- Spring Boot 4.1.1
- Spring Web
- Spring Data JPA / Hibernate
- PostgreSQL
- Flyway
- Docker Compose
- Maven
- JUnit 5 / Testcontainers

## Arquitetura

```text
src/main/java/com/fabricio/payments
├── controller
├── service
├── repository
├── domain
├── dto
├── event
├── exception
├── config
├── application
└── resources
```

A aplicação mantém separação entre comando e consulta: `controller` expõe a API, `service` concentra a lógica de negócio e `repository` acessa os dados. A estrutura em `application/command` e `application/query` existe, mas o fluxo real em execução usa os serviços ativos em `service/`.

## API

### POST /payments

```http
POST /payments
Content-Type: application/json
Idempotency-Key: payment-key-123
```

```json
{
  "customerId": "customer-123",
  "amount": 55.00
}
```

Resposta resumida:

```json
{
  "id": "...",
  "customerId": "customer-123",
  "amount": 55.00,
  "status": "PENDING",
  "createdAt": "...Z"
}
```

### GET /payments/{id}

Resposta resumida:

```json
{
  "id": "...",
  "customerId": "customer-123",
  "amount": 55.00,
  "status": "APPROVED",
  "createdAt": "...Z"
}
```

### GET /customers/{customerId}/payments

Resposta resumida:

```json
{
  "content": [
    {
      "id": "...",
      "customerId": "customer-123",
      "amount": 55.00,
      "status": "APPROVED"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10
  }
}
```

## Idempotência e Concorrência

- `Idempotency-Key` no header da requisição evita duplicação de criação.
- A tabela `idempotency_keys` usa `constraint UNIQUE` na chave de idempotência.
- O fluxo usa `INSERT ... ON CONFLICT DO NOTHING` para garantir que somente uma requisição registre a chave.
- A proteção transacional é feita com `@Transactional(isolation = Isolation.SERIALIZABLE)` e locks pessimistas em pontos críticos.
- O teste `ConcurrentPaymentCreationTest` executa 100 requisições concorrentes com a mesma chave e foi validado com sucesso:
  `Tests run: 1, Failures: 0, Errors: 0`
  `BUILD SUCCESS`

## Processamento Assíncrono

`POST` cria o pagamento com status `PENDING`. O evento de domínio é persistido e publicado após o commit da transação. Em `AFTER_COMMIT`, o listener assíncrono processa o pagamento e define o estado final como `APPROVED` ou `REJECTED`. O executor usa `Virtual Threads`.

## Banco de Dados

- PostgreSQL
- JPA/Hibernate
- Flyway
- `payments`
- `idempotency_keys`
- `payment_events`

## Testes

- Unitários
- Integração
- Concorrência
- Testcontainers

## Como executar

```bash
docker compose up -d
./mvnw spring-boot:run
./mvnw test
docker compose down
```

Exemplo curto de curl:

```bash
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: payment-key-123" \
  -d '{"customerId":"customer-123","amount":55.00}'
```

## Timezone

O projeto usa `java.time.Instant` e define UTC na configuração para manter consistência dos timestamps entre persistência e resposta da API. Essa escolha mantém dados temporais estáveis e previsíveis em todos os fluxos.

## Limitações e possíveis evoluções

- Kafka
- Redis
- observabilidade
- rate limiting
- circuit breaker

## Status

O núcleo do desafio técnico está implementado e validado: idempotência, concorrência, processamento assíncrono, PostgreSQL e testes automatizados. O projeto demonstra um fluxo real de criação e consulta de pagamentos com estabilidade sob carga concorrente.
