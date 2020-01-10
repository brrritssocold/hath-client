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

package hath.base;

import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

public class Out {
	private static final Logger LOGGER = LoggerFactory.getLogger(Out.class);
	private static List<OutListener> outListeners = new LinkedList<OutListener>();

	public static void startLoggers() {
		setRootLoggerLevel(Level.DEBUG);
		LOGGER.info("Logging started");
	}

	// FIXME Using SLF4J breaks listeners
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
		LOGGER.info("Logging stopped");
		setRootLoggerLevel(Level.OFF);
	}

	private static void setRootLoggerLevel(Level level) {
		// FIXME Hard dependency on Logback
		ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory
				.getLogger(Logger.ROOT_LOGGER_NAME);
		root.setLevel(level);
	}

	/**
	 * @deprecated This function does nothing!
	 */
	@Deprecated
	public static void flushLogs() {
		LOGGER.warn("This method no longer does anything!");
	}

	/**
	 * Log the message with severity DEBUG
	 * 
	 * @param x
	 *            message to log
	 * @deprecated Use SLF4J loggers directly
	 */
	@Deprecated
	public static void debug(String x) {
		LOGGER.debug(x);
	}

	/**
	 * Log the message with severity INFO
	 * 
	 * @param x
	 *            message to log
	 * @deprecated Use SLF4J loggers directly
	 */
	@Deprecated
	public static void info(String x) {
		LOGGER.info(x);
	}

	/**
	 * Log the message with severity WARNING
	 * 
	 * @param x
	 *            message to log
	 * @deprecated Use SLF4J loggers directly
	 */
	@Deprecated
	public static void warning(String x) {
		LOGGER.warn(x);
	}

	/**
	 * Log the message with severity ERROR
	 * 
	 * @param x
	 *            message to log
	 * @deprecated Use SLF4J loggers directly
	 */
	@Deprecated
	public static void error(String x) {
		LOGGER.error(x);
	}
}
