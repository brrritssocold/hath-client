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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OriginalFloodControl {
	private static final Logger LOGGER = LoggerFactory.getLogger(OriginalFloodControl.class);

	private Hashtable<String, OriginalFloodControlEntry> floodControlTable;

	public OriginalFloodControl() {
		floodControlTable = new Hashtable<String, OriginalFloodControlEntry>();
	}

	public void pruneTable() {
		List<String> toPrune = Collections.checkedList(new ArrayList<String>(), String.class);

		synchronized (floodControlTable) {
			Enumeration<String> keys = floodControlTable.keys();

			while (keys.hasMoreElements()) {
				String key = keys.nextElement();
				if (floodControlTable.get(key).isStale()) {
					toPrune.add(key);
				}
			}

			for (String key : toPrune) {
				floodControlTable.remove(key);
			}
		}

		toPrune.clear();
		toPrune = null;
		System.gc();
	}

	public boolean shouldForceClose(InetAddress addr) {
		LOGGER.trace("Checking flood limit for {}", addr);
		// this flood control will stop clients from opening more than ten connections over a (roughly) five second
		// floating window, and forcibly block them for 60 seconds if they do.
		String hostAddress = addr.getHostAddress().toLowerCase();
		OriginalFloodControlEntry fce = null;
		boolean forceClose = false;

		synchronized (floodControlTable) {
			fce = floodControlTable.get(hostAddress);
			if (fce == null) {
				LOGGER.trace("No flood entry found for {}, creating...", addr);
				fce = new OriginalFloodControlEntry(addr);
				floodControlTable.put(hostAddress, fce);
			}
		}

		if (!fce.isBlocked()) {
			LOGGER.trace("{} is NOT blocked, checking hit limit...", addr);
			if (!fce.hit()) {
				LOGGER.warn("Flood control activated for {} (blocking for 60 seconds)", hostAddress);
				forceClose = true;
			}
		} else {
			LOGGER.trace("{} is blocked, closing connection.", addr);
			forceClose = true;
		}

		return forceClose;
	}
}
