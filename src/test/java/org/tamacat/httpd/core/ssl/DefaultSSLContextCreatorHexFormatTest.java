/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.ssl;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.CRLException;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * FR-4.1/NFR-3.3/OQ-2: pins that {@code java.util.HexFormat}, the JDK-standard
 * replacement for the removed {@code javax.xml.bind.DatatypeConverter}
 * dependency, reproduces {@code DatatypeConverter.printHexBinary(byte[])}'s
 * exact output (uppercase, two hex digits per byte, no separators, raw byte
 * values only - no sign/BigInteger interpretation) for the byte arrays that
 * {@link DefaultSSLContextCreator#toStringWithAlgName(X509CRL)} feeds it
 * (CRL entry serial numbers via {@link BigInteger#toByteArray()}).
 *
 * <p>No existing test exercises {@code toStringWithAlgName} or any CRL
 * hex-formatting path (confirmed by searching {@code src/test/java} for
 * {@code toStringWithAlgName}/{@code printHexBinary}/{@code HexFormat}:
 * zero hits before this file was added), so this is new coverage rather
 * than a duplicate of an existing test.
 */
public class DefaultSSLContextCreatorHexFormatTest {

	/**
	 * {@code DatatypeConverter.printHexBinary(byte[])} was specified to
	 * convert each byte to two uppercase hex digits, concatenated with no
	 * separator - independent of the value's sign or origin. This method
	 * reproduces that specification directly (not via the removed API) so
	 * the assertions below have a same-file, dependency-free oracle to
	 * compare {@link HexFormat} against.
	 */
	private static String printHexBinaryReference(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(Character.forDigit((b >> 4) & 0xF, 16));
			sb.append(Character.forDigit(b & 0xF, 16));
		}
		return sb.toString().toUpperCase(java.util.Locale.ROOT);
	}

	@Test
	public void testHexFormatMatchesPrintHexBinaryReference_leadingZeroSignByte() {
		// A positive BigInteger built from a magnitude whose first byte has
		// its high bit set gets a leading 0x00 sign byte from toByteArray() -
		// the shape every CRL serial number with a high-bit-set leading byte
		// takes in entry.getSerialNumber().toByteArray().
		byte[] bytes = new BigInteger(1, new byte[] { (byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF }).toByteArray();
		assertEquals(5, bytes.length, "the high bit of the leading magnitude byte must force a 0x00 sign byte");
		String expected = printHexBinaryReference(bytes);
		assertEquals("00DEADBEEF", expected);
		assertEquals(expected, HexFormat.of().withUpperCase().formatHex(bytes));
	}

	@Test
	public void testHexFormatMatchesPrintHexBinaryReference_noSignByte() {
		byte[] bytes = new BigInteger(1, new byte[] { 0x01, 0x23, 0x45 }).toByteArray();
		String expected = printHexBinaryReference(bytes);
		assertEquals("012345", expected);
		assertEquals(expected, HexFormat.of().withUpperCase().formatHex(bytes));
	}

	@Test
	public void testHexFormatMatchesPrintHexBinaryReference_zero() {
		byte[] bytes = BigInteger.ZERO.toByteArray();
		String expected = printHexBinaryReference(bytes);
		assertEquals("00", expected);
		assertEquals(expected, HexFormat.of().withUpperCase().formatHex(bytes));
	}

	@Test
	public void testHexFormatDefaultIsLowercase_soWithUpperCaseIsRequired() {
		// BR-9: HexFormat.of().formatHex(...) alone returns lowercase, which
		// would NOT match DatatypeConverter.printHexBinary's uppercase output.
		byte[] bytes = { (byte) 0xDE, (byte) 0xAD };
		assertEquals("dead", HexFormat.of().formatHex(bytes));
		assertEquals("DEAD", HexFormat.of().withUpperCase().formatHex(bytes));
	}

	/**
	 * End-to-end: drives the actual production method with a minimal
	 * {@link X509CRL}/{@link X509CRLEntry} stub carrying one revoked
	 * certificate, and confirms the "Serial: " segment of the rendered
	 * diagnostic string is the uppercase hex of the entry's serial number -
	 * i.e. that {@code toStringWithAlgName} is actually wired to
	 * {@code HexFormat.of().withUpperCase().formatHex(...)} and not just
	 * that the API exists in isolation.
	 */
	@Test
	public void testToStringWithAlgNameRendersUppercaseHexSerial() {
		byte[] serialBytes = new BigInteger(1, new byte[] { (byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF }).toByteArray();
		BigInteger serial = new BigInteger(1, serialBytes);
		Date revocationDate = new Date(0L);

		X509CRLEntry entry = new StubX509CRLEntry(serial, revocationDate);
		X509CRL crl = new StubX509CRL(Collections.singleton(entry));

		DefaultSSLContextCreator creator = new DefaultSSLContextCreator();
		String result = creator.toStringWithAlgName(crl);

		assertTrue(result.contains("Serial: 00DEADBEEF"),
			"expected the uppercase hex serial in the rendered CRL diagnostic string, got:\n" + result);
		assertFalse(result.contains("00deadbeef"), "the serial must not be rendered in lowercase");
	}

	/** Minimal {@link X509CRL} stub exercising only what {@code toStringWithAlgName} reads. */
	private static class StubX509CRL extends X509CRL {
		private final Set<X509CRLEntry> entries;

		StubX509CRL(Set<X509CRLEntry> entries) {
			this.entries = new HashSet<>(entries);
		}

		@Override public int getVersion() { return 2; }
		// X509CRL#getIssuerDN() is a required abstract method with no non-deprecated
		// alternative to override (there is nothing to call - the JDK's own
		// getIssuerX500Principal() default implementation would otherwise delegate
		// to getEncoded(), which this stub does not support). getIssuerX500Principal()
		// below is overridden directly instead, so production code never reaches
		// this method through toStringWithAlgName; it exists solely to satisfy
		// the abstract-method contract required to instantiate the stub.
		@Override @SuppressWarnings("deprecation") public Principal getIssuerDN() { return null; }
		@Override public javax.security.auth.x500.X500Principal getIssuerX500Principal() { return null; }
		@Override public Date getThisUpdate() { return null; }
		@Override public Date getNextUpdate() { return null; }
		@Override public X509CRLEntry getRevokedCertificate(BigInteger serialNumber) { return null; }
		@Override public Set<X509CRLEntry> getRevokedCertificates() { return entries; }
		@Override public byte[] getTBSCertList() throws CRLException { throw new CRLException("not implemented in stub"); }
		@Override public byte[] getSignature() { return new byte[0]; }
		@Override public String getSigAlgName() { return null; }
		@Override public String getSigAlgOID() { return null; }
		@Override public byte[] getSigAlgParams() { return null; }
		@Override public byte[] getEncoded() throws CRLException { throw new CRLException("not implemented in stub"); }
		@Override public void verify(PublicKey key) { }
		@Override public void verify(PublicKey key, String sigProvider) { }
		@Override public boolean isRevoked(java.security.cert.Certificate cert) { return true; }
		@Override public String toString() { return "StubX509CRL"; }
		@Override public Set<String> getCriticalExtensionOIDs() { return null; }
		@Override public Set<String> getNonCriticalExtensionOIDs() { return null; }
		@Override public byte[] getExtensionValue(String oid) { return null; }
		@Override public boolean hasUnsupportedCriticalExtension() { return false; }
	}

	/** Minimal {@link X509CRLEntry} stub carrying one serial number + revocation date. */
	private static class StubX509CRLEntry extends X509CRLEntry {
		private final BigInteger serial;
		private final Date revocationDate;

		StubX509CRLEntry(BigInteger serial, Date revocationDate) {
			this.serial = serial;
			this.revocationDate = revocationDate;
		}

		@Override public byte[] getEncoded() throws CRLException { throw new CRLException("not implemented in stub"); }
		@Override public BigInteger getSerialNumber() { return serial; }
		@Override public Date getRevocationDate() { return revocationDate; }
		@Override public boolean hasExtensions() { return false; }
		@Override public String toString() { return "StubX509CRLEntry"; }
		@Override public Set<String> getCriticalExtensionOIDs() { return null; }
		@Override public Set<String> getNonCriticalExtensionOIDs() { return null; }
		@Override public byte[] getExtensionValue(String oid) { return null; }
		@Override public boolean hasUnsupportedCriticalExtension() { return false; }
	}
}
