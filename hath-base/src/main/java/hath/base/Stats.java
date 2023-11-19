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

// convenience class for the GUI

import java.util.List;
import java.util.ArrayList;

public class Stats {

	private static List<StatListener> statListeners;

	private static boolean clientRunning, clientSuspended;
	private static String programStatus;
	private static long clientStartTime;
	private static int filesSent, filesRcvd;
	private static long bytesSent, bytesRcvd;
	private static int cacheCount;
	private static long cacheSize;
	private static int[] bytesSentHistory;
	private static int openConnections;
	private static int lastServerContact;

	static {
		statListeners = new ArrayList<StatListener>();
		resetStats();
	}
	
	public static void trackBytesSentHistory() {
		bytesSentHistory = new int[361];
	}

	public static void addStatListener(StatListener listener) {
		synchronized(statListeners) {
			if(!statListeners.contains(listener)) {
				statListeners.add(listener);
			}
		}
	}

	public static void removeStatListener(StatListener listener) {
		synchronized(statListeners) {
			statListeners.remove(listener);
		}
	}

	private static void statChanged(String stat) {
		HentaiAtHomeClient client = Settings.getActiveClient();
		boolean announce = false;

		if(client == null) {
			announce = true;
		}
		else if(!client.isShuttingDown()) {
			announce = true;
		}

		if(announce) {
			synchronized(statListeners) {
				for(StatListener listener : statListeners) {
					listener.statChanged(stat);
				}
			}
		}
	}

	// modify methods
	public static void setProgramStatus(String newStatus) {
		programStatus = newStatus;
		statChanged("programStatus");
	}

	public static void resetStats() {
		clientRunning = false;
		programStatus = "Stopped";
		clientStartTime = 0;
		lastServerContact = 0;
		filesSent = 0;
		filesRcvd = 0;
		bytesSent = 0;
		bytesRcvd = 0;
		cacheCount = 0;
		cacheSize = 0;
		resetBytesSentHistory();

		statChanged("reset");
	}

	// run this from a thread every 10 seconds
	public static void shiftBytesSentHistory() {
		if(bytesSentHistory == null) {
			return;
		}
		
		for(int i=360; i>0; i--) {
			bytesSentHistory[i] = bytesSentHistory[i-1];
		}

		bytesSentHistory[0] = 0;

		statChanged("bytesSentHistory");
	}

	public static void resetBytesSentHistory() {
		if(bytesSentHistory == null) {
			return;
		}

		java.util.Arrays.fill(bytesSentHistory, 0);
		statChanged("bytesSentHistory");
	}

	public static void programStarted() {
		clientStartTime = System.currentTimeMillis();
		clientRunning = true;
		setProgramStatus("Running");
		statChanged("clientRunning");
	}

	public static void programSuspended() {
		clientSuspended = true;
		setProgramStatus("Suspended");
		statChanged("clientSuspended");
	}

	public static void programResumed() {
		clientSuspended = false;
		setProgramStatus("Running");
		statChanged("clientSuspended");
	}

	public static void serverContact() {
		lastServerContact = (int) (System.currentTimeMillis() / 1000);
		statChanged("lastServerContact");
	}

	public static void fileSent() {
		++filesSent;
		statChanged("fileSent");
	}

	public static void fileRcvd() {
		++filesRcvd;
		statChanged("fileRcvd");
	}

	public static void bytesSent(int b) {
		if(bytesSentHistory == null) {
			return;
		}

		if(clientRunning) {
			bytesSent += b;
			bytesSentHistory[0] += (int) b;
		}

		statChanged("bytesSent");
	}

	public static void bytesRcvd(int b) {
		if(clientRunning) {
			bytesRcvd += b;
			statChanged("bytesRcvd");
		}
	}

	public static void setCacheCount(int count) {
		cacheCount = count;
		statChanged("cacheCount");
	}

	public static void setCacheSize(long size) {
		cacheSize = size;
		statChanged("cacheSize");
	}

	public static void setOpenConnections(int conns) {
		openConnections = conns;
		statChanged("openConnections");
	}

	// accessor methods
	public static boolean isClientRunning() {
		return clientRunning;
	}

	public static boolean isClientSuspended() {
		return clientSuspended;
	}

	public static String getProgramStatus() {
		return programStatus;
	}

	public static int getUptime() {
		return (int) getUptimeDouble();
	}

	public static double getUptimeDouble() {
		if(clientRunning) {
			return ((System.currentTimeMillis() - clientStartTime) / 1000.0);
		}

		return 0;
	}

	public static long getFilesSent() {
		return filesSent;
	}

	public static long getFilesRcvd() {
		return filesRcvd;
	}

	public static long getBytesSent() {
		return bytesSent;
	}

	public static int[] getBytesSentHistory() {
		return bytesSentHistory;
	}

	public static long getBytesRcvd() {
		return bytesRcvd;
	}

	public static int getBytesSentPerSec() {
		double uptime = getUptimeDouble();
		return uptime > 0 ? (int) (bytesSent / uptime) : 0;
	}

	public static int getBytesRcvdPerSec() {
		double uptime = getUptimeDouble();
		return uptime > 0 ? (int) (bytesRcvd / uptime) : 0;
	}

	public static int getCacheCount() {
		return cacheCount;
	}

	public static long getCacheSize() {
		return cacheSize;
	}

	public static long getCacheFree() {
		return Settings.getDiskLimitBytes() - cacheSize;
	}

	public static float getCacheFill() {
		return Settings.getDiskLimitBytes() != 0 ? cacheSize / (float) Settings.getDiskLimitBytes() : 0;
	}

	public static int getOpenConnections() {
		return openConnections;
	}

	public static int getLastServerContact() {
		return lastServerContact;
	}
}
