# tamacat-httpd 1.7 Migration Guide (from 1.6)

`1.7.0-tc11.0.27` is the successor of `1.6.0-tc9.0.122`. It embeds
**Tomcat 11.0.27** instead of Tomcat 9.0.122 and is built for **Java 25**
instead of Java 8. Like 1.6, it keeps the vendored HttpCore 4.4 layer
(`org.tamacat.httpcore4`); it does not move to HttpComponents Core 5 (that is
the `v2.0` line).

This guide lists every change that can affect an existing 1.6 deployment
(items B-1 to B-14), what changes, and what you need to do. A table of every
dependency change follows at the end.

Items marked **Pending verification** describe behaviour that is derived from
reading the code and has not yet been confirmed by running the packaged jar,
the Docker image or the configuration in question. They will be confirmed or
corrected in build-and-test before release.

## Summary

| # | Change | Who is affected |
|---|---|---|
| B-1 | Java 25 is required | Everyone |
| B-2 | Tomcat 9 -> Tomcat 11; webapps move to `jakarta.*` | Users of the Tomcat integration |
| B-3 | `allowRemoteAddrValve` values are netmasks, not regular expressions | Users of the Tomcat access filter |
| B-4 | An unusable `allowRemoteAddrValve` value stops startup | Users of the Tomcat access filter |
| B-5 | `StringUtils.formatJson(String)` is removed | Code calling that method |
| B-6 | `logback-classic` 1.6.5 is a required dependency | Users of another SLF4J binding, or of another logback / Jackson line |
| B-7 | `commons-codec`, `commons-logging` and JAXB are no longer pulled in | Code that got those libraries through tamacat-httpd |
| B-8 | The mTLS certificate array has the type `java.security.cert.X509Certificate[]` | Code reading the client certificate attribute |
| B-9 | `jar-with-dependencies` is built with the shade plugin | Tooling that depends on the jar's internal layout |
| B-10 | `allowRemoteAddrValue(Context)` is renamed to `applyRemoteAddrFilter(Context)` | Subclasses of `TomcatHandler` / `TomcatServerHandler` |
| B-11 | The Jasper work directory must be deleted before the first start | Users of JSPs who keep the work directory across upgrades |
| B-12 | The Docker image is a distroless multi-stage build | Users of the bundled Docker files |
| B-13 | Requests whose URL cannot be built are answered with 404 | Clients that send unencoded special characters through the reverse proxy |
| B-14 | Configured URLs must follow `java.net.URI` syntax | Configurations with unencoded special characters in URLs |

**Read B-4 and B-11 first.** B-4 can stop a server that started under 1.6;
B-11 makes every JSP fail although nothing in your configuration is wrong.

---

## B-1. Java 25 is required

The classes are compiled for Java 25 (class-file major version 69). A JVM
older than 25 rejects them with `UnsupportedClassVersionError`.

**What to do:** run tamacat-httpd on a Java 25 runtime.

Java 25 is this project's choice, not a requirement of Tomcat 11: Tomcat 11.0.27
itself needs Java 17 (`org/apache/catalina/startup/Tomcat.class` is major
version 61). If you rebuild with a lower level, note that `pom.xml` declares it
in two places that must agree: the `maven.compiler.source` /
`maven.compiler.target` properties and the explicit `<source>` / `<target>` of
`maven-compiler-plugin`, which take precedence over the properties.

## B-2. Tomcat 11; webapps move to `jakarta.*`

The embedded Tomcat moves from 9.0.122 to 11.0.27 (Servlet 6.1, Jakarta EE 11).
Tomcat 11 implements the `jakarta.servlet` API; it does not load webapps that
are compiled against `javax.servlet`.

**What to do:**

- Migrate the webapps you deploy through `TomcatHandler` or
  `TomcatServerHandler` from `javax.servlet.*` to `jakarta.servlet.*` (and the
  other `javax.*` Jakarta EE packages to `jakarta.*`).
