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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.hath.base.Settings;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.net.HttpHeaders;
import com.google.common.net.InetAddresses;

@RunWith(MockitoJUnitRunner.class)
public class BaseHandlerTest {
	private static final String EXTERNAL_ADDRESS = "123.123.123.123";
	private static final String DEFAULT_TARGET = "/";
	
	@Mock
	private HTTPBandwidthMonitor bandwidthMonitor;
	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private HTTPResponseFactory responseFactory;
	@Mock
	private Socket socket;
	@Mock
	HTTPServer httpServer;
	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	Request baseRequest;
	@Mock
	HttpServletRequest request;
	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	HttpServletResponse response;

	private BaseHandler cut;

	@Before
	public void setUp() throws Exception {
		setDefaultBehavior();

		cut = new BaseHandler(socket, 2, true, bandwidthMonitor, responseFactory);
		cut.setHttpServer(httpServer);
	}

	private void setDefaultBehavior() throws Exception {
		when(responseFactory.create(any()).getResponseStatusCode()).thenReturn(HttpStatus.OK_200);
		when(responseFactory.create(any()).getHTTPResponseProcessor().getContentLength()).thenReturn(42);
		when(responseFactory.create(any()).getHTTPResponseProcessor().getContentType()).thenReturn("text/xml");

		when(socket.getInetAddress()).thenReturn(InetAddresses.forString(EXTERNAL_ADDRESS));

		when(baseRequest.getReader().readLine()).thenReturn("GET / HTTP/1.1", "");
		when(bandwidthMonitor.getActualPacketSize()).thenReturn(300);
	}

	@Test
	public void testLocalAccessNotProxyOrFile() throws Exception {
		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(bandwidthMonitor).getActualPacketSize();
	}


	@Test
	public void testLocalAccessIsProxyRequest() throws Exception {
		HTTPResponseProcessor rp = mock(HTTPResponseProcessorProxy.class);
		when(rp.getContentLength()).thenReturn(42);
		when(rp.getBytesRange(eq(42))).thenReturn(new byte[42]);
		when(responseFactory.create(any()).getHTTPResponseProcessor())
				.thenReturn(rp);

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(rp).getBytesRange(eq(42));
	}

	@Test
	public void testLocalAccessIsFileRequest() throws Exception {
		HTTPResponseProcessor rp = mock(HTTPResponseProcessorFile.class);
		when(rp.getContentLength()).thenReturn(42);
		when(rp.getBytes()).thenReturn(new byte[42]);
		when(responseFactory.create(any()).getHTTPResponseProcessor())
				.thenReturn(rp);

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(rp).getBytes();
	}

	@Test
	public void testNonLocalAccessIsFileRequest() throws Exception {
		cut = new BaseHandler(socket, 2, false, bandwidthMonitor, responseFactory);
		cut.setHttpServer(httpServer);

		HTTPResponseProcessor rp = mock(HTTPResponseProcessorFile.class);
		when(rp.getContentLength()).thenReturn(42);
		when(rp.getBytes()).thenReturn(new byte[42]);
		when(responseFactory.create(any()).getHTTPResponseProcessor()).thenReturn(rp);

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(bandwidthMonitor).getActualPacketSize();
		verify(rp).getBytesRange(eq(42));
	}

	@Test
	public void testZeroContentLength() throws Exception {
		when(responseFactory.create(any()).getHTTPResponseProcessor().getContentLength()).thenReturn(0);

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(baseRequest).setHandled(true);
		verify(bandwidthMonitor, never()).getActualPacketSize();
	}

	@Test
	public void testHeaderOnlyRequest() throws Exception {
		when(responseFactory.create(any()).isRequestHeadOnly()).thenReturn(true);
		when(baseRequest.isHead()).thenReturn(true);

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(baseRequest).setHandled(eq(true));
	}

	@Test
	public void testHeaderStatusCode() throws Exception {
		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(response).setStatus(HttpStatus.OK_200);
	}

	@Test
	public void testHeaderDate() throws Exception {
		SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy", java.util.Locale.US);
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(response).setHeader(eq(HttpHeaders.DATE), contains(sdf.format(new Date())));
	}

	@Test
	public void testHeaderDateTimezone() throws Exception {
		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(response).setHeader(eq(HttpHeaders.DATE), contains("GMT"));
	}

	@Test
	public void testHeaderServer() throws Exception {
		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(response).setHeader(eq(HttpHeaders.SERVER),
				eq("Genetic Lifeform and Distributed Open Server " + Settings.CLIENT_VERSION));
	}

	@Test
	public void testHeaderConnection() throws Exception {
		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(response).setHeader(eq(HttpHeaders.CONNECTION), eq("close"));
	}

	@Test
	public void testHeaderCacheControl() throws Exception {
		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(response).setHeader(eq(HttpHeaders.CACHE_CONTROL), eq("public, max-age=31536000"));
	}
	
	@Test
	public void testHeaderContentLength() throws Exception {
		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(response).setContentLength(eq(42));
	}
	
	@Test
	public void testHeaderCacheControlNoContent() throws Exception {
		when(responseFactory.create(any()).getHTTPResponseProcessor().getContentLength()).thenReturn(0);

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(response, never()).setHeader(eq(HttpHeaders.CACHE_CONTROL), eq("public, max-age=31536000"));
	}

	@Test
	public void testHeaderContentLengthNoContent() throws Exception {
		when(responseFactory.create(any()).getHTTPResponseProcessor().getContentLength()).thenReturn(0);

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(response, never()).setContentLength(eq(42));
	}
}
