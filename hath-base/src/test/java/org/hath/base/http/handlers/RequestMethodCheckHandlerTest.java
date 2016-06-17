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

import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;

public class RequestMethodCheckHandlerTest extends HandlerJunitTest {
	private RequestMethodCheckHandler cut;

	@Before
	public void setUp() throws Exception {
		cut = new RequestMethodCheckHandler(HttpMethod.GET, HttpMethod.HEAD);
	}

	@Test
	public void testPostRequest() throws Exception {
		when(request.getMethod()).thenReturn(HttpMethod.POST.toString());

		cut.handle(target, baseRequest, request, response);

		verify(response).setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
	}

	@Test
	public void testTraceRequest() throws Exception {
		when(request.getMethod()).thenReturn(HttpMethod.TRACE.toString());

		cut.handle(target, baseRequest, request, response);

		verify(response).setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
	}

	@Test
	public void testGetRequest() throws Exception {
		when(request.getMethod()).thenReturn(HttpMethod.GET.toString());

		cut.handle(target, baseRequest, request, response);

		verify(response, never()).setStatus(anyInt());
	}

	@Test
	public void testHeadRequest() throws Exception {
		when(request.getMethod()).thenReturn(HttpMethod.HEAD.toString());

		cut.handle(target, baseRequest, request, response);

		verify(response, never()).setStatus(anyInt());
	}
}
