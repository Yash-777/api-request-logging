<html>
<body>
<!--StartFragment--><html><head></head><body><h1>api-request-logging-spring-boot-starter</h1>

[![Maven Central](https://img.shields.io/maven-central/v/io.github.yash-777/api-request-logging-spring-boot-starter)](https://central.sonatype.com/artifact/io.github.yash-777/api-request-logging-spring-boot-starter)
[![Java 8+](https://img.shields.io/badge/Java-8%2B-blue)](https://www.java.com)
[![Spring Boot 2.x](https://img.shields.io/badge/Spring%20Boot-2.x-green)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](https://www.apache.org/licenses/LICENSE-2.0)
[![SLF4J](https://img.shields.io/badge/Logging-SLF4J-orange)](https://www.slf4j.org)
[![Logback](https://img.shields.io/badge/Logging-Logback-brightgreen)](https://logback.qos.ch)
[![System.out](https://img.shields.io/badge/Logging-System.out-yellow)](https://docs.oracle.com/javase/8/docs/api/java/io/PrintStream.html)


<p>A zero-boilerplate Spring Boot Auto-Configuration library that captures the <strong>full HTTP request/response lifecycle</strong> — headers, body, timing, correlation IDs, controller handler, RestTemplate calls, and third-party service logs — activated with a single property.</p>
<pre><code class="language-properties">api.request.logging.enabled=true
</code></pre>
<p>Compatible with <strong>Spring Boot 2.0.x+</strong> and <strong>Java 8+</strong>.<br>
<em>Tested against Spring Boot <code>2.0.1.RELEASE</code> as the minimum supported version.</em></p>

<hr>
<h2>Table of Contents</h2>
<ul>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#version-history">Version History</a></li>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#features">Features</a></li>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#quick-start">Quick Start</a></li>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#property-reference">Property Reference</a></li>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#what-gets-logged">What Gets Logged</a></li>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#new-in-v110">New in v1.1.0</a>
<ul>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#aop-controller-handler-auto-capture">AOP Controller Handler Auto-Capture</a></li>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#resttemplate-auto-capture">RestTemplate Auto-Capture</a></li>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#slf4j-logger-output">SLF4J Logger Output</a></li>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#secret-masking">Secret Masking</a></li>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#multipart--binary-request-skip">Multipart / Binary Request Skip</a></li>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#short-error-indicator">Short Error Indicator</a></li>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#header-skip-list">Header Skip List</a></li>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#redirect-path-capture">Redirect Path Capture</a></li>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#request-body-type-detection">Request Body Type Detection</a></li>
</ul>
</li>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#logging-third-party-calls-from-service">Logging Third-Party Calls from @Service</a></li>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#correlation-id--how-it-works">Correlation ID — How It Works</a></li>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#filter-execution-order">Filter Execution Order</a></li>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#stopwatch-version-compatibility">StopWatch Version Compatibility</a></li>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#running-the-demo-application">Running the Demo Application</a></li>
<li><a href="https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#architecture-overview">Architecture Overview</a></li>
</ul>
<hr>
<h2>Version History</h2>

Version | Status | Notes
-- | -- | --
1.1.0 | ✅ Current | AOP handler capture, RestTemplate logging, SLF4J, secret masking, multipart skip, error indicators — see What's new
1.0.1 | ✅ Stable | Plain library JAR fix — use if v1.1.0 features are not needed
1.0.0 | ❌ Do not use | Published as a fat JAR — classes at BOOT-INF/classes/, auto-configuration fails in consumer apps


<hr>
<h2>License</h2>
<p>Apache License, Version 2.0 — see <a href="https://github.com/Yash-777/api-request-logging/blob/main/LICENSE">LICENSE</a> for details.</p></body></html><!--EndFragment-->
</body>
</html># api-request-logging-spring-boot-starter

A zero-boilerplate Spring Boot Auto-Configuration library that captures the **full HTTP request/response lifecycle** — headers, body, timing, correlation IDs, controller handler, RestTemplate calls, and third-party service logs — activated with a single property.

```properties
api.request.logging.enabled=true
```

Compatible with **Spring Boot 2.0.x+** and **Java 8+**.  
*Tested against Spring Boot `2.0.1.RELEASE` as the minimum supported version.*

[![Maven Central](https://img.shields.io/maven-central/v/io.github.yash-777/api-request-logging-spring-boot-starter)](https://central.sonatype.com/artifact/io.github.yash-777/api-request-logging-spring-boot-starter)

---

## Table of Contents

- [[Version History](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#version-history)](#version-history)
- [[Features](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#features)](#features)
- [[Quick Start](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#quick-start)](#quick-start)
- [[Property Reference](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#property-reference)](#property-reference)
- [[What Gets Logged](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#what-gets-logged)](#what-gets-logged)
- [[New in v1.1.0](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#new-in-v110)](#new-in-v110)
  - [[AOP Controller Handler Auto-Capture](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#aop-controller-handler-auto-capture)](#aop-controller-handler-auto-capture)
  - [[RestTemplate Auto-Capture](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#resttemplate-auto-capture)](#resttemplate-auto-capture)
  - [[SLF4J Logger Output](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#slf4j-logger-output)](#slf4j-logger-output)
  - [[Secret Masking](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#secret-masking)](#secret-masking)
  - [[Multipart / Binary Request Skip](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#multipart--binary-request-skip)](#multipart--binary-request-skip)
  - [[Short Error Indicator](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#short-error-indicator)](#short-error-indicator)
  - [[Header Skip List](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#header-skip-list)](#header-skip-list)
  - [[Redirect Path Capture](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#redirect-path-capture)](#redirect-path-capture)
  - [[Request Body Type Detection](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#request-body-type-detection)](#request-body-type-detection)
- [[Logging Third-Party Calls from @Service](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#logging-third-party-calls-from-service)](#logging-third-party-calls-from-service)
- [[Correlation ID — How It Works](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#correlation-id--how-it-works)](#correlation-id--how-it-works)
- [[Filter Execution Order](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#filter-execution-order)](#filter-execution-order)
- [[StopWatch Version Compatibility](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#stopwatch-version-compatibility)](#stopwatch-version-compatibility)
- [[Running the Demo Application](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#running-the-demo-application)](#running-the-demo-application)
- [[Building and Publishing](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#building-and-publishing)](#building-and-publishing)
- [[Architecture Overview](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#architecture-overview)](#architecture-overview)

---

## Version History

| Version | Status | Notes |
|---------|--------|-------|
| **1.1.0** | ✅ **Current** | AOP handler capture, RestTemplate logging, SLF4J, secret masking, multipart skip, error indicators — see [[What's new](https://claude.ai/chat/366458c1-f8b5-478e-b691-ceb3227e7bd2#new-in-v110)](#new-in-v110) |
| 1.0.1 | ✅ Stable | Plain library JAR fix — use if v1.1.0 features are not needed |
| 1.0.0 | ❌ **Do not use** | Published as a fat JAR — classes at `BOOT-INF/classes/`, auto-configuration fails in consumer apps |

---

## Features

| Feature | Detail |
|---------|--------|
| **Zero config** | One property enables everything |
| **Correlation ID** | Reads from configurable headers (`X-Request-ID`, `request_id`, …); falls back to UUID |
| **Full body capture** | Request and response bodies via `ContentCachingRequestWrapper` / `ContentCachingResponseWrapper` |
| **AOP controller handler** *(v1.1.0)* | Automatically logs `controllerHandler: "UserController#listUsers"` — no code changes needed |
| **RestTemplate auto-capture** *(v1.1.0)* | Injects logging interceptor into all Spring-managed `RestTemplate` beans |
| **SLF4J logger output** *(v1.1.0)* | Routes all log output to a named SLF4J logger compatible with Logback, Log4j2, ELK |
| **Secret masking** *(v1.1.0)* | Replaces sensitive JSON field values and header values with `***MASKED***` |
| **Multipart / binary skip** *(v1.1.0)* | Skips body buffering for `image/*`, `video/*`, `multipart/*` — prevents heap pressure |
| **Short error indicator** *(v1.1.0)* | Logs `errorIndicator: "ERROR:NullPointerException"` + truncated stacktrace on exceptions |
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

```xml
<dependency>
    <groupId>io.github.yash-777</groupId>
    <artifactId>api-request-logging-spring-boot-starter</artifactId>
    <version>1.1.0</version>
</dependency>
```

### 2. Enable in `application.properties`

```properties
api.request.logging.enabled=true
```

That's it. Every HTTP request now produces a structured log block.

### 3. Optional: AOP controller handler (v1.1.0)

Add `spring-boot-starter-aop` to your project and the starter automatically captures which controller method handled each request:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

No other configuration needed. The `controllerHandler` field appears automatically in your logs.

---

## Property Reference

All properties share the prefix `api.request.logging`.

### Core properties (all versions)

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

### New properties in v1.1.0

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `aop.controller-handler-enabled` | `boolean` | `true` | Capture controller class and method name via AOP. Requires `spring-boot-starter-aop` on the classpath. |
| `rest-template.auto-capture-enabled` | `boolean` | `false` | Auto-inject logging interceptor into all Spring-managed `RestTemplate` beans. |
| `rest-template.log-request-body` | `boolean` | `true` | Log the outgoing RestTemplate request body. |
| `rest-template.log-response-body` | `boolean` | `true` | Log the RestTemplate response body. |
| `rest-template.max-body-length` | `int` | `4096` | Truncation limit for RestTemplate bodies. |
| `rest-template.skip.urls` | `List<String>` | `[]` | RestTemplate URLs to skip logging. |
| `rest-template.skip.urls-match-endswith` | `boolean` | `false` | Match skip URLs by path suffix (host-agnostic). |
| `rest-template.skip.urls-match-full` | `boolean` | `false` | Match skip URLs by exact full URL equality. |
| `logger.enabled` | `boolean` | `true` | Route log output through SLF4J. |
| `logger.name` | `String` | `api.request.logging` | SLF4J logger name to use. |
| `logger.sysout-enabled` | `boolean` | `false` | Also print to `System.out` (v1.0.x legacy behaviour). |
| `exception.max-lines` | `int` | `5` | Maximum stacktrace lines stored when an exception is caught. |
| `mask.enabled` | `boolean` | `false` | Enable secret masking for JSON bodies and headers. |
| `mask.fields` | `List<String>` | `[]` | Field/header names whose values will be masked. |
| `mask.replacement` | `String` | `***MASKED***` | Replacement string for masked values. |
| `multipart.skip-binary` | `boolean` | `true` | Skip body buffering for binary content types (`image/*`, `video/*`, `multipart/*`, `application/octet-stream`). |
| `skip-headers` | `List<String>` | `[]` | Header names to drop entirely from log output (distinct from masking which keeps the key). |

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
      skip-headers:
        - user-agent
        - postman-token
      aop:
        controller-handler-enabled: true
      rest-template:
        auto-capture-enabled: true
        log-request-body: true
        log-response-body: true
        max-body-length: 4096
        skip:
          urls:
            - /health
            - /metrics
          urls-match-endswith: true
      logger:
        enabled: true
        name: api.request.logging
        sysout-enabled: false
      exception:
        max-lines: 5
      mask:
        enabled: true
        fields:
          - password
          - token
          - authorization
          - cvv
        replacement: "***MASKED***"
      multipart:
        skip-binary: true
```

---

## What Gets Logged

For every HTTP request (that is not excluded) you will see a block like this in your logs:

```
=========== Request Logs [req-id: my-trace-001] ===========

── INCOMING
   requestId:            my-trace-001
   threadName:           http-nio-8080-exec-2
   url: /api/orders ➤ ContextPath[] — ServletPath[/api/orders]
   httpMethod:           POST
   timestamp:            25/3/2026, 10:32:15 am
   headers:              {"content-type":"application/json","x-request-id":"my-trace-001","host":"localhost:8080"}
   controllerHandler:    OrderController#createOrder
   requestBodyType:      raw
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

**Error example** (v1.1.0):

```
── INCOMING
   ...
   controllerHandler:    UserController#listUsers
   errorIndicator:       ERROR:NullPointerException
   responseStatus:       500
   requestProcessedTime: 0h 0m 0s 12ms

── exceptionStacktrace
   java.lang.NullPointerException: null
     at com.example.UserController.listUsers(UserController.java:25)
     ... (truncated to 5 lines)
```

---

## New in v1.1.0

### AOP Controller Handler Auto-Capture

When `spring-boot-starter-aop` is on the classpath, the starter automatically intercepts every `@RestController` / `@Controller` method and logs which handler processed the request — zero boilerplate required:

```
controllerHandler: "OrderController#createOrder"
```

The aspect is guarded by `@ConditionalOnClass(ProceedingJoinPoint.class)` and silently skipped if AOP is absent from the classpath.

```properties
api.request.logging.aop.controller-handler-enabled=true   # default
```

---

### RestTemplate Auto-Capture

When enabled, a `BeanPostProcessor` injects `RestTemplateLoggingInterceptor` into every Spring-managed `RestTemplate` bean (using `BufferingClientHttpRequestFactory` so the response body can be read). Each outgoing call is logged under its own timestamped key:

```
── https://payment-service/charge [14:32:05.042]
   request:   {"orderId":"ORD-1","amount":500.0}
   response:  {"txnId":"TXN-99","status":"SUCCESS"}
```

```properties
api.request.logging.rest-template.auto-capture-enabled=true
```

**Skip list** — to exclude specific URLs from being logged:

```properties
api.request.logging.rest-template.skip.urls=/health,/metrics
api.request.logging.rest-template.skip.urls-match-endswith=true   # match by path suffix
# OR
api.request.logging.rest-template.skip.urls-match-full=true       # exact URL match
```

> **Limitation:** only Spring-managed `RestTemplate` beans declared as `@Bean` are intercepted. Instances created with `new RestTemplate()` directly in a method body are invisible to the `BeanPostProcessor`.

---

### SLF4J Logger Output

All log output is now routed through a named SLF4J logger at INFO level, making it fully compatible with Logback, Log4j2, ELK, Splunk, CloudWatch, and any other SLF4J binding.

```properties
api.request.logging.logger.name=api.request.logging   # controls logger name in logback.xml / log4j2.xml
api.request.logging.logger.sysout-enabled=false        # set true for v1.0.x System.out behaviour
```

To control the log level in `logback.xml`:

```xml
<logger name="api.request.logging" level="INFO"/>
```

---

### Secret Masking

Sensitive JSON field values in request/response bodies and matching header values are replaced before any log output:

```properties
api.request.logging.mask.enabled=true
api.request.logging.mask.fields=password,token,authorization,cvv,latitude
api.request.logging.mask.replacement=***MASKED***
```

**Bug fixed in v1.1.0:** the original regex only matched quoted string values. Numeric fields such as `"latitude": 17.375` and boolean/null values are now correctly masked.

---

### Multipart / Binary Request Skip

`RequestBodyCachingFilter` now detects `Content-Type` and skips body buffering for binary content types, preventing heap pressure and out-of-memory risks from large file uploads:

- `image/*`, `audio/*`, `video/*`
- `multipart/form-data`
- `application/octet-stream`

```properties
api.request.logging.multipart.skip-binary=true   # default
```

---

### Short Error Indicator

When an exception escapes the controller, the `INCOMING` block now includes a grep-friendly one-liner plus a truncated stacktrace:

```
errorIndicator:      ERROR:NullPointerException
exceptionStacktrace: java.lang.NullPointerException: null
                       at com.example.UserController.listUsers(UserController.java:25)
                       ... (truncated to 5 lines)
```

```properties
api.request.logging.exception.max-lines=5   # default
```

---

### Header Skip List

Drop headers entirely before logging. Distinct from masking (which keeps the key but replaces the value), this removes the header from the log completely:

```properties
api.request.logging.skip-headers=user-agent,postman-token,cache-control
```

---

### Redirect Path Capture

For `3xx` responses, the `Location` response header is automatically captured and logged:

```
redirectPath: "https://example.com/new-location"
```

---

### Request Body Type Detection

Every request now logs its body category:

```
requestBodyType: raw         # application/json, text/*
requestBodyType: form-data   # application/x-www-form-urlencoded
requestBodyType: binary      # image/*, multipart/*, octet-stream
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

        collector.addLog(key, RequestLogCollector.LOG_REQUEST, request);   // log before HTTP call
        PaymentResponse res = gateway.post(request);                       // actual HTTP call
        collector.addLog(key, RequestLogCollector.LOG_RESPONSE, res);      // log after HTTP call

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
    collector.addLog(key, RequestLogCollector.LOG_REQUEST, request);
    try {
        PaymentResponse res = gateway.post(request);
        collector.addLog(key, RequestLogCollector.LOG_RESPONSE, res);
        return res;
    } catch (Exception e) {
        collector.addLog(key, RequestLogCollector.LOG_EXCEPTION, e);   // auto-truncates stacktrace
    }
}
```

### Log key constants (v1.1.0)

Use the provided constants instead of raw strings to avoid typos:

| Constant | Value | Usage |
|----------|-------|-------|
| `RequestLogCollector.LOG_REQUEST` | `"request"` | Outgoing request payload |
| `RequestLogCollector.LOG_RESPONSE` | `"response"` | Incoming response payload |
| `RequestLogCollector.LOG_EXCEPTION` | `"exceptionStacktrace"` | Caught exception (auto-truncated) |
| `RequestLogCollector.LOG_ERROR_INDICATOR` | `"errorIndicator"` | Short error label (auto-set by addLog) |
| `RequestLogCollector.LOG_CONTROLLER_HANDLER` | `"controllerHandler"` | Handler name (auto-set by AOP aspect) |

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
Order  -105   RequestContextFilter (auto-registered by starter if absent)
                                             ★ populates RequestContextHolder
                                               (@RequestScope becomes usable)
Order  -104   RequestBodyCachingFilter         wraps req + res in caching wrappers
                                               skips wrapping for binary/multipart (v1.1.0)
Order  -103   ApiLoggingFilter                reads wrapped bodies, logs timing,
                                               calls collector.printLogs()
Order  -100   Spring Security (if present)
              DispatcherServlet → @Controller / @RestController
                                               ↑ ControllerHandlerAspect fires here (v1.1.0)
```

Both filters are registered via `FilterRegistrationBean` (not `@Order`) to prevent double-registration.

**`@EnableWebMvc` compatibility fix (v1.1.0):** consumer applications that use `@EnableWebMvc` (e.g. with Thymeleaf) suppress `WebMvcAutoConfiguration`, so Spring's built-in `OrderedRequestContextFilter` at order `−105` is never registered. This caused:

```
BeanCreationException: No thread-bound request found: Are you referring to request
attributes outside of an actual web request?
```

The starter now auto-registers its own `OrderedRequestContextFilter` guarded by `@ConditionalOnMissingBean(RequestContextFilter.class)`, fixing compatibility with all `@EnableWebMvc` consumer apps.

---

## StopWatch Version Compatibility

| Spring Boot | Spring Framework | Method available |
|-------------|-----------------|-----------------|
| 2.0.x | 5.0.x | `getTotalTimeMillis()` ✅ |
| 2.3.x | 5.2.x | `getTotalTimeNanos()` ✅ (added in 5.2) |
| 2.5.x – 3.x | 5.3.x / 6.x | Both ✅ |

This starter uses `getTotalTimeMillis()` for broadest compatibility. If you are on Spring Boot 2.3+ and want nanosecond precision, replace in `ApiLoggingFilter`:

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

The starter ships with a built-in demo application that includes `OrderController`, `PaymentController`, `OrderService`, `PaymentService`, and (v1.1.0) filter dispatch showcase endpoints.

```bash
git clone https://github.com/Yash-777/api-request-logging.git
cd api-request-logging
mvn spring-boot:run
```

### Core demo endpoints

```bash
# 1. Create an order (INCOMING + InventoryService + PaymentGateway)
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

# 5. Fallback correlation header
curl -X POST http://localhost:8080/api/orders \
     -H "Content-Type: application/json" \
     -H "request_id: fallback-header-test" \
     -d '{"customerId":"C-202","itemName":"Mouse","amount":29.99}'

# 6. Actuator health — excluded from logging, zero overhead
curl http://localhost:8080/actuator/health
```

### v1.1.0 demo endpoints

These are activated by feature-flag properties and are **never registered in consumer applications**:

| Property | Activates |
|----------|-----------|
| `internal.app.non-consumer.once-per-request=true` | `ServletOncePerRequestFilter` vs `SpringOncePerRequestFilter` showcase |
| `internal.app.non-consumer.filter=true` | `ApiDemoFilter` — consumer filter demonstrating `sendError(401)` + `apilog.errorMessage` attribute |
| `internal.app.non-consumer.redirect=true` | 5 redirect variants at `/demo/redirect/**` |
| `internal.app.non-consumer.web.mvc=true` | `@EnableWebMvc` reproduction fixture |

**Filter dispatch showcase** — proves that a raw `javax.servlet.Filter` runs on every dispatch type while `OncePerRequestFilter` runs only once:

```bash
# Enable once-per-request demo, then:
curl -s "http://localhost:8080/demo/forward/a" | python -m json.tool
```

Expected console output:

```
[SpringOncePerRequestFilter]      ▶ dispatch=REQUEST  uri=/demo/forward/a  ← runs ONCE
[ServletOncePerRequestFilter]     ▶ dispatch=REQUEST  uri=/demo/forward/a  ← 1st run
[ControllerA] Forwarding to /demo/forward/b
[ServletOncePerRequestFilter]     ▶ dispatch=FORWARD  uri=/demo/forward/a  ← 2nd run ✅
[ControllerB] Handling FORWARD dispatch
[ServletOncePerRequestFilter]     ◀ dispatch=FORWARD  uri=/demo/forward/a DONE
[SpringOncePerRequestFilter]      ◀ dispatch=REQUEST  uri=/demo/forward/a DONE
```

### Sample log output

```
=========== Request Logs [req-id: my-trace-001] ===========
── INCOMING
   requestId: my-trace-001
   threadName: http-nio-8080-exec-2
   url: /api/orders ➤ ContextPath[] — ServletPath[/api/orders]
   httpMethod: POST
   timestamp: 26/3/2026, 11:47:18 am
   headers: {"content-type":"application/json","x-request-id":"my-trace-001","host":"localhost:8080"}
   controllerHandler: OrderController#createOrder
   requestBodyType: raw
   requestBody: {"customerId":"C-101","itemName":"Laptop","amount":999.99}
   responseStatus: 200
   responseBody: {"orderId":"ORD-DED4B762","status":"CONFIRMED","transactionId":"TXN-9249B0EB","requestId":"my-trace-001"}
   requestProcessedTime: 0h 0m 0s 309ms

── InventoryService/reserve [11:47:19.219]
   itemName: Laptop
   reserved: true

── PaymentGateway/charge [11:47:19.242]
   request: {"orderId":"ORD-DED4B762","amount":999.99,"currency":"INR"}
   response: {"txnId":"TXN-9249B0EB","status":"SUCCESS","orderId":"ORD-DED4B762","amount":999.99}

════════════════════════════════════════════════════════════
```

---

### Using from another project

```xml
<dependency>
    <groupId>io.github.yash-777</groupId>
    <artifactId>api-request-logging-spring-boot-starter</artifactId>
    <version>1.1.0</version>
</dependency>
```

Then in `application.properties`:

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
│                                               registers starterRequestContextFilter (v1.1.0)
│
├── properties/
│   └── ApiRequestLoggingProperties           ← @ConfigurationProperties(prefix="api.request.logging")
│                                               IDE auto-complete, type-safe binding
│                                               nested: AopProperties, RestTemplateProperties,
│                                               LoggerProperties, ExceptionProperties,
│                                               MaskProperties, MultipartProperties (v1.1.0)
│
├── filter/
│   ├── RequestBodyCachingFilter              ← Order -104: wraps req+res in caching wrappers
│   │                                           skips binary/multipart content types (v1.1.0)
│   └── ApiLoggingFilter                      ← Order -103: captures metadata, timing, bodies
│       └── FilterOrderConfig (inner)         ← registers both filters via FilterRegistrationBean
│
├── collector/
│   └── RequestLogCollector                   ← @RequestScope + CGLIB proxy
│                                               one instance per HTTP request
│                                               injectable from any @Service / @Component
│                                               LOG_* constants (v1.1.0)
│                                               SLF4J output + secret masking (v1.1.0)
│
├── aop/                                      ← NEW in v1.1.0
│   └── ControllerHandlerAspect               ← @ConditionalOnClass(ProceedingJoinPoint.class)
│                                               logs "controllerHandler: ClassName#method"
│
├── resttemplate/                             ← NEW in v1.1.0
│   ├── RestTemplateLoggingBeanPostProcessor  ← injects interceptor into all RestTemplate beans
│   ├── RestTemplateLoggingInterceptor        ← ClientHttpRequestInterceptor implementation
│   └── BufferedClientHttpResponse            ← re-buffers response body for double-read
│
├── masking/                                  ← NEW in v1.1.0
│   └── SecretMaskingUtil                     ← regex masking for JSON bodies + header values
│                                               fixed: covers numeric/boolean/null values
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
| `@ConditionalOnMissingBean(RequestContextFilter.class)` for `starterRequestContextFilter` | Fixes `@EnableWebMvc` consumer apps where `WebMvcAutoConfiguration` backs off and `OrderedRequestContextFilter` at `−105` is never registered |
| `@ConditionalOnClass(ProceedingJoinPoint.class)` on `ControllerHandlerAspect` | Aspect is silently skipped when `spring-aop` is absent — zero error, zero warning |
| `BeanPostProcessor` for RestTemplate interception | The only reliable hook that covers all Spring-managed `RestTemplate` beans without requiring consumer code changes |
| `StopWatch` as local variable | Thread safety by design — each request thread owns its own instance; no synchronisation needed |
| `ThreadLocal<String> CURRENT_REQUEST_ID` | Zero-injection access to correlation ID from async methods, MDC, utility classes |
| `@PreDestroy cleanup()` | Removes ThreadLocal to prevent request-ID leaks in Tomcat's thread pool |
| `ANY_JSON_VALUE` regex in `SecretMaskingUtil` | Correctly matches string, number, boolean, and null JSON values — the original only matched quoted strings |
| Java 8 compatible | `String.format` not `String.formatted()`, `trim()` not `strip()`, `getTotalTimeMillis()` not `getTotalTimeNanos()` |
| `provided` scope for `spring-boot-starter-web` | Consumer's classpath already has it; avoids duplicate-class conflicts |

---

## License

Apache License, Version 2.0 — see [LICENSE](LICENSE) for details.
