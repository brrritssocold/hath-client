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

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.util.Hashtable;

import org.hath.base.HVFile;
import org.hath.base.Settings;
import org.hath.base.http.HTTPResponse.Sensing;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.common.net.InetAddresses;


public class HTTPResponseTest {
	private HTTPSession sessionMock;
	private HVFile hvFileMock;
	private HTTPResponse cut;
	private InetAddress client_address;

	private static final String CLIENT_IP = "110.120.130.140";

	@Before
	public void setUp() throws Exception {
		client_address = InetAddresses.forString(CLIENT_IP);
		hvFileMock = mock(HVFile.class, Mockito.RETURNS_DEEP_STUBS);

		setUpMocks(hvFileMock);
		addIpToRPC(client_address);
		setProxyMode(4);
	}

	private void assertSensingPoint(Sensing point) {
		assertThat(cut.sensingPointsHit, hasItem(point));
	}

	private void addIpToRPC(InetAddress ipAddress) {
		Settings.updateSetting("rpc_server_ip", ipAddress.getHostAddress());
	}

	private void setProxyMode(int mode) {
		Settings.updateSetting("request_proxy_mode", String.valueOf(mode));
	}

	private void setUpMocks(HVFile hvFile, InetAddress ipAddress, boolean localNetwork) {
		sessionMock = mock(HTTPSession.class, Mockito.RETURNS_DEEP_STUBS);

		when(sessionMock.isLocalNetworkAccess()).thenReturn(localNetwork);
		when(sessionMock.getSocketInetAddress()).thenReturn(ipAddress);
		when(sessionMock.getHTTPServer().getHentaiAtHomeClient().getCacheHandler().getHVFile(anyString(), anyBoolean()))
				.thenReturn(hvFile);

		cut = new HTTPResponse(sessionMock);
	}

	private void setUpMocks(HVFile hvFile) {
		setUpMocks(hvFile, client_address, true);
	}

	private String generateKeystamp(String hvfile, int timeOffset) {
		int currentTime = Settings.getServerTime() + timeOffset;
		StringBuilder sb = new StringBuilder();

		sb.append("keystamp=");
		sb.append(currentTime);
		sb.append("-");
		sb.append(cut.calculateKeystamp(hvfile, currentTime));

		return sb.toString();
	}

	private String generateKeystamp(String hvfile) {
		return generateKeystamp(hvfile, 0);
	}

	private void addToStaticRange(String fileid) {
		Settings.updateSetting("static_ranges", fileid.substring(0, 4));
	}

	@Test
	public void testParseRequestInvalidRequest() throws Exception {
		cut.parseRequest("foo", true);

		assertThat(cut.getResponseStatusCode(), is(400));
	}

	@Test
	public void testParseRequestInvalidGet() throws Exception {
		cut.parseRequest("GET foo bar", true);

		assertThat(cut.getResponseStatusCode(), is(405));
	}

