/*

Copyright 2008-2016 E-Hentai.org
http://forums.e-hentai.org/
ehentai@gmail.com

This file is part of Hentai@Home.

Hentai@Home is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Hentai@Home is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Hentai@Home.  If not, see <http://www.gnu.org/licenses/>.

*/

package org.hath.base.http;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.Socket;

import org.hath.base.HentaiAtHomeClient;
import org.hath.base.ServerHandler;
import org.hath.base.Settings;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.common.net.InetAddresses;

public class HTTPServerTest {
	private static final int SERVER_TEST_PORT = 42421;
	private static final String LOCAL_ADDRESS = "http://localhost:" + SERVER_TEST_PORT;

	private HentaiAtHomeClient mockClient;
	private Socket mockSocket;
	private HTTPSessionFactory mockSessionFactory;
	private HTTPSession mockSession;
	private ServerHandler mockServerHandler;
	private HTTPServer hTTPServer;



	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		mockSocket = mock(Socket.class);
		mockClient = mock(HentaiAtHomeClient.class, Mockito.RETURNS_DEEP_STUBS);
		mockSessionFactory = mock(HTTPSessionFactory.class);
		mockSession = mock(HTTPSession.class);
		mockServerHandler = mock(ServerHandler.class);
		
		when(mockClient.isShuttingDown()).thenReturn(true);

		Settings.updateSetting("host", "123.123.123.123");

		hTTPServer = new HTTPServer(mockClient, mockSessionFactory);

		when(mockSessionFactory.create(any(), anyInt(), anyBoolean(), eq(hTTPServer))).thenReturn(mockSession,
				mockSession);
		
		when(mockClient.getServerHandler()).thenReturn(mockServerHandler);
	}

	@After
	public void tearDown() throws Exception {
		// hTTPServer.stopConnectionListener();
	}

	@Ignore("Do not want to open actual ports")
	@Test
	public void testStartConnectionListenerWhenAlreadyRunning() throws Exception {
		hTTPServer.startConnectionListener(SERVER_TEST_PORT);
	}

	@Ignore("Do not want to open actual ports")
	@Test
	public void testStopConnectionListener() throws Exception {
		hTTPServer.stopConnectionListener();
	}

	@Test
	public void testRejectDuringStartup() throws Exception {
		when(mockSocket.getInetAddress()).thenReturn(InetAddresses.forString("127.0.0.1"));

		hTTPServer.processConnection(mockSocket);

		verify(mockSocket).close();
	}

	@Test
	public void testTriggerOverload() throws Exception {
		hTTPServer.allowNormalConnections();

		for (int i = 0; i < 18; i++) {
			Socket socket = mock(Socket.class);
			when(socket.getInetAddress()).thenReturn(InetAddresses.forString("170.180.190." + i));
			hTTPServer.processConnection(socket);
		}

		verify(mockServerHandler).notifyOverload();
	}

	@Test
	public void testMaxConnections() throws Exception {
		hTTPServer.allowNormalConnections();

		for (int i = 0; i < 30; i++) {
			Socket socket = mock(Socket.class);
			when(socket.getInetAddress()).thenReturn(InetAddresses.forString("170.180.190." + i));
			hTTPServer.processConnection(socket);

			if (i > 20) {
				verify(socket).close();
			} else {
				verify(socket, never()).close();
			}
		}
	}

	@Test
	public void testLocalRequest() throws Exception {
		hTTPServer.allowNormalConnections();
		when(mockSocket.getInetAddress()).thenReturn(InetAddresses.forString("10.0.0.1"));

		hTTPServer.processConnection(mockSocket);

		verify(mockSession).handleSession();
	}

	@Test
	public void testExternalRequest() throws Exception {
		hTTPServer.allowNormalConnections();
		when(mockSocket.getInetAddress()).thenReturn(InetAddresses.forString("170.180.190.200"));

		hTTPServer.processConnection(mockSocket);

		verify(mockSession).handleSession();
	}

	@Ignore
	@Test
	public void testNukeOldConnections() throws Exception {
		throw new RuntimeException("not yet implemented");
	}

	@Test
	public void testAllowNormalConnections() throws Exception {
		hTTPServer.allowNormalConnections();
	}

	@Ignore
	@Test
	public void testRun() throws Exception {
		throw new RuntimeException("not yet implemented");
	}

	@Ignore
	@Test
	public void testRemoveHTTPSession() throws Exception {
		throw new RuntimeException("not yet implemented");
	}

	@Ignore
	@Test
	public void testGetBandwidthMonitor() throws Exception {
		throw new RuntimeException("not yet implemented");
	}

	@Test
	public void testGetHentaiAtHomeClient() throws Exception {
		HentaiAtHomeClient client = hTTPServer.getHentaiAtHomeClient();

		assertThat(client, is(mockClient));
	}
}
