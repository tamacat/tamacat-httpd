# syntax=docker/dockerfile:1
ARG APP_VERSION=2.0-tc11

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

# The jar is pre-built via `mvn package` OUTSIDE Docker, exactly as before.
COPY ./target/tamacat-httpd-${APP_VERSION}-jar-with-dependencies.jar ./app.jar

# Trimmed from the raw jdeps output (java.base,java.compiler,java.desktop,
# java.instrument,java.management,java.naming,java.security.jgss,java.sql,
# jdk.attach,jdk.jdi,jdk.net) after live-testing two hypotheses:
#  1. Removing java.desktop entirely FAILED a real smoke test with
#     NoClassDefFoundError: java.beans.IntrospectionException - the DI
#     container (DIContainerFactory -> PropertyUtils) genuinely uses
#     java.beans (bundled inside java.desktop in the modular JDK) at
#     startup, not just an unreachable jdeps false positive. Kept.
#  2. Dropping java.compiler/jdk.attach/jdk.jdi (debug/tooling APIs, no
#     plausible runtime use in a production HTTP server) changes NOTHING
#     about the native library footprint (java.desktop alone already pulls
#     in every AWT/X11/audio/font .so regardless of these three), so this
#     is a real, safe module-count reduction with zero functional risk.
# jdk.crypto.ec added explicitly since jdeps cannot detect the
# reflectively-loaded SunEC provider.
RUN jlink \
      --module-path "$JAVA_HOME/jmods" \
      --add-modules java.base,java.desktop,java.instrument,java.management,java.naming,java.security.jgss,java.sql,jdk.net,jdk.crypto.ec \
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
#   Live-verified in this session: `docker export` of this image contains
#   zero libssl/libcrypto files and zero dpkg/apt/apk/sh binaries anywhere
#   under bin/ or usr/bin/. The `:nonroot` UID/GID is 65532:65532.

ARG APP_VERSION
WORKDIR /app

COPY --from=builder /customjre /opt/jre
COPY --from=builder /build/app.jar /app/app.jar
# Native libraries the trimmed jlink runtime links against (confirmed via
# `ldd` against every .so + java binary in the actual built runtime, this
# session) that base-nossl-debian12 does not ship (confirmed via image
# export): libz (java.util.zip's native backing), libstdc++/libgcc_s
# (libjvm.so itself is a C++ binary - needed regardless of module set).
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
