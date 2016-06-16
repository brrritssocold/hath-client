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

import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;

import org.eclipse.jetty.http.HttpStatus;
import org.hath.base.HentaiAtHomeClient;
import org.hath.base.Settings;
import org.hath.base.http.HTTPRequestAttributes.BooleanAttributes;
import org.hath.base.http.HTTPResponse.Sensing;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import com.google.common.net.InetAddresses;

public class ServerCommandHandlerTest extends HandlerJunitTest {
	private static final String CLIENT_IP = "110.120.130.140";

	private ServerCommandHandler cut;
	private InetAddress client_address;

	@Mock
	private HentaiAtHomeClient clientMock;

	@Before
	public void setUp() throws Exception {
		client_address = InetAddresses.forString(CLIENT_IP);
		addIpToRPC(client_address);

		when(request.getAttribute(BooleanAttributes.LOCAL_NETWORK_ACCESS.toString())).thenReturn(true);
		when(request.getRemoteAddr()).thenReturn(CLIENT_IP);

		cut = new ServerCommandHandler(clientMock);
	}

	private void assertSensingPoint(Sensing point) {
		assertThat(cut.sensingPointsHit, hasItem(point));
	}

	private void addIpToRPC(InetAddress ipAddress) {
		Settings.updateSetting("rpc_server_ip", ipAddress.getHostAddress());
	}

	@Test
	public void testhandleServerCommandUnauthorizedIP() throws Exception {
		Settings.clearRPCServers();
		cut.handle("/foo", baseRequest, request, response);

		verify(response).setStatus(HttpStatus.FORBIDDEN_403);
		assertSensingPoint(Sensing.SERVER_CMD_INVALID_RPC_SERVER);
	}

	@Test
	public void testhandleServerCommandMalformed() throws Exception {
		cut.handle("/foo", baseRequest, request, response);

		verify(response).setStatus(HttpStatus.FORBIDDEN_403);
		assertSensingPoint(Sensing.SERVER_CMD_MALFORMED_COMMAND);
	}

	@Test
	public void testhandleServerCommandInvalidKey() throws Exception {
		cut.handle("/foo//1234/232434", baseRequest, request, response);

		verify(response).setStatus(HttpStatus.FORBIDDEN_403);
		assertSensingPoint(Sensing.SERVER_CMD_KEY_INVALID);
	}

	public static String buildServercmdRequest(int commandTime, String command) {
		StringBuilder sb = new StringBuilder();
		sb.append("/");
		sb.append(command);
		sb.append("/");
		sb.append("/");
		sb.append(commandTime);
		sb.append("/");
		sb.append(ServerCommandHandler.calculateServercmdKey("foo", "", commandTime));
		return sb.toString();
	}

	@Test
	public void testhandleServerCommandTimeDriftTooBig() throws Exception {
		int commandTime = Settings.getServerTime() + 2000;
		String command = "foo";

		cut.handle(buildServercmdRequest(commandTime, command), baseRequest, request, response);

		verify(response).setStatus(HttpStatus.FORBIDDEN_403);
		assertSensingPoint(Sensing.SERVER_CMD_KEY_INVALID);
	}

	@Test
	public void testhandleServerCommandValid() throws Exception {
		int commandTime = Settings.getServerTime();
		String command = "foo";

		cut.handle(buildServercmdRequest(commandTime, command), baseRequest, request, response);

		verify(response, never()).setStatus(anyInt());
		assertSensingPoint(Sensing.SERVER_CMD_KEY_VALID);
	}

	@Test
	public void testhandleServerCommandKeyInvalid() throws Exception {
		int commandTime = Settings.getServerTime();
		String command = "foo";

		StringBuilder sb = new StringBuilder();
		sb.append("/");
		sb.append(command);
		sb.append("/");
		sb.append("/");
		sb.append(commandTime);
		sb.append("/");
		sb.append(ServerCommandHandler.calculateServercmdKey("foo", "", commandTime));
		sb.append("bar");

		cut.handle(sb.toString(), baseRequest, request, response);

		verify(response).setStatus(HttpStatus.FORBIDDEN_403);
		assertSensingPoint(Sensing.SERVER_CMD_KEY_INVALID);
	}

}
