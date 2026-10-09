# tamacat-httpd 2.0 Migration Guide (from the earlier Tomcat 9 build)

`2.0.0-tc11.0.26` is the current 2.0 line of tamacat-httpd. It embeds
**Tomcat 11.0.26**, is built for **Java 25**, and replaces the earlier `2.0`
build, which embedded **Tomcat 9.0.121** and ran on Java 8.

That earlier build was a transitional artifact and is no longer maintained. The
`v2.0` branch now carries the Tomcat 11 line, which was developed as `v2.0-tc11`.
The version string names the embedded Tomcat, the same way 1.6's does
(`1.6.0-tc9.0.122`).

**2.0 is experimental.** It is built on HttpComponents Core 5.5, which is still
a beta release. The stable line is **1.6** (Java 8, Tomcat 9), on the `v1.6`
branch.

Only the embedded-Tomcat integration, the reverse proxy's backend connections
and the build settings changed. The Java SPI, the XML DI element and property
**names**, and the `server.properties` keys are all unchanged from the earlier
`2.0` build.

## Breaking changes

Nine changes can affect an existing `2.0` deployment. Items 2, 5, 7, 8 and 9
change runtime behaviour; the rest are build and packaging changes.

**If you read only one item, read 7** — it stops a correctly configured server
from serving JSPs, and nothing in your configuration will look wrong.

| # | Change | What breaks |
|---|---|---|
| 1 | Java 25 is required | A Java 8 (or any pre-25) runtime cannot load the jar |
| 2 | `allowRemoteAddrValve` values are netmasks, not regular expressions | Existing regex-style values become invalid |
| 3 | Maven coordinate is `2.0.0-tc11.0.26` | A dependency pinned to `2.0` does not pick this up |
| 4 | `TomcatHandler.allowRemoteAddrValue(Context)` is gone | A subclass overriding it no longer compiles |
| 5 | An unusable `allowRemoteAddrValve` value now stops startup | A server that used to start with a warning now refuses to start |
| 6 | The Docker image is a distroless multi-stage build | A pinned or customised `adoptopenjdk/openjdk8` layer, or anything that needs a shell in the image, no longer applies |
| 7 | The Jasper work directory must be deleted before the first start | A work directory left from Tomcat 9 serves `javax.servlet`-compiled JSPs and every JSP request fails |
| 8 | Proxied responses keep their `Content-Type` again (a fix) | Gzip and HTML-link interceptors start acting on proxied traffic that they silently skipped before |
| 9 | Backend connections are no longer reused | One backend connection per request, as in 1.6; backend authentication bound to a connection (NTLM, Negotiate) cannot work through the proxy |

---

### 1. Java 25 is required

`maven.compiler.source` and `maven.compiler.target` moved from `1.8` to `25`,
so the shipped classes are class-file major version 69. A pre-25 JVM rejects
them with `UnsupportedClassVersionError` at class-load time.

**25 is this project's choice, not a requirement of Tomcat 11.** Tomcat 11.0.26
itself needs only Java 17 (`org/apache/catalina/startup/Tomcat.class` is major
version 61). If you need to run on 17, rebuilding from source with the compiler
level lowered is possible as far as the Tomcat dependency is concerned. Note
that the level is declared in **two** places in `pom.xml` that must agree: the
`maven.compiler.source`/`maven.compiler.target` properties, and the explicit
`<source>`/`<target>` inside the `maven-compiler-plugin` configuration, which
takes precedence over the properties.

### 2. `allowRemoteAddrValve` values change syntax

This is the only external configuration contract that changes. The **property
name is unchanged** — `<property name="allowRemoteAddrValve">` in
`tomcat/components.xml` still works, and the setter is still
`setAllowRemoteAddrValve(String)`. Only the **value** is read differently.

The Tomcat 9 build passed the value to Tomcat's `RemoteAddrValve`, which compiles
it as a **regular expression**. The Tomcat 11 build passes it to
`RemoteCIDRValve`, which reads it as a **comma separated list of netmasks**.

Rewrite your values like this:

| Tomcat 9 build value (regular expression) | Tomcat 11 build value (netmask list) | Note |
|---|---|---|
| `127.0.0.1` | `127.0.0.1` | Unchanged. A bare address with no `/` is accepted as an exact IP. |
| `192\.168\..*` | `192.168.0.0/16` | A regex prefix match becomes a CIDR block. |
| `10\.0\.0\.1\|10\.0\.0\.2` | `10.0.0.1,10.0.0.2` | Regex alternation becomes a comma separated list. |

The value shipped in both bundled configurations
(`src/test/resources/tomcat/components.xml` and
`docker/tamacat/conf/tomcat/components.xml`) is `127.0.0.1`, which needs no
change.

Leaving `allowRemoteAddrValve` unset or empty still means "register no filter
at all", exactly as before. That is a configuration choice, not an error, and
item 5 below does not apply to it.

