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

import org.junit.Before;
import org.junit.Test;

public class UnhandledSessionHandlerTest extends HandlerJunitTest {
	private static final int TEST_STATUS = 42;
	private UnhandledSessionHandler cut;

	@Before
	public void setUp() throws Exception {
		cut = new UnhandledSessionHandler(TEST_STATUS);
	}

	@Test
	public void testHandleStatus() throws Exception {
		cut.handle(target, baseRequest, request, response);

		verify(response).setStatus(TEST_STATUS);
	}

	@Test
	public void testHandleIsHandled() throws Exception {
		cut.handle(target, baseRequest, request, response);

		verify(baseRequest).setHandled(true);
	}
}
