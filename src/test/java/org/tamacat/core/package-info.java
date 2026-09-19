/**
 * Vendored from tamacat-core 1.5's own test-support fixtures (Core, CoreFactory, DBCore, Param,
 * SampleCore), kept under their original org.tamacat.core package name rather than renamed to
 * org.tamacat.httpd.core.* — this is deliberate, not an oversight. src/test/resources/test.xml and
 * test_1_x.xml reference these classes by fully-qualified string (e.g. class="org.tamacat.core.SampleCore")
 * for the vendored DI container's reflective bean instantiation; renaming this package without also
 * editing those XML fixtures would silently break the DI-container tests that depend on them.
 */
package org.tamacat.core;
