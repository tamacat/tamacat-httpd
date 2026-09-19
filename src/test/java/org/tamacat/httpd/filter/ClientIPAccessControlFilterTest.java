package org.tamacat.httpd.filter;

import static org.junit.Assert.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;

import org.tamacat.httpcore4.HttpRequest;
import org.tamacat.httpcore4.HttpResponse;
import org.tamacat.httpcore4.protocol.HttpContext;

import org.tamacat.httpd.config.ServerConfig;
import org.tamacat.httpd.config.ServiceUrl;
import org.tamacat.httpd.exception.ForbiddenException;
import org.tamacat.httpd.mock.HttpObjectFactory;
import org.tamacat.httpd.util.RequestUtils;
import org.tamacat.httpd.util.SubnetUtils;

public class ClientIPAccessControlFilterTest {

	HttpContext context = HttpObjectFactory.createHttpContext();
	HttpRequest request = HttpObjectFactory.createHttpRequest("GET", "/test/");
	HttpResponse response = HttpObjectFactory.createHttpResponse(200, "OK");

	ClientIPAccessControlFilter filter;
	
	@Before
	public void setUp() throws Exception {
		ServerConfig config = new ServerConfig();
		ServiceUrl serviceUrl = new ServiceUrl(config);

		filter = new ClientIPAccessControlFilter();
		filter.init(serviceUrl);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testAllowAll() throws Exception {
		filter.setAllow("*");
		
		InetAddress allowAddress = InetAddress.getByName("192.168.10.123");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, allowAddress);
		filter.doFilter(request, response, context);
		
		InetAddress denyAddress = InetAddress.getByName("10.1.1.1");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, denyAddress);
		filter.doFilter(request, response, context);
		
