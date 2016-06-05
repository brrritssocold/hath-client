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

import org.eclipse.jetty.server.Request;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * This class is used as a light weight session tracker.
 */
public class SessionTracker {
	TimeUnit timeoutUnit;
	long timeoutValue;

	long maxSessions;
	double overloadPercentage;
	Cache<Integer, Request> sessionCache;

	/**
	 * Create a {@link SessionTracker} with the given Timeout. Sets a limit for
	 * active sessions and overload percentage.
	 * 
	 * @param timeout
	 *            time value to use timeout
	 * @param unit
	 *            the corresponding unit for the timeout
	 * @param maxSessions
	 *            the maximum allowed active sessions, 0 disables the limit
	 * @param overloadPercentage
	 *            at what percentage of maxSessions
	 *            {@link SessionTracker#isOverloaded()} returns true, if 0
	 *            {@link SessionTracker#isOverloaded()} will always return false
	 */
	public SessionTracker(long timeout, TimeUnit unit, long maxSessions, double overloadPercentage) {
		this.timeoutValue = timeout;
		this.timeoutUnit = unit;
		this.maxSessions = maxSessions;
		this.overloadPercentage = overloadPercentage;

		if (maxSessions < 0) {
			throw new IllegalArgumentException("Maximum sessions must be 0 or greater");
		}

		if ((overloadPercentage < 0) || overloadPercentage > 1.0) {
			throw new IllegalArgumentException("Overload percentage must be betwenn 0.0 and 1.0 (Disabled to 100%)");
		}

		sessionCache = CacheBuilder.newBuilder().build();
	}


	/**
	 * Create a {@link SessionTracker} with the given Timeout. No other limits
	 * are set.
	 */
	public SessionTracker(long timeout, TimeUnit unit) {
		this(timeout, unit, 0, 0);
	}

	/**
	 * Check if current active sessions are above the specified percentage.
	 * 
	 * @return false if below limit, or if no limit is set
	 */
	public boolean isOverloaded() {
		if (isOverloadPercentageDisabled() || isMaxSessionsDisabled()) {
			return false;
		}

		return (maxSessions * overloadPercentage <= activeSessions());
	}

	/**
	 * Add a request to the Session cache.
	 * 
	 * @param sessionId
	 *            id of the session
	 * @param request
	 *            the session corresponds to
	 */
	public void add(int sessionId, Request request) {
		sessionCache.put(sessionId, request);
	}

	/**
	 * Remove the session from the cache.
	 * 
	 * @param sessionId
	 *            session to remove
	 */
	public void remove(int sessionId) {
		sessionCache.invalidate(sessionId);
	}

	/**
	 * Get the approximate number of currently active sessions.
	 * 
	 * @return the approximate number of active sessions
	 */
	public long activeSessions() {
		return sessionCache.size();
	}

	/**
	 * Check if the given session is still active.
	 * 
	 * @param sessionId
	 *            to check
	 * @return true if still active
	 */
	public boolean isActive(int sessionId) {
		return (sessionCache.getIfPresent(sessionId) != null);
	}

	/**
	 * Check if maxSession is disabled (set to 0).
	 * 
	 * @return true if disabled
	 */
	public boolean isMaxSessionsDisabled() {
		return maxSessions == 0L;
	}

	/**
	 * Check if overloadPercentage is disabled (set to 0.0).
	 * 
	 * @return true if disabled
	 */
	public boolean isOverloadPercentageDisabled() {
		return overloadPercentage == 0.0;
	}

	/**
	 * Check if the maximum number of sessions has been reached. Since the
	 * active session size is approximate, this might not always be accurate.
	 * 
	 * @return true if the session limit has been reached or if maxSessions is
	 *         disabled (0)
	 */
	public boolean isMaxSessionReached() {
		return ((!isMaxSessionsDisabled()) && (activeSessions() >= maxSessions));
	}
}
