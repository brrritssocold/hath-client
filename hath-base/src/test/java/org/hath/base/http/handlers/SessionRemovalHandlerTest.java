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

import static org.mockito.Mockito.when;

import org.hath.base.http.HTTPRequestAttributes.IntegerAttributes;
import org.hath.base.http.SessionTracker;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class SessionRemovalHandlerTest extends HandlerJunitTest {
	private static final int SESSION_ID = 42;
	private SessionRemovalHandler cut;

	@Mock
	private SessionTracker sessionTrackerMock;
	
	@Before
	public void setUp() throws Exception {
		when(request.getAttribute(IntegerAttributes.SESSION_ID.toString())).thenReturn(SESSION_ID);

		cut = new SessionRemovalHandler(sessionTrackerMock);
	}

	@Test
	public void testHandleRemoveSession() throws Exception {
		cut.handle(target, baseRequest, request, response);
	}

	@Test
	public void testHandleNoSession() throws Exception {
		when(request.getAttribute(IntegerAttributes.SESSION_ID.toString())).thenReturn(null);

		cut.handle(target, baseRequest, request, response);
	}
}
