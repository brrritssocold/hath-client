/*

Copyright 2008-2016 E-Hentai.org
https://forums.e-hentai.org/
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

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class IFloodControlTest {
	private static final int FLOOD_HIT_LIMIT = 10;
	public static final long TABLE_PRUNE_WAIT_MILLI = 1000;

	private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);
	private static final String ADDRESS = "42.42.42.42";

	private IFloodControl cut;

	@BeforeEach
	public void setUp() throws Exception {
		cut = getCutInstance();
	}

	/**
	 * Provides a new instance of the class under test for each test.
	 * 
	 * @return a new instance for a test
	 */
	protected abstract IFloodControl getCutInstance();

	private void hitFloodControl(int times) {
		for (int i = 0; i < times; i++) {
			cut.hasExceededConnectionLimit(ADDRESS);
		}
	}

	@Test
	public void testAddressBelowThreshold() throws Exception {
		assertThat(cut.hasExceededConnectionLimit(ADDRESS), is(false));
	}

	@Test
	public void testBelowTriggerLimit() throws Exception {
		hitFloodControl(FLOOD_HIT_LIMIT - 1);

		assertThat(cut.hasExceededConnectionLimit(ADDRESS), is(false));
	}

	@Test
	public void testTriggerFloodControl() throws Exception {
		hitFloodControl(FLOOD_HIT_LIMIT);

		assertThat(cut.hasExceededConnectionLimit(ADDRESS), is(true));
	}

	@Test
	public void testBlockedAfterFloodTrigger() throws Exception {
		hitFloodControl(FLOOD_HIT_LIMIT);

		assertThat(cut.hasExceededConnectionLimit(ADDRESS), is(true));
		assertThat(cut.hasExceededConnectionLimit(ADDRESS), is(true));
	}

	/**
	 * This test checks that a call to {@link OriginalFloodControl#pruneTable()} does not throw an Exception.
	 */
	@Test
	public void testPruneTableDoesNotCrash() throws Exception {
		cut.pruneTable();
	}

	@Test
	public void testConnectionAllowedAfterBlockExpires() throws Exception {
		hitFloodControl(FLOOD_HIT_LIMIT);

		assertThat(cut.hasExceededConnectionLimit(ADDRESS), is(true)); // guard assert

		await().atMost(TEST_TIMEOUT).pollDelay(TABLE_PRUNE_WAIT_MILLI, TimeUnit.MILLISECONDS)
				.untilAsserted(() -> assertFalse(cut.hasExceededConnectionLimit(ADDRESS)));
	}
}
