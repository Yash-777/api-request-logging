# api-request-logging-spring-boot-starter

[![Maven Central](https://img.shields.io/maven-central/v/io.github.yash-777/api-request-logging-spring-boot-starter)](https://central.sonatype.com/artifact/io.github.yash-777/api-request-logging-spring-boot-starter)
[![Java 8+](https://img.shields.io/badge/Java-8%2B-blue)](https://www.java.com)
[![Spring Boot 2.x](https://img.shields.io/badge/Spring%20Boot-2.x-green)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](https://www.apache.org/licenses/LICENSE-2.0)
[![SSL PKIX](https://img.shields.io/badge/SSL-PKIX%20Trust-brightgreen)](https://docs.oracle.com/javase/8/docs/technotes/guides/security/certpath/CertPathProgGuide.html)

A zero-boilerplate Spring Boot Auto-Configuration library that captures the **full HTTP request/response lifecycle** — headers, body, timing, correlation IDs, and third-party call logs — activated with a single property.

```properties
api.request.logging.enabled=true
```

Compatible with **Spring Boot 2.0.x+** and **Java 8+**.
_Tested using Spring Boot `2.0.1.RELEASE` as the minimum supported version._

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
| **Spring Boot 2.x & 3.x** | Both `spring.factories` and `AutoConfiguration.imports` are included |
| **Java 8 compatible** | No `var`, no `String.formatted()`, no `strip()` calls |

---

## Quick Start

### 1. Add the dependency

Available on Maven Central — download from any of these locations:

| Repository | Link |
|------------|------|
| Sonatype Central (new portal) | [api-request-logging-spring-boot-starter](https://central.sonatype.com/artifact/io.github.yash-777/api-request-logging-spring-boot-starter) |
| Maven Central (repo1 — canonical) | [1.0.1 directory](https://repo1.maven.org/maven2/io/github/yash-777/api-request-logging-spring-boot-starter/1.0.1/) |
| mvnrepository.com | [1.0.1 listing](https://mvnrepository.com/artifact/io.github.yash-777/api-request-logging-spring-boot-starter/1.0.1) |

```xml
<dependency>
    <groupId>io.github.yash-777</groupId>
    <artifactId>api-request-logging-spring-boot-starter</artifactId>
    <version>1.0.1</version>
</dependency>
```

> **Note:** Version `1.0.0` was published as an executable fat JAR (`BOOT-INF/classes/…`) by mistake.
> Spring Boot's auto-configuration scanner cannot find classes inside `BOOT-INF/classes/`, which causes:
> ```
> java.io.FileNotFoundException: class path resource
>   [com/github/yash777/apirequestlogging/autoconfigure/ApiRequestLoggingAutoConfiguration.class]
>   cannot be opened because it does not exist
> ```
> Use **`1.0.1`** which is correctly packaged as a plain library JAR (`com/…` at the root).

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
        - requestId
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
        // buildRetryKey stamps the current time → each retry gets its own entry
        String key = collector.buildRetryKey("PaymentGateway/charge");

        collector.addLog(key, RequestLogCollector.LOG_REQUEST, request);   // before call

        PaymentResponse res = null;
        try {
            res = gateway.post(request);                                    // actual HTTP call
        } catch (Exception e) {
            // Stack trace is automatically truncated to 5 lines
            collector.addLog(key, RequestLogCollector.LOG_EXCEPTION, e);
        } finally {
            collector.addLog(key, RequestLogCollector.LOG_RESPONSE, res);  // null-safe
        }
        return res;
    }
}
```

### Standard inner-key constants

| Constant | Value | When to use |
|----------|-------|-------------|
| `RequestLogCollector.LOG_REQUEST` | `"request"` | Outgoing payload — log before the HTTP call |
| `RequestLogCollector.LOG_RESPONSE` | `"response"` | Incoming response — log in `finally` block (null-safe) |
| `RequestLogCollector.LOG_EXCEPTION` | `"exception"` | Caught `Throwable` — stack trace auto-truncated to 5 lines |

### Convenience method (no retry)

```java
collector.addRequestResponseLog("InventoryService/check", requestObj, responseObj);
```

### Retry pattern

```java
for (int attempt = 1; attempt <= 3; attempt++) {
    String key = collector.buildRetryKey("PaymentGateway/charge");  // new timestamp each time
    collector.addLog(key, RequestLogCollector.LOG_REQUEST, request);
    try {
        PaymentResponse res = gateway.post(request);
        collector.addLog(key, RequestLogCollector.LOG_RESPONSE, res);
        return res;
    } catch (Exception e) {
        collector.addLog(key, RequestLogCollector.LOG_EXCEPTION, e);
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

For a deep-dive on filter ordering, `@RequestScope` internals, `HIGHEST_PRECEDENCE` traps, and Spring Security's two-layer architecture see the [Filter Order Wiki](https://github.com/Yash777/api-request-logging-spring-boot-starter/wiki/Filter-Execution-Order).

---

## StopWatch Version Compatibility

| Spring Boot | Spring Framework | Method available |
|-------------|-----------------|-----------------|
| 2.0.x       | 5.0.x           | `getTotalTimeMillis()` ✅ |
| 2.3.x       | 5.2.x           | `getTotalTimeNanos()` ✅ (added in 5.2) |
| 2.5.x – 3.x | 5.3.x / 6.x    | Both ✅ |

This starter uses `getTotalTimeMillis()` for broadest compatibility. If you are on Spring Boot 2.3+ and want nanosecond precision, replace the call in `ApiLoggingFilter`:

```java
// Current (millis — works on all versions):
long totalMillis = sw.getTotalTimeMillis();

// Optional upgrade (nanos — requires Spring Boot 2.3+ / Spring 5.2+):
long totalMillis = TimeUnit.NANOSECONDS.toMillis(sw.getTotalTimeNanos());
```

---

## Running the Demo Application

The starter ships with a built-in demo (`OrderController`, `PaymentController`, `OrderService`, `PaymentService`) that is activated **only** when you run this project directly — it is never registered in a consumer application's context.

```bash
git clone https://github.com/Yash777/api-request-logging-spring-boot-starter.git
cd api-request-logging-spring-boot-starter
mvn spring-boot:run
```

### Demo endpoints

```bash
# 1. Create an order (INCOMING + InventoryService + PaymentGateway)
curl -X POST http://localhost:8080/api-request-logging-demo/api/orders \
     -H "Content-Type: application/json" \
     -H "X-Request-ID: my-trace-001" \
     -d '{"customerId":"C-101","itemName":"Laptop","amount":999.99}'

# 2. Get order by ID (INCOMING + OrderDB lookup)
curl http://localhost:8080/api-request-logging-demo/api/orders/ORD-ABC12345 \
     -H "X-Request-ID: my-trace-002"

# 3. Direct payment charge (INCOMING + PaymentGateway/charge)
curl -X POST http://localhost:8080/api-request-logging-demo/api/payments/charge \
     -H "Content-Type: application/json" \
     -H "X-Request-ID: my-trace-003" \
     -d '{"orderId":"ORD-99","amount":250.00}'

# 4. Payment status check (INCOMING + PaymentGateway/status)
curl http://localhost:8080/api-request-logging-demo/api/payments/status/TXN-ABCD1234 \
     -H "X-Request-ID: my-trace-004"

# 5. Fallback correlation header (request_id instead of X-Request-ID)
curl -X POST http://localhost:8080/api-request-logging-demo/api/orders \
     -H "Content-Type: application/json" \
     -H "request_id: fallback-header-test" \
     -d '{"customerId":"C-202","itemName":"Mouse","amount":29.99}'

# 6. Actuator health — excluded from logging
curl http://localhost:8080/api-request-logging-demo/actuator/health
```

### How demo beans are isolated from consumer apps

Demo beans carry `@ConditionalOnDemoEnvironment`, which delegates to `DemoEnvironmentCondition`. That condition returns `DemoApplication.getNonConsumer()` — a static flag set to `true` only inside `DemoApplication.main()` before `SpringApplication.run()` is called.

A consumer app's own `main()` never calls `DemoApplication.main()`, so the flag stays `false` and all demo beans (`OrderController`, `OrderService`, etc.) are silently skipped. No property, annotation, or test fixture can replicate the JVM entry-point guarantee.

---

## Building and Publishing

```bash
# Build and run all tests
mvn clean verify

# Install to local Maven repo (~/.m2)
mvn clean install

# Deploy to Maven Central (requires GPG key + Sonatype credentials in settings.xml)
mvn clean deploy -P release
```

> **Release workflow:** `mvn clean deploy -P release` is the complete release command.
> `mvn release:prepare` and `mvn release:perform` are **not needed** — they require a
> `-SNAPSHOT` version in `pom.xml` and an SCM URL, neither of which applies here.
> The `central-publishing-maven-plugin` handles upload and automatic publication directly.

### Adding to your project (after `mvn install`)

```xml
<dependency>
    <groupId>io.github.yash-777</groupId>
    <artifactId>api-request-logging-spring-boot-starter</artifactId>
    <version>1.0.1</version>
</dependency>
```

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
├── util/
│   └── TimestampUtils                        ← thread-safe timestamp formatting
│
└── demo/                                     ← only active when DemoApplication.main() runs
    ├── DemoApplication                       ← sets nonConsumer=true before SpringApplication.run()
    ├── condition/
    │   ├── ConditionalOnDemoEnvironment      ← composed @Conditional annotation
    │   └── DemoEnvironmentCondition          ← returns DemoApplication.getNonConsumer()
    ├── controller/
    │   ├── OrderController
    │   └── PaymentController
    └── service/
        ├── OrderService
        └── PaymentService

META-INF/
├── spring.factories                          ← Spring Boot 2.x SPI registration
└── spring/
    └── org...AutoConfiguration.imports       ← Spring Boot 3.x SPI registration
```

### Key design decisions

| Decision | Reason |
|----------|--------|
| `@RequestScope` + CGLIB proxy on `RequestLogCollector` | Allows singleton filters/services to hold a reference without thread-safety concerns |
| `FilterRegistrationBean` instead of `@Order` | Prevents double-registration — Spring Boot auto-registers `@Component` filters and `@Order` together causing the filter to run twice per request |
| `StopWatch` as local variable | Thread safety by design — each request thread owns its own instance; no synchronisation needed |
| `ThreadLocal<String> CURRENT_REQUEST_ID` | Zero-injection access to correlation ID from async methods, MDC, utility classes |
| `@PreDestroy cleanup()` | Removes ThreadLocal to prevent request-ID leaks in Tomcat's thread pool |
| `DemoApplication.nonConsumer` static flag | The only unforgeable demo isolation guard — set exclusively in `main()` before Spring starts; no property can replicate it |
| Plain library JAR (not fat JAR) | `spring-boot-maven-plugin` repackage is skipped — classes must be at JAR root for Spring's auto-config scanner to find them |
| Java 8 compatible | `String.format` not `String.formatted()`, `trim()` not `strip()`, `getTotalTimeMillis()` not `getTotalTimeNanos()` |
| `provided` scope for `spring-boot-starter-web` | Consumer's classpath already has it; avoids duplicate-class conflicts |

---

## Version History

| Version | Change |
|---------|--------|
| `1.0.1` | **Fix:** JAR now published as a plain library JAR. Version `1.0.0` was accidentally packaged as a Spring Boot fat JAR (`BOOT-INF/classes/…`), causing `FileNotFoundException` for `ApiRequestLoggingAutoConfiguration.class` in consumer applications. |
| `1.0.0` | Initial release — **do not use** (fat JAR packaging defect). |

---

## License

Apache License, Version 2.0 — see [LICENSE](LICENSE) for details.
