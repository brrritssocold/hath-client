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

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

public class SessionTrackerTest {
	private static final long START_SESSIONS = 5;
	private static final int MAX_SESSIONS = 10;
	private static final double OVERLOAD_PERCENTAGE = 0.8;
	private static final String DUMMY_SESSION = "just a session";


	private SessionTracker<String> cut;
	private int sessionCounter;

	@Before
	public void setUp() throws Exception {
		cut = new SessionTracker<String>(3, TimeUnit.SECONDS, MAX_SESSIONS, OVERLOAD_PERCENTAGE);
		sessionCounter = 0;

		addNumberOfSessions(START_SESSIONS);
	}

	private void addNumberOfSessions(long numToAdd) {
		for (long i = 0; i < numToAdd; i++) {
			cut.add(sessionCounter++, DUMMY_SESSION);
		}
	}

	@Test
	public void testIsOverloadedBelow() throws Exception {
		assertThat(cut.isOverloaded(), is(false));
	}

	@Test
	public void testIsOverloadedOver() throws Exception {
		addNumberOfSessions(5);

		assertThat(cut.isOverloaded(), is(true));
	}

	@Test
	public void testIsOverloadedDisabledByOverloadPercentage() throws Exception {
		cut = new SessionTracker<String>(3, TimeUnit.SECONDS, MAX_SESSIONS, 0);
		addNumberOfSessions(5);

		assertThat(cut.isOverloaded(), is(false));
	}

	@Test
	public void testIsOverloadedDisabledByMaxsessions() throws Exception {
		cut = new SessionTracker<String>(3, TimeUnit.SECONDS, 0, OVERLOAD_PERCENTAGE);
		addNumberOfSessions(5);

		assertThat(cut.isOverloaded(), is(false));
	}

	@Test
	public void testRemove() throws Exception {
		cut.remove(0);

		assertThat(cut.isActive(0), is(false));
	}

	@Test
	public void testActiveSessions() throws Exception {
		assertThat(cut.activeSessions(), is(START_SESSIONS));
	}

	@Test
	public void testActiveSessionsAfterRemoval() throws Exception {
		cut.remove(0);

		assertThat(cut.activeSessions(), is(START_SESSIONS - 1));
	}

	@Test(timeout = 500)
	public void testSessionTimeout() throws Exception {
		cut = new SessionTracker<String>(1, TimeUnit.MILLISECONDS);

		cut.add(9, DUMMY_SESSION);

		while (cut.isActive(1)) {
			Thread.sleep(10);
		}
	}

	@Test
	public void testIsActive() throws Exception {
		assertThat(cut.isActive(0), is(true));
	}

	@Test
	public void testIsActiveNotInCache() throws Exception {
		assertThat(cut.isActive(100), is(false));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInvalidMaxSession() throws Exception {
		new SessionTracker<String>(3, TimeUnit.SECONDS, -1, 0.8);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInvalidOverloadPercentageNegative() throws Exception {
		new SessionTracker<String>(3, TimeUnit.SECONDS, 5, -1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInvalidOverloadPercentageOver100Percent() throws Exception {
		new SessionTracker<String>(3, TimeUnit.SECONDS, 5, 1.1);
	}

	@Test
	public void testIsMaxSessionsDisabled() throws Exception {
		assertThat(cut.isMaxSessionsDisabled(), is(false));
	}

	@Test
	public void testIsMaxSessionsDisabledWhenZero() throws Exception {
		cut = new SessionTracker<String>(3, TimeUnit.SECONDS, 0, OVERLOAD_PERCENTAGE);

		assertThat(cut.isMaxSessionsDisabled(), is(true));
	}

	@Test
	public void testIsOverloadPercentageDisabled() throws Exception {
		assertThat(cut.isOverloadPercentageDisabled(), is(false));
	}

	@Test
	public void testIsOverloadPercentageDisabledWhenZero() throws Exception {
		cut = new SessionTracker<String>(3, TimeUnit.SECONDS, MAX_SESSIONS, 0.0);

		assertThat(cut.isOverloadPercentageDisabled(), is(true));
	}

	@Test
	public void testIsMaxSessionReachedBelow() throws Exception {
		assertThat(cut.isMaxSessionReached(), is(false));
	}

	@Test
	public void testIsMaxSessionReachedOver() throws Exception {
		addNumberOfSessions(5);

		assertThat(cut.isMaxSessionReached(), is(true));
	}

	@Test
	public void testIsMaxSessionReachedDisabled() throws Exception {
		assertThat(cut.isMaxSessionReached(), is(false));
	}
}
