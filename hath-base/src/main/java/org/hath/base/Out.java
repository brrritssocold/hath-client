/*

Copyright 2008-2015 E-Hentai.org
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

package org.hath.base;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Out {
	public static final int DEBUG = 1;
	public static final int INFO = 2;
	public static final int WARNING = 4;
	public static final int ERROR = 8;
	
	public static final int LOGOUT = DEBUG | INFO | WARNING | ERROR;
	public static final int LOGERR = WARNING | ERROR;
	public static final int OUTPUT = INFO | WARNING | ERROR;
	public static final int VERBOSE = ERROR;

	private static int suppressedOutput;
	private static boolean overridden, writeLogs;

	private static SimpleDateFormat sdf;

	private static List<OutListener> outListeners;

	private static Logger logger = LoggerFactory.getLogger(Out.class);

	static {
		try {
			Settings.initializeDataDir();
		} catch(java.io.IOException ioe) {
			System.err.println("Could not create data directory. Please check file access permissions and free disk space.");
			System.exit(-1);
		}

		overrideDefaultOutput();
	}
	
	public static void overrideDefaultOutput() {
		if(overridden) {
			return;
		}
		
		writeLogs = true;
		overridden = true;
		outListeners = new ArrayList<OutListener>();
	
		suppressedOutput = 0;

		sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"); // ISO 8601
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
	}
	
	public static void addOutListener(OutListener listener) {
		synchronized(outListeners) {
			if(!outListeners.contains(listener)) {
				outListeners.add(listener);
			}
		}
	}
	
	public static void removeOutListener(OutListener listener) {
		synchronized(outListeners) {
			outListeners.remove(listener);
		}		
	}
	
	public static void disableLogging() {
		if( writeLogs ) {
			info("Logging ended.");
			writeLogs = false;
			flushLogs();
		}
	}
	
	public static void flushLogs() {
		// TODO add command from logback or slf4j
	}

	public static void debug(String x) {
		logger.debug(x);
		notifyListeners(x, "debug", DEBUG);
	}

	public static void info(String x) {
		logger.info(x);
		notifyListeners(x, "info", INFO);
	}

	public static void warning(String x) {
		logger.warn(x);
		notifyListeners(x, "WARN", WARNING);
	}

	public static void error(String x) {
		logger.error(x);
		notifyListeners(x, "ERROR", ERROR);
	}

	public static String verbose(int severity) {
		if ((severity & VERBOSE) > 0) {
			java.lang.StackTraceElement[] ste = java.lang.Thread.currentThread().getStackTrace();
			
			int offset = 0;
			while (++offset < ste.length) {
				String s = ste[offset].getClassName();
				if (!s.equals("org.hath.base.Out") && !s.equals("org.hath.base.Out$OutPrintStream") && !s.equals("java.lang.Thread")) {
					break;
				}
			}
			
			if (offset < ste.length) {
				if (!ste[offset].getClassName().equals("java.lang.Throwable")) {
					return "{" + ste[offset] + "} ";
				} else {
					return "";
				}
			} else {
				return "{Unknown Source}";
			}
		} else {
			return "";
		}
	}

	public static void notifyListeners(String x, String name, int severity) {
		if (x == null) {
			return;
		}

		boolean output = (severity & Out.OUTPUT & ~Out.suppressedOutput) > 0;
		boolean log = (severity & (Out.LOGOUT | Out.LOGERR)) > 0;

		if (output || log) {
			synchronized (outListeners) {
				String v = Out.verbose(severity);
				String[] split = x.split("\n");
				for (String s : split) {
					String data = sdf.format(new Date()) + " [" + name + "] " + v + s;

					if (output) {

						for (OutListener listener : outListeners) {
							listener.outputWritten(data);
						}
					}
				}
			}
		}
	}
}
