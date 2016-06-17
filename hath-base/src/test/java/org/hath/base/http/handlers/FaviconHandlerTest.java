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

import static org.mockito.Mockito.verify;

import org.eclipse.jetty.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;

import com.google.common.net.HttpHeaders;

public class FaviconHandlerTest extends HandlerJunitTest {
	private FaviconHandler cut;

	@Before
	public void setUp() {
		cut = new FaviconHandler();
	}

	@Test
	public void testHeaderLocation() throws Exception {
		cut.handle(target, baseRequest, baseRequest, response);

		verify(response).setHeader(HttpHeaders.LOCATION, "http://g.e-hentai.org/favicon.ico");
	}

	@Test
	public void testReturnCode() throws Exception {
		cut.handle(target, baseRequest, baseRequest, response);

		verify(response).setStatus(HttpStatus.MOVED_PERMANENTLY_301);
	}

	@Test
	public void testContentType() throws Exception {
		cut.handle(target, baseRequest, baseRequest, response);

		verify(response).setContentType("text/html");
	}
}
