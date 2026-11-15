FROM eclipse-temurin:8-jre-alpine@sha256:fa5016dd5928b890b3ac406649228a664cf7e4eb09b29d4e68ea5511b5358435
# Passed by the release workflow (--build-arg APP_VERSION=<pom version>). No default,
# so a version bump cannot leave a stale jar name behind.
ARG APP_VERSION
LABEL maintainer="tamacat.org"

RUN apk update && apk upgrade && apk add --no-cache \
    bash \
    curl

RUN mkdir -p /usr/local/tamacat-httpd/lib
WORKDIR /usr/local/tamacat-httpd

COPY ./target/tamacat-httpd-${APP_VERSION}-jar-with-dependencies.jar /usr/local/tamacat-httpd/lib/
#COPY ./lib /usr/local/tamacat-httpd/lib

COPY ./conf /usr/local/tamacat-httpd/conf
COPY ./htdocs /usr/local/tamacat-httpd/htdocs
COPY ./webapps /usr/local/tamacat-httpd/webapps

ENV CLASSPATH=/usr/local/tamacat-httpd/conf:/usr/local/tamacat-httpd/lib/tamacat-httpd-${APP_VERSION}-jar-with-dependencies.jar

EXPOSE 80

# curl is installed above. check.html is served by RootHandler from htdocs/root.
HEALTHCHECK --interval=1m --timeout=20s --retries=3 \
  CMD curl -fsS http://localhost:80/check.html > /dev/null || exit 1

ENTRYPOINT ["java", "org.tamacat.httpd.Httpd", "httpd.xml"]
