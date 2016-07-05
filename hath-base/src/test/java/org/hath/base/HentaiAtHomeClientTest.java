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

package org.hath.base;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import org.hath.base.event.ClientEvent;
import org.hath.base.event.ClientEvent.ClientEventType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import com.google.common.eventbus.Subscribe;

public class HentaiAtHomeClientTest {
	private HentaiAtHomeClient cut;
	private ClientEvent event;

	@Mock
	InputQueryHandler iqh;

	@Before
	public void setUp() throws Exception {
		cut = new HentaiAtHomeClient(iqh, new String[] {});
		cut.getEventBus().register(this);
		event = null;
	}

	@Subscribe
	public void clientEventListener(ClientEvent event) {
		this.event = event;
	}

	@Test
	public void testPostClientEventPosted() throws Exception {
		cut.postClientEvent(ClientEventType.SHUTDOWN);

		assertThat(event, is(notNullValue()));
	}

	@Test
	public void testPostClientEventType() throws Exception {
		cut.postClientEvent(ClientEventType.SHUTDOWN);

		assertThat(event.getType(), is(ClientEventType.SHUTDOWN));
	}
}
