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

package hath.base.http;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import hath.base.HentaiAtHomeClient;
import hath.base.http.HTTPRequestAttributes.BooleanAttributes;
import hath.base.http.HTTPRequestAttributes.ClassAttributes;
import hath.base.http.HTTPRequestAttributes.IntegerAttributes;
import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
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

	@Test
	public void testGetAttributeSetNotBoolean() throws Exception {
		when(request.getAttribute(BooleanAttributes.LOCAL_NETWORK_ACCESS.toString())).thenReturn("foobar");

		assertThrows(ClassCastException.class, () -> HTTPRequestAttributes.getAttribute(request, BooleanAttributes.LOCAL_NETWORK_ACCESS));
	}

	@Test
	public void testGetAttributeIntNotSet() throws Exception {
		assertThat(HTTPRequestAttributes.getAttribute(request, IntegerAttributes.SESSION_ID), is(-1));
	}

	@Test
	public void testGetAttributeIntSet() throws Exception {
		when(request.getAttribute(IntegerAttributes.SESSION_ID.toString())).thenReturn(42);

		assertThat(HTTPRequestAttributes.getAttribute(request, IntegerAttributes.SESSION_ID), is(42));
	}

	@Test
	public void testGetAttributeSetNotInteger() throws Exception {
		when(request.getAttribute(IntegerAttributes.SESSION_ID.toString())).thenReturn("foobar");

		assertThrows(ClassCastException.class, () -> HTTPRequestAttributes.getAttribute(request, IntegerAttributes.SESSION_ID));
	}

	@Test
	public void testGetResponseProcessorSet() throws Exception {
		HTTPResponseProcessor mockProcessor = mock(HTTPResponseProcessor.class);
		when(request.getAttribute(ClassAttributes.HTTPResponseProcessor.toString())).thenReturn(mockProcessor);
		
		HTTPResponseProcessor result = HTTPRequestAttributes.getResponseProcessor(request);

		assertThat(result, is(mockProcessor));
	}

	@Test
	public void testGetResponseProcessorNotSet() throws Exception {
		HTTPResponseProcessor result = HTTPRequestAttributes.getResponseProcessor(request);

		assertThat(result, nullValue());
	}

	@Test
	public void testGetClientSet() throws Exception {
		HentaiAtHomeClient clientMock = mock(HentaiAtHomeClient.class);
		when(request.getAttribute(ClassAttributes.HentaiAtHomeClient.toString())).thenReturn(clientMock);

		HentaiAtHomeClient result = HTTPRequestAttributes.getClient(request);

		assertThat(result, is(clientMock));
	}

	@Test
	public void testGetClientNotSet() throws Exception {
		HentaiAtHomeClient result = HTTPRequestAttributes.getClient(request);

		assertThat(result, nullValue());
	}

	@Test
	public void testSetResponseProcessor() throws Exception {
		HTTPResponseProcessor processorMock = mock(HTTPResponseProcessor.class);

		HTTPRequestAttributes.setResponseProcessor(request, processorMock);

		verify(request).setAttribute(ClassAttributes.HTTPResponseProcessor.toString(), processorMock);
	}

	@Test
	public void testSetClient() throws Exception {
		HentaiAtHomeClient clientMock = mock(HentaiAtHomeClient.class);
		HTTPRequestAttributes.setClient(request, clientMock);

		verify(request).setAttribute(ClassAttributes.HentaiAtHomeClient.toString(), clientMock);
	}
}
