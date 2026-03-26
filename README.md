# api-request-logging-spring-boot-starter

A zero-boilerplate Spring Boot Auto-Configuration library that captures the **full HTTP request/response lifecycle** — headers, body, timing, correlation IDs, and third-party call logs — activated with a single property.

```properties
api.request.logging.enabled=true
```

Compatible with **Spring Boot 2.5+** and **Java 8+**.

---

## Table of Contents

- [Features](#features)
- [Quick Start](#quick-start)
- [Property Reference](#property-reference)
- [What Gets Logged](#what-gets-logged)
- [Logging Third-Party Calls from @Service](#logging-third-party-calls-from-service)
- [Correlation ID — How It Works](#correlation-id--how-it-works)
- [Filter Execution Order](#filter-execution-order)
- [StopWatch Version Compatibility](#stopwatch-version-compatibility)
- [Running the Demo Application](#running-the-demo-application)
- [Building and Publishing](#building-and-publishing)
- [Architecture Overview](#architecture-overview)

---

## Features

| Feature | Detail |
|---------|--------|
| **Zero config** | One property enables everything |
| **Correlation ID** | Reads from configurable headers (`X-Request-ID`, `request_id`, …); falls back to UUID |
| **Full body capture** | Request and response bodies via `ContentCachingRequestWrapper` / `ContentCachingResponseWrapper` |
| **Third-party call logging** | Inject `RequestLogCollector` in any `@Service` to log outgoing HTTP calls |
| **Retry-aware keys** | `buildRetryKey("ServiceName/endpoint")` → `"ServiceName/endpoint [14:32:05.001]"` — each retry is a separate log entry |
| **Request timing** | Spring `StopWatch` per request — formatted as `0h 0m 0s 312ms` |
| **Configurable exclusions** | Skip paths (`/actuator`, `/swagger-ui`) and extensions (`.js`, `.css`, …) via properties |
| **Body truncation** | `max-body-length` guards against heap pressure on large payloads |
| **ThreadLocal request ID** | `RequestLogCollector.currentRequestId()` — zero-injection access from anywhere on the request thread; integrates with SLF4J MDC |
| **Spring Boot 2.5+ & 3.x** | Both `spring.factories` and `AutoConfiguration.imports` are included |
| **Java 8 compatible** | No `var`, no `String.formatted()`, no `strip()` calls |

---

## Quick Start

### 1. Add the dependency

```xml
<dependency>
    <groupId>com.github.yash777</groupId>
    <artifactId>api-request-logging-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Enable in `application.properties`

```properties
api.request.logging.enabled=true
```

That's it. Every HTTP request now produces a structured console log block.

---

## Property Reference

All properties share the prefix `api.request.logging`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `false` | Master switch — set to `true` to activate all filters. When `false`, **zero overhead**: no filters, no beans, no body caching. |
| `request-id-headers` | `List<String>` | `X-Request-ID` | Ordered list of header names checked for an incoming correlation ID. First non-blank value wins; UUID fallback if none match. |
| `exclude-paths` | `List<String>` | `/actuator`, `/swagger-ui`, `/v3/api-docs`, … | URI prefixes skipped by both filters. |
| `exclude-extensions` | `List<String>` | `.js`, `.css`, `.html`, `.png`, `.ico`, … | File extension suffixes skipped by both filters. |
| `log-request-body` | `boolean` | `true` | Capture the HTTP request body. Set `false` for file-upload endpoints. |
| `log-response-body` | `boolean` | `true` | Capture the HTTP response body. |
| `log-headers` | `boolean` | `true` | Capture all request headers as a JSON object. |
| `max-body-length` | `int` | `4096` | Maximum characters stored from bodies. Longer bodies are truncated with `[TRUNCATED at N chars]`. Set `-1` for unlimited. |

### Full YAML example

```yaml
api:
  request:
    logging:
      enabled: true
      request-id-headers:
        - X-Request-ID
        - request_id
        - X-Correlation-ID
        - traceparent
      exclude-paths:
        - /actuator
        - /swagger-ui
        - /v3/api-docs
        - /internal/metrics
      exclude-extensions:
        - .js
        - .css
        - .png
        - .ico
      log-request-body: true
      log-response-body: true
      log-headers: true
      max-body-length: 8192
```

---

## What Gets Logged

For every HTTP request (that is not excluded) you will see a block like this in your console:

```log
=========== Request Logs [req-id: my-correlation-id-001] ===========

── INCOMING
   requestId:            my-correlation-id-001
   threadName:           http-nio-8080-exec-2
   url:                  /api/orders
   httpMethod:           POST
   timestamp:            25/3/2026, 10:32:15 am
   headers:              {"content-type":"application/json","x-request-id":"my-correlation-id-001","host":"localhost:8080"}
   requestBody:          {"customerId":"C-101","itemName":"Laptop","amount":999.99}
   responseStatus:       200
   responseBody:         {"orderId":"ORD-A1B2C3D4","status":"CONFIRMED","transactionId":"TXN-XY12"}
   requestProcessedTime: 0h 0m 0s 87ms

── InventoryService/reserve [10:32:15.031]
   itemName:  Laptop
   reserved:  true

── PaymentGateway/charge [10:32:15.042]
   request:   {"orderId":"ORD-A1B2C3D4","amount":999.99,"currency":"INR"}
   response:  {"txnId":"TXN-XY12","status":"SUCCESS","orderId":"ORD-A1B2C3D4","amount":999.99}

════════════════════════════════════════════════════════════
```

---

## Logging Third-Party Calls from @Service

Inject `RequestLogCollector` into any `@Service`. It is request-scoped with a CGLIB proxy — safe to hold in a singleton.

```java
@Service
public class PaymentService {

    private final RequestLogCollector collector;

    public PaymentService(RequestLogCollector collector) {
        this.collector = collector;
    }

    public PaymentResponse charge(PaymentRequest request) {

        // Each call to buildRetryKey stamps the current time.
        // If you retry, each attempt gets its own timestamped key → separate log entry.
        String key = collector.buildRetryKey("PaymentGateway/charge");

        collector.addLog(key, "request", request);           // log before HTTP call
        PaymentResponse res = gateway.post(request);         // actual HTTP call
        collector.addLog(key, "response", res);              // log after HTTP call

        return res;
    }
}
```

### Convenience method (no retry)

```java
collector.addRequestResponseLog("InventoryService/check", requestObj, responseObj);
```

### Retry pattern

```java
for (int attempt = 1; attempt <= 3; attempt++) {
    String key = collector.buildRetryKey("PaymentGateway/charge");  // new timestamp each time
    collector.addLog(key, "attempt", attempt);
    collector.addLog(key, "request", request);
    try {
        PaymentResponse res = gateway.post(request);
        collector.addLog(key, "response", res);
        return res;
    } catch (Exception e) {
        collector.addLog(key, "error", e.getMessage());
    }
}
```

---

## Correlation ID — How It Works

The correlation/idempotency ID is resolved in priority order:

1. Check each header in `api.request.logging.request-id-headers` (left to right)
2. Use the first non-blank value found
3. Fall back to `UUID.randomUUID().toString()`

The resolved ID is stored in three places:

| Location | How to access |
|----------|---------------|
| Request attribute | `(String) request.getAttribute("requestId")` |
| ThreadLocal | `RequestLogCollector.currentRequestId()` |
| Log collector | Automatically logged as `requestId` in the `INCOMING` block |

### MDC integration (Logback / Log4j2)

```java
// In a HandlerInterceptor or Servlet filter after the logging filter:
MDC.put("requestId", RequestLogCollector.currentRequestId());
```

Then in `logback.xml`:
```xml
<pattern>%d [%X{requestId}] %-5level %logger - %msg%n</pattern>
```

---

## Filter Execution Order

```
Order  -105   RequestContextFilter         ★ populates RequestContextHolder
                                             (@RequestScope becomes usable)
Order  -104   RequestBodyCachingFilter       wraps req + res in caching wrappers
Order  -103   ApiLoggingFilter              reads wrapped bodies, logs timing
Order  -100   Spring Security (if present)
              DispatcherServlet → @Controller → @Service
```

Both filters are registered via `FilterRegistrationBean` (not `@Order`) to prevent double-registration.

---

## StopWatch Version Compatibility

| Spring Boot | Spring Framework | Method available |
|-------------|-----------------|-----------------|
| 2.0.x       | 5.0.x           | `getTotalTimeMillis()` ✅ |
| 2.3.x       | 5.2.x           | `getTotalTimeNanos()` ✅ (added in 5.2) |
| 2.5.x – 3.x | 5.3.x / 6.x   | Both ✅ |

This starter uses `getTotalTimeMillis()` for broadest compatibility. If you are on Spring Boot 2.3+ and want nanosecond precision, you can fork `ApiLoggingFilter` and replace:

```java
// Current (millis — works on all versions):
long totalMillis = sw.getTotalTimeMillis();
formatElapsed(totalMillis);

// Optional upgrade (nanos — requires Spring Boot 2.3+ / Spring 5.2+):
long totalNanos = sw.getTotalTimeNanos();
formatElapsed(TimeUnit.NANOSECONDS.toMillis(totalNanos));
```

---

## Running the Demo Application

The starter ships with a built-in demo that includes `OrderController`, `PaymentController`, `OrderService`, and `PaymentService`.

```bash
# Clone and build
git clone https://github.com/Yash777/api-request-logging-spring-boot-starter.git
cd api-request-logging-spring-boot-starter
mvn spring-boot:run
```

### Try the endpoints

```bash
# 1. Create an order (full chain: INCOMING + InventoryService + PaymentGateway)
curl -X POST http://localhost:8080/api/orders \
     -H "Content-Type: application/json" \
     -H "X-Request-ID: my-trace-001" \
     -d '{"customerId":"C-101","itemName":"Laptop","amount":999.99}'

# 2. Get order by ID (INCOMING + OrderDB lookup)
curl http://localhost:8080/api/orders/ORD-ABC12345 \
     -H "X-Request-ID: my-trace-002"

# 3. Direct payment charge (INCOMING + PaymentGateway/charge)
curl -X POST http://localhost:8080/api/payments/charge \
     -H "Content-Type: application/json" \
     -H "X-Request-ID: my-trace-003" \
     -d '{"orderId":"ORD-99","amount":250.00}'

# 4. Payment status check (INCOMING + PaymentGateway/status)
curl http://localhost:8080/api/payments/status/TXN-ABCD1234 \
     -H "X-Request-ID: my-trace-004"

# 5. Test fallback correlation header
curl -X POST http://localhost:8080/api/orders \
     -H "Content-Type: application/json" \
     -H "request_id: fallback-header-test" \
     -d '{"customerId":"C-202","itemName":"Mouse","amount":29.99}'

# 6. Actuator health — excluded, no logging overhead
curl http://localhost:8080/actuator/health
```

#### endpoints - loggs
```log
=========== Request Logs [req-id: my-trace-001] ===========
── INCOMING
   requestId: my-trace-001
   threadName: http-nio-8080-exec-2
   url: /api/orders
   httpMethod: POST
   timestamp: 26/3/2026, 11:47:18 am
   headers: {"content-type":"application/json","x-request-id":"my-trace-001","user-agent":"PostmanRuntime/7.52.0","accept":"*/*","cache-control":"no-cache","postman-token":"1c61a106-8c52-4b8f-be7b-a06e2c39800b","host":"localhost:8080","accept-encoding":"gzip, deflate, br","connection":"keep-alive","content-length":"62"}
   requestBody: {"customerId":"C-101","itemName":"Laptop","amount":999.99} 
   responseStatus: 200
   responseBody: {"orderId":"ORD-DED4B762","status":"CONFIRMED","customerId":"C-101","itemName":"Laptop","amount":999.99,"transactionId":"TXN-9249B0EB","requestId":"my-trace-001"}
   requestProcessedTime: 0h 0m 0s 309ms

── InventoryService/reserve [11:47:19.219]
   itemName: Laptop
   reserved: true

── PaymentGateway/charge [11:47:19.242]
   request: {"orderId":"ORD-DED4B762","amount":999.99,"currency":"INR"}
   response: {"txnId":"TXN-9249B0EB","status":"SUCCESS","orderId":"ORD-DED4B762","amount":999.99}

════════════════════════════════════════════════════════

[2m2026-03-26 11:47:28.834[0;39m [32m INFO[0;39m [35m3192[0;39m [2m---[0;39m [2m[nio-8080-exec-5][0;39m [36mo.springdoc.api.AbstractOpenApiResource [0;39m [2m:[0;39m Init duration for springdoc-openapi is: 360 ms

=========== Request Logs [req-id: my-trace-002] ===========
── INCOMING
   requestId: my-trace-002
   threadName: http-nio-8080-exec-10
   url: /api/orders/ORD-ABC12345
   httpMethod: GET
   timestamp: 26/3/2026, 11:51:57 am
   headers: {"x-request-id":"my-trace-002","user-agent":"PostmanRuntime/7.52.0","accept":"*/*","cache-control":"no-cache","postman-token":"1344d27b-4f07-4a4f-be19-8910e3ae7e14","host":"localhost:8080","accept-encoding":"gzip, deflate, br","connection":"keep-alive"}
   responseStatus: 200
   responseBody: {"orderId":"ORD-ABC12345","status":"CONFIRMED","customerId":"C-101","itemName":"Laptop","amount":999.99,"transactionId":"TXN-DEMO1234","requestId":"my-trace-002"}
   requestProcessedTime: 0h 0m 0s 11ms

── OrderDB/findById [11:51:57.723]
   query: SELECT * FROM orders WHERE id = 'ORD-ABC12345'
   rowsFound: 1

════════════════════════════════════════════════════════


=========== Request Logs [req-id: my-trace-003] ===========
── INCOMING
   requestId: my-trace-003
   threadName: http-nio-8080-exec-3
   url: /api/payments/charge
   httpMethod: POST
   timestamp: 26/3/2026, 11:54:12 am
   headers: {"content-type":"application/json","x-request-id":"my-trace-003","user-agent":"PostmanRuntime/7.52.0","accept":"*/*","cache-control":"no-cache","postman-token":"5f3c8a57-2204-4b0c-82d5-15ecd9bf689e","host":"localhost:8080","accept-encoding":"gzip, deflate, br","connection":"keep-alive","content-length":"36"}
   requestBody: {"orderId":"ORD-99","amount":250.00}
   responseStatus: 200
   responseBody: {"txnId":"TXN-CC648529","status":"SUCCESS","orderId":"ORD-99","amount":250.0}
   requestProcessedTime: 0h 0m 0s 36ms

── PaymentGateway/charge [11:54:12.601]
   request: {"orderId":"ORD-99","amount":250.0,"currency":null}
   response: {"txnId":"TXN-CC648529","status":"SUCCESS","orderId":"ORD-99","amount":250.0}

════════════════════════════════════════════════════════


=========== Request Logs [req-id: my-trace-004] ===========
── INCOMING
   requestId: my-trace-004
   threadName: http-nio-8080-exec-4
   url: /api/payments/status/TXN-ABCD1234
   httpMethod: GET
   timestamp: 26/3/2026, 11:55:10 am
   headers: {"x-request-id":"my-trace-004","user-agent":"PostmanRuntime/7.52.0","accept":"*/*","cache-control":"no-cache","postman-token":"3f3b0c9d-1f88-4ece-bfa4-9c27b24ab075","host":"localhost:8080","accept-encoding":"gzip, deflate, br","connection":"keep-alive"}
   responseStatus: 200
   responseBody: {"txnId":"TXN-ABCD1234","status":"CAPTURED","orderId":"ORD-UNKNOWN","amount":0.0}
   requestProcessedTime: 0h 0m 0s 2ms

── PaymentGateway/status [11:55:10.376]
   txnId: TXN-ABCD1234
   response: {"txnId":"TXN-ABCD1234","status":"CAPTURED","orderId":"ORD-UNKNOWN","amount":0.0}

════════════════════════════════════════════════════════


=========== Request Logs [req-id: fallback-header-test] ===========
── INCOMING
   requestId: fallback-header-test
   threadName: http-nio-8080-exec-7
   url: /api/orders
   httpMethod: POST
   timestamp: 26/3/2026, 11:56:31 am
   headers: {"content-type":"application/json","request_id":"fallback-header-test","user-agent":"PostmanRuntime/7.52.0","accept":"*/*","cache-control":"no-cache","postman-token":"af012637-4715-45b6-8ebd-777acd932629","host":"localhost:8080","accept-encoding":"gzip, deflate, br","connection":"keep-alive","content-length":"56"}
   requestBody: {"customerId":"C-202","itemName":"Mouse","amount":29.99}
   responseStatus: 200
   responseBody: {"orderId":"ORD-4A7F84BC","status":"CONFIRMED","customerId":"C-202","itemName":"Mouse","amount":29.99,"transactionId":"TXN-C38810B9","requestId":"fallback-header-test"}
   requestProcessedTime: 0h 0m 0s 56ms

── InventoryService/reserve [11:56:31.715]
   itemName: Mouse
   reserved: true

── PaymentGateway/charge [11:56:31.736]
   request: {"orderId":"ORD-4A7F84BC","amount":29.99,"currency":"INR"}
   response: {"txnId":"TXN-C38810B9","status":"SUCCESS","orderId":"ORD-4A7F84BC","amount":29.99}

════════════════════════════════════════════════════════
```

---

## Building and Publishing

```bash
# Build and run all tests
mvn clean verify

# Install to local Maven repo (~/.m2)
mvn clean install

# Generate sources + Javadoc JARs
mvn clean package

# Deploy to Maven Central (requires GPG key + Sonatype credentials in settings.xml)
mvn clean deploy -P release
```

### Using from another project

After `mvn install`, add to any Spring Boot project:

```xml
<dependency>
    <groupId>com.github.yash777</groupId>
    <artifactId>api-request-logging-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Then add to `application.properties`:
```properties
api.request.logging.enabled=true
```

---

## Architecture Overview

```
api-request-logging-spring-boot-starter
│
├── autoconfigure/
│   └── ApiRequestLoggingAutoConfiguration   ← Spring Boot SPI entry point
│                                               @ConditionalOnProperty(enabled=true)
│                                               @EnableConfigurationProperties
│
├── properties/
│   └── ApiRequestLoggingProperties           ← @ConfigurationProperties(prefix="api.request.logging")
│                                               IDE auto-complete, type-safe binding
│
├── filter/
│   ├── RequestBodyCachingFilter              ← Order -104: wraps req+res in caching wrappers
│   └── ApiLoggingFilter                      ← Order -103: captures metadata, timing, bodies
│       └── FilterOrderConfig (inner)         ← registers both filters via FilterRegistrationBean
│
├── collector/
│   └── RequestLogCollector                   ← @RequestScope + CGLIB proxy
│                                               one instance per HTTP request
│                                               injectable from any @Service / @Component
│
└── util/
    └── TimestampUtils                        ← thread-safe timestamp formatting

META-INF/
├── spring.factories                          ← Spring Boot 2.x SPI registration
└── spring/
    └── org...AutoConfiguration.imports       ← Spring Boot 3.x SPI registration
```

### Key design decisions

| Decision | Reason |
|----------|--------|
| `@RequestScope` + CGLIB proxy on `RequestLogCollector` | Allows singleton filters/services to hold a reference without thread-safety concerns |
| `FilterRegistrationBean` instead of `@Order` | Prevents double-registration (Spring Boot auto-registers `@Component` filters and `@Order` together) |
| `StopWatch` as local variable | Thread safety by design — each request thread owns its own instance; no synchronisation needed |
| `ThreadLocal<String> CURRENT_REQUEST_ID` | Zero-injection access to correlation ID from async methods, MDC, utility classes |
| `@PreDestroy cleanup()` | Removes ThreadLocal to prevent request-ID leaks in Tomcat's thread pool |
| Java 8 compatible | `String.format` not `String.formatted()`, `trim()` not `strip()`, `getTotalTimeMillis()` not `getTotalTimeNanos()` |
| `provided` scope for `spring-boot-starter-web` | Consumer's classpath already has it; avoids duplicate-class conflicts |

---

## License

Apache License, Version 2.0 — see [LICENSE](LICENSE) for details.
