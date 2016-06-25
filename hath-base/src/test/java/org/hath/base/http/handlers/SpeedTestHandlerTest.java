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

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.eclipse.jetty.http.HttpStatus;
import org.hath.base.Settings;
import org.junit.Before;
import org.junit.Test;

public class SpeedTestHandlerTest extends HandlerJunitTest {
	private SpeedTestHandler cut;

	@Before
	public void setUp() throws Exception {
		cut = new SpeedTestHandler();
	}

	@Test
	public void testParseRequestValid() throws Exception {
		int testSize = 42;
		int testTime = Settings.getServerTime();

		cut.handle("/" + testSize + "/" + testTime + "/" + SpeedTestHandler.calculateTestKey(testSize, testTime),
				baseRequest, request, response);

		verify(response, never()).setStatus(HttpStatus.OK_200);
	}

	@Test
	public void testParseRequestTestInvalidRequest() throws Exception {
		cut.handle("/", baseRequest, request, response);

		verify(response).setStatus(eq(HttpStatus.BAD_REQUEST_400));
	}

	@Test
	public void testParseRequestTestTimeDriftTooLarge() throws Exception {
		cut.handle("/42/5/foobar", baseRequest, request, response);

		verify(response).setStatus(HttpStatus.FORBIDDEN_403);
	}

	@Test
	public void testParseRequestTestInvalidKey() throws Exception {
		cut.handle("/42/" + Settings.getServerTime() + "/foobar", baseRequest, request, response);

		verify(response).setStatus(HttpStatus.FORBIDDEN_403);
	}
}
