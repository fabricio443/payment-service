# Payment Service

API REST para criação e consulta de pagamentos, desenvolvida com Java 21 e Spring Boot, com foco em idempotência, concorrência, consistência transacional e processamento assíncrono.

O projeto utiliza PostgreSQL como banco principal, Flyway para versionamento do schema e Testcontainers para testes de integração com PostgreSQL real.

## Objetivos técnicos

O projeto foi desenvolvido para demonstrar, de forma prática:

- criação e consulta de pagamentos por API REST;
- idempotência baseada em `Idempotency-Key`;
- proteção contra race conditions em requisições concorrentes;
- transações e controle de isolamento no PostgreSQL;
- persistência e publicação de eventos internos;
- processamento assíncrono após o commit da transação;
- uso de Virtual Threads;
- separação entre operações de comando e consulta;
- testes unitários, de integração e de concorrência.

## Stack

- Java 21
- Spring Boot 4.1.1
- Spring Web MVC
- Spring Data JPA / Hibernate
- PostgreSQL 16
- Flyway
- Docker / Docker Compose
- Maven
- JUnit 5
- Mockito
- Testcontainers
- Awaitility

## Arquitetura

A aplicação organiza o fluxo principal entre controllers, camada de aplicação, serviços especializados, repositórios, domínio e eventos.

```text
                    HTTP
                     │
                     ▼
              ┌─────────────┐
              │ Controllers │
              └──────┬──────┘
                     │
             ┌───────┴───────┐
             ▼               ▼
       Command App       Query App
             │               │
             ▼               ▼
     Serviços específicos  Repository
             │               │
             ▼               ▼
         Repository      PostgreSQL
             │
             ▼
         PostgreSQL
             │
             ▼
       PaymentEvent
             │
             ▼
        AFTER_COMMIT
             │
             ▼
       Async Listener
             │
             ▼
   PaymentProcessorService
             │
        ┌────┴────┐
        ▼         ▼
    APPROVED   REJECTED
```

### Command e Query

A separação entre comando e consulta está implementada na camada `application`:

- `application/command/PaymentCommandService`: cria pagamentos, coordena a idempotência e registra/publica o evento de criação.
- `application/query/PaymentQueryService`: realiza consultas por ID e por cliente, incluindo paginação.

Essa separação é uma organização lógica de Command/Query. O projeto não implementa CQRS completo com bancos ou modelos de leitura separados.

### Serviços especializados

O pacote `service` contém responsabilidades específicas utilizadas pela camada de aplicação, entre elas:

- `IdempotencyService`: controla a criação/reutilização da operação associada à `Idempotency-Key`.
- `PaymentEventService`: registra eventos no banco e publica eventos internos.
- `PaymentProcessorService`: executa o processamento do pagamento e atualiza seu status.

O pacote `infrastructure` contém atualmente a abstração `PaymentProcessor`; ele não representa uma camada de infraestrutura extensa ou um broker externo implementado.

## Estrutura principal

```text
src/main/java/com/fabricio/payments
├── application
│   ├── command
│   │   └── PaymentCommandService.java
│   └── query
│       └── PaymentQueryService.java
├── config
│   └── AsyncConfig.java
├── controller
│   ├── PaymentCommandController.java
│   └── PaymentQueryController.java
├── domain
│   ├── IdempotencyKey.java
│   ├── Payment.java
│   ├── PaymentStatus.java
│   └── event
├── dto
│   ├── CreatePaymentRequest.java
│   ├── PaymentMapper.java
│   └── PaymentResponse.java
├── event
│   ├── PaymentDomainEvent.java
│   └── listener
├── exception
├── infrastructure
│   └── PaymentProcessor.java
├── repository
└── service
```

## Fluxo de criação de pagamento

O fluxo principal de `POST /payments` é:

```text
POST /payments
      │
      ▼
PaymentCommandController
      │
      ▼
PaymentCommandService
      │
      ▼
IdempotencyService
      │
      ├── chave nova ──► cria Payment PENDING
      │                       │
      │                       ▼
      │                PaymentEventService
      │                       │
      │                       ▼
      │                 commit da transação
      │                       │
      │                       ▼
      │              PaymentEventListener
      │                       │
      │                       ▼
      │                processamento async
      │                       │
      │                       ▼
      │               APPROVED / REJECTED
      │
      └── chave existente ──► recupera o pagamento associado à chave
```

O controller retorna `201 Created` após a criação bem-sucedida do pagamento.

## Idempotência e concorrência

A API exige o header `Idempotency-Key` no endpoint de criação.

A proteção contra duplicidade é coordenada pelo PostgreSQL, e não por estruturas de sincronização em memória.

O mecanismo utiliza:

- `idempotency_key` como chave primária na tabela `idempotency_keys`;
- `INSERT ... ON CONFLICT DO NOTHING` para disputar atomicamente o registro da chave;
- transação com isolamento `SERIALIZABLE` no fluxo de idempotência;
- lock pessimista (`PESSIMISTIC_WRITE`) nos pontos críticos de leitura da chave;
- persistência de `response_body`, `status_code` e `payment_id` associados à chave de idempotência.

