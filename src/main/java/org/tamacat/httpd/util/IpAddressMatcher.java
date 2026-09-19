/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tamacat.httpd.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.tamacat.httpd.core.util.StringUtils;

/**
 * <p>Base code is Spring Security 3.1.3.RELEASE (org.springframework.security.web.util.IpAddressMatcher)
 * <li>http://www.springsource.org/spring-security
 * 
 * Matches a request based on IP Address or subnet mask matching against the remote address.
 * <p>
 * Both IPv6 and IPv4 addresses are supported, but a matcher which is configured with an IPv4 address will
 * never match a request which returns an IPv6 address, and vice-versa.
 *
 * @author Luke Taylor
 * @author tamacat.org
 */
public class IpAddressMatcher {

    private final int nMaskBits;
    private final InetAddress requiredAddress;
    private String ipAddress;
    
    /**
     * Takes a specific IP address or a range specified using the
     * IP/Netmask (e.g. 192.168.1.0/24 or 202.24.0.0/14).
     *
     * @param ipAddress the address or range of addresses from which the request must come.
     */
    public IpAddressMatcher(String ipAddress) {
    	this.ipAddress = ipAddress;
        if (ipAddress.indexOf('/') > 0) {
            String[] addressAndMask = ipAddress.split("/");
            ipAddress = addressAndMask[0];
            nMaskBits = StringUtils.parse(addressAndMask[1], -1);
        } else {
            nMaskBits = -1;
        }
        requiredAddress = parseAddress(ipAddress);
    }

    public boolean matches(String address) {
        if (!isIpLiteral(address)) {
            // address may be attacker-controlled (e.g. a spoofed X-Forwarded-For value via
            // ClientIPAccessControlFilter's useForwardHeader option) — InetAddress.getByName()
            // silently performs a DNS lookup for anything that isn't a literal IP address, which
            // would let an attacker bypass an allow/deny list by pointing a domain they control
            // at an allowed address. Never resolve untrusted input; treat it as non-matching.
            return false;
        }
        InetAddress remoteAddress = parseAddress(address);
        if (!requiredAddress.getClass().equals(remoteAddress.getClass())) {
            return false;
        }

        if (nMaskBits < 0) {
            return remoteAddress.equals(requiredAddress);
        }

        byte[] remAddr = remoteAddress.getAddress();
        byte[] reqAddr = requiredAddress.getAddress();

        int oddBits = nMaskBits % 8;
        int nMaskBytes = nMaskBits/8 + (oddBits == 0 ? 0 : 1);
        byte[] mask = new byte[nMaskBytes];

        Arrays.fill(mask, 0, oddBits == 0 ? mask.length : mask.length - 1, (byte)0xFF);

        if (oddBits != 0) {
            int finalByte = (1 << oddBits) - 1;
            finalByte <<= 8-oddBits;
            mask[mask.length - 1] = (byte) finalByte;
        }

        for (int i=0; i < mask.length; i++) {
            if ((remAddr[i] & mask[i]) != (reqAddr[i] & mask[i])) {
                return false;
            }
        }
        return true;
    }

    public String getIpAddress() {
    	return ipAddress;
    }

    static InetAddress parseAddress(String address) {
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Failed to parse address" + address, e);
        }
    }

    private static final Pattern IPV4_LITERAL = Pattern.compile(
        "^(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])(\\.(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){3}$");

    private static final Pattern IPV6_LITERAL_CHARS = Pattern.compile("^[0-9A-Fa-f:.]+$");

    /**
     * Returns true only if {@code address} is the syntactic form of an IPv4 or IPv6 literal —
     * never a hostname. Used to guard {@link #matches(String)} against resolving untrusted
     * input via DNS: {@link InetAddress#getByName(String)} performs a live name-service lookup
     * for anything that doesn't parse as a literal, and a hostname can never contain ':' or be a
     * valid dotted-decimal quad, so this check is sufficient to rule out DNS resolution without
     * needing a full RFC-correctness validator.
     */
    static boolean isIpLiteral(String address) {
        if (address == null || address.isEmpty()) {
            return false;
        }
        String value = address;
        if (value.startsWith("[") && value.endsWith("]") && value.length() > 2) {
            value = value.substring(1, value.length() - 1);
        }
        int percent = value.indexOf('%');
        if (percent >= 0) {
            value = value.substring(0, percent);
        }
        if (IPV4_LITERAL.matcher(value).matches()) {
            return true;
        }
        return value.indexOf(':') >= 0 && IPV6_LITERAL_CHARS.matcher(value).matches();
    }
}
