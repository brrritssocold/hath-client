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

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

/**
 * This flood control will stop clients from opening more than ten connections
 * over a (roughly) five second floating window, and forcibly block them for 60
 * seconds if they do.
 */
public class FloodControl implements IFloodControl {
	private static final Logger LOGGER = LoggerFactory.getLogger(FloodControl.class);

	private static final int ONE_SECOND_IN_MILLI = 1000;
	private static final int MAX_CONNECT_COUNT = 10;
	private static final int BLOCK_TIME_MILLI = 60000;

	private LoadingCache<String, FloodControlEntry> floodControlTable;

	private static class EntryValueLoader extends CacheLoader<String, FloodControlEntry> {
		private FloodControlEntryFactory factory;

		EntryValueLoader(FloodControlEntryFactory factory) {
			super();
			this.factory = factory;
		}

		@Override
		public FloodControlEntry load(String key) {
			return factory.create();
		}
	}

	/**
	 * Create a new {@link FloodControl} with the given timeout. The default {@link FloodControlEntryFactory} is used.
	 * 
	 * @param expireDuration
	 *            the timeout duration value
	 * @param timeUnit
	 *            the unit of the timeout duration
	 */
	public FloodControl(long expireDuration, TimeUnit timeUnit) {
		this(new FloodControlEntryFactory(), expireDuration, timeUnit);
	}

	/**
	 * Create a new {@link FloodControl} with the given timeout. Uses the supplied {@link FloodControlEntryFactory} to
	 * create new entries.
	 * 
	 * @param factory
	 *            for creating new {@link FloodControlEntry}
	 * @param expireDuration
	 *            the timeout duration value
	 * @param timeUnit
	 *            the unit of the timeout duration
	 */
	public FloodControl(FloodControlEntryFactory factory, long expireDuration, TimeUnit timeUnit) {
		this.floodControlTable = CacheBuilder.newBuilder().expireAfterAccess(expireDuration, timeUnit)
				.build(new EntryValueLoader(factory));
	}

	/**
	 * Check if the given address is currently blocked.
	 * 
	 * @param address
	 *            to check
	 * @return true if blocked
	 */
	public boolean isBlocked(String address) {
		FloodControlEntry entry = floodControlTable.getUnchecked(address);
		return isBlocked(entry);
	}

	private boolean isBlocked(FloodControlEntry entry) {
		return entry.getBlocktime() > System.currentTimeMillis();
	}

	/**
	 * Registers a request for the address
	 * 
	 * @param address
	 *            that initiated the request
	 * @return true if the request is allowed
	 */
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

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasExceededConnectionLimit(String address) {
		boolean forceClose = false;

		FloodControlEntry fce = floodControlTable.getUnchecked(address);

		if (!isBlocked(fce)) {
			if (!hit(fce)) {
				LOGGER.warn("Flood control activated for {} (blocking for 60 seconds)", address);
				forceClose = true;
			}
		} else {
			forceClose = true;
		}

		return forceClose;
	}

	/**
	 * This implementation prunes old entries automatically, however a prune can be force with this method.
	 */
	@Override
	public void pruneTable() {
		floodControlTable.cleanUp();
	}
}