- Tomcat is still an **optional** dependency. If you use the Tomcat
  integration, declare `org.apache.tomcat.embed:tomcat-embed-core` and
  `tomcat-embed-jasper` at version 11.0.27 yourself, as you did for 9.0.122.

The Tomcat API that tamacat-httpd itself calls has the same signatures in
9.0.122 and 11.0.27, so the XML DI configuration of the handlers is unchanged.

## B-3. `allowRemoteAddrValve` values are netmasks

The property name is unchanged (`<property name="allowRemoteAddrValve">`,
`setAllowRemoteAddrValve(String)`). Only the value is read differently:
1.6 passed it to Tomcat's `RemoteAddrValve`, which reads a **regular
expression**; 1.7 passes it to `RemoteCIDRValve`, which reads a **comma
separated list of netmasks**.

**What to do:** rewrite regular-expression values as netmasks.

| 1.6 value (regular expression) | 1.7 value (netmask list) | Note |
|---|---|---|
| `127.0.0.1` | `127.0.0.1` | Unchanged. A single address without `/` is accepted as is. |
| `192\.168\..*` | `192.168.0.0/16` | A prefix match becomes a CIDR block. |
| `10\.0\.0\.1\|10\.0\.0\.2` | `10.0.0.1,10.0.0.2` | Alternation becomes a comma separated list. |

The value in both bundled configurations
(`docker/tamacat/conf/tomcat/components.xml` and
`src/test/resources/tomcat/components.xml`) is `127.0.0.1` and needs no change.
An unset or empty value still means "no filter" (no valve is registered).

## B-4. An unusable `allowRemoteAddrValve` value stops startup

In 1.6 the filter was applied inside the `try` block that deploys the webapp.
An unusable value was caught, logged as a WARN, and the server started **with
the webapp deployed and no access filter on it**.

In 1.7 the filter is applied outside that block. An unusable value raises
`IllegalArgumentException`, which propagates out of `setServiceUrl` and stops
the `HttpEngine`'s initialisation before its listening socket is opened. Because
B-3 reads the value as a netmask list, a regular-expression value that is not
also a valid netmask list (for example `192\.168\..*`) is unusable, and a
configuration that started under 1.6 can refuse to start under 1.7. Values that
are valid in both syntaxes, such as `127.0.0.1` (see the table in B-3), keep
working. That refusal reports a protection gap that already existed; fix the
value (B-3) rather than reverting.

Failures that are not about the filter are unchanged: a missing webapps
directory or a failing `addWebapp` still logs a WARN and the server starts. With
war auto-deployment (`useWarDeploy=true`), all wars are deployed first and the
filter is then applied to every deployed context; a filter failure still stops
startup.

What is guaranteed, and what is not:

| Configuration | `TomcatHandler` (Tomcat binds `127.0.0.1` by default; tamacat-httpd proxies to it) | `TomcatServerHandler` (Tomcat binds `0.0.0.0` by default; its connector faces clients) |
|---|---|---|
| One `HttpEngine` in the JVM | The listening socket is not opened and Tomcat is not started. | The same. Tomcat's connector is not opened. |
| Several `HttpEngine`s in the JVM (`Httpd.main` accepts several comma separated configuration files and starts one engine per file) | **Not guaranteed.** The failed engine's Tomcat instance is already registered in the JVM-wide `TomcatManager.MANAGER`, with its webapps added and no filter. When another engine starts successfully, `TomcatManager.start()` starts every registered instance, including that one. Exposure is limited to loopback. | **Not guaranteed**, by the same mechanism, and Tomcat listens on all interfaces. |

The process itself is not terminated in either case.

**Pending verification:** both rows are derived from the startup order in the
code. The multi-engine mechanism has been observed inside the test suite (one
engine's `TomcatManager.start()` started a Tomcat instance registered by another
test), but neither row has yet been measured with an invalid value against the
packaged jar (listening ports, a direct request to Tomcat's port, whether the
process stays alive).

