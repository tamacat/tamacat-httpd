package org.tamacat.httpd.core.util;

/**
 * Runtime IOException.
 * <p>Vendored from tamacat-core 1.5 (originally org.tamacat.io.RuntimeIOException).
 * Folded into org.tamacat.httpd.core.util since it is the only class vendored from
 * org.tamacat.io — a separate .io subpackage for a single class was judged unnecessary
 * package sprawl (developer decision per code-generation-plan.md Step 3.2).</p>
 * @since 0.9
 */
public class RuntimeIOException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RuntimeIOException() {
	}

	public RuntimeIOException(String message) {
		super(message);
	}

	public RuntimeIOException(Throwable cause) {
		super(cause);
	}

	public RuntimeIOException(String message, Throwable cause) {
		super(message, cause);
	}

}
