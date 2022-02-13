# ktor-client-logging
An opinionated logging for the Ktor client.

## Why another way of logging?
This project is inspired by the [Logging](https://ktor.io/docs/client-logging.html) feature that ships with Ktor.
However, there are certain challenges with Ktor logging. Namely, it's [hard to reliably pass MDC context](https://youtrack.jetbrains.com/issue/KTOR-2435) throughout
the processing pipeline.

## Features
* Works with slf4j Logger. No need to create a custom interface as a thin wrapper around the underlying logger.
* Resolves the issue with passing of MDC context.
* Trace ID is explicitly supported in the configuration.

## Usage

### Default Configuration

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
      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%-10mdc{traceId}] [%20.20thread] %50.50logger{50}: %message%n%ex{full}</pattern>
    </encoder>
  </appender>
```

Resulting application logs:
```
2022-02-13 19:34:29.404 DEBUG [edvaf1kmao8] [                main]                          io.ktor.client.HttpClient: Request GET https://api.ipify.org/?format=json, headers: [Accept=[application/json], Accept-Charset=[UTF-8]], body: [request body omitted]
2022-02-13 19:34:29.466 DEBUG [edvaf1kmao8] [                main]                          io.ktor.client.HttpClient: Response from: GET https://api.ipify.org/?format=json, statusCode: 200 OK, headers: [Content-Type=[application/json], Content-Type=[application/json]], body: {"ip":"127.0.0.1"}
```

### Custom Configuration

For now, you can only define a different name for the trace ID attribute. Let's call it `correlationId` instead:

```kotlin
val httpClient = HttpClient(CIO) {
    install(ClientLogging) {
        tracingConfig = TracingConfig(traceIdKey = "correlationId")
    }
}
```

I will add more options if there is a need to tweak the tracing config any further.

