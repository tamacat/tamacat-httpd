FROM adoptopenjdk/openjdk8:alpine-jre
MAINTAINER tamacat.org

RUN apk update && apk upgrade && apk add --no-cache \
    bash \
    curl

RUN mkdir -p /usr/local/tamacat-httpd/lib
WORKDIR /usr/local/tamacat-httpd

COPY ./target/tamacat-httpd-1.5-jar-with-dependencies.jar /usr/local/tamacat-httpd/lib/
#COPY ./lib /usr/local/tamacat-httpd/lib

COPY ./conf /usr/local/tamacat-httpd/conf
COPY ./htdocs /usr/local/tamacat-httpd/htdocs
COPY ./webapps /usr/local/tamacat-httpd/webapps

ENV CLASSPATH=/usr/local/tamacat-httpd/conf:/usr/local/tamacat-httpd/lib/tamacat-httpd-1.5-jar-with-dependencies.jar

EXPOSE 80

ENTRYPOINT ["java", "org.tamacat.httpd.Httpd", "httpd.xml"]