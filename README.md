# ktor-client-logging
An opinionated logging for the Ktor client.

[![CircleCI](https://circleci.com/gh/zezutom/ktor-client-logging/tree/main.svg?style=shield)](https://circleci.com/gh/zezutom/ktor-client-logging/tree/main)
[![codecov](https://codecov.io/gh/zezutom/ktor-client-logging/branch/main/graph/badge.svg)](https://codecov.io/gh/zezutom/ktor-client-logging)

## Why another way of logging?

This project is inspired by the [Logging](https://ktor.io/docs/client-logging.html) feature that
ships with Ktor. However, there are certain challenges with Ktor logging. For example,
it's [hard to reliably pass MDC context](https://youtrack.jetbrains.com/issue/KTOR-2435) throughout
the processing pipeline.

## Features

* Works with slf4j Logger. No need to create a custom interface as a thin wrapper around the
  underlying logger.
* Resolves the issue with passing of MDC context.
* Trace ID is explicitly supported in the configuration.
* Flexibility. It's possible to log requests and responses at a different log level or switch it off
  completely.
* Consideration to privacy concerns. For example, `Authorization` request header is excluded by
  default and there are option how it can be safely included in the logs.

## Tutorial

* [Default Configuration and Trace ID](#default-configuration-and-trace-id)
* [Custom Log Level](#custom-log-level)
* [Log Filters](#log-filters)
* [Excluding Request Headers](#excluding-request-headers)

### Default Configuration and Trace ID

```kotlin
val httpClient = HttpClient(CIO) {
  install(ClientLogging)
}
```

Puts `traceId` into MDC context pairing each request and response.

Example:

```kotlin
fun main(args: Array<String>) = runBlocking {
    // Initialize tracing with the default config, e.g. "traceId"
    val tracing = Tracing.DefaultInstance

    tracing.withTraceId(TraceId.generate()) {
        httpClient.get("https://api.ipify.org/?format=json")
    }   
}
```

Let's assume logging into the console using the following pattern:

```xml

<appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
  <encoder>
    <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%-10mdc{traceId}] [%20.20thread]
      %50.50logger{50}: %message%n%ex{full}
    </pattern>
  </encoder>
</appender>
```

Resulting application logs:

![Trace id is reliably captured](./doc/default-output-with-trace-id.png)

### Custom Log Level

```kotlin
val httpClient = HttpClient(CIO) {
  install(ClientLogging) {
    req {
      level { info() }
    }

    res {
      level { trace() }
    }
  }
}
```

Resulting application logs:

![Different log level for requests vs responses](./doc/different-log-level.png)

### Log Filters

#### Responses Only

```kotlin
val httpClient = HttpClient(CIO) {
  install(ClientLogging) {
    req { off() }
  }
}
```

Resulting application logs:

![Only log responses](./doc/log-filtering-no-requests.png)

#### Requests Only

```kotlin
val httpClient = HttpClient(CIO) {
  install(ClientLogging) {
    res { off() }
  }
}
```

Resulting application logs:

![Only log responses](./doc/log-filtering-no-responses.png)

### Excluding Request Headers

```kotlin
val httpClient = HttpClient(CIO) {
  install(ClientLogging) {
    req { excludeHeaders("Authorization", "X-Sensitive") }
  }
}
```

Send a request with explicitly defined headers:
```kotlin
httpClient.get("https://api.ipify.org/?format=json") {
  header(HttpHeaders.Authorization, "Basic dXNlcjpwYXNzd29yZA==")  // Base64-encoded credentials. Plain text: "user:password"
  header(HttpHeaders.Origin, "http://localhost:8080")
  header("X-Sensitive", "sensitive content")}
```

Resulting application logs:

![Excludes defined headers](./doc/excluding-request-headers.png)


