/*

Copyright 2008-2012 E-Hentai.org
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

import org.hath.base.Settings;

public class HTTPBandwidthMonitor {
	private int sleepTrigger;

	public HTTPBandwidthMonitor() {
		sleepTrigger = 0;
	}
	
	private double getMinMillisPerPacket() {
		return Settings.getThrottleBytesPerSec() > 0 ? (1000.0 * getActualPacketSize() / (double) Settings.getThrottleBytesPerSec()) : 0.0;
	}
	
	public synchronized void synchronizedWait() {
		long sleepTime = Math.round(getMinMillisPerPacket() * ++sleepTrigger + (Math.random() - 0.5));
		//System.out.println(sleepTime);
		
		if(sleepTime > 2) {
			sleepTrigger = 0;
			
			try {
				Thread.sleep(sleepTime);
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}

	// accessors
	public int getActualPacketSize() {
		if(Settings.getThrottleBytesPerSec() == 0 || Settings.getThrottleBytesPerSec() >= 15000) {
			return Settings.TCP_PACKET_SIZE_HIGH;
		}
		else {
			return Settings.TCP_PACKET_SIZE_LOW;
		}
	}	
}
