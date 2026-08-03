# tamacat-httpd 2.0 — Release Notes

**Version: `1.5.2-tc9.0.120` → `2.0`**

2.0 migrates tamacat-httpd from Apache HttpComponents **HttpCore 4.4.16** to
**HttpComponents Core 5.5-beta2**, and removes the **HttpClient 4.5**
dependency entirely. HttpCore types appear in tamacat-httpd's public
signatures, so this is a **source- and binary-incompatible** major release.
Every 1.5 extension must be recompiled and most must be edited.

Upgrading from 1.5? Start with the
**[2.0 Migration Guide](MIGRATION-2.0.md)** — it tells you what to change,
member by member.

There are **two known limitations** in 2.0. They are in
[§4](#4-known-limitations). Read them before deploying.

---

## 1. Breaking API changes

1. **Public signatures move from `org.apache.http.*` to
   `org.apache.hc.core5.http.*`.** The main source tree contains zero
   `org.apache.http` imports.
2. **Entity-bearing request/response types change.** core5's `HttpRequest` and
   `HttpResponse` carry no entity; tamacat-httpd's SPI now uses
   `ClassicHttpRequest` / `ClassicHttpResponse` throughout. A package-only
   rename will not compile.
3. **`ReverseUtils.createSSLSocketFactory(...)` return type** changes from
   httpclient's `SSLConnectionSocketFactory` to the JDK's
   `javax.net.ssl.SSLSocketFactory`.
4. **`ReverseUtils.createGenerousTrustManager()` removed**, and the
   `createSSLSocket(ReverseUrl, HttpProxyConfig, boolean)` overload removed.
   Use `createSSLSocket(ReverseUrl, boolean)`.
5. **`ServiceType.LB` removed**, along with
   `ServiceUrl.getLoadBalancerMethod()` / `setLoadBalancerMethod(String)`.
   `url-config.xml` containing `type="lb"` now throws
   `IllegalArgumentException` **at startup**. This is deliberate: in 1.5 the
   same mistake failed silently at request time.
6. **`ReverseHttpEntityEnclosingRequest` folded into `ReverseHttpRequest`.**
   In core5 a `ClassicHttpRequest` can always carry an entity, so the separate
   entity-enclosing class had nothing left to distinguish.
7. **`DefaultHttpService` replaced by `TamacatHttpServerRequestHandler`.**
   core5 offers no `doService` extension point, so the 1.5 subclassing pattern
   has no equivalent. Move `doService` logic into a filter or a handler.
8. **`ReverseProxyHandler.setHttpProxyConfig(...)` removed.**
9. **`HostRequestHandlerMapper` now implements
   `HttpRequestMapper<HttpRequestHandler>`**; `lookup` takes core5 types and
   declares `throws HttpException`.
10. **`RequestUtils.setRemoteAddress(HttpContext, HttpServerConnection)` and
    `RequestUtils.getHttpConnection(HttpContext)` removed.** core5 populates
    `EndpointDetails` itself; delete the calls.
11. **`ClientHttpConnection` constructors taking `MessageConstraints` replaced
    by `Http1Config` forms.** Only the setting the class actually configured —
    the buffer size — is carried over; every other `Http1Config` value stays at
    its default.
12. **`GzipResponseInterceptor.GzipCompressingEntity.getContentEncoding()`
    returns `String`, not `Header`,** following core5's `EntityDetails`
    contract.

## 2. Removed features

1. **`org.tamacat.httpd.config.lb` — load balancing.** Round-robin and
   least-connection balancing, and the backend health checks that went with
   them, are removed. **No replacement.** Put a load balancer in front of
   tamacat-httpd. Startup no longer creates the per-backend `Monitor-N`
   threads: in the shipped test configuration the `type="lb"` block declared
   two backend URLs, and both monitor threads are gone.
2. **`org.tamacat.httpd.core.jmx` — JMX reload and performance counters.**
   Removed. **No replacement.** Take metrics from the access log or an external
   agent.
3. **`HttpProxyConfig` and upstream-proxy support, including authentication.**
   `ProxyClient`, `UsernamePasswordCredentials` and `AuthScope` are all gone
   with the httpclient dependency. **This does not affect tamacat-httpd's own
   reverse-proxy function**, which is fully retained — what is removed is the
   ability to reach a backend *through* an upstream HTTP proxy.
4. **`filter.PerformanceCounterFilter`.** Removed with the JMX package.
   **Its `components.xml` wiring must be removed too** — the bean definition
   plus every `httpFilter` reference to it (three, in the shipped test
   configuration). Leaving it in place makes configuration parsing fail at
   runtime. See `docs/MIGRATION-2.0.md` §5a for the exact diff.
5. **`core.StandardHttpRequestFactory`.** Removed; see §3.1.
6. `monitor.properties`, the configuration file used only by `config.lb`.

## 3. Behaviour changes

### 3.1 Method admission moved layers, and the status code changed

| | 1.5 | 2.0 |
|---|---|---|
| Where | `StandardHttpRequestFactory`, at parse time | `AbstractHttpHandler.isAllowedMethod`, at dispatch |
| Admitted set | 8 methods: `GET`, `POST`, `PUT`, `HEAD`, `OPTIONS`, `DELETE`, `TRACE`, `CONNECT` | the handler's `allowMethods`, default **`GET,HEAD,POST,OPTIONS`** |
| Rejection | `MethodNotSupportedException` | **405 Method Not Allowed** |

Measured: `PUT`, `DELETE`, `TRACE`, `PROPFIND` and an unknown method such as
`BREW` all return **405**. An unknown method yields 405, not 501. Handlers that
must serve `PUT` or `DELETE` need `allowMethods` set explicitly.

**The `TomcatServerHandler` path returns 404 for every method** — its
`handle(...)` throws `NotFoundException` unconditionally. Method admission
there is Tomcat's connector's job. Measured across all nine methods tested;
fail-closed in every case, never fail-open.

### 3.2 `BackEndSocketTimeout` now actually works — and can produce new 503s

This fixes a long-standing defect. In 1.5 the configured
`BackEndSocketTimeout` was never installed on the backend socket, so a backend
that accepted the connection and then went silent blocked a worker thread
indefinitely. In 2.0 the timeout is applied: the backend is cut off at the
configured value and the client is answered **503** with a 714-byte error page.
Measured at 3,004 ms against a 3,000 ms setting.

**The default is 5000 ms.** A backend that legitimately takes longer than five
seconds worked in 1.5 only because the timeout was broken, and will now start
returning **503**. Operators must raise `BackEndSocketTimeout` to cover their
slowest legitimate backend before upgrading.

### 3.3 Cookies

* `Set-Cookie` expiry is emitted as **`Max-Age=<seconds>`** rather than
  `Expires=<absolute date>`. `java.net.HttpCookie`, which replaced httpclient's
  cookie types, has no absolute-expiry field; RFC 6265 treats the two forms as
  equivalent. Cookies without expiry are byte-identical to 1.5 — 144 of 144
  measured cases matched exactly.
* **`HeaderUtils.getCookies(String)` now skips malformed cookie names** instead
  of accepting them. `java.net.HttpCookie` rejects non-token names and names
  beginning with `$`, notably the RFC 2965 `$Version` attribute, which
  httpclient's `BasicClientCookie` accepted.

### 3.4 Wire-level spellings

* The `Connection` header token is emitted as `keep-alive` / `close` instead of
  `Keep-Alive` / `Close`. Wire-visible only — HTTP compares these
  case-insensitively.
* A malformed `Connection` header no longer raises `ParseException`. core5's
  `BasicTokenIterator` is lenient and skips what it cannot tokenise; the
  keep-alive decision falls through to the same `false` as before.

### 3.5 TLS

Backend hostname verification moves from httpclient's
`DefaultHostnameVerifier` to the JDK's JSSE mechanism —
`SSLParameters` with `endpointIdentificationAlgorithm = "HTTPS"`, applied
before the handshake begins. `strictHttps=false` disables endpoint
identification, matching httpclient's `NoopHostnameVerifier`. A hostname
mismatch now surfaces during `startHandshake()` rather than on first read or
write.

## 4. Known limitations

Neither of these is fixed in 2.0.

### 4.1 `BackEndConnectionTimeout` does not work

`BackEndConnectionTimeout` appears in `ServerConfig`'s known-key list and is
set in **all four** `server.properties` files shipped in this repository.
**Nothing in the main source reads it.** Backend sockets get the operating
system's default connect timeout regardless of what the configuration file
says.

An operator reading their own `server.properties` would reasonably conclude
that a backend connect-timeout is in force. It is not. If you need a bounded
connect time to an unreachable backend, 2.0 does not provide one.

Recorded in 2.0; deferred to a later release.

### 4.2 Malformed request targets are forwarded to the backend unvalidated

A request target that forms a valid request line but fails `new URI(...)` — for
example `/ex/a%zz` or `/ex/<>` — is **forwarded to the backend verbatim and
answered 200**. The root cause is upstream: core5's
`message/BasicHttpRequest` catches `URISyntaxException` and retains the raw
path, and nothing downstream rechecks it.

**This is not a regression** — 1.5 behaves identically. It is stated here
because it is a security-relevant property a reader is entitled to know, and
because a release note headlined "migrated the HTTP core" could otherwise be
read as implying it was addressed. It was not.

Request lines malformed at the *line* level are still rejected with **400** by
core5's parser before any handler runs. The gap is specifically targets that
parse as a request line but not as a URI.

Recorded in 2.0; input validation is deferred to separate work.

## 5. Dependency surface

* **`httpclient` 4.5.14 dropped.**
* **`httpcore` 4.4.16 → `httpcore5` 5.5-beta2**, which pulls **zero**
  transitive dependencies.
* **Direct declarations of `commons-codec` (1.22.0), `commons-logging` (1.3.6)
  and `commons-lang3` (3.20.0) removed.** Two consequences:
  * they no longer reach consumers transitively through tamacat-httpd —
    `commons-codec` and `commons-logging` are absent from the 2.0 dependency
    tree entirely;
  * **`commons-lang3` drops from 3.20.0 to 3.17.0**, now arriving only as an
    optional transitive of `velocity-engine-core`. Pin it yourself if you need
    a higher version.
* PowerMock (`powermock-module-junit4`, `powermock-api-mockito2`) removed from
  the test scope. Affects builds of tamacat-httpd only, not consumers.
* **Java 8 bytecode retained.** `maven.compiler.source`/`target` and the
  compiler plugin all remain at `1.8`; all 238 class files produced by
  `mvn clean test` (120 main + 118 test) are class file major version 52.

## 6. Version

| Artifact | 1.5 | 2.0 |
|---|---|---|
| `org.tamacat:tamacat-httpd` | `1.5.2-tc9.0.120` | **`2.0`** |
| `org.tamacat:tamacat-core` | `1.5` | **`2.0`** |

`tamacat-httpd`'s dependency declaration on `tamacat-core` points at `2.0`.
`tamacat-core`'s Java sources are unchanged in this release; only its version
follows.

## 7. Verification

* `mvn -o clean test` — **BUILD SUCCESS**, `Tests run: 418, Failures: 0,
  Errors: 0, Skipped: 0`.
* `mvn -o dependency:tree` — `httpcore5:5.5-beta2` is a leaf; no
  `httpclient`, `commons-codec` or `commons-logging` anywhere in the tree.

---

Every version number, count and behaviour in this document was checked against
the committed 2.0 source tree. Where a figure could not be confirmed it was
removed rather than restated.
