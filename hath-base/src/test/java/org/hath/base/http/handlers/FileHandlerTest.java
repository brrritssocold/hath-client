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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jetty.http.HttpStatus;
import org.hath.base.CacheHandler;
import org.hath.base.HVFile;
import org.hath.base.HentaiAtHomeClient;
import org.hath.base.Settings;
import org.hath.base.event.RequestEvent;
import org.hath.base.event.RequestType;
import org.hath.base.http.HTTPRequestAttributes.BooleanAttributes;
import org.hath.base.http.HTTPRequestAttributes.ClassAttributes;
import org.hath.base.http.HTTPRequestAttributes.IntegerAttributes;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

public class FileHandlerTest extends HandlerJunitTest {
	private FileHandler cut;
	private EventBus eventBus;
	private List<RequestEvent> events;

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private ResponseProcessorHandler sessionMock;

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private HVFile hvFileMock;

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private HentaiAtHomeClient clientMock;

	@Mock
	private CacheHandler cacheHandlerMock;

	@Before
	public void setUp() throws Exception {
		when(request.getAttribute(BooleanAttributes.LOCAL_NETWORK_ACCESS.toString())).thenReturn(true);
		when(request.getAttribute(ClassAttributes.HentaiAtHomeClient.toString())).thenReturn(clientMock);
		when(request.getAttribute(IntegerAttributes.SESSION_ID.toString())).thenReturn(2);
		when(cacheHandlerMock.getHVFile(anyString(), anyBoolean())).thenReturn(hvFileMock);

		events = new LinkedList<RequestEvent>();
		eventBus = new EventBus();
		eventBus.register(this);
		cut = new FileHandler(cacheHandlerMock, eventBus);
	}

	@Subscribe
	private void requestEventListner(RequestEvent event){
		events.add(event);
	}

	private String generateKeystamp(String hvfile, int timeOffset) {
		int currentTime = Settings.getServerTime() + timeOffset;
		StringBuilder sb = new StringBuilder();

		sb.append("keystamp=");
		sb.append(currentTime);
		sb.append("-");
		sb.append(FileHandler.calculateKeystamp(hvfile, currentTime));

		return sb.toString();
	}
	
	private String generateKeystamp(String hvfile) {
		return generateKeystamp(hvfile, 0);
	}

	private void addToStaticRange(String fileid) {
		Settings.updateSetting("static_ranges", fileid.substring(0, 4));
	}

	@Test
	public void testParseRequestFileInvalidKeystamp() throws Exception {
		cut.handle("/foo/keystamp=derp/baz", baseRequest, request, response);

		verify(response).setStatus(HttpStatus.FORBIDDEN_403);
	}

	@Test
	public void testParseRequestFileInvalidKeystampTimeDriftTooBig() throws Exception {
		cut.handle("/foo/" + generateKeystamp("foo", 2000) + "/baz", baseRequest, request, response);

		verify(response).setStatus(HttpStatus.FORBIDDEN_403);
	}

	@Test
	public void testParseRequestFileInvalidKeystampHashMismatch() throws Exception {
		cut.handle("/foo/" + generateKeystamp("foo") + "a" + "/baz", baseRequest, request, response);

		verify(response).setStatus(HttpStatus.FORBIDDEN_403);
	}

	@Test
	public void testParseRequestFileInvalidKeystampTimeTooLarge() throws Exception {
		cut.handle("/foo/keystamp=" + Long.MAX_VALUE + "-foobar/baz", baseRequest, request, response);

		verify(response).setStatus(HttpStatus.FORBIDDEN_403);
	}

	@Test
	public void testParseRequestFileHVFileNull() throws Exception {
		when(cacheHandlerMock.getHVFile(anyString(), anyBoolean())).thenReturn(null);

		cut.handle("/foo/" + generateKeystamp("foo") + "/baz", baseRequest, request, response);

		verify(response).setStatus(HttpStatus.NOT_FOUND_404);
	}

	@Test
	public void testParseRequestFileHVFileExists() throws Exception {
		when(hvFileMock.getLocalFileRef().exists()).thenReturn(true);

		cut.handle("/foo/" + generateKeystamp("foo") + "/baz", baseRequest, request, response);

		verify(response, never()).setStatus(anyInt());
	}

