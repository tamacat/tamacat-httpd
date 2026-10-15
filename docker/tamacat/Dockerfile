# syntax=docker/dockerfile:1
ARG APP_VERSION=1.7.0-tc11.0.27

########################################
# Stage 1: builder - produces the custom JRE
########################################
FROM amazoncorretto:25@sha256:10a3c794ac57ecfc386938283fba9da351c5f0bda3a155482a1193b9806f1854 AS builder
# glibc-based (NOT amazoncorretto:25-alpine/musl) - must match the final
# stage's glibc distroless base.

ARG APP_VERSION
WORKDIR /build

# binutils provides `objcopy`, required by jlink's --strip-debug below.
# Builder-stage-only: this package never reaches the final image.
RUN dnf install -y binutils && dnf clean all

# The jar is pre-built via `mvn package` OUTSIDE Docker and staged at
# docker/tamacat/target/ (the compose build context is docker/tamacat).
COPY ./target/tamacat-httpd-${APP_VERSION}-jar-with-dependencies.jar ./app.jar

# Module set for the 1.7 jar.
#
# Raw jdeps output (JDK 25.0.1, `jdeps --print-module-deps
# --ignore-missing-deps --multi-release 25` on the jar-with-dependencies
# with the shaded commons-lang3 META-INF/versions/9/module-info.class
# removed first - with it, jdeps analyses the whole jar as the
# org.apache.commons.lang3 module and reports only java.base,java.desktop):
#   java.base,java.compiler,java.desktop,java.instrument,java.management,
#   java.naming,java.rmi,java.security.jgss,java.sql,jdk.attach,jdk.jdi
#
# Kept from that output:
#  - java.desktop: the DI container (DIContainerFactory -> PropertyUtils)
#    uses java.beans, which lives in java.desktop. Removing it failed a
#    real smoke test of the 2.0 image with NoClassDefFoundError:
#    java.beans.IntrospectionException.
#  - java.rmi: org.tamacat.httpd.core.jmx.JMXServer (java.rmi.registry).
# Dropped from that output (no runtime use in this server):
#  - java.compiler: only org.apache.jasper.compiler.JavaCompiler (the JSR 199
#    compiler, used when ecj is absent - ecj is bundled) and ecj's apt/batch/
#    tool packages reference it.
#  - jdk.attach, jdk.jdi: only javassist.util (HotSwapper/HotSwapAgent).
# Added although jdeps cannot see them:
#  - java.management.rmi: JMXServer's JMXConnectorServerFactory finds the
#    "rmi" connector through a service provider in this module.
#  - jdk.crypto.ec: carried over from the 2.0 image, where it was added
#    because jdeps cannot detect the reflectively-loaded SunEC provider. On
#    JDK 25 the SunEC classes are in java.base and this module is empty.
# Not carried over from the 2.0 image: jdk.net (required by httpcore5,
# which 1.7 does not use).
# Whether this set runs (startup, JSP, health check) is verified by building
# and starting the image, not by jdeps.
RUN jlink \
      --module-path "$JAVA_HOME/jmods" \
      --add-modules java.base,java.desktop,java.instrument,java.management,java.management.rmi,java.naming,java.rmi,java.security.jgss,java.sql,jdk.crypto.ec \
      --output /customjre \
      --strip-debug \
      --no-header-files \
      --no-man-pages \
      --compress=zip-6

########################################
# Stage 2: final - the shipped runtime image
########################################
FROM gcr.io/distroless/base-nossl-debian12@sha256:8e80c746dfbb1aa5891456d3414e5f7fafed1ecc62a6e1579f8ba4311b016d83
LABEL maintainer="tamacat.org"
# ^ glibc distroless: no shell, no package manager, no OpenSSL package.
#   Verified for the 1.7 image (and earlier for the 2.0 image) built from
#   this same base digest: `docker export` contained zero libssl/libcrypto
#   files and no files under bin/, sbin/, usr/bin/ or usr/sbin/.
#   This digest's image config sets User 65532, so the server runs as UID
#   65532 although there is no USER instruction here (1.7 image: `docker
#   inspect` User 65532, `docker top` UID 65532).

ARG APP_VERSION
WORKDIR /app

COPY --from=builder /customjre /opt/jre
COPY --from=builder /build/app.jar /app/app.jar
# Native libraries the jlink runtime links against that base-nossl-debian12
# does not ship, as found by `ldd` on the 2.0 image's runtime: libz
# (java.util.zip's native backing), libstdc++/libgcc_s (libjvm.so itself is
# a C++ binary - needed regardless of module set).
COPY --from=builder /lib64/libz.so.1* /lib/x86_64-linux-gnu/
COPY --from=builder /lib64/libstdc++.so.6* /lib/x86_64-linux-gnu/
COPY --from=builder /lib64/libgcc_s.so.1 /lib64/libgcc_s-14-20250110.so.1 /lib/x86_64-linux-gnu/

# conf/htdocs/webapps come from the host build context directly.
COPY --chown=65532:65532 ./conf /app/conf
COPY --chown=65532:65532 ./htdocs /app/htdocs
COPY --chown=65532:65532 ./webapps /app/webapps

ENV CLASSPATH=/app/conf:/app/app.jar

EXPOSE 80

# JVM-based healthcheck probe - no shell, no curl.
HEALTHCHECK --interval=1m --timeout=20s --retries=3 \
  CMD ["/opt/jre/bin/java", "-cp", "/app/app.jar", "org.tamacat.httpd.HealthCheck"]

ENTRYPOINT ["/opt/jre/bin/java", "org.tamacat.httpd.Httpd", "httpd.xml"]
