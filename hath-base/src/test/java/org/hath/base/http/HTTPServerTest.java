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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.SocketException;

import org.hath.base.HentaiAtHomeClient;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class HTTPServerTest {
	private static final int SERVER_TEST_PORT = 42421;
	private static final String LOCAL_ADDRESS = "http://localhost:" + SERVER_TEST_PORT;

	private HentaiAtHomeClient mockClient = mock(HentaiAtHomeClient.class);
	private HTTPServer hTTPServer;



	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void createHTTPServer() throws Exception {
		hTTPServer = new HTTPServer(mockClient);
		hTTPServer.startConnectionListener(SERVER_TEST_PORT);
		
		when(mockClient.isShuttingDown()).thenReturn(true);
	}

	@After
	public void tearDown() throws Exception {
		hTTPServer.stopConnectionListener();
	}

	@Test
	public void testStartConnectionListenerWhenAlreadyRunning() throws Exception {
		hTTPServer.startConnectionListener(SERVER_TEST_PORT);
	}

	@Test
	public void testStopConnectionListener() throws Exception {
		hTTPServer.stopConnectionListener();
	}

	@Test(expected = SocketException.class)
	public void testRejectDuringStartup() throws Exception {
		Document doc = Jsoup.connect(LOCAL_ADDRESS).get();
	}

	@Test
	public void testLocalRequest() throws Exception {
		hTTPServer.allowNormalConnections();
		Response response = Jsoup.connect(LOCAL_ADDRESS).ignoreHttpErrors(true).execute();
		
		assertThat(response.statusCode(), is(404));
	}

	@Test
	public void testExternalRequest() throws Exception {
		hTTPServer.allowNormalConnections();
		hTTPServer.setTestForceExternal(true);
		Response response = Jsoup.connect(LOCAL_ADDRESS).ignoreHttpErrors(true).execute();

		assertThat(response.statusCode(), is(404));
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
