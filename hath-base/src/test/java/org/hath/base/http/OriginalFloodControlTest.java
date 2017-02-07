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
package org.hath.base.http;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.net.InetAddress;

import org.junit.Before;
import org.junit.Test;

import com.google.common.net.InetAddresses;

public class OriginalFloodControlTest {
	private static final int FLOOD_HIT_LIMIT = 10;

	private static final InetAddress ADDRESS = InetAddresses.forString("42.42.42.42");

	private OriginalFloodControl cut;

	@Before
	public void setUp() throws Exception {
		cut = new OriginalFloodControl();
	}

	private void hitFloodControl(int times) {
		for (int i = 0; i < times; i++) {
			cut.shouldForceClose(ADDRESS);
		}
	}

	@Test
	public void testAddressBelowThreshold() throws Exception {
		assertThat(cut.shouldForceClose(ADDRESS), is(false));
	}

	@Test
	public void testBelowTriggerLimit() throws Exception {
		hitFloodControl(FLOOD_HIT_LIMIT - 1);

		assertThat(cut.shouldForceClose(ADDRESS), is(false));
	}

	@Test
	public void testTriggerFloodControl() throws Exception {
		hitFloodControl(FLOOD_HIT_LIMIT);

		assertThat(cut.shouldForceClose(ADDRESS), is(true));
	}

	@Test
	public void testBlockedAfterFloodTrigger() throws Exception {
		hitFloodControl(FLOOD_HIT_LIMIT);

		assertThat(cut.shouldForceClose(ADDRESS), is(true));
		assertThat(cut.shouldForceClose(ADDRESS), is(true));
	}

	/**
	 * This test checks that a call to {@link OriginalFloodControl#pruneTable()} does not throw an Exception.
	 */
	@Test
	public void testPruneTableDoesNotCrash() throws Exception {
		cut.pruneTable();
	}

	/**
	 * This test will take VERY long to run! Should probably be a integration test, if at all.
	 */
	@Test
	public void testConnectionAllowedAfterPrune() throws Exception {
		hitFloodControl(FLOOD_HIT_LIMIT);

		assertThat(cut.shouldForceClose(ADDRESS), is(true)); // guard assert

		Thread.sleep(65000);
		cut.pruneTable();

		assertThat(cut.shouldForceClose(ADDRESS), is(false));
	}
}