The switch to `RemoteCIDRValve` is not something Tomcat 11 forces —
`RemoteAddrValve` still works in 11. It is deliberate preparation for Tomcat 12,
where `RemoteAddrValve` is scheduled for removal; its javadoc in 11 already says
to use `RemoteCIDRValve` instead.

### 3. The Maven coordinate is `2.0.0-tc11.0.26`

```xml
<dependency>
  <groupId>org.tamacat</groupId>
  <artifactId>tamacat-httpd</artifactId>
  <version>2.0.0-tc11.0.26</version>
</dependency>
```

A build pinned to `2.0` does not pick this up, and the two must not share a
classpath.

The jar `mvn package` produces is
`tamacat-httpd-2.0.0-tc11.0.26-jar-with-dependencies.jar`.

The jar metadata follows: `Implementation-Version` is `2.0.0-tc11.0.26` and
`Bundle-Version` is `2.0.0`, matching 1.6's `1.6.0-tc9.0.122` and `1.6.0`. The
Tomcat part is left out of `Bundle-Version` because OSGi versions are dotted
`major.minor.micro.qualifier` strings. Both fields had been left at stale
`1.5.2` values in the earlier `2.0` build.

Tomcat remains an **optional** dependency, so it is still not pulled onto your
classpath transitively. If you use `TomcatHandler` you must declare
`tomcat-embed-core` and `tomcat-embed-jasper` yourself, as before.

### 4. `allowRemoteAddrValue(Context)` is removed

The `protected` method on both `TomcatHandler` and `TomcatServerHandler` is
renamed:

```java
// earlier 2.0 (Tomcat 9)
protected void allowRemoteAddrValue(Context ctx)

// 2.0.0-tc11.0.26
protected void applyRemoteAddrFilter(Context ctx)
```

The old name is not kept as a deprecated delegate. If you subclass either
handler and override the method, rename your override; nothing else in the
class changed, so the body usually carries over unmodified.

The old name was a typo: the field and setter have always ended in `Valve`
(`allowRemoteAddrValve`) while the method ended in `Value`. The rename also
drops the now-inaccurate `RemoteAddr` reference. The `@Deprecated` marker that
`TomcatHandler` carried on that method (and `TomcatServerHandler` did not) is
gone from both, since the deprecated Tomcat API it warned about is no longer
used.

### 5. A bad `allowRemoteAddrValve` value now stops the server

This is the change most likely to surprise you, and it interacts with item 2.

In the Tomcat 9 build, applying the filter happened inside a `try` block whose
`catch` logged a warning and continued. An unusable value therefore produced one
WARN line and the server started **with the webapp deployed and no access
filter on it** — the exact opposite of what the setting asks for.

Now the filter is applied outside that `catch`. An unusable value raises
`IllegalArgumentException`, which propagates out of `setServiceUrl` and aborts
startup before the listening socket opens. Tomcat's `RemoteCIDRValve` logs each
offending element first, so the log names the value that was rejected.

Because item 2 turns every regex-style value into an unusable one, a
configuration that started fine under the Tomcat 9 build can now refuse to
start. **That refusal is reporting a protection gap that was already there**,
silently. Fix the value using the table in item 2 rather than reverting.

Failures that are **not** about the filter are unchanged: a missing webapps
directory or a failing `addWebapp` still logs a warning and lets the server
start.

For war auto-deployment (`useWarDeploy=true`), deployment and filtering are now
two separate passes: every war found is deployed first, then the filter is
applied to each. A filter failure therefore does not prevent the earlier wars
from being *deployed* — but it does stop the server from starting, so none of
them ever serves a request.

### 6. The Docker image is a distroless multi-stage build

`docker/tamacat/Dockerfile` moves from `adoptopenjdk/openjdk8:alpine-jre` to a
two-stage build. An `amazoncorretto:25` builder stage creates a minimal JRE with
`jlink`; the final stage, `gcr.io/distroless/base-nossl-debian12`, holds only
that JRE, the jar and the `conf`/`htdocs`/`webapps` directories. Item 1 forces
the change of JRE: the old JRE 8 image cannot run class-file version 69 at all.

The final image has no shell, no package manager and no OpenSSL. A
customisation that adds packages or runs a shell inside the image no longer
works, and the image's `HEALTHCHECK` runs `org.tamacat.httpd.HealthCheck` on the
bundled JRE instead of a shell command. The image still exposes port 80 and
starts `org.tamacat.httpd.Httpd httpd.xml`.

The `COPY` takes `tamacat-httpd-${APP_VERSION}-jar-with-dependencies.jar` with
`APP_VERSION=2.0.0-tc11.0.26` — the file `mvn package` produces. If you build
the image yourself, stage that jar at `docker/tamacat/target/` as before.
`docker/docker-compose.yml` names the container `tamacat-httpd-2.0`.

---

### 7. Delete the Jasper work directory before the first start

**This one bites on upgrade even though nothing in your configuration is wrong.**

