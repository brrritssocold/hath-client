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
import org.hath.base.http.HTTPRequestAttributes.IntegerAttributes;
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
	private static final String REMOTE_ADDRESS = "233.233.233.233";
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
	@Mock
	HTTPResponseProcessor hpcMock;

	private BaseHandler cut;

	@Before
	public void setUp() throws Exception {
		setDefaultBehavior();

		cut = new BaseHandler(bandwidthMonitor, responseFactory);
		cut.setHttpServer(httpServer);
	}

	private void setHttpResponseProcessor(HTTPResponseProcessor hpc) {
		// TODO replace me with helper
		when(request.getAttribute("org.hath.base.http.httpResponseProcessor")).thenReturn(hpcMock);
	}

	private void setDefaultBehavior() throws Exception {
		when(responseFactory.create(any()).getResponseStatusCode()).thenReturn(HttpStatus.OK_200);

		when(socket.getInetAddress()).thenReturn(InetAddresses.forString(EXTERNAL_ADDRESS));

		when(baseRequest.getReader().readLine()).thenReturn("GET / HTTP/1.1", "");
		when(bandwidthMonitor.getActualPacketSize()).thenReturn(300);

		when(request.getAttribute(HTTPRequestAttributes.LOCAL_NETWORK_ACCESS)).thenReturn(true);
		setHttpResponseProcessor(hpcMock);
		when(request.getRemoteAddr()).thenReturn(REMOTE_ADDRESS);
		when(request.getAttribute(IntegerAttributes.SESSION_ID.toString())).thenReturn(2);

		when(hpcMock.getContentLength()).thenReturn(42);
	}

	@Test
	public void testLocalAccessNotProxyOrFile() throws Exception {
		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(bandwidthMonitor).getActualPacketSize();
	}


	@Test
	public void testLocalAccessIsProxyRequest() throws Exception {
		hpcMock = mock(HTTPResponseProcessorProxy.class);
		when(hpcMock.getContentLength()).thenReturn(42);
		when(hpcMock.getBytesRange(eq(42))).thenReturn(new byte[42]);
		setHttpResponseProcessor(hpcMock);

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(hpcMock).getBytesRange(eq(42));
	}

	@Test
	public void testLocalAccessIsFileRequest() throws Exception {
		hpcMock = mock(HTTPResponseProcessorFile.class);
		when(hpcMock.getContentLength()).thenReturn(42);
		when(hpcMock.getBytes()).thenReturn(new byte[42]);
		setHttpResponseProcessor(hpcMock);

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(hpcMock).getBytes();
	}

	@Test
	public void testNonLocalAccessIsFileRequest() throws Exception {
		cut.setHttpServer(httpServer);

		hpcMock = mock(HTTPResponseProcessorFile.class);
		when(hpcMock.getContentLength()).thenReturn(42);
		when(hpcMock.getBytes()).thenReturn(new byte[42]);
		when(request.getAttribute(HTTPRequestAttributes.LOCAL_NETWORK_ACCESS)).thenReturn(false);

		setHttpResponseProcessor(hpcMock);

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(bandwidthMonitor).getActualPacketSize();
		verify(hpcMock).getBytesRange(eq(42));
	}

	@Test
	public void testZeroContentLength() throws Exception {
		when(hpcMock.getContentLength()).thenReturn(0);

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
		when(hpcMock.getContentLength()).thenReturn(0);

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(response, never()).setHeader(eq(HttpHeaders.CACHE_CONTROL), eq("public, max-age=31536000"));
	}

	@Test
	public void testHeaderContentLengthNoContent() throws Exception {
		when(hpcMock.getContentLength()).thenReturn(0);

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(response, never()).setContentLength(eq(42));
	}
}