	@Test
	public void testParseRequestValidGetResponse() throws Exception {
		cut.parseRequest("GET foo HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(404));
	}

	@Test
	public void testParseRequestValidGet() throws Exception {
		cut.parseRequest("GET foo HTTP/1.1", true);

		assertThat(cut.isValidRequest(), is(true));
	}

	@Test
	public void testParseRequestValidHead() throws Exception {
		cut.parseRequest("HEAD foo HTTP/1.1", true);

		assertThat(cut.isValidRequest(), is(true));
	}

	@Test
	public void testParseRequestIsRequestHeadOnly() throws Exception {
		cut.parseRequest("HEAD foo HTTP/1.1", true);

		assertThat(cut.isRequestHeadOnly(), is(true));
	}

	@Test
	public void testParseRequestFileTooShort() throws Exception {
		cut.parseRequest("GET /h/foo HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(400));
		assertSensingPoint(Sensing.FILE_REQUEST_TOO_SHORT);
	}

	@Test
	public void testParseRequestInvalidRequestType() throws Exception {
		cut.parseRequest("GET /z/foo HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(404));
	}

	@Test
	public void testParseRequestMalformedRequestLeading() throws Exception {
		cut.parseRequest("GET _/h/foo HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(404));
	}

	@Test
	public void testParseRequestMalformedRequestRequestType() throws Exception {
		cut.parseRequest("POST /h/foo HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(405));
	}

	@Test
	public void testParseRequestFileInvalidKeystamp() throws Exception {
		cut.parseRequest("GET /h/foo/keystamp=derp/baz HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(403));
		assertSensingPoint(Sensing.FILE_REQUEST_INVALID_KEY);
	}

	@Test
	public void testParseRequestFileInvalidKeystampTimeDriftTooBig() throws Exception {
		cut.parseRequest("GET /h/foo/" + generateKeystamp("foo", 2000) + "/baz HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(403));
		assertSensingPoint(Sensing.FILE_REQUEST_INVALID_KEY);
	}

	@Test
	public void testParseRequestFileInvalidKeystampHashMismatch() throws Exception {
		cut.parseRequest("GET /h/foo/" + generateKeystamp("foo") + "a" + "/baz HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(403));
		assertSensingPoint(Sensing.FILE_REQUEST_INVALID_KEY);
	}

	@Test
	public void testParseRequestFileInvalidKeystampTimeTooLarge() throws Exception {
		cut.parseRequest("GET /h/foo/keystamp=" + Long.MAX_VALUE + "-foobar/baz HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(403));
		assertSensingPoint(Sensing.FILE_REQUEST_INVALID_KEY);
	}

	@Test
	public void testParseRequestFileHVFileNull() throws Exception {
		setUpMocks(null);

		cut.parseRequest("GET /h/foo/" + generateKeystamp("foo") + "/baz HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(404));
		assertSensingPoint(Sensing.FILE_REQUEST_FILE_NOT_FOUND);
	}

	@Test
	public void testParseRequestFileHVFileExists() throws Exception {
		when(hvFileMock.getLocalFileRef().exists()).thenReturn(true);
		cut.parseRequest("GET /h/foo/" + generateKeystamp("foo") + "/baz HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(500));
		assertSensingPoint(Sensing.FILE_REQUEST_FILE_LOCAL);
	}

	@Test
	public void testParseRequestHVFileExistsExternalRequest() throws Exception {
		when(hvFileMock.getLocalFileRef().exists()).thenReturn(true);
		cut.parseRequest("GET /h/foo/" + generateKeystamp("foo") + "/baz HTTP/1.1", false);

		assertThat(cut.getResponseStatusCode(), is(500));
		assertSensingPoint(Sensing.FILE_REQUEST_FILE_LOCAL);
	}

	@Test
	public void testParseRequestFileNotFound() throws Exception {
		when(hvFileMock.getLocalFileRef().exists()).thenReturn(false);
		when(hvFileMock.getFileid()).thenReturn("bazbaz");

		cut.parseRequest("GET /h/bazbaz/" + generateKeystamp("bazbaz") + "/baz HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(404));
		assertSensingPoint(Sensing.FILE_REQUEST_FILE_NOT_LOCAL_OR_STATIC);
	}

	@Test
	public void testParseRequestFileStaticRangeIdNotInTokens() throws Exception {
		String fileid = "foobar";
		when(hvFileMock.getLocalFileRef().exists()).thenReturn(false);
		when(hvFileMock.getFileid()).thenReturn(fileid);

		addToStaticRange(fileid);

		cut.parseRequest("GET /h/" + fileid + "/" + generateKeystamp(fileid) + "/baz HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(404));
		assertSensingPoint(Sensing.FILE_REQUEST_INVALID_FILE_TOKEN);
	}

	@Test
	public void testParseRequestFileStaticRange() throws Exception {
		String fileid = "foobar";

		Hashtable<String, String> tokens = new Hashtable<>();
		tokens.put(fileid, "1");

		when(hvFileMock.getLocalFileRef().exists()).thenReturn(false);
		when(hvFileMock.getFileid()).thenReturn(fileid);
		when(sessionMock.getHTTPServer().getHentaiAtHomeClient().getServerHandler()
				.getFileTokens(anyListOf(String.class)))
						.thenReturn(tokens);

		addToStaticRange(fileid);

		cut.parseRequest("GET /h/" + fileid + "/" + generateKeystamp(fileid) + "/baz HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(500));
		assertSensingPoint(Sensing.FILE_REQUEST_VALID_FILE_TOKEN);
	}

	@Test
	public void testParseRequestNull() throws Exception {
		cut.parseRequest(null, true);

		assertThat(cut.getResponseStatusCode(), is(400));
	}

	@Ignore
	@Test
	public void testGetHTTPResponseProcessor() throws Exception {
		throw new RuntimeException("not yet implemented");
	}

	@Test
	public void testGetResponseStatusCodeDefault() throws Exception {
		assertThat(cut.getResponseStatusCode(), is(500));
	}

	@Test
	public void testIsValidRequestDefault() throws Exception {
		assertThat(cut.isValidRequest(), is(false));
	}

	@Test
	public void testIsRequestHeadOnlyDefault() throws Exception {
		assertThat(cut.isRequestHeadOnly(), is(false));
	}

	@Test
	public void testIsServercmdDefault() throws Exception {
		assertThat(cut.isServercmd(), is(false));
	}

	@Test
	public void testParseRequestServerCommandUnauthorizedIP() throws Exception {
		Settings.clearRPCServers();
		cut.parseRequest("GET /servercmd/foo HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(403));
		assertSensingPoint(Sensing.SERVER_CMD_INVALID_RPC_SERVER);
	}

	@Test
	public void testParseRequestServerCommandMalformed() throws Exception {
		cut.parseRequest("GET /servercmd/foo HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(403));
		assertSensingPoint(Sensing.SERVER_CMD_MALFORMED_COMMAND);
	}
	
	@Test
	public void testParseRequestServerCommandInvalidKey() throws Exception {
		cut.parseRequest("GET /servercmd/foo//1234/232434 HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(403));
		assertSensingPoint(Sensing.SERVER_CMD_KEY_INVALID);
	}

	protected String buildServercmdRequest(int commandTime, String command) {
		StringBuilder sb = new StringBuilder();
		sb.append("Get ");
		sb.append("/servercmd");
		sb.append("/");
		sb.append(command);
		sb.append("/");
		sb.append("/");
		sb.append(commandTime);
		sb.append("/");
		sb.append(cut.calculateServercmdKey("foo", "", commandTime));
		sb.append(" HTTP/1.1");
		return sb.toString();
	}

	@Test
	public void testParseRequestServerCommandTimeDriftTooBig() throws Exception {
		int commandTime = Settings.getServerTime() + 2000;
		String command = "foo";

		cut.parseRequest(buildServercmdRequest(commandTime, command), true);

		assertThat(cut.getResponseStatusCode(), is(403));
		assertSensingPoint(Sensing.SERVER_CMD_KEY_INVALID);
	}

	@Test
	public void testParseRequestServerCommandValid() throws Exception {
		int commandTime = Settings.getServerTime();
		String command = "foo";

		cut.parseRequest(buildServercmdRequest(commandTime, command), true);

		assertThat(cut.getResponseStatusCode(), is(200));
		assertSensingPoint(Sensing.SERVER_CMD_KEY_VALID);
	}

	@Test
	public void testParseRequestServerCommandIsServerCmd() throws Exception {
		int commandTime = Settings.getServerTime();
		String command = "foo";

		cut.parseRequest(buildServercmdRequest(commandTime, command), true);

		assertThat(cut.isServercmd(), is(true));
	}

	@Test
	public void testParseRequestServerCommandIsValidRequest() throws Exception {
		int commandTime = Settings.getServerTime();
		String command = "foo";

		cut.parseRequest(buildServercmdRequest(commandTime, command), true);

		assertThat(cut.isValidRequest(), is(true));
	}

	@Test
	public void testParseRequestServerCommandKeyInvalid() throws Exception {
		int commandTime = Settings.getServerTime();
		String command = "foo";

		StringBuilder sb = new StringBuilder();
		sb.append("Get ");
		sb.append("/servercmd");
		sb.append("/");
		sb.append(command);
		sb.append("/");
		sb.append("/");
		sb.append(commandTime);
		sb.append("/");
		sb.append(cut.calculateServercmdKey("foo", "", commandTime));
		sb.append("bar");
		sb.append(" HTTP/1.1");

		cut.parseRequest(sb.toString(), true);

		assertThat(cut.getResponseStatusCode(), is(403));
		assertSensingPoint(Sensing.SERVER_CMD_KEY_INVALID);
	}

	@Test
	public void testParseRequestProxyDisabled() throws Exception {
		setProxyMode(0);
		assertThat(Settings.getRequestProxyMode(), is(0));

		cut.parseRequest("GET /p/ HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(404));
	}

	@Test
	public void testParseRequestProxyValid() throws Exception {
		setProxyMode(1);
		assertThat(Settings.getRequestProxyMode(), is(1));

		cut.parseRequest("GET /p/fileid=foobar/bazbaz HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(404));
	}

	@Test
	public void testParseRequestProxyNotLocal() throws Exception {
		setProxyMode(2);
		assertThat(Settings.getRequestProxyMode(), is(2));
		setUpMocks(hvFileMock, client_address, false);

		cut.parseRequest("GET /p/fileid=foobar/bazbaz HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(404));
	}

	@Test
	public void testParseRequestProxyNotLocalExternalAllowed() throws Exception {
		setProxyMode(1);
		assertThat(Settings.getRequestProxyMode(), is(1));
		setUpMocks(hvFileMock, client_address, false);

		cut.parseRequest("GET /p/ HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(404));
	}

	@Test
	public void testParseRequestProxyInvalidPasskey() throws Exception {
		setProxyMode(3);
		assertThat(Settings.getRequestProxyMode(), is(3));

		cut.parseRequest("GET /p/fileid=foobar;passkey=notAkey/bazbaz HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(404));
	}

	@Test
	public void testParseRequestProxyNoPasskey() throws Exception {
		setProxyMode(3);
		assertThat(Settings.getRequestProxyMode(), is(3));

		cut.parseRequest("GET /p/fileid=foobar/bazbaz HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(404));
	}

	@Test
	public void testParseRequestProxyValidPasskey() throws Exception {
		setProxyMode(3);
		assertThat(Settings.getRequestProxyMode(), is(3));

		String passkey = cut.calculateProxyKey("foobar");

		cut.parseRequest("GET /p/fileid=foobar;passkey=" + passkey + "/bazbaz HTTP/1.1", true);

		assertThat(cut.getResponseStatusCode(), is(404));
	}
}