**What to do:** fix the value. If you run several engines in one JVM together
with `TomcatServerHandler`, check the startup log for the
`IllegalArgumentException` instead of relying on the server not starting.

## B-5. `StringUtils.formatJson(String)` is removed

`org.tamacat.httpd.core.util.StringUtils.formatJson(String)` relied on the
`javax.script` JavaScript engine, which no longer ships with the JDK. The
method and its `javax.script` imports are removed.

**What to do:** if your code calls it, use a JSON library (for example Jackson,
which 1.7 already depends on; see B-6) to pretty-print.

## B-6. `logback-classic` 1.6.5 is required

1.6 declared `logback-classic` and `logback-core` 1.3.16 as optional
dependencies. 1.7 declares `logback-classic` 1.6.5 as a required dependency,
together with `net.logstash.logback:logstash-logback-encoder` 9.0 and
`tools.jackson.core:jackson-databind` 3.2.3. `logback-core` is no longer
declared directly; it arrives as a dependency of `logback-classic` (1.6.5).

**What to do:**

- If you use another SLF4J binding (for example `slf4j-simple` or
  `log4j-slf4j2-impl`), exclude `ch.qos.logback:logback-classic` from the
  tamacat-httpd dependency, or SLF4J will see two providers.
- If you depend on another logback line or on Jackson 2, check for version
  conflicts. `jackson-databind` 3.x uses the `tools.jackson` group and
  packages, not Jackson 2's `com.fasterxml.jackson`; it brings
  `com.fasterxml.jackson.core:jackson-annotations` 2.22, which a Jackson 2
  dependency of yours may also resolve to another version.
- Logback 1.6 is dual-licensed under EPL 2.0 or LGPL 2.1 (1.3 was EPL 1.0 or
  LGPL 2.1).

**Logback's servlet initializer is disabled by default.** `logback-classic`
1.6.5 registers
`ch.qos.logback.classic.servlet.LogbackServletContainerInitializer` for
`jakarta.servlet`, and embedded Tomcat would run it for every webapp context.
When any such context stops, the listener it installs stops the JVM-wide
logback `LoggerContext`, and all logging of the process would stop from then
on. 1.7 therefore sets the system property
`logbackDisableServletContainerInitializer` to `true` before it creates the
first embedded Tomcat, unless that system property or an environment variable
of the same name already has a non-blank value. To get logback's default
behaviour back, start the JVM with

```
-DlogbackDisableServletContainerInitializer=false
```

The system property is JVM-wide: it also applies to any other servlet
container in the same JVM whose webapps start after tamacat-httpd created its
first Tomcat. A single webapp can still opt back in with a `<context-param>` of
the same name, which logback reads before the system property. Unit tests
start and stop a webapp and check that application logging continues by
default and stops with the value `false`. Whether 1.6 behaves the same was not
measured (`logback-classic` 1.3.16 registers the same initializer for
`javax.servlet`).

## B-7. `commons-codec`, `commons-logging` and JAXB are no longer pulled in

1.6 had these compile-scope dependencies, which reached your classpath
transitively:

- `commons-codec:commons-codec` 1.22.1
- `commons-logging:commons-logging` 1.4.0 (still used by tamacat-httpd's own
  tests, now test scope only)
- `jakarta.xml.bind:jakarta.xml.bind-api` 2.3.3 with
  `jakarta.activation:jakarta.activation-api` 1.2.2
- `com.sun.xml.bind:jaxb-impl` 2.3.9 with `com.sun.activation:jakarta.activation`
  1.2.2 (runtime)

tamacat-httpd no longer uses them: hexadecimal formatting uses
`java.util.HexFormat`, and the `javax.xml.bind` dependency is gone.

