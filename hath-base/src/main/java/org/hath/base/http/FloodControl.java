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

import java.util.concurrent.TimeUnit;

import org.hath.base.Out;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

/**
 * This flood control will stop clients from opening more than ten connections
 * over a (roughly) five second floating window, and forcibly block them for 60
 * seconds if they do.
 */
public class FloodControl implements IFloodControl {
	private static final int ONE_SECOND_IN_MILLI = 1000;
	private static final int MAX_CONNECT_COUNT = 10;
	private static final int BLOCK_TIME_MILLI = 60000;

	private LoadingCache<String, FloodControlEntry> floodControlTable;
	private boolean senseFloodMessageTrigger = false;

	private static class EntryValueLoader extends CacheLoader<String, FloodControlEntry> {
		private FloodControlEntryFactory factory;

		public EntryValueLoader(FloodControlEntryFactory factory) {
			super();
			this.factory = factory;
		}

		@Override
		public FloodControlEntry load(String key) {
			return factory.create();
		}
	}

	public FloodControl(long expireDuration, TimeUnit timeUnit) {
		this(new FloodControlEntryFactory(), expireDuration, timeUnit);
	}

	public FloodControl(FloodControlEntryFactory factory, long expireDuration, TimeUnit timeUnit) {
		this.floodControlTable = CacheBuilder.newBuilder().expireAfterAccess(expireDuration, timeUnit)
				.build(new EntryValueLoader(factory));
	}

	public boolean isBlocked(String address) {
		FloodControlEntry entry = floodControlTable.getUnchecked(address);
		return isBlocked(entry);
	}

	private boolean isBlocked(FloodControlEntry entry) {
		return entry.getBlocktime() > System.currentTimeMillis();
	}

	public boolean hit(String address) {
		FloodControlEntry entry = floodControlTable.getUnchecked(address);
		return hit(entry);
	}

	private boolean hit(FloodControlEntry entry) {
		long nowtime = System.currentTimeMillis();
		int connectCount = Math.max(0,
				entry.getConnectCount() - (int) Math.floor((nowtime - entry.getLastConnect()) / ONE_SECOND_IN_MILLI)) + 1;

		entry.setConnectCount(connectCount);
		entry.setLastConnect(nowtime);

		if (connectCount > MAX_CONNECT_COUNT) {
			entry.setBlocktime(nowtime + BLOCK_TIME_MILLI);
			return false;
		} else {
			return true;
		}
	}

	@Override
	public boolean hasExceededConnectionLimit(String address) {
		boolean forceClose = false;
		senseFloodMessageTrigger = false;

		FloodControlEntry fce = floodControlTable.getUnchecked(address);

		if (!isBlocked(fce)) {
			if (!hit(fce)) {
				Out.warning("Flood control activated for  " + address + " (blocking for 60 seconds)");
				forceClose = true;
				senseFloodMessageTrigger = true;
			}
		} else {
			forceClose = true;
		}

		return forceClose;
	}

	/**
	 * Method for testing
	 * 
	 * @return true if the flood message has been triggered
	 */
	public boolean isSenseFloodMessageTrigger() {
		return senseFloodMessageTrigger;
	}

	@Override
	public void pruneTable() {
		floodControlTable.cleanUp();
	}
}
