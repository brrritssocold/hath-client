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

/**
 * Data storage class for blocked addresses.
 */
public class FloodControlEntry {
	private int connectCount;
	private long lastConnect;
	private long blocktime;

	/**
	 * Create a new {@link FloodControlEntry} with all fields set to 0;
	 */
	public FloodControlEntry() {
		this.connectCount = 0;
		this.lastConnect = 0;
		this.blocktime = 0;
	}

	/**
	 * Get the average number of connections in the last 5 seconds.
	 * 
	 * @return the connection count
	 */
	public int getConnectCount() {
		return connectCount;
	}

	/**
	 * Set the average number of connections in the last 5 seconds.
	 * 
	 * @param connectCount
	 *            the connection count
	 */
	public void setConnectCount(int connectCount) {
		this.connectCount = connectCount;
	}

	/**
	 * Get the time of the last connection.
	 * 
	 * @return the time in milliseconds
	 */
	public long getLastConnect() {
		return lastConnect;
	}

	/**
	 * Set the time of the last connection.
	 * 
	 * @param lastConnect
	 *            the time in milliseconds
	 */
	public void setLastConnect(long lastConnect) {
		this.lastConnect = lastConnect;
	}

	/**
	 * Get the time when the block expires.
	 * 
	 * @return the time in milliseconds
	 */
	public long getBlocktime() {
		return blocktime;
	}

	/**
	 * Set the time when the block expires.
	 * 
	 * @param blocktime
	 *            the time in milliseconds
	 */
	public void setBlocktime(long blocktime) {
		this.blocktime = blocktime;
	}
}
