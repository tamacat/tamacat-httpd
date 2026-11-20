/*
 * Copyright (c) 2026, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.mock;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.tamacat.logging.Configuration;
import org.tamacat.logging.Level;
import org.tamacat.logging.LoggerContext;

/**
 * Captures what the application logs through tamacat-logging, for assertions.
 *
 * <p>While it is open, the output of the JVM-wide {@link LoggerContext} goes into a
 * buffer instead of stdout, as one {@code LEVEL|logger|message} line per record.
 * {@link #close()} restores the output and the configuration that were active
 * before, so a test cannot change the logging of the tests after it. Tests of one
 * JVM run one after another, so the global state is safe to swap here.
 *
 * <p>The levels of the test class path configuration stay in force, so a test that
 * needs a more detailed level for one logger passes it to
 * {@link #start(Class, Level)}.
 */
public final class LogCapture implements AutoCloseable {

	/** One captured log record. The message has tamacat-logging's text escaping applied. */
	public record Entry(String level, String logger, String message) {}

	private final Configuration saved;
	private final ByteArrayOutputStream out = new ByteArrayOutputStream();

	private LogCapture(String loggerPrefix, Level level) {
		LoggerContext context = LoggerContext.get();
		saved = context.configuration();
		Configuration.Builder builder = Configuration.builder()
			.format(Configuration.Format.TEXT)
			.pattern("%level|%logger|%msg%n")
			.rootLevel(saved.rootLevel());
		saved.levels().forEach(builder::level);
		if (loggerPrefix != null) {
			builder.level(loggerPrefix, level);
		}
		context.setOutput(out);
		context.configure(builder.build());
	}

	/** Captures with the levels of the current configuration. */
	public static LogCapture start() {
		return new LogCapture(null, null);
	}

	/** Captures, and logs {@code type} and the loggers below its name at {@code level}. */
	public static LogCapture start(Class<?> type, Level level) {
		return new LogCapture(type.getName(), level);
	}

	/** Every record captured so far, in order. */
	public List<Entry> entries() {
		List<Entry> entries = new ArrayList<>();
		for (String line : out.toString(StandardCharsets.UTF_8).split("\n")) {
			String[] parts = line.split("\\|", 3);
			if (parts.length == 3) {
				entries.add(new Entry(parts[0].trim(), parts[1], parts[2]));
			}
		}
		return entries;
	}

	/** The records of the logger named after {@code type}, in order. */
	public List<Entry> entries(Class<?> type) {
		return entries().stream().filter(e -> e.logger().equals(type.getName())).toList();
	}

	/** Forgets the records captured so far. */
	public void clear() {
		out.reset();
	}

	@Override
	public void close() {
		LoggerContext context = LoggerContext.get();
		context.setOutput(null);
		context.configure(saved);
	}
}