		InetAddress allowIPv6Address = InetAddress.getByName("fe80::21f:5bff:fe33:bd68");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, allowIPv6Address);
		filter.doFilter(request, response, context);
	}
	
	@Test
	public void testAllow1() throws Exception {
		filter.setAllow("192.*");
		
		InetAddress allowAddress = InetAddress.getByName("192.168.10.123");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, allowAddress);
		filter.doFilter(request, response, context);
		
		InetAddress denyAddress = InetAddress.getByName("193.168.10.123");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, denyAddress);
		try {
			filter.doFilter(request, response, context);
			fail();
		} catch (Exception e) {
			assertTrue(e instanceof ForbiddenException);
		}
	}

	@Test
	public void testAllow2() throws Exception {
		filter.setAllow("192.168.*");
		
		InetAddress allowAddress = InetAddress.getByName("192.168.10.123");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, allowAddress);
		filter.doFilter(request, response, context);
		
		InetAddress denyAddress = InetAddress.getByName("192.169.10.123");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, denyAddress);
		try {
			filter.doFilter(request, response, context);
			fail();
		} catch (Exception e) {
			assertTrue(e instanceof ForbiddenException);
		}
	}
	
	@Test
	public void testAllow3() throws Exception {
		filter.setAllow("192.168.10.*");
		
		InetAddress allowAddress = InetAddress.getByName("192.168.10.123");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, allowAddress);
		filter.doFilter(request, response, context);
		
		InetAddress denyAddress = InetAddress.getByName("192.168.11.123");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, denyAddress);
		try {
			filter.doFilter(request, response, context);
			fail();
		} catch (Exception e) {
			assertTrue(e instanceof ForbiddenException);
		}
	}
	
	@Test
	public void testAllow4() throws Exception {
		filter.setAllow("192.168.10.123");
		
		InetAddress allowAddress = InetAddress.getByName("192.168.10.123");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, allowAddress);
		filter.doFilter(request, response, context);
		
		InetAddress denyAddress = InetAddress.getByName("192.168.10.124");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, denyAddress);
		try {
			filter.doFilter(request, response, context);
			fail();
		} catch (Exception e) {
			assertTrue(e instanceof ForbiddenException);
		}
	}
	
	@Test
	public void testDenyAll() throws Exception {
		filter.setDeny("*");
		
		InetAddress allowAddress = InetAddress.getByName("192.168.10.123");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, allowAddress);
		try {
			filter.doFilter(request, response, context);
			fail();
		} catch (Exception e) {
			assertTrue(e instanceof ForbiddenException);
		}		
		InetAddress denyAddress = InetAddress.getByName("10.1.1.1");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, denyAddress);
		try {
			filter.doFilter(request, response, context);
			fail();
		} catch (Exception e) {
			assertTrue(e instanceof ForbiddenException);
		}
		
		InetAddress allowIPv6Address = InetAddress.getByName("fe80::21f:5bff:fe33:bd68");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, allowIPv6Address);
		try {
			filter.doFilter(request, response, context);
			fail();
		} catch (Exception e) {
			assertTrue(e instanceof ForbiddenException);
		}
	}
	
	@Test
	public void testDeny1() throws Exception {
		filter.setDeny("193.*");
		
		InetAddress allowAddress = InetAddress.getByName("192.168.10.123");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, allowAddress);
		filter.doFilter(request, response, context);
		
		InetAddress denyAddress = InetAddress.getByName("193.168.10.123");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, denyAddress);
		try {
			filter.doFilter(request, response, context);
			fail();
		} catch (Exception e) {
			assertTrue(e instanceof ForbiddenException);
		}
	}
	
	@Test
	public void testDeny2() throws Exception {
		filter.setDeny("192.169.*");
		
		InetAddress allowAddress = InetAddress.getByName("192.168.10.123");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, allowAddress);
		filter.doFilter(request, response, context);
		
		InetAddress denyAddress = InetAddress.getByName("192.169.10.123");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, denyAddress);
		try {
			filter.doFilter(request, response, context);
			fail();
		} catch (Exception e) {
			assertTrue(e instanceof ForbiddenException);
		}
	}
	
	@Test
	public void testDeny3() throws Exception {
		filter.setDeny("192.168.11.*");
		
		InetAddress allowAddress = InetAddress.getByName("192.168.10.123");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, allowAddress);
		filter.doFilter(request, response, context);
		
		InetAddress denyAddress = InetAddress.getByName("192.168.11.123");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, denyAddress);
		try {
			filter.doFilter(request, response, context);
			fail();
		} catch (Exception e) {
			assertTrue(e instanceof ForbiddenException);
		}
	}
	
	@Test
	public void testDeny4() throws Exception {
		filter.setDeny("192.168.10.124");
		
		InetAddress allowAddress = InetAddress.getByName("192.168.10.123");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, allowAddress);
		filter.doFilter(request, response, context);
		
		InetAddress denyAddress = InetAddress.getByName("192.168.10.124");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, denyAddress);
		try {
			filter.doFilter(request, response, context);
			fail();
		} catch (Exception e) {
			assertTrue(e instanceof ForbiddenException);
		}
		
		InetAddress allowIPv6Address = InetAddress.getByName("fe80::21f:5bff:fe33:bd68");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, allowIPv6Address);
		filter.doFilter(request, response, context);
	}
	
	@Test
	public void testAllowAndDeny1() throws Exception {
		filter.setAllow("192.168.10.123");
		filter.setDeny("10.*");
		
		InetAddress allowAddress = InetAddress.getByName("192.168.10.123");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, allowAddress);
		filter.doFilter(request, response, context);
		
		InetAddress denyAddress = InetAddress.getByName("10.1.1.1");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, denyAddress);
		try {
			filter.doFilter(request, response, context);
			fail();
		} catch (Exception e) {
			assertTrue(e instanceof ForbiddenException);
		}
	}
	
	@Test
	public void testSubnetmask() throws Exception {
		String address = "192.168.10.0/28";
		assertEquals(true, new SubnetUtils(address).getInfo().isInRange("192.168.10.1"));
		assertEquals(false, new SubnetUtils(address).getInfo().isInRange("192.168.10.100"));
	}
	
	@Test
	public void testAllowSubnet1() throws Exception {
		filter.setAllow("192.168.10.0/28");
		
		InetAddress allowAddress = InetAddress.getByName("192.168.10.10");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, allowAddress);
		filter.doFilter(request, response, context);
		
		InetAddress denyAddress = InetAddress.getByName("192.168.10.110");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, denyAddress);
		try {
			filter.doFilter(request, response, context);
			fail();
		} catch (Exception e) {
			assertTrue(e instanceof ForbiddenException);
		}
	}
	
	@Test
	public void testDenySubnet1() throws Exception {
		filter.setDeny("192.168.10.0/28");
		
		InetAddress allowAddress = InetAddress.getByName("192.168.10.110");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, allowAddress);
		filter.doFilter(request, response, context);
		
		InetAddress denyAddress = InetAddress.getByName("192.168.10.10");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, denyAddress);
		try {
			filter.doFilter(request, response, context);
			fail();
		} catch (Exception e) {
			assertTrue(e instanceof ForbiddenException);
		}
	}
	
	// Security regression: a spoofed X-Forwarded-For hostname must not bypass an IP allow list,
	// even if that hostname would resolve (via DNS) into the allowed range.
	@Test
	public void testForwardHeaderHostnameDoesNotBypassAllowList() throws Exception {
		filter.setUseForwardHeader(true);
		filter.setAllow("10.0.0.0/8");

		// The real, directly-connected peer is NOT in the allowed range.
		InetAddress realPeerAddress = InetAddress.getByName("203.0.113.5");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, realPeerAddress);

		// Attacker supplies a hostname (not an IP literal) they control, hoping it either
		// resolves into the allowed range or otherwise short-circuits the check.
		request.setHeader("X-Forwarded-For", "attacker-controlled.example.com");
		try {
			filter.doFilter(request, response, context);
			fail("a non-literal X-Forwarded-For value must not be treated as an allowed address");
		} catch (Exception e) {
			assertTrue(e instanceof ForbiddenException);
		}
	}

	@Test
	public void testForwardHeaderWithLiteralIpStillWorks() throws Exception {
		filter.setUseForwardHeader(true);
		filter.setAllow("10.0.0.0/8");

		// The real, directly-connected peer (e.g. a trusted load balancer) is irrelevant here;
		// only the forwarded literal IP is evaluated once useForwardHeader is enabled.
		InetAddress realPeerAddress = InetAddress.getByName("192.0.2.1");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, realPeerAddress);

		request.setHeader("X-Forwarded-For", "10.1.2.3");
		filter.doFilter(request, response, context);
	}

	@Test
	public void testAllowDenySubnet1() throws Exception {
		filter.setAllow("192.168.10.0/28");
		filter.setDeny("192.168.11.0/28");
		
		InetAddress allowAddress = InetAddress.getByName("192.168.10.10");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, allowAddress);
		filter.doFilter(request, response, context);
		
		InetAddress denyAddress = InetAddress.getByName("192.168.11.10");
		context.setAttribute(RequestUtils.REMOTE_ADDRESS, denyAddress);
		try {
			filter.doFilter(request, response, context);
			fail();
		} catch (Exception e) {
			assertTrue(e instanceof ForbiddenException);
		}
	}
}
