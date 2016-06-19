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

package org.hath.base.gallery;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.hath.base.HentaiAtHomeClient;
import org.hath.base.MiscTools;
import org.hath.base.Settings;
import org.hath.base.Stats;
import org.hath.base.gallery.GalleryFileDownloader.Sensing;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

public class GalleryFileDownloaderTest {
	private static final String VALID_FILEID = "0000000000000000000000000000000000000000-10-0-0-jpg";
	private static final String VALID_TOKEN = "1-0000000000000000000000000000000000000000";

	private static Server server;
	private static HttpClient httpClient;
	private static int testPort;

	private int clientID;
	private String clientKey;

	private HentaiAtHomeClient clientMock;
	private GalleryFileDownloader cut;

	private String fileid;
	private String token;
	private int gid;
	private int page;
	private String filename;
	private URL testURL;

	private static TestHandler testHandler;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		server = new Server(0);
		httpClient = new HttpClient();
		testHandler = new TestHandler();
		server.setHandler(testHandler);
		server.start();
		httpClient.start();
		testPort = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
		
		Settings.updateSetting("request_server", "localhost");
	}

	private URL buildRequestUrl(String fileid, String token, int gid, int page, String filename, boolean skipHath)
			throws MalformedURLException {
		return new URL("http", Settings.getRequestServer(), testPort,
				"/r/" + fileid + "/" + token + "/" + gid + "-" + page + "/" + filename + (skipHath ? "?nl=1" : ""));
	}

	private void setUpMocks() {
		clientMock = mock(HentaiAtHomeClient.class, Mockito.RETURNS_DEEP_STUBS);
	}

	private void assertHeader(String headerName, String expected) {
		Collection<String> header = testHandler.getHeaders().get(headerName);

		if (header.size() == 0) {
			fail("Header not present");
		}

		if (header.size() > 1) {
			fail("Header contains more than one value");
		}

		assertThat(header.iterator().next(), is(expected));
	}

	private void assertSensingPoint(Sensing point) {
		assertThat(cut.sensingPointHits, hasItem(point));
	}

	@SuppressWarnings("deprecation")
	@Before
	public void setUp() throws Exception {
		testHandler.reset();
		Stats.resetStats();
		Stats.programStarted();
		setUpMocks();
		
		clientID = 1234;
		clientKey = "foobarbaz42";

		Settings.setClientKey(clientKey);
		Settings.setClientID(clientID);
		
		fileid = VALID_FILEID;
		token = VALID_TOKEN;
		gid = 42;
		page = 2;
		filename = "baz";
		


		testURL = buildRequestUrl(fileid, token, gid, page, filename, false);
		cut = new GalleryFileDownloader(clientMock, fileid, token, gid, page, filename, false, httpClient);
	}

	@Test
	public void testInitialize() throws Exception {
		assertThat(cut.initialize(testURL), is(HttpStatus.OK_200));
	}

	@Test
	public void testInitializeNoUrlMalformedUrl() throws Exception {
		cut = new GalleryFileDownloader(clientMock, ",.#!@", token, gid, page, filename, false, httpClient);
		assertThat(cut.initialize(), is(HttpStatus.INTERNAL_SERVER_ERROR_500));
	}

	// FIXME fails on Linux with "connection refused"
	@Ignore("Test fails on Linux machines")
	@Test
	public void testInitializeNoUrl() throws Exception {
		assertThat(cut.initialize(), is(HttpStatus.OK_200));
	}

	@Test
	public void testRequestPropertyUserAgent() throws Exception {
		cut.initialize(testURL);

		assertHeader("User-Agent",
				"Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.8.1.12) Gecko/20080201 Firefox/2.0.0.12");
	}

	@Test
	public void testRequestPropertyHathRequest() throws Exception {
		cut.initialize(testURL);

		String hash = MiscTools.getSHAString(Settings.getClientKey() + fileid);
		assertHeader("Hath-Request",
				Settings.getClientID() + "-" + hash);
	}
	
	@Test
	public void testRequestContentLengthTooLarge() throws Exception {
		testHandler.setContentLength(10585760);

		cut.initialize(testURL);

		assertSensingPoint(Sensing.CONTENT_LENGTH_GREATER_10MB);
	}

	@Test
	public void testRequestContentLengthTooLargeResponseCode() throws Exception {
		testHandler.setContentLength(10585760);

		assertThat(cut.initialize(testURL), is(502));
	}

	@Test
	public void testRequestContentLengthTooLargeDownloadState() throws Exception {
		testHandler.setContentLength(10585760);

		assertThat(cut.getDownloadState(), is(0));
	}

	@Test
	public void testRequestContentLengthMismatch() throws Exception {
		testHandler.setContentLength(5);

		cut.initialize(testURL);

		assertSensingPoint(Sensing.CONTENT_LENGTH_MISMATCH);
	}

	@Test
	public void testRequestContentLengthMatch() throws Exception {
		testHandler.setContentLength(10);

		cut.initialize(testURL);

		assertSensingPoint(Sensing.CONTENT_LENGTH_MATCH);
	}

	@Test(timeout = 1000)
	public void testRequestDownloadDataContents() throws Exception {
		testHandler.setContentLength(10);
		testHandler.returnData(true);
		byte[] testData = testHandler.generateRandomData();

		cut.initialize(testURL);

		while (cut.getDownloadState() == 0) {
			Thread.sleep(50);
		}

		byte[] loadedData = cut.getDownloadBufferRange(0, 10);

		assertArrayEquals(loadedData, testData);
	}

	@Test(timeout = 1000)
	public void testCorruptFile() throws Exception {
		testHandler.setContentLength(10);
		testHandler.returnData(true);
		testHandler.generateRandomData();

		cut.initialize(testURL);

		while (cut.getDownloadState() == 0) {
			Thread.sleep(50);
		}

		assertSensingPoint(Sensing.CORRUPT_FILE);
	}

	@Test(timeout = 1000)
	public void testRequestDownloadDataStats() throws Exception {
		testHandler.setContentLength(10);
		testHandler.returnData(true);
		testHandler.generateRandomData();

		cut.initialize(testURL);

		while (cut.getDownloadState() == 0) {
			Thread.sleep(50);
		}

		cut.getDownloadBufferRange(0, 10);

		assertThat(Stats.getBytesRcvd(), is(10L));
	}

	@Test
	public void testRequestDownloadDataDownloadSize() throws Exception {
		testHandler.setContentLength(10);
		testHandler.returnData(true);
		testHandler.generateRandomData();

		cut.initialize(testURL);

		byte[] loadedData = cut.getDownloadBufferRange(0, 10);

		assertThat(loadedData.length, is(10));
	}

	@Test(timeout = 5000)
	public void testRequestDownloadDataLargeSensingPoint() throws Exception {
		int testSize = 3145728; // 3 MB
		testHandler.setContentLength(testSize);
		testHandler.returnData(true);
		testHandler.generateRandomData();

		cut.initialize(testURL);

		while (cut.getDownloadState() == 0) {
			Thread.sleep(50);
		}

		cut.getDownloadBufferRange(0, testSize);

		assertSensingPoint(Sensing.BYTES_READ);
	}

	@Test(timeout = 5000)
	public void testRequestDownloadDataLargeContents() throws Exception {
		int testSize = 3145728; // 3 MB
		testHandler.setContentLength(testSize);
		testHandler.returnData(true);
		byte[] testData = testHandler.generateRandomData();

		cut.initialize(testURL);

		while (cut.getDownloadState() == 0) {
			Thread.sleep(50);
		}

		byte[] loadedData = cut.getDownloadBufferRange(0, testSize);

		assertArrayEquals(testData, loadedData);
	}

	@Test(timeout = 5000)
	public void testRequestDownloadDataLargeStats() throws Exception {
		// FIXME Download size reporting is incorrect for large files
		long testSize = 3145728; // 3 MB
		testHandler.setContentLength(testSize);
		testHandler.returnData(true);
		testHandler.generateRandomData();

		cut.initialize(testURL);

		while (cut.getDownloadState() == 0) {
			Thread.sleep(50);
		}

		cut.getDownloadBufferRange(0, (int) testSize);

		assertThat(Stats.getBytesRcvd(), is(testSize + 3145));
	}

	@Test
	public void testRequestContentLengthMismatchResponseCode() throws Exception {
		testHandler.setContentLength(5);

		assertThat(cut.initialize(testURL), is(200));
	}
	
	@Test
	public void testRequestContentLengthMismatchDownloadState() throws Exception {
		testHandler.setContentLength(5);

		assertThat(cut.getDownloadState(), is(0));
	}

	@Test
	public void testRequestContentLengthImageLimitReached() throws Exception {
		testHandler.setContentLength(1009);

		cut.initialize(testURL);

		verify(clientMock.getServerHandler()).notifyMoreFiles();
	}
	
	@Test
	public void testRequestContentLengthImageLimitReached2() throws Exception {
		testHandler.setContentLength(28658);

		cut.initialize(testURL);

		verify(clientMock.getServerHandler()).notifyMoreFiles();
	}

	@Test
	public void testRequestContentLengthImageLimitReachedResponseCode() throws Exception {
		testHandler.setContentLength(1009);

		assertThat(cut.initialize(testURL), is(502));

	}

	@Test
	public void testRequestContentLengthImageLimitReachedDownloadState() throws Exception {
		testHandler.setContentLength(1009);

		assertThat(cut.getDownloadState(), is(0));

	}

	@Test
	public void testInitializeFailDownloadState() throws Exception {
		testHandler.setContentLength(1009);
		when(clientMock.getServerHandler().notifyMoreFiles()).thenThrow(new RuntimeException("Tesing init failure"));

		cut.initialize(testURL);

		assertThat(cut.getDownloadState(), is(-1));
	}

	@Test
	public void testInitializeFail() throws Exception {
		testHandler.setContentLength(1009);
		when(clientMock.getServerHandler().notifyMoreFiles()).thenThrow(new RuntimeException("Tesing init failure"));

		cut.initialize(testURL);

		assertSensingPoint(Sensing.INTI_FAIL);
	}

	@Test(timeout = 5000)
	public void testRequestTimeout() throws Exception {
		testHandler.setContentLength(10);
		testHandler.returnData(true);
		testHandler.generateRandomData();
		testHandler.setResponseDelay(1000);

		cut.initialize(testURL, 500, 30000);

		while (cut.getDownloadState() == 0) {
			Thread.sleep(50);
		}


		assertThat(cut.getDownloadState(), is(-1));
	}

	@Test
	public void testFileCached() throws Exception {
		testHandler.setContentLength(10);
		testHandler.returnData(true);
		byte[] data = testHandler.generateRandomData();

		when(clientMock.getCacheHandler().moveFileToCacheDir(any(), any())).thenReturn(true);

		fileid = MiscTools.getSHAString(data) + "-10-0-0-jpg";
		token = VALID_TOKEN;
		gid = 42;
		page = 2;
		filename = "baz";

		cut = new GalleryFileDownloader(clientMock, fileid, token, gid, page, filename, false, httpClient);
		testURL = buildRequestUrl(fileid, token, gid, page, filename, false);

		cut.initialize(testURL);

		while (cut.getDownloadState() == 0) {
			Thread.sleep(50);
		}

		assertSensingPoint(Sensing.FILE_SAVED_TO_CACHE);
	}

	@Test
	public void testFileNotCached() throws Exception {
		testHandler.setContentLength(10);
		testHandler.returnData(true);
		byte[] data = testHandler.generateRandomData();

		fileid = MiscTools.getSHAString(data) + "-10-0-0-jpg";
		token = VALID_TOKEN;
		gid = 42;
		page = 2;
		filename = "baz";

		cut = new GalleryFileDownloader(clientMock, fileid, token, gid, page, filename, false, httpClient);
		testURL = buildRequestUrl(fileid, token, gid, page, filename, false);

		cut.initialize(testURL);

		while (cut.getDownloadState() == 0) {
			Thread.sleep(50);
		}

		assertSensingPoint(Sensing.FILE_NOT_SAVED_TO_CACHE);
	}

	@Ignore
	@Test
	public void testGetContentType() throws Exception {
		throw new RuntimeException("not yet implemented");
	}

	@Test
	public void testGetContentLength() throws Exception {
		testHandler.setContentLength(5);

		cut.initialize(testURL);

		assertThat(cut.getContentLength(), is(10));
	}

	@Ignore
	@Test
	public void testGetCurrentWriteoff() throws Exception {
		throw new RuntimeException("not yet implemented");
	}

	@Ignore
	@Test
	public void testGetDownloadBufferRange() throws Exception {
		throw new RuntimeException("not yet implemented");
	}

	static class TestHandler extends AbstractHandler {
		public void setResponseDelay(int responseDelay) {
			this.responseDelay = responseDelay;
		}

		private Multimap<String, String> headerMap = MultimapBuilder.hashKeys().linkedListValues().build();
		private long contentLength = 0;
		private byte[] randomData = new byte[0];
		private boolean returnData = false;
		private int responseDelay = -1;

		public void reset() {
			headerMap.clear();
			contentLength = 0;
			returnData = false;
			responseDelay = -1;
		}

		public byte[] generateRandomData() {
			randomData = new byte[(int) contentLength];

			for (int i = 0; i < contentLength; i++) {
				randomData[i] = (byte) (Math.random() * Byte.MAX_VALUE);
			}

			return randomData;
		}

		public void setContentLength(long contentLength) {
			this.contentLength = contentLength;
		}

		public Multimap<String, String> getHeaders() {
			return headerMap;
		}

		public void returnData(boolean returnData) {
			this.returnData = returnData;
		}

		private void storeHeaders(Request request) {
			Enumeration<String> headerNames = request.getHeaderNames();

			while (headerNames.hasMoreElements()) {
				String headerName = headerNames.nextElement();

				Enumeration<String> headers = request.getHeaders(headerName);

				while (headers.hasMoreElements()) {
					String header = headers.nextElement();
					headerMap.put(headerName, header);
				}
			}
		}

		@Override
		public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
				throws IOException, ServletException {
			storeHeaders(baseRequest);

			response.setContentType("application/octet-stream");
			response.setContentLengthLong(contentLength);

			if (responseDelay > 0) {
				try {
					Thread.sleep(responseDelay);
				} catch (InterruptedException e) {
				}
			}
			
			if (returnData) {
				response.getOutputStream().write(randomData, 0, (int) contentLength);
				response.getOutputStream().flush();
			}
			response.setStatus(HttpServletResponse.SC_OK);
			baseRequest.setHandled(true);
		}
	}
}
