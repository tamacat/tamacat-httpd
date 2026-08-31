The tamacat-httpd is an open source Java Web Server software, powered by " Apache HttpComponents".
This is a customizable HTTP/HTTPS Server framework and Reverse Proxy.

https://tamacat.org/

Features
* Standards based, pure Java HTTP Server, implementation of HTTP versions 1.0 and 1.1
* Pluggable architecture for custom request/response handler and filters
* It provides handlers: Reverse Proxy, Thymeleaf Page and Static Contents Web Server
* Support TLS 1.2/1.3 and Server Name Indication (SNI)
* Tomcat Embedded 11.0 Integration (Servlet/JSP)
* Required Apache HttpComponents 5.5
* Required Java Platform, Standard Edition 25 (JRE/JDK)

Version 2.0-tc11
* Built on HttpComponents Core 5.5-beta2 (2.0) instead of HttpCore 4.4 (1.5).
  This is a source- and binary-incompatible major release.
* Upgrading from 1.5: docs/MIGRATION-2.0.md
* Upgrading from Tomcat Embedded 9.0: docs/MIGRATION-2.0-tc11.md
* What changed and why: docs/RELEASE-NOTES-2.0.md

Source code
* https://github.com/tamacat/tamacat-httpd/tree/v2.0-tc11
