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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Pattern;

import org.hath.base.HentaiAtHomeClient;
import org.hath.base.Out;
import org.hath.base.Settings;
import org.hath.base.Stats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HTTPServer implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger(HTTPServer.class);

	private static final int THREAD_LOAD_FACTOR = 5;
	private static final int CORE_POOL_SIZE = 1;

	private HentaiAtHomeClient client;
	private HTTPBandwidthMonitor bandwidthMonitor = null;
	private ServerSocketChannel listener = null;
	private Thread myThread = null;
	private List<HTTPSession> sessions;
	private int currentConnId = 0;
	private boolean allowNormalConnections = false;
	private Pattern localNetworkPattern;
	private Executor sessionThreadPool;
	private IFloodControl floodControl;

	public HTTPServer(HentaiAtHomeClient client, IFloodControl floodControl) {
		this.client = client;
		setupThreadPool();
		this.floodControl = floodControl;

		sessions = Collections.checkedList(new ArrayList<HTTPSession>(), HTTPSession.class);
		
		if (!Settings.getInstance().isDisableBWM()) {
			bandwidthMonitor = new HTTPBandwidthMonitor();
		}
		
		//  private network: localhost, 127.x.y.z, 10.0.0.0 - 10.255.255.255, 172.16.0.0 - 172.31.255.255,  192.168.0.0 - 192.168.255.255, 169.254.0.0 -169.254.255.255
		localNetworkPattern = Pattern.compile("^((localhost)|(127\\.)|(10\\.)|(192\\.168\\.)|(172\\.((1[6-9])|(2[0-9])|(3[0-1]))\\.)|(169\\.254\\.)|(::1)|(0:0:0:0:0:0:0:1)|(fc)|(fd)).*$");
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

	private int sessionPoolSize() {
		return Runtime.getRuntime().availableProcessors() * THREAD_LOAD_FACTOR;
	}

	/**
	 * Add a session to the thread pool for execution.
	 * 
	 * @param session
	 *            to execute
	 */
	private void handleSession(HTTPSession session) {
		sessionThreadPool.execute(session);
	}

	public boolean startConnectionListener(int port) {
		try {
			Out.info("Starting up the internal HTTP Server...");

			listener = ServerSocketChannel.open();
			ServerSocket ss = listener.socket();
			ss.bind(new InetSocketAddress(port));

			myThread = new Thread(this, HTTPServer.class.getSimpleName());
			myThread.start();

			Out.info("Internal HTTP Server was successfully started, and is listening on port " + port);

			return true;
		}
		catch(Exception e) {
			allowNormalConnections();

			e.printStackTrace();
			Out.info("");
			Out.info("************************************************************************************************************************************");
			Out.info("Could not start the internal HTTP server.");
			Out.info("This is most likely caused by something else running on the port H@H is trying to use.");
			Out.info("In order to fix this, either shut down whatever else is using the port, or assign a different port to H@H.");
			Out.info("************************************************************************************************************************************");
			Out.info("");
		}

		return false;
	}

	public void stopConnectionListener() {
		if(listener != null) {
			try {
				listener.close();	// will cause listener.accept() to throw an exception, terminating the accept thread
			} catch(Exception e) {}

			listener = null;
		}
	}

	public void pruneFloodControlTable() {
		floodControl.pruneTable();
	}

	public void nukeOldConnections(boolean killall) {
		synchronized(sessions) {
			// in some rare cases, the connection is unable to remove itself from the session list. if so, it will return true for doTimeoutCheck, meaning that we will have to clear it out from here instead
			List<HTTPSession> remove = Collections.checkedList(new ArrayList<HTTPSession>(), HTTPSession.class);

			for(HTTPSession session : sessions) {
				if(session.doTimeoutCheck(killall)) {
					Out.debug("Killing stuck session " + session);
					remove.add(session);
				}
			}

			for(HTTPSession session : remove) {
				sessions.remove(session);
			}
		}
	}

	public void allowNormalConnections() {
		allowNormalConnections = true;
	}

	public void run() {
		try {
			while(true) {
				SocketChannel socketChannel = listener.accept();

				synchronized(sessions) {
					boolean forceClose = false;
					InetAddress addr = socketChannel.socket().getInetAddress();
					String hostAddress = addr.getHostAddress().toLowerCase();
					boolean localNetworkAccess = Settings.getInstance().getClientHost().replace("::ffff:", "")
							.equals(hostAddress) || localNetworkPattern.matcher(hostAddress).matches();
					boolean apiServerAccess = Settings.getInstance().isValidRPCServer(addr);

					if(!apiServerAccess && !allowNormalConnections) {
						Out.warning("Rejecting connection request from " + hostAddress + " during startup.");
						forceClose = true;
					}
					else if(!apiServerAccess && !localNetworkAccess) {
						// connections from the API Server and the local network are not subject to the max connection limit or the flood control

						int maxConnections = Settings.getInstance().getMaxConnections();
						int currentConnections = sessions.size();

						if(currentConnections > maxConnections) {
							Out.warning("Exceeded the maximum allowed number of incoming connections (" + maxConnections + ").");
							forceClose = true;
						}
						else {
							if(currentConnections > maxConnections * 0.8) {
								// let the dispatcher know that we're close to the breaking point. this will make it back off for 30 sec, and temporarily turns down the dispatch rate to half.
								client.getServerHandler().notifyOverload();
							}

							forceClose = floodControl.hasExceededConnectionLimit(hostAddress);
						}
					}

					if(forceClose) {
						try {
							socketChannel.close();
						} catch(Exception e) {}
					}
					else {
						// all is well. keep truckin'
						HTTPSession hs = new HTTPSession(socketChannel, getNewConnId(), localNetworkAccess, this);
						sessions.add(hs);
						Stats.setOpenConnections(sessions.size());
						handleSession(hs);
					}
				}
			}
		}
		catch(java.io.IOException e) {
			if(!client.isShuttingDown()) {
				Out.error("ServerSocket terminated unexpectedly!");
				HentaiAtHomeClient.dieWithError(e);
			}
			else {
				Out.info("ServerSocket was closed and will no longer accept new connections.");
			}

			listener = null;
		}
	}

	private synchronized int getNewConnId() {
		return ++currentConnId;
	}

	public void removeHTTPSession(HTTPSession httpSession) {
		synchronized(sessions) {
			sessions.remove(httpSession);
			Stats.setOpenConnections(sessions.size());
		}
	}

	public HTTPBandwidthMonitor getBandwidthMonitor() {
		return bandwidthMonitor;
	}

	public HentaiAtHomeClient getHentaiAtHomeClient() {
		return client;
	}
}
