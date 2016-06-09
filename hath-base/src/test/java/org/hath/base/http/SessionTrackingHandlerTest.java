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

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.hath.base.HentaiAtHomeClient;
import org.hath.base.Settings;
import org.hath.base.http.HTTPRequestAttributes.IntegerAttributes;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class SessionTrackingHandlerTest {
	private static String DEFAULT_TARGET = "";
	private static String HOST_ADDRESS = "200.210.220.210";
	private static String RPC_SERVER_ADDRESS = "222.222.222.222";
	private SessionTrackingHandler cut;

	private HttpServletResponse responseMock;
	private Request baseRequestMock;
	private HttpServletRequest requestMock;
	private HentaiAtHomeClient client;
	private FloodControl floodControl;
	private SessionTracker sessionTracker;

	@Before
	public void setUp() throws Exception {
		Settings.updateSetting("host", HOST_ADDRESS);
		Settings.updateSetting("rpc_server_ip", RPC_SERVER_ADDRESS);

		baseRequestMock = mock(Request.class);
		requestMock = mock(HttpServletRequest.class);
		responseMock = mock(HttpServletResponse.class);

		client = mock(HentaiAtHomeClient.class, Mockito.RETURNS_DEEP_STUBS);
		floodControl = mock(FloodControl.class);
		sessionTracker = mock(SessionTracker.class);

		when(baseRequestMock.getRemoteAddr()).thenReturn("110.120.130.140");

		cut = new SessionTrackingHandler(client, floodControl, sessionTracker);
	}

	@Test
	public void testRejectDuringStartup() throws Exception {
		cut.handle(DEFAULT_TARGET, baseRequestMock, requestMock, responseMock);

		verify(responseMock).setStatus(HttpStatus.SERVICE_UNAVAILABLE_503);
	}

	@Test
	public void testTriggerOverload() throws Exception {
		cut.allowNormalConnections();
		when(sessionTracker.isOverloaded()).thenReturn(true);

		cut.handle(DEFAULT_TARGET, baseRequestMock, requestMock, responseMock);

		verify(client.getServerHandler()).notifyOverload();
	}

	@Test
	public void testMaxConnections() throws Exception {
		cut.allowNormalConnections();
		when(sessionTracker.isMaxSessionReached()).thenReturn(true);

		cut.handle(DEFAULT_TARGET, baseRequestMock, requestMock, responseMock);

		verify(responseMock).setStatus(HttpStatus.SERVICE_UNAVAILABLE_503);
	}

	@Test
	public void testLocalRequest() throws Exception {
		cut.allowNormalConnections();
		when(baseRequestMock.getRemoteAddr()).thenReturn("127.0.0.1");

		cut.handle(DEFAULT_TARGET, baseRequestMock, requestMock, responseMock);

		verify(sessionTracker, never()).isMaxSessionReached();
	}

	@Test
	public void testExternalRequest() throws Exception {
		cut.allowNormalConnections();

		cut.handle(DEFAULT_TARGET, baseRequestMock, requestMock, responseMock);

		verify(sessionTracker).isMaxSessionReached();
	}

	@Test
	public void testLocalNetworkAccessAttribute() throws Exception {
		cut.allowNormalConnections();
		when(baseRequestMock.getRemoteAddr()).thenReturn("127.0.0.1");

		cut.handle(DEFAULT_TARGET, baseRequestMock, requestMock, responseMock);

		verify(requestMock).setAttribute(HTTPRequestAttributes.LOCAL_NETWORK_ACCESS, true);
	}

	@Test
	public void testApiServerAccessAttribute() throws Exception {
		cut.allowNormalConnections();
		when(baseRequestMock.getRemoteAddr()).thenReturn(RPC_SERVER_ADDRESS);

		cut.handle(DEFAULT_TARGET, baseRequestMock, requestMock, responseMock);

		verify(requestMock).setAttribute(HTTPRequestAttributes.API_SERVER_ACCESS, true);
	}

	@Test
	public void testSessionIdAttribute() throws Exception {
		cut.allowNormalConnections();
		when(baseRequestMock.getRemoteAddr()).thenReturn("127.0.0.1");

		cut.handle(DEFAULT_TARGET, baseRequestMock, requestMock, responseMock);

		verify(requestMock).setAttribute(eq(IntegerAttributes.SESSION_ID.toString()), eq(1));
	}
}