**What to do:** if your code uses any of these libraries, declare them in your
own build. The full list of dependency changes is in
[Dependency changes](#dependency-changes).

## B-8. The mTLS certificate array is `java.security.cert.X509Certificate[]`

With TLS client authentication, `RequestUtils` stores the peer certificate chain
in the `HttpContext`. 1.6 stored the result of the deprecated
`SSLSession.getPeerCertificateChain()`, a `javax.security.cert.X509Certificate[]`.
1.7 stores the X.509 entries of `SSLSession.getPeerCertificates()`, in their
original order, as a `java.security.cert.X509Certificate[]`.

The attribute keys are unchanged, including the historical key
`"javax.security.cert.X509Certificate[]"`; only the value's type changes. The
principal (`SSLSession.getPeerPrincipal`), the session id and the cipher suite
attributes are unchanged.

**What to do:** where your code casts the value of
`"javax.security.cert.X509Certificate[]"`, cast it to
`java.security.cert.X509Certificate[]` instead.

## B-9. `jar-with-dependencies` is built with the shade plugin

1.6 built `jar-with-dependencies` with `maven-assembly-plugin`; 1.7 builds it
with `maven-shade-plugin` 3.6.0. The file name keeps the same form:
`tamacat-httpd-1.7.0-tc11.0.27-jar-with-dependencies.jar`. Service files under
`META-INF/services` are merged, and the manifest now contains
`Multi-Release: true`.

The jar contains `META-INF/versions/9/module-info.class` of the shaded
`commons-lang3`. On the module path, the whole jar would therefore be read as
the module `org.apache.commons.lang3`.

**What to do:** keep using the jar on the class path (as the bundled Docker
image does). If a tool inspects the jar's internal layout, re-check it against
the new jar.

## B-10. `allowRemoteAddrValue(Context)` is renamed

The `protected` method on `TomcatHandler` and `TomcatServerHandler` is renamed:

```java
// 1.6
protected void allowRemoteAddrValue(Context ctx)

// 1.7
protected void applyRemoteAddrFilter(Context ctx)
```

The old name is not kept. The XML property `allowRemoteAddrValve` and its
setter are unchanged (B-3).

**What to do:** if you subclass either handler and override or call the method,
rename it.

## B-11. Delete the Jasper work directory before the first start

Jasper caches the servlets it generates from JSPs under the handler's work
directory (`TomcatHandler`'s `work` property, default `${server.home}`) and
decides whether a cached servlet is stale only by timestamps. A work directory
populated by Tomcat 9 holds classes compiled against `javax.servlet`; Tomcat 11
loads them as they are and every JSP request fails, for example with
`java.lang.NoClassDefFoundError: javax/servlet/ServletResponse`.

**What to do:** before the first start of 1.7, delete the work directory (the
path that the handler's `work` property resolves to). It is regenerated on the
next request. Do the same in any deployment that reuses a work volume between
releases.

**Pending verification:** the failure was observed when moving the `v2.0` line
from Tomcat 9 to Tomcat 11; it has not yet been reproduced with 1.7 itself.

## B-12. The Docker image is a distroless multi-stage build

`docker/tamacat/Dockerfile` and `docker/docker-compose.yml` change as follows
compared with 1.6:

| Aspect | 1.6 | 1.7 |
|---|---|---|
| Base image | `adoptopenjdk/openjdk8:alpine-jre` (Java 8 JRE, cannot run B-1's class files) | Two stages, both pinned by digest: an `amazoncorretto:25` builder that creates a reduced JRE with `jlink`, and a final `gcr.io/distroless/base-nossl-debian12` image |
| Shell and packages | `apk` installs `bash` and `curl` | No shell, no package manager, no `curl` in the final image |
| JRE | the base image's `java` | `/opt/jre` (jlink modules: `java.base`, `java.desktop`, `java.instrument`, `java.management`, `java.management.rmi`, `java.naming`, `java.rmi`, `java.security.jgss`, `java.sql`, `jdk.crypto.ec`) |
| Layout in the image | `/usr/local/tamacat-httpd/lib/<jar>`, `/usr/local/tamacat-httpd/{conf,htdocs,webapps}` | `/app/app.jar`, `/app/{conf,htdocs,webapps}` |
| File ownership | not set | `conf`, `htdocs`, `webapps` are copied with `--chown=65532:65532` |
| `CLASSPATH` | `.../conf:.../lib/tamacat-httpd-1.6.0-tc9.0.122-jar-with-dependencies.jar` | `/app/conf:/app/app.jar` |
| `ENTRYPOINT` | `["java", "org.tamacat.httpd.Httpd", "httpd.xml"]` | `["/opt/jre/bin/java", "org.tamacat.httpd.Httpd", "httpd.xml"]` |
| Health check | compose only: `curl -f http://localhost/check.html` | Dockerfile `HEALTHCHECK` and compose `healthcheck` both run `java -cp /app/app.jar org.tamacat.httpd.HealthCheck` (new class; GET `http://localhost/check.html`, exit 0 on 200) |
| compose `container_name` | `tamacat-httpd-1.6` | `tamacat-httpd-1.7` |
| compose `build.context` | `tamacat-httpd` (a directory that does not exist under `docker/`) | `tamacat` (`docker/tamacat`) |
| compose `cap_add` | none | `NET_BIND_SERVICE` |
| Exposed port, command | `EXPOSE 80`, `httpd.xml` | unchanged |

The jar is still built with `mvn package` outside Docker. Copy
`target/tamacat-httpd-1.7.0-tc11.0.27-jar-with-dependencies.jar` to
`docker/tamacat/target/` before `docker compose build`. `docker/tamacat/conf`
is unchanged.

**What to do:** customisations that run shell commands or install packages in
the image no longer work; build them in another stage. If you mount volumes
into `/app`, check their ownership against UID/GID 65532. Update anything that
refers to the old paths or the old container name.

**Pending verification:** the image has not yet been built or started for 1.7.
To be confirmed: that the jlink module set starts the server, serves a JSP and
passes the health check; the user the container runs as (the Dockerfile has no
`USER` instruction, so it is whatever the pinned distroless digest sets; the
2.0 image's notes give 65532 for the `:nonroot` variant); and that the final
image contains no OpenSSL libraries and no shell (verified for the 2.0 image
built from the same base digest).

## B-13. Requests whose URL cannot be built are answered with 404

1.7 builds URLs with `java.net.URI` instead of the deprecated `java.net.URL`
constructors. `URI` enforces the RFC 2396 syntax, so the reverse proxy (and
`RedirectHttpHandler`) can no longer build a backend URL for a request whose
request URI, **path or query string**, does not follow that syntax. Such a
request is not forwarded and is answered with **404 Not Found**. In 1.6 most of
these requests were forwarded as they were.

Examples:

| Request URI | 1.6 | 1.7 |
|---|---|---|
| Space, `\|`, `"`, `^`, `` ` ``, `\`, `<`, `>`, `[`, `]` in the path (`/x y`, `/x\|y`, `/x[1]`) | forwarded | **404** |
| `\|`, `{`, `}` in the query (`/x?a=1\|2`, `/x?a={b}`) | forwarded | **404** |
| Invalid percent escape (`/x%zz`, `/x?q=%zz`) | forwarded | **404** |
| Unencoded non-ASCII (`/x/é`, `/x?q=日本`) | forwarded | forwarded |
| `[`, `]` in the query (`/x?a[]=1`), fragment (`/x#f`) | forwarded | forwarded |

The table shows tested examples, not a complete list of rejected characters.

Also answered with 404:

- A `Host` header whose value is not a valid URL host (for example `a b` or
  `h{1}`) when the reverse proxy rewrites the `Host` header.
- A request URI that does not start with `/` (for example `*`, `test.html`,
  `?q=1`, or `http://host` without a path) when the reverse proxy rewrites the
  `Host` header. The public static methods `RequestUtils.getRequestURL(...)`
  and `RequestUtils.getRequestHostURL(...)` now return `null` for such request
  URIs (1.6 returned a URL built from the `Host` header and the request URI).
  Building the URL through `URI` from such a request URI would let it become
  part of the authority (for example `@evil.example/x` would change the host
  to `evil.example`), so it is rejected. If you call these methods yourself,
  handle `null`; both methods could already return `null` in 1.6.
- When `RedirectHttpHandler` cannot build the target URL. (In 1.6 this case
  raised a `NullPointerException`, which the handler turned into a 503-class
  error response, by reading of the code.)

**What to do:** make sure clients percent-encode the characters above (for
example `?filter=a%7Cb` instead of `?filter=a|b`).

## B-14. Configured URLs must follow `java.net.URI` syntax

The same `URI` syntax now applies to URLs in the configuration. A value that 1.6
accepted but that contains a space, `|`, `{`, `}`, an invalid percent escape or
another character `URI` rejects is no longer accepted. The consequence depends
on where the value is configured:

| Where | Consequence in 1.7 |
|---|---|
| `url-config.xml`, `<reverse>` element | Loading the configuration fails with a `NullPointerException`. (1.6 failed the same way for values it already rejected, such as a URL without a scheme.) |
| `url-config.xml`, `host` attribute of `<service>` | No error. The service's host is silently left unset, exactly as if the attribute were absent. |
| `server.properties`, `https.CRL` with an `http://` or `https://` URL | `IllegalArgumentException` with the same message as before: `https.CRL [<value>] file not found.` `file:` and class-path CRL locations are not affected. |
| Service path of a service handled by `TomcatHandler` (the `path` attribute) | Startup continues, but the handler's backend URL is not set: every request to that service is answered with 503 (`reverseUrl is null.`). A WARN naming the service path and the parse error is logged at startup. |

The `https.CRL` row and the rejection of these values by the URL parsing are
covered by unit tests, as are the WARN and the unset backend URL for a Tomcat
service path. The other consequences (`<reverse>`, `host` attribute, the 503
for a Tomcat service path) come from reading the code; they have not been
executed against a running server.

Values that `URI` accepts, such as `http://localhost:8080/app/` or
`http://my_host:8080/x`, give the same result as in 1.6.

**What to do:** percent-encode such characters in configured URLs and in the
service paths of Tomcat-handled services.

---

## Not breaking, but new

- `org.tamacat.httpd.HealthCheck`: a standalone probe used by the Docker
  `HEALTHCHECK` (B-12). It sends `GET http://localhost/check.html` and exits
  with 0 on status 200, 1 otherwise.
- The test suite moved to JUnit 5 (JUnit Jupiter 6.1.3); Mockito and PowerMock
  are no longer used. This affects only builds of tamacat-httpd itself.

## What did not change

- The Java SPI (public interfaces, abstract classes and enums), apart from the
  items above.
- XML DI class names and property names, including `allowRemoteAddrValve` and
  `TomcatEmbeded`.
- `server.properties` keys.
- The contents of `META-INF/services` in the project's own resources.
- The vendored HttpCore 4.4 layer (`org.tamacat.httpcore4`), apart from the
  removal of an `AccessController.doPrivileged` call in `BasicConnFactory`.

## Dependency changes

From `mvn dependency:tree` of `1.6.0-tc9.0.122` and of `1.7.0-tc11.0.27`
(all 33 and 29 entries compared; unchanged entries are listed at the end).

### Compile scope (reaches your classpath)

| Dependency | 1.6.0-tc9.0.122 | 1.7.0-tc11.0.27 |
|---|---|---|
| `org.apache.tomcat.embed:tomcat-embed-core` | 9.0.122 (direct, optional) | 11.0.27 (direct, optional) |
| `org.apache.tomcat.embed:tomcat-embed-jasper` | 9.0.122 (direct, optional) | 11.0.27 (direct, optional) |
| `org.apache.tomcat.embed:tomcat-embed-el` | 9.0.122 (transitive, optional) | 11.0.27 (transitive, optional) |
| `org.apache.tomcat:tomcat-annotations-api` | 9.0.122 (transitive, optional) | 11.0.27 (transitive, optional) |
| `org.eclipse.jdt:ecj` | 3.26.0 (transitive via Jasper, optional) | 3.46.100 (direct, **not optional**) |
| `org.attoparser:attoparser` | 2.0.7.RELEASE (transitive via Thymeleaf) | 2.0.9.RELEASE (direct) |
| `org.apache.commons:commons-lang3` | 3.20.0 | 3.21.0 |
| `ch.qos.logback:logback-classic` | 1.3.16 (direct, optional) | 1.6.5 (direct, **required**) |
| `ch.qos.logback:logback-core` | 1.3.16 (direct, optional) | 1.6.5 (transitive via `logback-classic`) |
| `net.logstash.logback:logstash-logback-encoder` | - | 9.0 (direct) |
| `tools.jackson.core:jackson-databind` | - | 3.2.3 (direct) |
| `tools.jackson.core:jackson-core` | - | 3.2.3 (transitive) |
| `com.fasterxml.jackson.core:jackson-annotations` | - | 2.22 (transitive) |
| `commons-codec:commons-codec` | 1.22.1 (direct) | - |
| `commons-logging:commons-logging` | 1.4.0 (direct) | test scope only |
| `jakarta.xml.bind:jakarta.xml.bind-api` | 2.3.3 (direct) | - |
| `jakarta.activation:jakarta.activation-api` | 1.2.2 (transitive) | - |
| `com.sun.xml.bind:jaxb-impl` | 2.3.9 (direct) | - |
| `com.sun.activation:jakarta.activation` | 1.2.2 (transitive, runtime scope) | - |

### Test scope (affects only builds of tamacat-httpd)

| Dependency | 1.6.0-tc9.0.122 | 1.7.0-tc11.0.27 |
|---|---|---|
| `junit:junit` | 4.13.2 (direct) | - |
| `org.hamcrest:hamcrest-core` | 1.3 | - |
| `org.powermock:powermock-module-junit4` | 2.0.9 (direct) | - |
| `org.powermock:powermock-api-mockito2` | 2.0.9 (direct) | - |
| `org.powermock:powermock-module-junit4-common`, `powermock-reflect`, `powermock-core`, `powermock-api-support` | 2.0.9 | - |
| `org.mockito:mockito-core` | 3.3.3 | - |
| `net.bytebuddy:byte-buddy`, `byte-buddy-agent` | 1.10.14 | - |
| `org.objenesis:objenesis` | 2.6 | - |
| `org.junit.jupiter:junit-jupiter` | - | 6.1.3 (direct) |
| `org.junit.jupiter:junit-jupiter-api`, `junit-jupiter-engine`, `junit-jupiter-params` | - | 6.1.3 |
| `org.junit.platform:junit-platform-commons`, `junit-platform-engine` | - | 6.1.3 |
| `org.opentest4j:opentest4j` | - | 1.3.0 |
| `org.apiguardian:apiguardian-api` | - | 1.1.2 |
| `org.jspecify:jspecify` | - | 1.0.0 |
| `commons-logging:commons-logging` | (compile, above) | 1.4.0 (direct) |

### Unchanged

`org.thymeleaf:thymeleaf` 3.1.5.RELEASE, `ognl:ognl` 3.3.4,
`org.javassist:javassist` 3.29.0-GA, `org.unbescape:unbescape` 1.1.6.RELEASE,
`org.apache.velocity:velocity-engine-core` 2.4.1 (optional),
`org.slf4j:slf4j-api` 2.0.20.
