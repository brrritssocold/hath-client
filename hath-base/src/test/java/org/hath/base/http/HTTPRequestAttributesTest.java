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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;

import org.hath.base.http.HTTPRequestAttributes.BooleanAttributes;
import org.hath.base.http.HTTPRequestAttributes.IntegerAttributes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HTTPRequestAttributesTest {
	@Mock
	private HttpServletRequest request;

	@Test
	public void testGetAttributeNotSet() throws Exception {
		assertThat(HTTPRequestAttributes.getAttribute(request, BooleanAttributes.LOCAL_NETWORK_ACCESS), is(false));
	}

	@Test
	public void testGetAttributeSetFalse() throws Exception {
		when(request.getAttribute(BooleanAttributes.LOCAL_NETWORK_ACCESS.toString())).thenReturn(false);

		assertThat(HTTPRequestAttributes.getAttribute(request, BooleanAttributes.LOCAL_NETWORK_ACCESS), is(false));
	}

	@Test
	public void testGetAttributeSetTrue() throws Exception {
		when(request.getAttribute(BooleanAttributes.LOCAL_NETWORK_ACCESS.toString())).thenReturn(true);

		assertThat(HTTPRequestAttributes.getAttribute(request, BooleanAttributes.LOCAL_NETWORK_ACCESS), is(true));
	}

	@Test(expected = ClassCastException.class)
	public void testGetAttributeSetNotBoolean() throws Exception {
		when(request.getAttribute(BooleanAttributes.LOCAL_NETWORK_ACCESS.toString())).thenReturn("foobar");

		HTTPRequestAttributes.getAttribute(request, BooleanAttributes.LOCAL_NETWORK_ACCESS);
	}

	@Test
	public void testGetAttributeIntNotSet() throws Exception {
		assertThat(HTTPRequestAttributes.getAttribute(request, IntegerAttributes.SESSION_ID), is(0));
	}

	@Test
	public void testGetAttributeIntSet() throws Exception {
		when(request.getAttribute(IntegerAttributes.SESSION_ID.toString())).thenReturn(42);

		assertThat(HTTPRequestAttributes.getAttribute(request, IntegerAttributes.SESSION_ID), is(42));
	}

	@Test(expected = ClassCastException.class)
	public void testGetAttributeSetNotInteger() throws Exception {
		when(request.getAttribute(IntegerAttributes.SESSION_ID.toString())).thenReturn("foobar");

		HTTPRequestAttributes.getAttribute(request, IntegerAttributes.SESSION_ID);
	}
}
