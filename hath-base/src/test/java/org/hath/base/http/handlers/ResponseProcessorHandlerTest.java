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

package org.hath.base.http.handlers;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.eclipse.jetty.server.Request;
import org.hath.base.Settings;
import org.hath.base.http.HTTPBandwidthMonitor;
import org.hath.base.http.HTTPRequestAttributes.BooleanAttributes;
import org.hath.base.http.HTTPRequestAttributes.ClassAttributes;
import org.hath.base.http.HTTPRequestAttributes.IntegerAttributes;
import org.hath.base.http.HTTPResponseProcessor;
import org.hath.base.http.HTTPResponseProcessorFile;
import org.hath.base.http.HTTPResponseProcessorProxy;
import org.hath.base.http.HTTPServer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.google.common.net.HttpHeaders;
import com.google.common.net.InetAddresses;

@RunWith(MockitoJUnitRunner.class)
public class ResponseProcessorHandlerTest {
	private static final String EXTERNAL_ADDRESS = "123.123.123.123";
	private static final String REMOTE_ADDRESS = "233.233.233.233";
	private static final String DEFAULT_TARGET = "/";
	
	@Mock
	private HTTPBandwidthMonitor bandwidthMonitor;
	@Mock
	private Socket socket;
	@Mock
	private HTTPServer httpServer;
	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private Request baseRequest;


	private MockHttpServletRequest request;
	private MockHttpServletResponse response;

	@Mock
	private HTTPResponseProcessor hpcMock;

	private ResponseProcessorHandler cut;

	@Before
	public void setUp() throws Exception {
		request = new MockHttpServletRequest();
		response = new MockHttpServletResponse();
		setDefaultBehavior();
		cut = new ResponseProcessorHandler(bandwidthMonitor);
	}

	private void setHttpResponseProcessor(HTTPResponseProcessor hpc) {
		request.setAttribute(ClassAttributes.HTTPResponseProcessor.toString(), hpc);
	}

	private void setDefaultBehavior() throws Exception {
		when(socket.getInetAddress()).thenReturn(InetAddresses.forString(EXTERNAL_ADDRESS));

		when(baseRequest.getReader().readLine()).thenReturn("GET / HTTP/1.1", "");
		when(bandwidthMonitor.getActualPacketSize()).thenReturn(300);

		request.setAttribute(BooleanAttributes.LOCAL_NETWORK_ACCESS.toString(), true);
		setHttpResponseProcessor(hpcMock);
		request.setRemoteAddr(REMOTE_ADDRESS);
		request.setAttribute(IntegerAttributes.SESSION_ID.toString(), 2);

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
		hpcMock = mock(HTTPResponseProcessorFile.class);
		when(hpcMock.getContentLength()).thenReturn(42);
		when(hpcMock.getBytes()).thenReturn(new byte[42]);

		request.setAttribute(BooleanAttributes.LOCAL_NETWORK_ACCESS.toString(), false);

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
		when(baseRequest.isHead()).thenReturn(true);

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(baseRequest).setHandled(eq(true));
	}

	@Test
	public void testHeaderDate() throws Exception {
		SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy", java.util.Locale.US);
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		assertThat(response.getHeader(HttpHeaders.DATE), startsWith(sdf.format(new Date())));
	}

	@Test
	public void testHeaderDateTimezone() throws Exception {
		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		assertThat(response.getHeader(HttpHeaders.DATE), endsWith("GMT"));
	}

	@Test
	public void testHeaderServer() throws Exception {
		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		assertThat(response.getHeader(HttpHeaders.SERVER),
				is("Genetic Lifeform and Distributed Open Server " + Settings.CLIENT_VERSION));
	}

	@Test
	public void testHeaderConnection() throws Exception {
		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		assertThat(response.getHeader(HttpHeaders.CONNECTION), is("close"));
	}

	@Test
	public void testHeaderCacheControl() throws Exception {
		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL), is("public, max-age=31536000"));
	}
	
	@Test
	public void testHeaderContentLength() throws Exception {
		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		assertThat(response.getContentLength(), is(42));
	}
	
	@Test
	public void testHeaderCacheControlNoContent() throws Exception {
		when(hpcMock.getContentLength()).thenReturn(0);

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		assertThat(response.containsHeader(HttpHeaders.CACHE_CONTROL), is(false));
	}

	@Test
	public void testHeaderContentLengthNoContent() throws Exception {
		when(hpcMock.getContentLength()).thenReturn(0);

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		assertThat(response.containsHeader(HttpHeaders.CONTENT_LENGTH), is(false));
	}

	@Test
	public void testResponseProcessorNullContentLength() throws Exception {
		setHttpResponseProcessor(null);

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		assertThat(response.containsHeader(HttpHeaders.CONTENT_LENGTH), is(false));
	}

	@Test
	public void testResponseProcessorNullStatus() throws Exception {
		setHttpResponseProcessor(null);

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		assertThat(response.getStatus(), is(200));
	}

	@Test
	public void testResponseProcessorNullHandled() throws Exception {
		setHttpResponseProcessor(null);

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(baseRequest, never()).setHandled(anyBoolean());
	}

	@Test
	public void testResponseProcessorExceptionThrown() throws Exception {
		hpcMock = mock(HTTPResponseProcessorFile.class);
		Mockito.doThrow(new RuntimeException("Testing")).when(hpcMock).initialize(response);
		setHttpResponseProcessor(hpcMock);

		cut.handle(DEFAULT_TARGET, baseRequest, request, response);

		verify(baseRequest).setHandled(true);
	}
}