	@Test
	public void testParseRequestHVFileExistsExternalRequest() throws Exception {
		when(request.getAttribute(BooleanAttributes.LOCAL_NETWORK_ACCESS.toString())).thenReturn(true);
		when(hvFileMock.getLocalFileRef().exists()).thenReturn(true);
		cut.handle("/foo/" + generateKeystamp("foo") + "/baz", baseRequest, request, response);

		verify(response, never()).setStatus(anyInt());
	}

	@Test
	public void testParseRequestFileNotFound() throws Exception {
		when(hvFileMock.getLocalFileRef().exists()).thenReturn(false);
		when(hvFileMock.getFileid()).thenReturn("bazbaz");

		cut.handle("/bazbaz/" + generateKeystamp("bazbaz") + "/baz", baseRequest, request, response);

		verify(response).setStatus(HttpStatus.NOT_FOUND_404);
	}

	@Test
	public void testParseRequestFileStaticRangeIdNotInTokens() throws Exception {
		String fileid = "foobar";
		when(hvFileMock.getLocalFileRef().exists()).thenReturn(false);
		when(hvFileMock.getFileid()).thenReturn(fileid);
		when(clientMock.getServerHandler().getFileTokens(any())).thenReturn(new Hashtable<>());
		addToStaticRange(fileid);

		cut.handle("/" + fileid + "/" + generateKeystamp(fileid) + "/baz", baseRequest, request,
				response);

		verify(response).setStatus(HttpStatus.NOT_FOUND_404);
	}

	@Test
	public void testParseRequestFileStaticRange() throws Exception {
		String fileid = "foobar";

		Hashtable<String, String> tokens = new Hashtable<>();
		tokens.put(fileid, "1");

		when(hvFileMock.getLocalFileRef().exists()).thenReturn(false);
		when(hvFileMock.getFileid()).thenReturn(fileid);
		when(clientMock.getServerHandler().getFileTokens(any())).thenReturn(tokens);
		addToStaticRange(fileid);

		cut.handle("/" + fileid + "/" + generateKeystamp(fileid) + "/baz", baseRequest, request,
				response);

		verify(response, never()).setStatus(anyInt());
	}

	@Test
	public void testParseRequestFileTooShort() throws Exception {
		cut.handle("/foo", baseRequest, request, response);

		verify(response).setStatus(400);
	}

	@Test
	public void testFileRequestEventCount() throws Exception {
		when(hvFileMock.getLocalFileRef().exists()).thenReturn(true);

		cut.handle("/foo/" + generateKeystamp("foo") + "/baz", baseRequest, request, response);

		assertThat(events.size(), is(1));
	}

	@Test
	public void testFileRequestEventType() throws Exception {
		when(hvFileMock.getLocalFileRef().exists()).thenReturn(true);

		cut.handle("/foo/" + generateKeystamp("foo") + "/baz", baseRequest, request, response);

		RequestEvent event = events.get(0);
		assertThat(event.getRequestType(), is(RequestType.H));
	}

	@Test
	public void testFileRequestEventRequest() throws Exception {
		when(hvFileMock.getLocalFileRef().exists()).thenReturn(true);
		target = "/foo/" + generateKeystamp("foo") + "/baz";

		cut.handle(target, baseRequest, request, response);

		RequestEvent event = events.get(0);
		assertThat(event.getRequest(), is(target));
	}

	@Test
	public void testFileRequestEventAdditionalsSize() throws Exception {
		when(hvFileMock.getLocalFileRef().exists()).thenReturn(true);

		cut.handle("/foo/" + generateKeystamp("foo") + "/baz", baseRequest, request, response);

		RequestEvent event = events.get(0);
		assertThat(event.getAdditionals().size(), is(1));
	}

	@Test
	public void testFileRequestEventStaticRange() throws Exception {
		String fileid = "foobar";

		Hashtable<String, String> tokens = new Hashtable<>();
		tokens.put(fileid, "1");

		when(hvFileMock.getLocalFileRef().exists()).thenReturn(false);
		when(hvFileMock.getFileid()).thenReturn(fileid);
		when(clientMock.getServerHandler().getFileTokens(any())).thenReturn(tokens);
		addToStaticRange(fileid);

		cut.handle("/" + fileid + "/" + generateKeystamp(fileid) + "/baz", baseRequest, request, response);

		assertThat(events.size(), is(1));
	}
}
