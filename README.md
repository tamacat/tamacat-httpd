The tamacat-httpd is an open source Java Web Server software, powered by " Apache HttpComponents".
This is a customizable HTTP/HTTPS Server framework and Reverse Proxy.

https://tamacat.org/

Version 1.7 (1.7.0-b20261120) - stable
* The stable line of tamacat-httpd.
  It keeps the vendored Apache HttpComponents Core 4.4 code (org.tamacat.httpcore4).
* Required Java Platform, Standard Edition 25 (JRE/JDK)
* Tomcat Embedded 11.0.29 Integration (Servlet 6.1 / JSP), an optional dependency
* Logging: SLF4J 2.x with tamacat-logging (https://github.com/tamacat/tamacat-logging), standard output only.
  Configure it with tamacat-logging.properties; logback.xml is no longer read (docs/MIGRATION-1.7.md, B-6)
* Upgrading from 1.6 (Java 8, Tomcat Embedded 9.0): docs/MIGRATION-1.7.md
* Release notes: RELEASE_NOTES.txt

Version 2.0 - experimental
* Built on Apache HttpComponents Core 5.5 (a beta release) instead of HttpCore 4.4.
  Source- and binary-incompatible with 1.x.
* https://github.com/tamacat/tamacat-httpd/tree/v2.0

Build (JDK 25, Maven)
* mvn clean package
* Output: target/tamacat-httpd-<version>-jar-with-dependencies.jar (<version> is the version in pom.xml)
* Docker files: docker/ (see docs/MIGRATION-1.7.md, B-12)

Source code
* https://github.com/tamacat/tamacat-httpd


Previous version (1.4)
* https://github.com/tamacat-1-4/tamacat-httpd-1.4
