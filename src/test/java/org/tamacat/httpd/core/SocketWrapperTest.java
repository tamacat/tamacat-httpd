package org.tamacat.httpd.core;

import static org.junit.jupiter.api.Assertions.*;

import java.net.Socket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.httpd.mock.DummySocket;

public class SocketWrapperTest {

	SocketWrapper wrapper;

	@BeforeEach
	public void setUp() throws Exception {
		Socket socket = new DummySocket();
		wrapper = new SocketWrapper(socket);
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetSocket() {
		assertNotNull(wrapper.getSocket());
	}

}
