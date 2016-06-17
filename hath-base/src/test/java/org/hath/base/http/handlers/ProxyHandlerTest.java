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
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import java.net.InetAddress;

import org.hath.base.HVFile;
import org.hath.base.HentaiAtHomeClient;
import org.hath.base.Settings;
import org.hath.base.http.HTTPRequestAttributes.BooleanAttributes;
import org.hath.base.http.HTTPRequestAttributes.ClassAttributes;
import org.hath.base.http.HTTPRequestAttributes.IntegerAttributes;
import org.hath.base.http.handlers.ProxyHandler.Sensing;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;

import com.google.common.net.InetAddresses;

public class ProxyHandlerTest extends HandlerJunitTest {
	public static final String VALID_FILEID = "0000000000000000000000000000000000000000-0-0-0-jpg";
	public static final String VALID_TOKEN = "1-0000000000000000000000000000000000000000";
	private static final String CLIENT_IP = "110.120.130.140";
	private InetAddress client_address;

	private ProxyHandler cut;

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private HentaiAtHomeClient clientMock;

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private HVFile hvFileMock;

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private ResponseProcessorHandler sessionMock;

	@Before
	public void setUp() throws Exception {
		client_address = InetAddresses.forString(CLIENT_IP);
		addIpToRPC(client_address);
		setProxyMode(2);

		when(request.getAttribute(BooleanAttributes.LOCAL_NETWORK_ACCESS.toString())).thenReturn(true);
		when(request.getAttribute(ClassAttributes.HentaiAtHomeClient.toString())).thenReturn(clientMock);
		when(request.getAttribute(IntegerAttributes.SESSION_ID.toString())).thenReturn(2);
		when(clientMock.getCacheHandler().getHVFile(anyString(), anyBoolean())).thenReturn(hvFileMock);

		cut = new ProxyHandler(clientMock);
	}

	private void addIpToRPC(InetAddress ipAddress) {
		Settings.updateSetting("rpc_server_ip", ipAddress.getHostAddress());
	}

	private void setProxyMode(int mode) {
		Settings.updateSetting("request_proxy_mode", String.valueOf(mode));
	}

	private static void kVpair(StringBuilder sb, String key, String value, boolean isLastPair) {
		sb.append(key + "=" + value);

		if (!isLastPair) {
			sb.append(";");
		}
	}

	public static String buildProxyRequest(String fileid, String token, String gid, String page, String passkey,
			String filename) {
		// /p/fileid=asdf;token=asdf;gid=123;page=321;passkey=asdf/filename
		StringBuilder sb = new StringBuilder();

		sb.append("/");
		kVpair(sb, "fileid", fileid, false);
		kVpair(sb, "token", token, false);
		kVpair(sb, "gid", gid, false);

		if (passkey == null) {
			kVpair(sb, "page", page, true);
		} else {
			kVpair(sb, "page", page, false);
			kVpair(sb, "passkey", ProxyHandler.calculateProxyKey(fileid), true);
		}
		sb.append("/");
		sb.append(filename);

		return sb.toString();
	}

	private void assertSensingPoint(Sensing point) {
		assertThat(cut.sensingPointsHit, hasItem(point));
	}

	@Test
	public void testParseRequestProxyDisabled() throws Exception {
		setProxyMode(0);
		assertThat(Settings.getRequestProxyMode(), is(0));

		cut.handle("/", baseRequest, request, response);

		assertSensingPoint(Sensing.PROXY_REQUEST_DENIED);
	}

	@Test
	public void testParseRequestProxyValid() throws Exception {
		setProxyMode(1);
		assertThat(Settings.getRequestProxyMode(), is(1));

		cut.handle("/fileid=foobar/bazbaz", baseRequest, request, response);

		assertSensingPoint(Sensing.PROXY_REQUEST_GRANTED);
	}

	@Test
	public void testParseRequestProxyNotLocal() throws Exception {
		setProxyMode(2);
		assertThat(Settings.getRequestProxyMode(), is(2));

		cut.handle("/fileid=foobar/bazbaz", baseRequest, request, response);

		assertSensingPoint(Sensing.PROXY_REQUEST_PASSKEY_NOT_REQUIRED);
	}

