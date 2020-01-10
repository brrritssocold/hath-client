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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pool for HTTP sessions, so threads can be reused instead of constantly getting created and destroyed (expensive!).
 */
public class HTTPSessionPool {
	private static final Logger LOGGER = LoggerFactory.getLogger(HTTPSessionPool.class);

	private static final int THREAD_LOAD_FACTOR = 5;
	private static final int CORE_POOL_SIZE = 1;
	
	private Executor sessionThreadPool;
	
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
	
	private int sessionPoolSize() {
		return Runtime.getRuntime().availableProcessors() * THREAD_LOAD_FACTOR;
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

		ThreadPoolExecutor pool = (ThreadPoolExecutor) sessionThreadPool;
		int maximumPoolSize = sessionPoolSize();
		pool.setMaximumPoolSize(maximumPoolSize);
		pool.setCorePoolSize(CORE_POOL_SIZE);

		LOGGER.debug("Session pool size is {} to {} thread(s)", CORE_POOL_SIZE, maximumPoolSize);
	}
}
