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

import org.eclipse.jetty.client.HttpClient;
import org.junit.Test;

public class SettingsTest {
	@Test
	public void testGetHttpClientNotNull() throws Exception {
		HttpClient client = Settings.getHttpClient();

		assertThat(client, is(notNullValue()));
	}

	@Test
	public void testGetHttpClientRunning() throws Exception {
		HttpClient client = Settings.getHttpClient();

		assertThat(client.isRunning(), is(true));
	}

	@Test
	public void testGetHttpClientUserStoppedClient() throws Exception {
		Settings.getHttpClient().stop();

		HttpClient client = Settings.getHttpClient();

		assertThat(client.isRunning(), is(true));
	}
}
