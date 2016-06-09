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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.hath.base.HentaiAtHomeClient;
import org.hath.base.ServerHandler;
import org.hath.base.Settings;
import org.hath.base.http.handlers.SpeedTestHandler;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HTTPServerRoutingTest {
	private static final int SERVER_TEST_PORT = 42421;

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private static HentaiAtHomeClient mockClient;
	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private static HTTPSessionFactory mockSessionFactory;
	@Mock
	private static ServerHandler mockServerHandler;

	private static HTTPServer hTTPServer;
	private static HttpClient httpClient;

	@BeforeClass
	public static void setUpClass() throws Exception {
		Settings.updateSetting("host", "123.123.123.123");

		hTTPServer = new HTTPServer(mockClient, mockSessionFactory);

		hTTPServer.startConnectionListener(SERVER_TEST_PORT);
		httpClient = new HttpClient();
		httpClient.start();
	}

	@Before
	public void setUp() throws Exception {
		when(mockClient.isShuttingDown()).thenReturn(true);
		when(mockClient.getServerHandler()).thenReturn(mockServerHandler);
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		hTTPServer.stopConnectionListener();
		httpClient.stop();
	}

	@Test
	public void testFaviconRouting() throws Exception {
		hTTPServer.allowNormalConnections();
		ContentResponse response = httpClient.newRequest("http://localhost:" + SERVER_TEST_PORT + "/favicon.ico")
				.method(HttpMethod.GET).followRedirects(false).send();

		assertThat(response.getStatus(), is(HttpStatus.FOUND_302));
	}

	@Test
	public void testRobotsRouting() throws Exception {
		hTTPServer.allowNormalConnections();
		ContentResponse response = httpClient.GET("http://localhost:" + SERVER_TEST_PORT + "/robots.txt");

		assertThat(response.getContentAsString(), containsString("User-agent: *"));
		assertThat(response.getContentAsString(), containsString("Disallow: /"));
	}

	@Test
	public void testSpeedTestRoutingStatus() throws Exception {
		hTTPServer.allowNormalConnections();
		int testSize = 1234;
		int testTime = Settings.getServerTime();
		ContentResponse response = httpClient.GET("http://localhost:" + SERVER_TEST_PORT + "/t/" + testSize + "/"
				+ testTime + "/" + SpeedTestHandler.calculateTestKey(testSize, testTime));

		assertThat(response.getStatus(), is(HttpStatus.OK_200));
	}
}
