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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hath.base.HentaiAtHomeClient;
import org.hath.base.ServerHandler;
import org.hath.base.Settings;
import org.hath.base.http.handlers.BaseHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

public class HTTPServerTest {
	private static final int SERVER_TEST_PORT = 42421;

	private HentaiAtHomeClient mockClient;
	private HTTPSessionFactory mockSessionFactory;
	private BaseHandler mockSession;
	private ServerHandler mockServerHandler;
	private HTTPServer hTTPServer;

	@Before
	public void setUp() throws Exception {
		mockClient = mock(HentaiAtHomeClient.class, Mockito.RETURNS_DEEP_STUBS);
		mockSessionFactory = mock(HTTPSessionFactory.class);
		mockSession = mock(BaseHandler.class);
		mockServerHandler = mock(ServerHandler.class);
		
		when(mockClient.isShuttingDown()).thenReturn(true);

		Settings.updateSetting("host", "123.123.123.123");

		hTTPServer = new HTTPServer(mockClient, mockSessionFactory);

		when(mockSessionFactory.create(any(), any())).thenReturn(mockSession,
				mockSession);
		
		when(mockClient.getServerHandler()).thenReturn(mockServerHandler);

		hTTPServer.startConnectionListener(SERVER_TEST_PORT);
	}

	@After
	public void tearDown() throws Exception {
		hTTPServer.stopConnectionListener();
	}

	@Test
	public void testStartConnectionListener() throws Exception {
		boolean response = hTTPServer.startConnectionListener(SERVER_TEST_PORT);
		assertThat(response, is(true));
	}

	@Test
	public void testStartConnectionListenerFails() throws Exception {
		boolean response = hTTPServer.startConnectionListener(-1);
		assertThat(response, is(false));
	}

	@Test
	public void testStartConnectionListenerWhenAlreadyRunning() throws Exception {
		hTTPServer.startConnectionListener(SERVER_TEST_PORT);
		boolean response = hTTPServer.startConnectionListener(SERVER_TEST_PORT);
		assertThat(response, is(true));
	}

	@Test
	public void testStopConnectionListener() throws Exception {
		hTTPServer.stopConnectionListener();
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
