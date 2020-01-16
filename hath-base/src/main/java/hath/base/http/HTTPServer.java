/*

Copyright 2008-2019 E-Hentai.org
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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import hath.base.FileDownloader;
import hath.base.HentaiAtHomeClient;
import hath.base.Out;
import hath.base.ServerHandler;
import hath.base.Settings;
import hath.base.Stats;

public class HTTPServer implements Runnable {
	private HentaiAtHomeClient client;
	private HTTPBandwidthMonitor bandwidthMonitor = null;
	private SSLServerSocket listener = null;
	private SSLContext sslContext = null;
	private Thread myThread = null;
	private List<HTTPSession> sessions;
	private int sessionCount = 0, currentConnId = 0;
	private boolean allowNormalConnections = false, isRestarting = false, isTerminated = false;
	private Hashtable<String,FloodControlEntry> floodControlTable;
	private Pattern localNetworkPattern;
	private final HTTPSessionPool sessionPool;
	
	public HTTPServer(HentaiAtHomeClient client) {
		this.client = client;
		sessions = Collections.checkedList(new ArrayList<HTTPSession>(), HTTPSession.class);
		floodControlTable = new Hashtable<String,FloodControlEntry>();
		sessionPool = new HTTPSessionPool();
		
		if(!Settings.isDisableBWM()) {
			bandwidthMonitor = new HTTPBandwidthMonitor();
		}
		
		//  private network: localhost, 127.x.y.z, 10.0.0.0 - 10.255.255.255, 172.16.0.0 - 172.31.255.255,  192.168.0.0 - 192.168.255.255, 169.254.0.0 -169.254.255.255
		localNetworkPattern = Pattern.compile("^((localhost)|(127\\.)|(10\\.)|(192\\.168\\.)|(172\\.((1[6-9])|(2[0-9])|(3[0-1]))\\.)|(169\\.254\\.)|(::1)|(0:0:0:0:0:0:0:1)|(fc)|(fd)).*$");
	}

	public boolean startConnectionListener(int port) {
		try {
			final String certPass = Settings.getClientKey();

			Out.info("Requesting certificate from server...");
			File certFile = new File(Settings.getDataDir(), "hathcert.p12");
			URL certUrl = ServerHandler.getServerConnectionURL(ServerHandler.ACT_GET_CERTIFICATE);
			FileDownloader certdl = new FileDownloader(certUrl, 10000, 300000, certFile.toPath());
			certdl.downloadFile();

			if(!certFile.exists()) {
				Out.error("Could not retrieve certificate file " + certFile);
				return false;
			}

			KeyStore ks = KeyStore.getInstance("PKCS12");
			InputStream keystoreFile = new FileInputStream(certFile.getPath());
			ks.load(keystoreFile, certPass.toCharArray());
			
			Out.debug("Initialized KeyStore with cert=" + ks.getCertificate("hath.network").toString());

			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(ks);
			
			Out.debug("Initialized TrustManagerFactory with algorithm=" + tmf.getAlgorithm());

			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(ks, certPass.toCharArray());

			Out.debug("Initialized KeyManagerFactory with algorithm=" + kmf.getAlgorithm());

			sslContext = SSLContext.getInstance("TLS");
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

			Out.info("Starting up the internal HTTP Server...");
			SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
			listener = (SSLServerSocket) ssf.createServerSocket(port);
			listener.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.1", "TLSv1"});

			Out.debug("Initialized SSLContext with cert " + certFile + " and protocol " + sslContext.getProtocol());
			Out.debug("Supported ciphers: " + Arrays.toString(sslContext.getSupportedSSLParameters().getCipherSuites()));
			Out.debug("Enabled protocols: " + Arrays.toString(listener.getEnabledProtocols()));
			
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

	public void stopConnectionListener(boolean restart) {
		isRestarting = restart;
		
		if(listener != null) {
			try {
				listener.close();	// will cause listener.accept() to throw an exception, terminating the accept thread
			} catch(Exception e) {}

			listener = null;
		}
	}

	public void pruneFloodControlTable() {
		List<String> toPrune = Collections.checkedList(new ArrayList<String>(), String.class);

		synchronized(floodControlTable) {
			Enumeration<String> keys = floodControlTable.keys();

			while(keys.hasMoreElements()) {
				String key = keys.nextElement();
				if(floodControlTable.get(key).isStale()) {
					toPrune.add(key);
				}
			}

			for(String key : toPrune) {
				floodControlTable.remove(key);
			}
		}

		toPrune.clear();
		toPrune = null;
		System.gc();
	}

	public void nukeOldConnections() {
		// in some rare cases, the connection is unable to remove itself from the session list. if so, it will return true for doTimeoutCheck, meaning that we will have to clear it out from here instead
		List<HTTPSession> remove = Collections.checkedList(new ArrayList<HTTPSession>(), HTTPSession.class);

		synchronized(sessions) {
			for(HTTPSession session : sessions) {
				if(session.doTimeoutCheck()) {
					Out.debug("Adding session " + session + " to timeout kill queue");
					remove.add(session);
				}
			}
			
			for(HTTPSession session : remove) {
				removeHTTPSession(session);
			}
		}

		if(!remove.isEmpty()) {
			// it can take a long time (several minutes) to kill SSL sockets for some reason, so fire up a new thread to handle the wet work
			(new HTTPSessionKiller(remove)).satsuriku();
		}
	}

	public void allowNormalConnections() {
		allowNormalConnections = true;
	}

	public void run() {
		try {
			while(true) {
				SSLSocket socket = (SSLSocket) listener.accept();
				boolean forceClose = false;
				InetAddress addr = socket.getInetAddress();
				String hostAddress = addr.getHostAddress().toLowerCase();
				boolean localNetworkAccess = Settings.getClientHost().replace("::ffff:", "").equals(hostAddress) || localNetworkPattern.matcher(hostAddress).matches();
				boolean apiServerAccess = Settings.isValidRPCServer(addr);

				if(!apiServerAccess && !allowNormalConnections) {
					Out.warning("Rejecting connection request from " + hostAddress + " during startup.");
					forceClose = true;
				}
				else if(!apiServerAccess && !localNetworkAccess) {
					// connections from the API Server and the local network are not subject to the max connection limit or the flood control

					int maxConnections = Settings.getMaxConnections();

					if(sessionCount > maxConnections) {
						Out.warning("Exceeded the maximum allowed number of incoming connections (" + maxConnections + ").");
						forceClose = true;
					}
					else {
						if(sessionCount > maxConnections * 0.8) {
							// let the dispatcher know that we're close to the breaking point. this will make it back off for 30 sec, and temporarily turns down the dispatch rate to half.
							client.getServerHandler().notifyOverload();
						}
						
						if(!Settings.isDisableFloodControl()) {
							// this flood control will stop clients from opening more than ten connections over a (roughly) five second floating window, and forcibly block them for 60 seconds if they do.
							FloodControlEntry fce = null;
							synchronized(floodControlTable) {
								fce = floodControlTable.get(hostAddress);
								if(fce == null) {
									fce = new FloodControlEntry(addr);
									floodControlTable.put(hostAddress, fce);
								}
							}

							if(!fce.isBlocked()) {
								if(!fce.hit()) {
									Out.warning("Flood control activated for  " + hostAddress + " (blocking for 60 seconds)");
									forceClose = true;
								}
							}
							else {
								forceClose = true;
							}
						}
					}
				}

				if(forceClose) {
					try {
						socket.close();
					} catch(Exception e) {}
				}
				else {
					// all is well. keep truckin'
					HTTPSession hs = new HTTPSession(socket, getNewConnId(), localNetworkAccess, this);

					synchronized(sessions) {
						sessions.add(hs);
						sessionCount = sessions.size();
					}
					
					Stats.setOpenConnections(sessionCount);
					handleSession(hs);
				}
			}
		}
		catch(java.io.IOException e) {
			if(!isRestarting && !client.isShuttingDown()) {
				Out.error("ServerSocket terminated unexpectedly!");
				HentaiAtHomeClient.dieWithError(e);
			}
			else {
				Out.info("ServerSocket was closed and will no longer accept new connections.");
			}

			listener = null;
		}
		
		isTerminated = true;
	}
	
	private void handleSession(HTTPSession session) {
		sessionPool.execute(session);
	}
	
	public boolean isThreadTerminated() {
		return isTerminated;
	}

	private synchronized int getNewConnId() {
		return ++currentConnId;
	}

	public void removeHTTPSession(HTTPSession httpSession) {
		synchronized(sessions) {
			sessions.remove(httpSession);
			sessionCount = sessions.size();
			Stats.setOpenConnections(sessionCount);
		}
	}

	public HTTPBandwidthMonitor getBandwidthMonitor() {
		return bandwidthMonitor;
	}

	public HentaiAtHomeClient getHentaiAtHomeClient() {
		return client;
	}

	private class FloodControlEntry {
		private InetAddress addr;
		private int connectCount;
		private long lastConnect;
		private long blocktime;

		public FloodControlEntry(InetAddress addr) {
			this.addr = addr;
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

			if(connectCount > 10) {
				// block this client from connecting for 60 seconds
				blocktime = nowtime + 60000;
				return false;
			}
			else {
				return true;
			}
		}
	}
}
