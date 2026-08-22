# tamacat-httpd 2.0-tc11 Migration Guide (from 2.0)

`2.0-tc11` is the Tomcat 11 line of tamacat-httpd. It embeds **Tomcat 11.0.25**
where `2.0` embeds **Tomcat 9.0.121**, and it is built for **Java 25**.

The two lines are maintained in parallel. `2.0` is not deprecated by this
release and continues to target Tomcat 9; `2.0-tc11` is a separate artifact,
not an upgrade in place. Pick one.

Only the embedded-Tomcat integration and the build settings changed. The Java
SPI, the XML DI element and property **names**, and the `server.properties`
keys are all unchanged from `2.0`.

## Breaking changes

Eight changes can affect an existing `2.0` deployment. Items 2, 5, 7 and 8
change runtime behaviour; the rest are build and packaging changes.

**If you read only one item, read 7** — it stops a correctly configured server
from serving JSPs, and nothing in your configuration will look wrong.

| # | Change | What breaks |
|---|---|---|
| 1 | Java 25 is required | A Java 8 (or any pre-25) runtime cannot load the jar |
| 2 | `allowRemoteAddrValve` values are netmasks, not regular expressions | Existing regex-style values become invalid |
| 3 | Maven coordinate is `2.0-tc11` | A dependency pinned to `2.0` does not pick this up |
| 4 | `TomcatHandler.allowRemoteAddrValue(Context)` is gone | A subclass overriding it no longer compiles |
| 5 | An unusable `allowRemoteAddrValve` value now stops startup | A server that used to start with a warning now refuses to start |
| 6 | The Docker base image is `amazoncorretto:25-alpine` | A pinned or customised `adoptopenjdk/openjdk8` layer no longer applies |
| 7 | The Jasper work directory must be deleted before the first start | A work directory left from Tomcat 9 serves `javax.servlet`-compiled JSPs and every JSP request fails |
| 8 | Proxied responses keep their `Content-Type` again (a fix) | Gzip and HTML-link interceptors start acting on proxied traffic that they silently skipped before |

---

### 1. Java 25 is required

`maven.compiler.source` and `maven.compiler.target` moved from `1.8` to `25`,
so the shipped classes are class-file major version 69. A pre-25 JVM rejects
them with `UnsupportedClassVersionError` at class-load time.

**25 is this project's choice, not a requirement of Tomcat 11.** Tomcat 11.0.25
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

`2.0` passed the value to Tomcat's `RemoteAddrValve`, which compiles it as a
**regular expression**. `2.0-tc11` passes it to `RemoteCIDRValve`, which reads
it as a **comma separated list of netmasks**.

Rewrite your values like this:

| `2.0` value (regular expression) | `2.0-tc11` value (netmask list) | Note |
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

### 3. The Maven coordinate is `2.0-tc11`

```xml
<dependency>
  <groupId>org.tamacat</groupId>
  <artifactId>tamacat-httpd</artifactId>
  <version>2.0-tc11</version>
</dependency>
```

`2.0` and `2.0-tc11` are different artifacts of the same source line. Do not
expect a build pinned to `2.0` to pick this up, and do not put both on one
classpath.

The assembly jar is renamed to match:
`tamacat-httpd-2.0-tc11-jar-with-dependencies.jar`.

The jar metadata follows too — `Implementation-Version` becomes `2.0-tc11` and
`Bundle-Version` becomes `2.0.0.tc11`. The two differ because OSGi versions are
dotted `major.minor.micro.qualifier` strings and `2.0-tc11` is not one. Both
fields had been left at stale `1.5.2` values and are corrected here.

Tomcat remains an **optional** dependency, so it is still not pulled onto your
classpath transitively. If you use `TomcatHandler` you must declare
`tomcat-embed-core` and `tomcat-embed-jasper` yourself, as before.

### 4. `allowRemoteAddrValue(Context)` is removed

The `protected` method on both `TomcatHandler` and `TomcatServerHandler` is
renamed:

```java
// 2.0
protected void allowRemoteAddrValue(Context ctx)

// 2.0-tc11
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

In `2.0`, applying the filter happened inside a `try` block whose `catch`
logged a warning and continued. An unusable value therefore produced one WARN
line and the server started **with the webapp deployed and no access filter on
it** — the exact opposite of what the setting asks for.

In `2.0-tc11` the filter is applied outside that `catch`. An unusable value
raises `IllegalArgumentException`, which propagates out of `setServiceUrl` and
aborts startup before the listening socket opens. Tomcat's `RemoteCIDRValve`
logs each offending element first, so the log names the value that was rejected.

Because item 2 turns every regex-style value into an unusable one, a
configuration that started fine under `2.0` can now refuse to start. **That
refusal is reporting a protection gap that was already there**, silently, in
`2.0`. Fix the value using the table in item 2 rather than reverting.

Failures that are **not** about the filter are unchanged: a missing webapps
directory or a failing `addWebapp` still logs a warning and lets the server
start.

For war auto-deployment (`useWarDeploy=true`), deployment and filtering are now
two separate passes: every war found is deployed first, then the filter is
applied to each. A filter failure therefore does not prevent the earlier wars
from being *deployed* — but it does stop the server from starting, so none of
them ever serves a request.

### 6. The Docker base image is `amazoncorretto:25-alpine`

`docker/tamacat/Dockerfile` moves from `adoptopenjdk/openjdk8:alpine-jre` to
`amazoncorretto:25-alpine`. This is forced by item 1: the old JRE 8 image
cannot run class-file version 69 at all.

The image is still Alpine based, so the existing
`apk add --no-cache bash curl` line is unchanged and still installs both (they
are not preinstalled in the Corretto image). `EXPOSE 80`, the `ENTRYPOINT` and
the `conf`/`htdocs`/`webapps` copies are untouched.

The `COPY` and `ENV CLASSPATH` lines now name
`tamacat-httpd-2.0-tc11-jar-with-dependencies.jar`; they had been left pointing
at a `1.5.2` jar that the `2.0` build already did not produce.
`docker/docker-compose.yml` gets the matching `container_name:
tamacat-httpd-2.0-tc11` for the same reason. If you build the image yourself,
stage the assembly jar at `docker/tamacat/target/` as before.

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
so it belongs on this list.

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
- Every Tomcat API this project consumes. All of it exists in 11.0.25 with the
  same signatures as in 9.0.121, which is why the migration needed no
  compatibility shims.

## Not covered here

`docs/MIGRATION-2.0.md` and `docs/RELEASE-NOTES-2.0.md` describe the 1.5.x to
2.0 move (HttpCore 4.4 to 5.5). They are unchanged by this release and still
apply — read them first if you are coming from 1.5.x rather than from 2.0.
