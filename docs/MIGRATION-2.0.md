# tamacat-httpd 2.0 Migration Guide (from 1.5.x)

tamacat-httpd 2.0 replaces Apache HttpComponents **HttpCore 4.4** with
**HttpComponents Core 5.5-beta2**, and drops the **HttpClient 4.5** dependency
entirely. Because HttpCore types appear in tamacat-httpd's own public
signatures, this is a **source- and binary-incompatible** release. Every
extension you have written against 1.5 — handlers, filters, workers,
`ReverseUrl` implementations — must be recompiled, and most must be edited.

This guide tells you **what you have to change**. For the full list of what
changed and why, see [RELEASE-NOTES-2.0.md](RELEASE-NOTES-2.0.md).

Read the two **Known limitations** at the end before you upgrade a production
instance. They are not footnotes.

---

## Table of contents

1. [Version and dependency changes](#1-version-and-dependency-changes)
2. [The one-line summary of the type change](#2-the-one-line-summary-of-the-type-change)
3. [Layer 1 — Java SPI: the complete 4.x → 5.x mapping](#3-layer-1--java-spi-the-complete-4x--5x-mapping)
4. [Layer 1 — removed and changed members you may be calling](#4-layer-1--removed-and-changed-members-you-may-be-calling)
5. [Layer 2 — `components.xml` setters that were removed](#5-layer-2--componentsxml-setters-that-were-removed)
6. [Layer 3 — `server.properties` keys](#6-layer-3--serverproperties-keys)
7. [`url-config.xml` — `type="lb"` no longer exists](#7-url-configxml--typelb-no-longer-exists)
8. [Behaviour changes that need no code edit but change what you observe](#8-behaviour-changes-that-need-no-code-edit-but-change-what-you-observe)
9. [Known limitations in 2.0](#9-known-limitations-in-20)
10. [Upgrade checklist](#10-upgrade-checklist)

---

## 1. Version and dependency changes

| | 1.5 | 2.0 |
|---|---|---|
| `org.tamacat:tamacat-httpd` | `1.5.2-tc9.0.120` | **`2.0`** |
| `org.tamacat:tamacat-core` (dependency) | `1.5` | **`2.0`** |
| HTTP core library | `org.apache.httpcomponents:httpcore:4.4.16` | **`org.apache.httpcomponents.core5:httpcore5:5.5-beta2`** |
| HTTP client library | `org.apache.httpcomponents:httpclient:4.5.14` | **removed** |
| `commons-codec` | `1.22.0` (declared) | **declaration removed** |
| `commons-logging` | `1.3.6` (declared) | **declaration removed** |
| `commons-lang3` | `3.20.0` (declared) | **declaration removed** |
| Java bytecode target | 1.8 | 1.8 (unchanged) |

`[verified]` — `git diff a688748..HEAD -- pom.xml` on the `v2.0` branch; the
2.0 `pom.xml` declares `<version>2.0</version>` and
`tamacat-core` `<version>2.0</version>`. `tamacat-core`'s own `pom.xml` also
declares `2.0`.

`[verified]` — Java 8 bytecode is retained: `maven.compiler.source`/`target`
and the `maven-compiler-plugin` configuration all still say `1.8`, and all 238
`.class` files produced by `mvn clean test` (120 main + 118 test) carry class
file major version **52**.

### What this means for your build

* **Bump both coordinates.** If you depend on `tamacat-core` directly, move it
  to `2.0` as well; 1.5 and 2.0 of the two artifacts are not mixable.
* **`commons-codec`, `commons-logging` and `commons-lang3` no longer reach you
  transitively through tamacat-httpd.** If your own code imports them, declare
  them yourself.
  `[verified]` — `mvn dependency:tree` shows neither `commons-codec` nor
  `commons-logging` anywhere in the 2.0 tree.
* **`commons-lang3` resolves to a lower version.** It is still on the tree, but
  only as an *optional* transitive of `velocity-engine-core`, at **3.17.0**
  instead of the 3.20.0 tamacat-httpd 1.5 pinned. If you were relying on the
  higher version arriving through tamacat-httpd, pin it yourself.
  `[verified]` — `mvn dependency:tree`.
* **`httpcore5` pulls in nothing.** `[verified]` — it appears as a leaf in
  `mvn dependency:tree` with no children.
* PowerMock (`powermock-module-junit4`, `powermock-api-mockito2`) was removed
  from the test scope. This affects only builds of tamacat-httpd itself, not
  consumers.

---

## 2. The one-line summary of the type change

Almost every edit you have to make is one of these two:

```
org.apache.http.*             ->  org.apache.hc.core5.http.*
HttpRequest / HttpResponse    ->  ClassicHttpRequest / ClassicHttpResponse
```

The second one is the part people miss. In core5, `HttpRequest` and
`HttpResponse` **do not carry an entity**. The entity-bearing types are
`ClassicHttpRequest` (which is `HttpRequest` + `HttpEntityContainer`) and
`ClassicHttpResponse`. tamacat-httpd's SPI uses the `Classic*` forms
throughout, so a mechanical package rename alone will not compile.

`[verified]` — main source tree contains **0** files importing
`org.apache.http.` (`grep -rc "import org.apache.http\." src/main/java`).

Common member-level changes that follow from the type change:

| 1.5 call | 2.0 call |
|---|---|
| `request.getRequestLine().getUri()` | `request.getRequestUri()` |
| `request.getRequestLine().getMethod()` | `request.getMethod()` |
| `response.getStatusLine().getStatusCode()` | `response.getCode()` |
| `response.setStatusCode(int)` | `response.setCode(int)` |
| `entity.getContentType()` returns `Header` | returns `String` |
| `entity.getContentEncoding()` returns `Header` | returns `String` |

---

## 3. Layer 1 — Java SPI: the complete 4.x → 5.x mapping

**Scope and completeness.** "Layer 1" is tamacat-httpd's compile-time public
contract: every **`public interface`**, **`public abstract class`** and
**`public enum`** declared at top level under `src/main/java`. In 1.5 there are
**29** such types — **21 interfaces, 4 abstract classes, 4 enums** — and all 29
are listed below. This is a count of *type declarations*, not of grep matches:
each declaration was read out of the committed 1.5 tree file by file, and the
result was cross-checked against a second scan that also looked for nested and
non-`public` declarations (it found none beyond these). The 21/4/4 split
matches the reverse-engineering baseline independently. Of the 29, **20 survive
in 2.0 and 9 are removed**.

`[verified]` — enumerated by reading every `src/main/java/**/*.java` at both
`a688748` (1.5.2-tc9.0.120) and the 2.0 branch head.

Types **not listed here** — concrete `public class`es such as
`ReverseProxyHandler`, `ServiceUrl`, `ServerConfig`, `RequestUtils`,
`ReverseUtils` — are not layer 1, but many of them changed too; §4 covers the
ones most likely to break you.

### 3a. Interfaces (21 in 1.5)

| # | Type | 1.5 (4.x) | 2.0 (5.x) | What you must do |
|---|---|---|---|---|
| 1 | `config.ReverseUrl` | `HttpHost getTargetHost()` where `HttpHost` = `org.apache.http.HttpHost` | `HttpHost` = `org.apache.hc.core5.http.HttpHost` | Change the import. Method shape unchanged. |
| 2 | `config.UrlType` | `String getType()` | identical | Nothing. |
| 3 | `config.lb.HealthCheckSupport` | — | **removed** | See §3d. No replacement. |
| 4 | `config.lb.MonitorEvent` | — | **removed** | See §3d. No replacement. |
| 5 | `core.HttpStatus` | no 4.x types | identical | Nothing. |
| 6 | `core.Worker` | `void setHttpService(org.apache.http.protocol.HttpService)` | `void setHttpService(org.apache.hc.core5.http.impl.io.HttpService)` | Change the import. Note the package is `impl.io`, not `protocol`. |
| 7 | `core.WorkerExecutor` | `void setHttpService(org.apache.http.protocol.HttpService)` | `void setHttpService(org.apache.hc.core5.http.impl.io.HttpService)` | Same as `Worker`. |
| 8 | `core.jmx.BasicHttpMonitor` | — | **removed** | See §3d. |
| 9 | `core.jmx.JMXReloadableHttpd` | — | **removed** | See §3d. |
| 10 | `core.jmx.PerformanceCounter` | — | **removed** | See §3d. |
| 11 | `core.jmx.PerformanceCounterMonitor` | — | **removed** | See §3d. |
| 12 | `core.jmx.Reloadable` | — | **removed** | See §3d. |
| 13 | `core.jmx.ReloadaleMXServer` | — | **removed** | See §3d. |
| 14 | `core.ssl.SSLContextCreator` | `javax.net.ssl` only | identical | Nothing. |
| 15 | `filter.HttpFilter` | `void init(ServiceUrl)` + 2 String constants | identical | Nothing. |
| 16 | `filter.RequestFilter` | `void doFilter(HttpRequest, HttpResponse, HttpContext)` | `void doFilter(ClassicHttpRequest, ClassicHttpResponse, HttpContext)` | **Every filter you wrote changes.** Import `org.apache.hc.core5.http.ClassicHttpRequest` / `ClassicHttpResponse` / `org.apache.hc.core5.http.protocol.HttpContext`. |
| 17 | `filter.ResponseFilter` | `void afterResponse(HttpRequest, HttpResponse, HttpContext)` | `void afterResponse(ClassicHttpRequest, ClassicHttpResponse, HttpContext)` | Same as `RequestFilter`. |
| 18 | `filter.acl.AccessUrl` | no 4.x types | identical | Nothing. |
| 19 | `handler.HttpHandler` | `extends org.apache.http.protocol.HttpRequestHandler`; inherited `void handle(HttpRequest, HttpResponse, HttpContext)` | `extends org.apache.hc.core5.http.io.HttpRequestHandler`; inherited `void handle(ClassicHttpRequest, ClassicHttpResponse, HttpContext)` | **Every handler you wrote changes.** Note the core5 package is `http.io`, not `http.protocol`. |
| 20 | `handler.HttpHandlerFactory` | `HttpHandler getHttpHandler(ServiceUrl)` | identical declaration | Nothing in the signature — but the `HttpHandler` it returns changed (row 19). |
| 21 | `middleware.Middleware` | `void startup()` / `void shutdown()` | identical | Nothing. |

### 3b. Abstract classes (4 in 1.5)

| # | Type | 1.5 (4.x) | 2.0 (5.x) | What you must do |
|---|---|---|---|---|
| 22 | `config.lb.LbHealthCheckServiceUrl` | — | **removed** | See §3d. |
| 23 | `filter.AbstractAccessControlFilter` | `public void doFilter(HttpRequest, HttpResponse, HttpContext)`, `protected String getRemoteUser(HttpRequest, HttpResponse, HttpContext)` | both take `ClassicHttpRequest` / `ClassicHttpResponse` | Update the overrides in your subclass. The `remoteUserKey` default is now the constant `HttpContextKeys.REMOTE_USER`, whose value is still `"REMOTE_USER"` — behaviour unchanged. |
| 24 | `handler.AbstractHttpHandler` | `public void handle(HttpRequest, HttpResponse, HttpContext)`, `protected void handleException(HttpRequest, HttpResponse, Exception)`, `public boolean isAllowedMethod(HttpRequest)` | all three take `ClassicHttpRequest` / `ClassicHttpResponse` | **This is the main extension point — every handler subclass changes.** See also the method-check behaviour change in §8. |
| 25 | `util.EncodeUtils` | no 4.x types | identical | Nothing. |

### 3c. Enums (4 in 1.5)

| # | Type | 1.5 | 2.0 | What you must do |
|---|---|---|---|---|
| 26 | `config.ServiceType` | `NORMAL`, `REVERSE`, **`LB`**, `ERROR` | `NORMAL`, `REVERSE`, `ERROR` | **`LB` is gone.** Code referencing `ServiceType.LB` will not compile; `url-config.xml` using `type="lb"` will not start. See §7. |
| 27 | `core.BasicHttpStatus` | — | identical | Nothing. |
| 28 | `core.ssl.KeyStoreType` | — | identical | Nothing. |
| 29 | `core.ssl.SSLProtocol` | — | identical | Nothing. |

### 3d. The 9 removed layer-1 types

| Type | Package removed with it | Replacement |
|---|---|---|
| `config.lb.HealthCheckSupport` | `org.tamacat.httpd.config.lb` | **none** |
| `config.lb.MonitorEvent` | `org.tamacat.httpd.config.lb` | **none** |
| `config.lb.LbHealthCheckServiceUrl` | `org.tamacat.httpd.config.lb` | **none** |
| `core.jmx.BasicHttpMonitor` | `org.tamacat.httpd.core.jmx` | **none** |
| `core.jmx.JMXReloadableHttpd` | `org.tamacat.httpd.core.jmx` | **none** |
| `core.jmx.PerformanceCounter` | `org.tamacat.httpd.core.jmx` | **none** |
| `core.jmx.PerformanceCounterMonitor` | `org.tamacat.httpd.core.jmx` | **none** |
| `core.jmx.Reloadable` | `org.tamacat.httpd.core.jmx` | **none** |
| `core.jmx.ReloadaleMXServer` | `org.tamacat.httpd.core.jmx` | **none** |

`[verified]` — neither `src/main/java/org/tamacat/httpd/config/lb` nor
`.../core/jmx` exists in the 2.0 tree.

If you depended on load balancing or on the JMX counters, **there is nothing in
2.0 to move to.** Put a load balancer in front of tamacat-httpd, and take
metrics from the access log or from an external agent.

---

## 4. Layer 1 — removed and changed members you may be calling

These are not layer-1 *types*, but they are public members of public classes,
and they break at compile time.

### 4a. Removed public methods

| Class | Removed member | What to do |
|---|---|---|
| `util.ReverseUtils` | `createGenerousTrustManager()` | Gone with the httpclient dependency. Use `SSLContextCreator` / the JDK `TrustManager` API directly. |
| `util.ReverseUtils` | `createSSLSocket(ReverseUrl, HttpProxyConfig, boolean)` overload | Use `createSSLSocket(ReverseUrl, boolean)`. The upstream-proxy overload had no meaning after `HttpProxyConfig` was removed. |
| `util.RequestUtils` | `setRemoteAddress(HttpContext, HttpServerConnection)` | Delete the call. core5 populates `EndpointDetails` on the context itself; there is no step to perform. |
| `util.RequestUtils` | `getHttpConnection(HttpContext)` | Same — go through core5's `EndpointDetails`. |
| `handler.ReverseProxyHandler` | `setHttpProxyConfig(HttpProxyConfig)` | Delete the call and the XML wiring (§5). |
| `config.ServiceUrl` | `getLoadBalancerMethod()` / `setLoadBalancerMethod(String)` | No replacement — load balancing is gone. |

### 4b. Changed return types and signatures

| Class | 1.5 | 2.0 |
|---|---|---|
| `util.ReverseUtils.createSSLSocketFactory(...)` | returns httpclient `SSLConnectionSocketFactory` | returns JDK **`javax.net.ssl.SSLSocketFactory`** |
| `filter.GzipResponseInterceptor.GzipCompressingEntity.getContentEncoding()` | returns `Header` | returns **`String`** (core5's `EntityDetails` contract) |
| `handler.HostRequestHandlerMapper` | implemented httpcore 4.4's `HttpRequestHandlerMapper`; `lookup(HttpRequest)` | implements **`HttpRequestMapper<HttpRequestHandler>`**; `HttpRequestHandler lookup(HttpRequest, HttpContext) throws HttpException` (core5 types) |
| `core.ClientHttpConnection` | constructors took `org.apache.http.config.MessageConstraints` | constructors take **`org.apache.hc.core5.http.config.Http1Config`**. Use `ClientHttpConnection(ServerConfig)` or `ClientHttpConnection(int buffersize)` if you were only setting the buffer size. |

### 4c. Removed classes you may be referencing

| Class | Fate |
|---|---|
| `config.HttpProxyConfig` | Removed with upstream-proxy support (§5). |
| `handler.DefaultHttpService` | Replaced by **`handler.TamacatHttpServerRequestHandler`**. core5's `HttpService` is `final`-shaped around a `HttpServerRequestHandler` and offers no `doService` extension point, so the subclassing pattern of 1.5 has no equivalent. If you overrode `doService`, move that logic into a filter or a handler. |
| `handler.ReverseHttpEntityEnclosingRequest` | Folded into **`handler.ReverseHttpRequest`**. In core5 a `ClassicHttpRequest` always *can* carry an entity, so the separate entity-enclosing class had nothing left to distinguish. Replace all uses with `ReverseHttpRequest`. |
| `core.StandardHttpRequestFactory` | Removed. Method admission now happens in `AbstractHttpHandler` (§8). |
| `filter.PerformanceCounterFilter` | Removed with the JMX package. **Also delete its `components.xml` wiring** — see §5. |

---

## 5. Layer 2 — `components.xml` setters that were removed

These are **runtime** contracts: the DI container resolves them by name, so a
stale entry is not caught by the compiler. It fails when the server parses the
configuration.

### 5a. Setters that must be deleted from `components.xml`

| Bean class | Property | Reason |
|---|---|---|
| `org.tamacat.httpd.config.HttpProxyConfig` | the whole bean | Class removed. |
| `org.tamacat.httpd.config.HttpProxyConfig` | `proxyHost` | Class removed. |
| `org.tamacat.httpd.config.HttpProxyConfig` | `proxyPort` | Class removed. |
| `org.tamacat.httpd.config.HttpProxyConfig` | `nonProxyHosts` | Class removed. |
| `org.tamacat.httpd.config.HttpProxyConfig` | `username`, `password` | Upstream-proxy authentication removed. |
| `org.tamacat.httpd.handler.ReverseProxyHandler` | `httpProxyConfig` | Setter removed. |
| `org.tamacat.httpd.filter.PerformanceCounterFilter` | the whole bean, and every `<property name="httpFilter"><ref bean="PerformanceCounterFilter"/></property>` that references it | Class removed. |
| `org.tamacat.httpd.filter.PerformanceCounterFilter` | `objectName` | Class removed. |

**If you leave the `PerformanceCounterFilter` wiring in place, configuration
parsing fails at startup.** In the shipped test configuration this accounted
for 24 removed lines of `components.xml` across four beans — the bean
definition plus three `httpFilter` references.
`[verified]` — `git diff a688748..HEAD -- src/test/resources/components.xml`
removes 26 lines in total: the `PerformanceCounterFilter` bean and its three
references, plus the `httpProxy` bean and the `httpProxyConfig` reference.

### 5b. Setters removed from classes that were themselves deleted

You will only be wiring these if you were using load balancing or JMX. Listed
for completeness so a `components.xml` audit can be mechanical.

| Class (removed) | Setters |
|---|---|
| `config.lb.LbHealthCheckServiceUrl` | `reverseUrl` |
| `config.lb.LbLeastConnectionServiceUrl` | `host`, `reverse`, `reverseUrl` |
| `config.lb.HttpMonitor` | `healthCheckTarget`, `monitorConfig`, `target` |
| `config.lb.MonitorConfig` | `interval`, `timeout`, `url` |
| `core.jmx.JMXServer` | `serverConfig` |
| `core.jmx.BasicCounter` | `path`, `responseTime` |
| `core.jmx.URLBasicCounter` | `objectName` |
| `handler.DefaultHttpService` | `classLoader`, `contentType`, `encoding`, `hostHandlerResolver` |

`[verified]` — computed as the set difference of `public void set*(`
declarations across all `src/main/java` files between `a688748` and the 2.0
head, then attributed to their declaring class. **Caveat on the method of
counting:** the scan keys on the `public void set…(` form. Non-`void` (fluent)
setters were picked up by a second scan, which found exactly two removals —
`HttpProxyConfig.setProxy(HttpClientBuilder)` and the `static`
`RequestUtils.setRemoteAddress(...)` — neither of which is DI-wirable. Setters
that are `protected`, or declared over more than one line, are not covered by
either scan; audit your own `components.xml` against the 2.0 classes rather
than treating this table as exhaustive.

### 5c. Setters that did **not** change

Every other `components.xml` property name in the shipped configuration is
unchanged: `docsRoot`, `listings`, `httpFilter`, `httpResponseInterceptor`,
`strictHttps`, `overrideHostHeaderWithReverseUrl`, `overrideHostHeader`,
`accessControlAllowOrigin`, `accessControlAllowMethods`,
`accessControlAllowHeaders`, `statusCode`, `contentType`, `path`,
`forceReplaceErrorPage`, `appendResponseHeader`, `cacheSize`, `cacheExpire`.

---

## 6. Layer 3 — `server.properties` keys

**No `server.properties` key was renamed or removed in 2.0.** Two keys need
your attention anyway.

| Key | Status in 2.0 | Action |
|---|---|---|
| `BackEndSocketTimeout` | **Now actually applied.** In 1.5 it was read but never installed on the backend socket. Default `5000` ms. | See the warning below. |
| `BackEndConnectionTimeout` | **Still not implemented.** | See §9. |

### `BackEndSocketTimeout` — read this before upgrading

In 1.5 a backend that accepted the connection and then went silent would block
a worker thread indefinitely; the configured `BackEndSocketTimeout` had no
effect. 2.0 fixes that: the timeout is applied to the backend socket, and a
backend that exceeds it is cut off and answered with **503** and a 714-byte
error page.

**The default is 5000 ms.** If any of your backends legitimately takes longer
than five seconds to produce a response — a slow report, a batch endpoint — it
worked in 1.5 by accident and will start returning **503** in 2.0. Raise
`BackEndSocketTimeout` in `server.properties` to cover your slowest legitimate
backend response before you upgrade.

`[verified]` — `ClientHttpConnection` applies
`serverConfig.getParam("BackEndSocketTimeout", 5000)` to the socket, with the
source comment `DEFECT FIX (2.0): BackEndSocketTimeout was never applied.`

---

## 7. `url-config.xml` — `type="lb"` no longer exists

`ServiceType.LB` is gone, and `ServiceType.find(String)` resolves through
`valueOf(name.toUpperCase())`, so an unknown value throws
`IllegalArgumentException`. A `url-config.xml` containing `type="lb"`
**fails at startup**, loudly.

This is deliberate. In 1.5 a mis-declared type could fail silently at request
time; in 2.0 it stops the server at configuration load, where you can see it.

Migrate each `type="lb"` block to a single `type="reverse"` backend, and move
the balancing to a real load balancer in front of tamacat-httpd:

```xml
<!-- 1.5 -->
<url path="/lb/" type="lb" lb-method="RoundRobin" handler="ReverseHandler">
  <reverse>http://localhost:8080/test/v1/</reverse>
  <reverse>http://localhost:8080/test/v2/</reverse>
</url>

<!-- 2.0 -->
<url path="/lb/" type="reverse" handler="ReverseHandler">
  <reverse>http://localhost:8080/test/v1/</reverse>
</url>
```

The `lb-method` attribute (`RoundRobin`, `LeastConnection`) and the health
checks that went with it have no replacement.

`[verified]` — the shipped `url-config.xml` was migrated exactly this way, and
the second `<reverse>` entry was dropped.

---

## 8. Behaviour changes that need no code edit but change what you observe

Full detail in [RELEASE-NOTES-2.0.md](RELEASE-NOTES-2.0.md); this is the part
that changes what a client sees.

### 8a. Method admission moved, and the status code changed

| | 1.5 | 2.0 |
|---|---|---|
| Where | `StandardHttpRequestFactory`, at request-parse time | `AbstractHttpHandler.isAllowedMethod`, at handler dispatch |
| Admitted set | 8 methods: `GET`, `POST`, `PUT`, `HEAD`, `OPTIONS`, `DELETE`, `TRACE`, `CONNECT` | the handler's configured `allowMethods`, default **`GET,HEAD,POST,OPTIONS`** |
| Rejection | `MethodNotSupportedException` (surfacing as 501) | **405 Method Not Allowed** |

Concretely: `PUT`, `DELETE`, `TRACE`, `PROPFIND` and an unknown method such as
`BREW` all now return **405**. In 1.5, `PUT`/`DELETE`/`TRACE` passed the
parse-time gate and `PROPFIND`/`BREW` were rejected as 501. If you serve `PUT`
or `DELETE`, set `allowMethods` on the handler.

`[verified]` — the 1.5 method arrays and the 2.0
`setAllowMethods("GET,HEAD,POST,OPTIONS")` default plus
`throw new HttpException(BasicHttpStatus.SC_METHOD_NOT_ALLOWED)` were both read
from the committed source. The observed 405 responses come from Step 21's
measurements.

**The `TomcatServerHandler` path is different: it returns 404 for every
method,** because `handle(...)` throws `NotFoundException` unconditionally.
Method admission on that path belongs to Tomcat's own connector. This is
fail-closed, never fail-open. `[verified]` — the unconditional
`throw new NotFoundException();` is in the source.

### 8b. Cookies

* `Set-Cookie` expiry is emitted as **`Max-Age=<seconds>`** instead of
  `Expires=<absolute date>`. `java.net.HttpCookie`, which replaced httpclient's
  cookie types, has no absolute-expiry field; RFC 6265 treats the two as
  equivalent, with `Max-Age` taking precedence when both are present. Cookies
  with no expiry are byte-identical to 1.5.
* `HeaderUtils.getCookies(String)` now **skips** malformed cookie names instead
  of accepting them. `java.net.HttpCookie` rejects non-token names and names
  beginning with `$` — notably the RFC 2965 `$Version` attribute — which
  httpclient's `BasicClientCookie` accepted. If you were reading `$Version` out
  of the parsed list, it is no longer there.

### 8c. Wire-level spellings

* The `Connection` header token is now emitted as `keep-alive` / `close`
  instead of `Keep-Alive` / `Close`. HTTP compares these case-insensitively, so
  only byte-comparing tooling notices. `[verified]` — core5's
  `HeaderElements.CLOSE` is `"close"` and `KEEP_ALIVE` is `"keep-alive"`.
* A malformed `Connection` header no longer raises `ParseException`. core5's
  `BasicTokenIterator` is lenient and skips what it cannot tokenise; the
  keep-alive decision falls through to the same `false` it produced before.

### 8d. TLS

Backend hostname verification moved from httpclient's `DefaultHostnameVerifier`
to the JDK's own JSSE mechanism: `SSLParameters` with
`endpointIdentificationAlgorithm = "HTTPS"`, applied **before** the handshake
starts. `strictHttps=false` sets no endpoint identification, which is what
httpclient's `NoopHostnameVerifier` did. A hostname mismatch now surfaces
during `startHandshake()` rather than on the first read or write.

---

## 9. Known limitations in 2.0

These are real. Neither is fixed in 2.0.

### 9a. `BackEndConnectionTimeout` does not work

`BackEndConnectionTimeout` is listed in `ServerConfig`'s known-key table and is
set in **all four** `server.properties` files shipped in the repository
(`docker/tamacat/conf/`, `src/test/resources/`, `src/test/resources/https/`,
`src/test/resources/https/client-cert/`). **Nothing in the main source reads
it.** Backend sockets get the operating system's default connect timeout,
whatever your configuration file says.

This matters because it is silent and it looks configured: an operator reading
their own `server.properties` would reasonably conclude that a backend
connect-timeout is in force. It is not. If you need a bounded connect time to
an unreachable backend, 2.0 does not give you one.

Recorded, not fixed, in 2.0; deferred to a later release.

`[verified]` — `grep -rn BackEndConnectionTimeout src/main/java` yields exactly
two hits: the key-name list in `ServerConfig.java` and a javadoc note in
`ClientHttpConnection.java` stating that it is still not implemented.

### 9b. Malformed request targets are forwarded to the backend unvalidated

A request target that is syntactically a valid request line but fails
`new URI(...)` — for example `/ex/a%zz` or `/ex/<>` — is **forwarded to the
backend verbatim and answered 200**. The root cause is upstream: core5's
`message/BasicHttpRequest` catches `URISyntaxException` and keeps the raw path,
and nothing downstream rechecks it.

**This is not a regression.** 1.5 behaves the same way. It is stated here
because it is a security-relevant property of the product that a reader is
entitled to know, and because "we migrated the HTTP core" could otherwise be
read as implying it was addressed. It was not.

Request lines that are malformed at the *line* level are still rejected with
**400** by core5's parser before any handler sees them; the gap is specifically
targets that parse as a request line but not as a URI.

Recorded, not fixed, in 2.0. Input validation is deferred to a separate piece
of work.

---

## 10. Upgrade checklist

1. Bump `tamacat-httpd` to `2.0` and `tamacat-core` to `2.0`.
2. Declare `commons-codec` / `commons-logging` / `commons-lang3` yourself if
   your code uses them; pin `commons-lang3` if you need a version above 3.17.0.
3. Recompile. Fix, in this order:
   - `org.apache.http.*` → `org.apache.hc.core5.http.*` imports;
   - `HttpRequest`/`HttpResponse` → `ClassicHttpRequest`/`ClassicHttpResponse`
     in every `HttpHandler`, `RequestFilter`, `ResponseFilter`,
     `AbstractHttpHandler` and `AbstractAccessControlFilter` subclass;
   - `getRequestLine()` / `getStatusLine()` accessor changes (§2);
   - `ReverseHttpEntityEnclosingRequest` → `ReverseHttpRequest`;
   - `DefaultHttpService` subclasses → move the logic to a filter or handler;
   - any reference to `config.lb`, `core.jmx`, `HttpProxyConfig`,
     `PerformanceCounterFilter`, `StandardHttpRequestFactory` — no replacement.
4. Edit `components.xml`: delete the `HttpProxyConfig` bean, the
   `httpProxyConfig` property, the `PerformanceCounterFilter` bean **and every
   `httpFilter` reference to it** (§5).
5. Edit `url-config.xml`: replace `type="lb"` with `type="reverse"` (§7).
6. Review `server.properties`: raise `BackEndSocketTimeout` if any backend
   legitimately takes longer than 5 s (§6). Do not rely on
   `BackEndConnectionTimeout` (§9a).
7. Set `allowMethods` on any handler that must serve `PUT`, `DELETE` or another
   method outside `GET,HEAD,POST,OPTIONS` (§8a).
8. Re-run your integration tests against the wire-level changes in §8b–8d.

---

## Source-tag convention

`[verified]` — read or executed against the committed 2.0 tree during the
authoring of this guide. `[assumption]` — stated but not confirmed here. No
`[assumption]` claims remain in this document; anything that could not be
confirmed was removed rather than softened.
