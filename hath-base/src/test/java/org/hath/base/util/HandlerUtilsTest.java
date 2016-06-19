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

package org.hath.base.util;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import org.hath.base.http.handlers.HandlerJunitTest;
import org.junit.Test;

public class HandlerUtilsTest extends HandlerJunitTest {

	@Test
	public void testIsHandledStatusNull() throws Exception {
		assertThat(HandlerUtils.isHandledStatus(null), is("handled: null"));
	}

	@Test
	public void testIsHandledStatus() throws Exception {
		when(baseRequest.isHandled()).thenReturn(true);

		assertThat(HandlerUtils.isHandledStatus(baseRequest), is("handled: true"));
	}

	@Test
	public void testRequestStatusNull() throws Exception {
		assertThat(HandlerUtils.requestStatus(null), is("request: null"));
	}

	@Test
	public void testRequestStatus() throws Exception {
		when(request.toString()).thenReturn("foo");
		
		assertThat(HandlerUtils.requestStatus(request), is("request: foo"));
	}

	@Test
	public void testStatusCodeStatusNull() throws Exception {
		assertThat(HandlerUtils.statusCodeStatus(null), is("status code: null"));
	}

	@Test
	public void testStatusCodeStatus() throws Exception {
		when(response.getStatus()).thenReturn(42);

		assertThat(HandlerUtils.statusCodeStatus(response), is("status code: 42"));
	}

	@Test
	public void testHandlerStatus() throws Exception {
		when(response.getStatus()).thenReturn(42);
		when(request.toString()).thenReturn("foo");
		when(baseRequest.isHandled()).thenReturn(true);

		assertThat(HandlerUtils.handlerStatus(baseRequest, request, response),
				is("request: foo, handled: true, status code: 42"));
	}
}
