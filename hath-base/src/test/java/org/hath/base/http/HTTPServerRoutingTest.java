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
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.hath.base.CacheHandler;
import org.hath.base.HVFile;
import org.hath.base.HentaiAtHomeClient;
import org.hath.base.ServerHandler;
import org.hath.base.Settings;
import org.hath.base.http.handlers.FileHandler;
import org.hath.base.http.handlers.ProxyHandlerTest;
import org.hath.base.http.handlers.ServerCommandHandlerTest;
import org.hath.base.http.handlers.SpeedTestHandler;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HTTPServerRoutingTest {
	private static final int SERVER_TEST_PORT = 42421;

	private static HentaiAtHomeClient mockClient;
	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private static HTTPSessionFactory mockSessionFactory;
	@Mock
	private static ServerHandler mockServerHandler;
	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private static HVFile hvFileMock;
	private static CacheHandler cacheHandlerMock;

	private static HTTPServer hTTPServer;
	private static HttpClient httpClient;

	@BeforeClass
	public static void setUpClass() throws Exception {
		Settings.updateSetting("host", "123.123.123.123");

		mockClient = mock(HentaiAtHomeClient.class, Mockito.RETURNS_DEEP_STUBS);
		cacheHandlerMock = mock(CacheHandler.class);

		when(mockClient.getCacheHandler()).thenReturn(cacheHandlerMock);
		when(cacheHandlerMock.getHVFile(anyString(), anyBoolean())).thenReturn(hvFileMock);

		hTTPServer = new HTTPServer(mockClient, mockSessionFactory);

		hTTPServer.startConnectionListener(SERVER_TEST_PORT);
		httpClient = new HttpClient();
		httpClient.start();
	}

	@Before
	public void setUp() throws Exception {
		when(mockClient.isShuttingDown()).thenReturn(true);
		when(mockClient.getServerHandler()).thenReturn(mockServerHandler);
		when(mockClient.getCacheHandler()).thenReturn(cacheHandlerMock);
		when(cacheHandlerMock.getHVFile(anyString(), anyBoolean())).thenReturn(hvFileMock);
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

	@Test
	public void testFileRoutingStatus() throws Exception {
		hTTPServer.allowNormalConnections();
		when(hvFileMock.getLocalFileRef().exists()).thenReturn(true);
		Path testFile = Files.createTempFile("FileRoutingTest", ".jpg");
		when(hvFileMock.getLocalFileRef()).thenReturn(testFile.toFile());

		ContentResponse response = httpClient
				.GET("http://localhost:" + SERVER_TEST_PORT + "/h/foo/" + generateKeystamp("foo") + "/baz");

		assertThat(response.getStatus(), is(HttpStatus.OK_200));
	}

	@Test
	public void testProxyRoutingStatus() throws Exception {
		hTTPServer.allowNormalConnections();
		when(hvFileMock.getLocalFileRef().exists()).thenReturn(true);
		Path testFile = Files.createTempFile("FileRoutingTest", ".jpg");
		when(hvFileMock.getLocalFileRef()).thenReturn(testFile.toFile());
		Settings.updateSetting("request_proxy_mode", String.valueOf(1));

		String proxyRequest = ProxyHandlerTest.buildProxyRequest(ProxyHandlerTest.VALID_FILEID,
				ProxyHandlerTest.VALID_TOKEN, "42", "1", null, "foobar");

		ContentResponse response = httpClient
				.GET("http://localhost:" + SERVER_TEST_PORT + "/p"
						+ proxyRequest);

		assertThat(response.getStatus(), is(HttpStatus.OK_200));
	}

	@Test
	public void testServerCommandRoutingStatus() throws Exception {
		hTTPServer.allowNormalConnections();
		Settings.updateSetting("rpc_server_ip", "127.0.0.1");
		int commandTime = Settings.getServerTime();
		String command = "foo";

		String serverCommand = ServerCommandHandlerTest.buildServercmdRequest(commandTime, command);

		ContentResponse response = httpClient
				.GET("http://localhost:" + SERVER_TEST_PORT + "/servercmd" + serverCommand);

		assertThat(response.getStatus(), is(HttpStatus.OK_200));
	}

	@Test
	public void testMethodNotAllowed() throws Exception {
		hTTPServer.allowNormalConnections();
		Settings.updateSetting("rpc_server_ip", "127.0.0.1");
		int commandTime = Settings.getServerTime();
		String command = "foo";

		String serverCommand = ServerCommandHandlerTest.buildServercmdRequest(commandTime, command);

		ContentResponse response = httpClient
				.POST("http://localhost:" + SERVER_TEST_PORT + "/servercmd" + serverCommand).send();

		assertThat(response.getStatus(), is(HttpStatus.METHOD_NOT_ALLOWED_405));
	}

	@Test
	public void testInvalidRequestRoutingStatus() throws Exception {
		hTTPServer.allowNormalConnections();
		ContentResponse response = httpClient.GET("http://localhost:" + SERVER_TEST_PORT + "/foo");

		assertThat(response.getStatus(), is(HttpStatus.NOT_FOUND_404));
	}

	private String generateKeystamp(String hvfile) {
		int currentTime = Settings.getServerTime();
		StringBuilder sb = new StringBuilder();

		sb.append("keystamp=");
		sb.append(currentTime);
		sb.append("-");
		sb.append(FileHandler.calculateKeystamp(hvfile, currentTime));

		return sb.toString();
	}
}
