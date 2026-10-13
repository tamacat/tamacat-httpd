/*
 * Copyright (c) 2026, tamacat.org
 * All rights reserved.
 */
package org.tamacat.httpd.core.ssl;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.CRLException;
import java.security.cert.Certificate;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.junit.jupiter.api.Test;

/**
 * <p>T-HEX: the serial numbers in the CRL summary are written as upper-case
 * hexadecimal without separators, exactly as
 * {@code javax.xml.bind.DatatypeConverter.printHexBinary(byte[])} wrote them
 * before it was replaced by {@link HexFormat}.
 *
 * <p>{@code DatatypeConverter} is no longer on the class path, so the expected
 * values are string literals. The first test states the formatting rule on the
 * boundary bytes; the second drives the production method,
 * {@link DefaultSSLContextCreator#toStringWithAlgName(X509CRL)}, because the
 * first one alone would pass whatever that method did.
 */
public class DefaultSSLContextCreatorHexFormatTest {

	@Test
	public void testUpperCaseHexFormatOnBoundaryBytes() {
		HexFormat hex = HexFormat.of().withUpperCase();
		assertEquals("", hex.formatHex(new byte[0]));
		assertEquals("00", hex.formatHex(new byte[] { 0x00 }));
		assertEquals("0F", hex.formatHex(new byte[] { 0x0f }));
		assertEquals("7F", hex.formatHex(new byte[] { 0x7f }));
		assertEquals("80", hex.formatHex(new byte[] { (byte) 0x80 }));
		assertEquals("FF", hex.formatHex(new byte[] { (byte) 0xff }));
		assertEquals("000F7F80FFAB", hex.formatHex(
			new byte[] { 0x00, 0x0f, 0x7f, (byte) 0x80, (byte) 0xff, (byte) 0xab }));
	}

	/**
	 * The production path: every revoked entry's serial number goes through
	 * {@code BigInteger.toByteArray()} and the hex formatter. The serial numbers
	 * are chosen so that the byte arrays hit the same boundaries as above,
	 * including the leading 0x00 sign byte of a positive number whose top bit is
	 * set and the single bytes 0x80 and 0xFF of small negative numbers.
	 */
	@Test
	public void testToStringWithAlgNameWritesSerialNumbersInUpperCaseHex() {
		Map<BigInteger, String> expected = new LinkedHashMap<>();
		expected.put(BigInteger.ZERO, "00");
		expected.put(BigInteger.valueOf(0x0f), "0F");
		expected.put(BigInteger.valueOf(0x7f), "7F");
		expected.put(BigInteger.valueOf(0x80), "0080");
		expected.put(BigInteger.valueOf(0xff), "00FF");
		expected.put(BigInteger.valueOf(-128), "80");
		expected.put(BigInteger.valueOf(-1), "FF");
		expected.put(new BigInteger("deadbeef", 16), "00DEADBEEF");
		expected.put(new BigInteger("0123456789abcdef", 16), "0123456789ABCDEF");

		Set<X509CRLEntry> entries = new LinkedHashSet<>();
		for (BigInteger serial : expected.keySet()) {
			entries.add(new StubEntry(serial));
		}

		String text = new DefaultSSLContextCreator().toStringWithAlgName(new StubCRL(entries));

		assertTrue(text.contains("Revoked Certificates: " + expected.size()),
			"the summary does not list the revoked entries at all:\n" + text);
		for (String serial : expected.values()) {
			assertTrue(text.contains(" Serial: " + serial + ", Revocation: "),
				"expected the serial number " + serial + " in upper-case hex without separators, got:\n" + text);
		}
	}

	/** The parts of an X.509 CRL that {@code toStringWithAlgName} reads. */
	static class StubCRL extends X509CRL {
		final Set<X509CRLEntry> entries;

		StubCRL(Set<X509CRLEntry> entries) {
			this.entries = entries;
		}

		@Override public int getVersion() { return 2; }
		@Override public X500Principal getIssuerX500Principal() { return new X500Principal("CN=Test CA"); }
		@Override public Principal getIssuerDN() { return getIssuerX500Principal(); }
		@Override public Date getThisUpdate() { return new Date(0L); }
		@Override public Date getNextUpdate() { return new Date(86400000L); }
		@Override public X509CRLEntry getRevokedCertificate(BigInteger serialNumber) { return null; }
		@Override public Set<? extends X509CRLEntry> getRevokedCertificates() { return entries; }
		@Override public String getSigAlgName() { return "SHA256withRSA"; }
		@Override public String getSigAlgOID() { return "1.2.840.113549.1.1.11"; }
		@Override public byte[] getSigAlgParams() { return null; }
		@Override public byte[] getSignature() { return new byte[0]; }
		@Override public byte[] getTBSCertList() throws CRLException { throw new CRLException("stub"); }
		@Override public byte[] getEncoded() throws CRLException { throw new CRLException("stub"); }
		@Override public void verify(PublicKey key) { }
		@Override public void verify(PublicKey key, String sigProvider) { }
		@Override public boolean isRevoked(Certificate cert) { return false; }
		@Override public String toString() { return "StubCRL"; }
		@Override public boolean hasUnsupportedCriticalExtension() { return false; }
		@Override public Set<String> getCriticalExtensionOIDs() { return null; }
		@Override public Set<String> getNonCriticalExtensionOIDs() { return null; }
		@Override public byte[] getExtensionValue(String oid) { return null; }
	}

	static class StubEntry extends X509CRLEntry {
		final BigInteger serial;

		StubEntry(BigInteger serial) {
			this.serial = serial;
		}

		@Override public BigInteger getSerialNumber() { return serial; }
		@Override public Date getRevocationDate() { return new Date(0L); }
		@Override public boolean hasExtensions() { return false; }
		@Override public byte[] getEncoded() throws CRLException { throw new CRLException("stub"); }
		@Override public String toString() { return "StubEntry " + serial; }
		@Override public boolean hasUnsupportedCriticalExtension() { return false; }
		@Override public Set<String> getCriticalExtensionOIDs() { return null; }
		@Override public Set<String> getNonCriticalExtensionOIDs() { return null; }
		@Override public byte[] getExtensionValue(String oid) { return null; }
	}
}