	@Test
	public void testParseRequestProxyNotLocalRequestLocalOnly() throws Exception {
		setProxyMode(1);
		assertThat(Settings.getRequestProxyMode(), is(1));
		when(request.getAttribute(BooleanAttributes.LOCAL_NETWORK_ACCESS.toString())).thenReturn(false);

		cut.handle("/", baseRequest, request, response);

		assertSensingPoint(Sensing.PROXY_REQUEST_DENIED);
	}

	@Test
	public void testParseRequestProxyInvalidPasskey() throws Exception {
		setProxyMode(3);
		assertThat(Settings.getRequestProxyMode(), is(3));

		cut.handle("/fileid=foobar;passkey=notAkey/bazbaz", baseRequest, request, response);

		assertSensingPoint(Sensing.PROXY_REQUEST_PASSKEY_INVALID);
	}

	@Test
	public void testParseRequestProxyNoPasskey() throws Exception {
		setProxyMode(3);
		assertThat(Settings.getRequestProxyMode(), is(3));

		cut.handle("/fileid=foobar/bazbaz", baseRequest, request, response);

		assertSensingPoint(Sensing.PROXY_REQUEST_PASSKEY_INVALID);
	}

	@Test
	public void testParseRequestProxyValidPasskey() throws Exception {
		setProxyMode(3);
		assertThat(Settings.getRequestProxyMode(), is(3));

		String passkey = ProxyHandler.calculateProxyKey("foobar");

		cut.handle("/fileid=foobar;passkey=" + passkey + "/bazbaz", baseRequest, request, response);

		assertSensingPoint(Sensing.PROXY_REQUEST_PASSKEY_AS_EXPECTED);
	}

	@Test
	public void testParseRequestProxyValidRequestFileNotLocal() throws Exception {
		cut.handle(buildProxyRequest(VALID_FILEID, VALID_TOKEN, "42", "1", null, "foobar"), baseRequest, request,
				response);

		assertSensingPoint(Sensing.PROXY_REQUEST_PROXY_FILE);
	}

	@Test
	public void testParseRequestProxyValidRequestFileLocal() throws Exception {
		when(hvFileMock.getLocalFileRef().exists()).thenReturn(true);
		cut.handle(buildProxyRequest(VALID_FILEID, VALID_TOKEN, "42", "1", null, "foobar"), baseRequest, request,
				response);

		assertSensingPoint(Sensing.PROXY_REQUEST_LOCAL_FILE);
	}

	@Test
	public void testParseRequestProxyValidRequestFileLocalUseRegex() throws Exception {
		when(hvFileMock.getLocalFileRef().exists()).thenReturn(true);
		when(request.toString()).thenReturn(
				"Request(GET //localhost:42421/p/fileid=0000000000000000000000000000000000000000-0-0-0-jpg;token=1-0000000000000000000000000000000000000000;gid=42;page=1/foobar)@1234abcd");
		cut.handle(buildProxyRequest(VALID_FILEID, VALID_TOKEN, "42", "1", null, "foobar"), baseRequest, request,
				response);

		assertSensingPoint(Sensing.PROXY_REQUEST_LOCAL_FILE);
	}

	@Test
	public void testParseRequestProxyInvalidGid() throws Exception {
		cut.handle(buildProxyRequest(VALID_FILEID, VALID_TOKEN, "0", "1", null, "foobar"), baseRequest, request,
				response);

		assertSensingPoint(Sensing.PROXY_REQUEST_INVALID_GID_OR_PAGE);
	}

	@Test
	public void testParseRequestProxyInvalidPage() throws Exception {
		when(hvFileMock.getLocalFileRef().exists()).thenReturn(true);
		cut.handle(buildProxyRequest(VALID_FILEID, VALID_TOKEN, "42", "0", null, "foobar"), baseRequest, request,
				response);

		assertSensingPoint(Sensing.PROXY_REQUEST_INVALID_GID_OR_PAGE);
	}

	@Test
	public void testParseRequestProxyInvalidInteger() throws Exception {
		when(hvFileMock.getLocalFileRef().exists()).thenReturn(true);
		cut.handle(
				buildProxyRequest(VALID_FILEID, VALID_TOKEN, String.valueOf(Long.MAX_VALUE), "1", null, "foobar"),
				baseRequest, request, response);

		assertSensingPoint(Sensing.PROXY_REQUEST_INVALID_GID_OR_PAGE_INTEGERS);
	}
}