Jasper caches the servlet it generates from each JSP under the handler's work
directory (`work/Tomcat/<host>/<context>/org/apache/jsp/`), and it decides
whether that cache is stale by comparing timestamps against the JSP source. It
has no notion of the servlet API having been replaced underneath it.

So a work directory populated while you were running Tomcat 9 holds classes
compiled against `javax.servlet`. Tomcat 11 loads them as-is and the request
fails:

```
java.lang.NoClassDefFoundError: javax/servlet/ServletResponse
  ... root cause java.lang.ClassNotFoundException: javax.servlet.ServletResponse
```

The JSP source is fine. Point Tomcat 11's Jasper at the same source with an
empty work directory and it generates `jakarta.servlet.*` correctly.

**Before the first start after upgrading, delete the work directory:**

```
rm -rf work
```

(or whatever path the handler's `work` property resolves to — it defaults to
`${server.home}`). The directory is regenerated on the next request. Nothing
in it is worth keeping.

The same applies to any deployment pipeline that reuses a work volume between
releases: clear it as part of the Tomcat 11 rollout, not just once by hand.

### 8. Proxied responses keep their `Content-Type` again

This is a fix, not a new restriction, but it changes what your clients receive,
so it belongs on this list. The earlier `2.0` build also carries it from commit
3ead7c5 on; it matters if your build predates that.

`reverse-header.properties` listed `Content-Type` among the hop-by-hop response
headers to strip. On the 1.5 line that was harmless: HttpCore 4.4's
`ResponseContent` interceptor added `entity.getContentType()` back to the
response whenever the header was missing, so the client still got it.
HttpCore 5's `ResponseContent` does not do that — it handles only
`Content-Length` and `Transfer-Encoding` — so from the 2.0 (HttpCore 5)
release onwards **every response passing through the reverse proxy lost its
`Content-Type`**, not just error responses.

Two interceptors read that header to decide whether to act, and were therefore
silently inert for proxied traffic:

- `GzipResponseInterceptor` (decides what is worth compressing)
- `HtmlLinkConvertInterceptor` (decides what is HTML worth rewriting)

`Content-Type` has been removed from the strip list. `Content-Encoding` stays
on it deliberately, because `GzipResponseInterceptor` sets that header itself.

**What you may notice after upgrading:**

- Responses now carry the backend's `Content-Type` verbatim, charset included.
- Gzip compression and HTML link conversion start working on proxied responses
  if you have those interceptors configured. If you sized anything around them
  being inactive, re-check it.
- If you configured `SecureResponseHeaderFilter` to supply a default
  `Content-Type`, it will now fire less often: it only sets the header when one
  is absent, and one usually will not be.

### 9. Backend connections are no longer reused

The earlier `2.0` build kept each backend (reverse-proxy) connection open
across the requests of one client connection and reused it. That reuse failed
in ways that are hard to see. When the backend closed an idle connection just
as the next request was sent, or answered `Connection: close` (which was
ignored), or a firewall or NAT dropped the idle connection silently, that
request failed with 503 — in the last case only after `BackEndSocketTimeout`.

Now every request gets a backend connection of its own. The request is sent
with `Connection: close`, so the backend closes its side after the response,
and tamacat-httpd closes the connection as soon as the response has been sent
to the client. This is what 1.6 does too.

**What you may notice after upgrading:**

- One TCP connection to the backend per request — and one TLS handshake, for
  an `https` backend. This includes the embedded Tomcat, because
  `TomcatHandler` forwards to it over loopback.
- Keep-alive between the client and tamacat-httpd is not affected: the
  backend's `Connection` and `Keep-Alive` headers are not passed on.
- Backend authentication that is bound to a connection (NTLM,
  Negotiate/Kerberos) cannot work through the proxy, as in 1.6.
- `BackEndKeepAliveConnReuseStrategy` is removed. Nothing ever called it, so
  the `BackEndKeepAlive`, `BackEndKeepAliveTimeout` and
  `BackEndMaxKeepAliveRequests` settings had no effect before either.

## What did not change

- The Java SPI: the public interfaces, abstract classes and enums are untouched.
- XML DI element and property **names**, including
  `<property name="allowRemoteAddrValve">` — only its value syntax changed
  (item 2).
- `server.properties` keys.
- The bundled sample JSPs under `webapps/`. They use no `javax.*` or
  `jakarta.*` namespace and needed no edit for Jakarta EE 10 / Jasper 11.
  (They will still fail on a stale work directory — see item 7. The source is
  correct; the servlet cached from it is not.)
- Every Tomcat API this project consumes. All of it exists in Tomcat 11 with
  the same signatures as in 9.0.121 (compared against 11.0.25; the build
  compiles against 11.0.26 unchanged), which is why the migration needed no
  compatibility shims.

## Not covered here

`docs/MIGRATION-2.0.md` and `docs/RELEASE-NOTES-2.0.md` describe the 1.5.x to
2.0 move (HttpCore 4.4 to 5.5). They are unchanged by this release and still
apply — read them first if you are coming from 1.5.x rather than from the
earlier `2.0` build.