Nas requisições subsequentes, o pagamento é recuperado pelo `payment_id` persistido na chave de idempotência e convertido novamente para `PaymentResponse`.

Isso permite que múltiplas requisições concorrentes com a mesma chave disputem o mesmo registro no banco, evitando a criação de múltiplos pagamentos para a mesma operação.

### Teste de concorrência

`ConcurrentPaymentCreationTest` cobre o cenário de 100 requisições concorrentes usando a mesma `Idempotency-Key`.

O teste verifica principalmente:

- ausência de respostas HTTP `500`;
- existência de apenas um pagamento persistido;
- existência de um único registro de idempotência;
- associação da chave ao pagamento criado;
- respostas com status HTTP `200` ou `201`.

O teste é executado como teste de integração e utiliza PostgreSQL fornecido pelo Testcontainers.

## Processamento assíncrono e eventos

O pagamento é criado inicialmente com status `PENDING`.

O evento de criação é persistido em `payment_events` e publicado como evento interno. O processamento posterior ocorre somente após o commit da transação:

```text
Transação
   │
   ├── Payment
   └── PaymentEvent
        │
        ▼
     COMMIT
        │
        ▼
AFTER_COMMIT listener
        │
        ▼
@Async("paymentTaskExecutor")
        │
        ▼
PaymentProcessorService
        │
        ├── APPROVED
        └── REJECTED
```

O executor assíncrono utiliza `SimpleAsyncTaskExecutor` com Virtual Threads e limite de concorrência configurado em 64 tarefas.

Os eventos são internos à própria aplicação. Não há Kafka, RabbitMQ ou outro broker distribuído implementado no projeto.

O registro `payment_events` também não caracteriza Event Sourcing; ele funciona como registro dos eventos relacionados ao fluxo de pagamento e suporte ao processamento assíncrono.

## API

### Criar pagamento

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

Resposta inicial:

```json
{
  "id": "...",
  "customerId": "customer-123",
  "amount": 55.00,
  "status": "PENDING",
  "createdAt": "...Z"
}
```

### Buscar pagamento por ID

```http
GET /payments/{id}
```

A consulta retorna os dados do pagamento, incluindo seu status atual.

### Buscar pagamentos por cliente

```http
GET /customers/{customerId}/payments
```

A consulta utiliza paginação do Spring Data. Exemplo:

```http
GET /customers/customer-123/payments?page=0&size=10&sort=createdAt,desc
```

## Banco de dados

O banco principal é PostgreSQL 16.

O schema é versionado pelo Flyway. As migrations atuais criam as principais estruturas:

```text
payments
idempotency_keys
payment_events
```

As migrations ficam em:

```text
src/main/resources/db/migration/
```

## Testes

O projeto possui diferentes níveis de testes, incluindo:

- testes unitários;
- testes de controllers;
- testes de domínio;
- testes de serviços;
- testes de repositórios;
- testes de integração;
- teste de concorrência com 100 requisições;
- testes com PostgreSQL real utilizando Testcontainers;
- testes assíncronos utilizando Awaitility.

Para executar a suíte:

```bash
./mvnw test
```

Para uma validação completa do projeto:

```bash
./mvnw clean verify
```

## Como executar localmente

### Pré-requisitos

- Java 21
- Docker
- Docker Compose

### 1. Subir o PostgreSQL

```bash
docker compose up -d
```

### 2. Executar a aplicação

```bash
./mvnw spring-boot:run
```

A API fica disponível em:

```text
http://localhost:8080
```

### 3. Criar um pagamento

```bash
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: payment-key-123" \
  -d '{"customerId":"customer-123","amount":55.00}'
```

### 4. Encerrar o ambiente

```bash
docker compose down
```

## Timezone

A aplicação utiliza `java.time.Instant` para representar timestamps e configura UTC como timezone da aplicação. Isso mantém os valores temporais consistentes entre persistência e respostas da API.

## Limitações e possíveis evoluções

O projeto demonstra os mecanismos centrais do desafio, mas não implementa uma arquitetura completa de produção para todos os cenários de escala e resiliência.

Possíveis evoluções:

- política robusta de retry e tratamento de falhas assíncronas;
- Dead Letter Queue (DLQ);
- Outbox Pattern para garantir publicação confiável de eventos;
- observabilidade com métricas, tracing e dashboards;
- rate limiting;
- circuit breaker;
- Kafka ou outro broker distribuído, caso o domínio exija mensageria externa;
- Redis ou outro mecanismo de cache, caso métricas indiquem necessidade;
- evolução da separação Command/Query para uma implementação CQRS mais completa, se houver necessidade real.

## Status

O projeto possui uma implementação funcional de API REST para pagamentos, com idempotência baseada em banco, controle de concorrência, processamento assíncrono, persistência de eventos, PostgreSQL, Flyway, Docker e testes automatizados.

O foco atual está nos fundamentos de consistência, concorrência e processamento assíncrono, sem adicionar componentes distribuídos que não sejam necessários para o escopo atual.
