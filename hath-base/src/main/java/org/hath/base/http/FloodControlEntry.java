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

public class FloodControlEntry {
	private int connectCount;
	private long lastConnect;
	private long blocktime;

	public FloodControlEntry() {
		this.connectCount = 0;
		this.lastConnect = 0;
		this.blocktime = 0;
	}

	public boolean isStale() {
		return lastConnect < System.currentTimeMillis() - 60000;
	}

	public boolean isBlocked() {
		return blocktime > System.currentTimeMillis();
	}

	public boolean hit() {
		long nowtime = System.currentTimeMillis();
		connectCount = Math.max(0, connectCount - (int) Math.floor((nowtime - lastConnect) / 1000)) + 1;
		lastConnect = nowtime;

		if (connectCount > 10) {
			blocktime = nowtime + 60000; // block this client from connecting
											// for 60 seconds
			return false;
		} else {
			return true;
		}
	}
}
