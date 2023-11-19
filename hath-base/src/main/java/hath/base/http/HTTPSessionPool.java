/*

Copyright 2008-2020 E-Hentai.org
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

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hath.base.Settings;

/**
 * Pool for HTTP sessions, so threads can be reused instead of constantly getting created and destroyed (expensive!).
 */
public class HTTPSessionPool {
	private static final Logger LOGGER = LoggerFactory.getLogger(HTTPSessionPool.class);
	/**
	 * Based on {@link Settings#getMaxConnections()}
	 */
	private static final int CORE_POOL_SIZE = 20;
	
	private Executor sessionThreadPool;
	private ThreadPoolExecutor pool;
	private int lastMaxPoolSize;
	
	public HTTPSessionPool() {
		setupThreadPool();
	}
	
	/**
	 * Execute the runnable in a thread from the pool.
	 * 
	 * @param runnable to execute
	 */
	public void execute(Runnable runnable) {
		sessionThreadPool.execute(runnable);
	}
	
	private void setupThreadPool() {
		sessionThreadPool = Executors.newCachedThreadPool(new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				Thread thread = new Thread(r, "Pooled HTTP Session");
				thread.setDaemon(true);
				return thread;
			}
		});

		pool = (ThreadPoolExecutor) sessionThreadPool;

		int maximumPoolSize = Settings.getMaxConnections();
		lastMaxPoolSize = maximumPoolSize;

		pool.setMaximumPoolSize(maximumPoolSize);
		pool.setCorePoolSize(CORE_POOL_SIZE);
		pool.setKeepAliveTime(5, TimeUnit.MINUTES);

		LOGGER.debug("Session pool size is {} to {} thread(s)", CORE_POOL_SIZE, maximumPoolSize);
	}

	/**
	 * Update max thread pool size. Will only apply change and log message if there was any change.
	 * @param maxPoolSize max pool size to set
	 */
	public void updateMaxPoolSize(int maxPoolSize) {
		if (this.lastMaxPoolSize != maxPoolSize) {
			this.lastMaxPoolSize = maxPoolSize;

			pool.setMaximumPoolSize(maxPoolSize);
			LOGGER.debug("Session pool max size updated to {} thread(s)", maxPoolSize);
		}
	}
}
