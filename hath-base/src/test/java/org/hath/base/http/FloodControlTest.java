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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

public class FloodControlTest {
	private static final String TEST_ADDRESS = "foo";
	private FloodControl cut;

	private void hitAddress(String address, int hitNumber) {
		for (int i = 0; i < hitNumber; i++) {
			cut.hit(address);
		}
	}

	private FloodControlEntry setupMockedCutForPrune() {
		FloodControlEntryFactory mockFactory = mock(FloodControlEntryFactory.class);
		FloodControlEntry mockEntry = mock(FloodControlEntry.class);

		when(mockFactory.create()).thenReturn(mockEntry);

		cut = new FloodControl(mockFactory);
		return mockEntry;
	}

	@Before
	public void createFloodControl() throws Exception {
		cut = new FloodControl();
	}

	@Test
	public void testPruneFloodControlTable() throws Exception {
		FloodControlEntry mockEntry = setupMockedCutForPrune();
		when(mockEntry.isStale()).thenReturn(false);

		cut.addAddress(TEST_ADDRESS);
		cut.pruneFloodControlTable();

		assertThat(cut.addAddress(TEST_ADDRESS), is(false));
	}

	@Test
	public void testPruneFloodControlTableStaleAddress() throws Exception {
		FloodControlEntry mockEntry = setupMockedCutForPrune();
		when(mockEntry.isStale()).thenReturn(true);

		cut.addAddress(TEST_ADDRESS);
		cut.pruneFloodControlTable();

		assertThat(cut.addAddress(TEST_ADDRESS), is(true));
	}

	@Test
	public void testIsBlockedNewAddress() throws Exception {
		assertThat(cut.isBlocked(TEST_ADDRESS), is(false));
	}

	@Test
	public void testIsBlockedExistingAddress() throws Exception {
		assertThat(cut.isBlocked(TEST_ADDRESS), is(false));
		assertThat(cut.isBlocked(TEST_ADDRESS), is(false));
	}

	@Test
	public void testIsBlocked() throws Exception {
		hitAddress(TEST_ADDRESS, 20);

		assertThat(cut.isBlocked(TEST_ADDRESS), is(true));
	}

	@Test
	public void testHitOnce() throws Exception {
		assertThat(cut.hit(TEST_ADDRESS), is(true));
	}

	@Test
	public void testHitTwenty() throws Exception {
		hitAddress(TEST_ADDRESS, 19);
		assertThat(cut.hit(TEST_ADDRESS), is(false));
	}

	@Test
	public void testAddAddressNew() throws Exception {
		assertThat(cut.addAddress(TEST_ADDRESS), is(true));
	}

	@Test
	public void testAddAddressExisting() throws Exception {
		assertThat(cut.addAddress(TEST_ADDRESS), is(true));
		assertThat(cut.addAddress(TEST_ADDRESS), is(false));
	}

	@Test
	public void testHasExceededConnectionLimit() throws Exception {
		assertThat(cut.hasExceededConnectionLimit(TEST_ADDRESS), is(false));
	}

	@Test
	public void testHasExceededConnectionLimitTooManyHits() throws Exception {
		hitAddress(TEST_ADDRESS, 19);

		assertThat(cut.hasExceededConnectionLimit(TEST_ADDRESS), is(true));
		assertThat(cut.isSenseFloodMessageTrigger(), is(false));
	}

	@Test
	public void testHasExceededConnectionLimitTriggerFloodControlMessage() throws Exception {
		hitAddress(TEST_ADDRESS, 10);

		assertThat(cut.hasExceededConnectionLimit(TEST_ADDRESS), is(true));
		assertThat(cut.isSenseFloodMessageTrigger(), is(true));
	}
}
