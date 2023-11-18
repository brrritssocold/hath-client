/*

Copyright 2008-2023 E-Hentai.org
https://forums.e-hentai.org/
tenboro@e-hentai.org

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

package hath.base;

import java.lang.Thread;

public class HTTPBandwidthMonitor {
	private final int TIME_RESOLUTION = 50;
	private final int WINDOW_LENGTH = 5;
	private int millisPerTick, bytesPerTick;
	private int[] tickBytes, tickSeconds;

	public HTTPBandwidthMonitor() {
		bytesPerTick = (int) Math.ceil(Settings.getThrottleBytesPerSec() / TIME_RESOLUTION);
		millisPerTick = (int) (1000 / TIME_RESOLUTION);
		tickBytes = new int[TIME_RESOLUTION];
		tickSeconds = new int[TIME_RESOLUTION];
	}

	public synchronized void waitForQuota(Thread thread, int bytecount) {
		do {
			long now = System.currentTimeMillis();
			long epochSeconds = (long) (now / 1000);
			int currentTick = (int) ((now - epochSeconds * 1000) / millisPerTick);
			int currentSecond = (int) epochSeconds;
			int bytesThisTick = 0, bytesLastWindow = 0, bytesLastSecond = 0;
			int tickCounter = currentTick - TIME_RESOLUTION;
			int tickIndex, validSecond;

			while(++tickCounter <= currentTick) {
				tickIndex = tickCounter < 0 ? TIME_RESOLUTION + tickCounter : tickCounter;
				validSecond = tickCounter < 0 ? currentSecond - 1 : currentSecond;

				if(tickSeconds[tickIndex] == validSecond) {
					if(tickCounter == currentTick) {
						bytesThisTick += tickBytes[tickIndex];
					}
					else {
						if(tickCounter >= currentTick - WINDOW_LENGTH) {
							bytesLastWindow += tickBytes[tickIndex];
						}

						// technically, 49/50ths of a second
						bytesLastSecond += tickBytes[tickIndex];
					}
				}
			}

			if(bytesThisTick > bytesPerTick * 1.1 || bytesLastWindow > bytesPerTick * WINDOW_LENGTH * 1.05 || bytesLastSecond > bytesPerTick * TIME_RESOLUTION) {
				//Out.debug("sleeping with currentTick=" + currentTick + " second=" + currentSecond + " bytesPerTick=" + bytesPerTick + " bytesThisTick=" + bytesThisTick + " bytesLastWindow=" + bytesLastWindow + " bytesLastSecond=" + bytesLastSecond);

				try {
					thread.sleep(10);
				} catch(Exception e) {
					e.printStackTrace();
				}

				continue;
			}

			//Out.debug("granted with currentTick=" + currentTick + " second=" + currentSecond + " bytesPerTick=" + bytesPerTick + " bytesThisTick=" + bytesThisTick + " bytesLastWindow=" + bytesLastWindow + " bytesLastSecond=" + bytesLastSecond);

			if(tickSeconds[currentTick] != currentSecond) {
				tickSeconds[currentTick] = currentSecond;
				tickBytes[currentTick] = 0;
			}

			tickBytes[currentTick] += bytecount;

			break;
		} while(true);
	}
}
