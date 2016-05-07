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

import java.net.Socket;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.lang.Thread;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Enumeration;

public class HTTPServer implements Runnable {
	private HentaiAtHomeClient client;
	private HTTPBandwidthMonitor bandwidthMonitor;
	private ServerSocket ss;
	private Thread myThread;
	private List<HTTPSession> sessions;
	private int currentConnId;	
	private boolean allowNormalConnections;
	private Hashtable<String,FloodControlEntry> floodControlTable;
	
	public HTTPServer(HentaiAtHomeClient client) {
		this.client = client;
		bandwidthMonitor = new HTTPBandwidthMonitor();
		sessions = Collections.checkedList(new ArrayList<HTTPSession>(), HTTPSession.class);
		ss = null;
		myThread = null;
		currentConnId = 0;
		allowNormalConnections = false;
		floodControlTable = new Hashtable<String,FloodControlEntry>();
	}
	
	public boolean startConnectionListener(int port) {
		try {
			Out.info("Starting up the internal HTTP Server...");
		
			if(ss != null) {
				stopConnectionListener();
			}
			
			ss = new ServerSocket(port);
			myThread = new Thread(this);
			myThread.start();
			
			Out.info("Internal HTTP Server was successfully started, and is listening on port " + port);
			
			return true;
		} catch(Exception e) {
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
		if(ss != null) {
			try {
				ss.close();	// will cause ss.accept() to throw an exception, terminating the accept thread
			} catch(Exception e) {}
			ss = null;
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
	
	public void nukeOldConnections(boolean killall) {
		synchronized(sessions) {
			// in some rare cases, the connection is unable to remove itself from the session list. if so, it will return true for doTimeoutCheck, meaning that we will have to clear it out from here instead
			List<HTTPSession> remove = Collections.checkedList(new ArrayList<HTTPSession>(), HTTPSession.class);
			
			for(HTTPSession session : sessions) {
				if(session.doTimeoutCheck(killall)) {
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
				Socket s = ss.accept();
				
				synchronized(sessions) {
					boolean forceClose = false;

					//  private network: localhost, 127.x.y.z, 10.0.0.0 - 10.255.255.255, 172.16.0.0 - 172.31.255.255,  192.168.0.0 - 192.168.255.255, 169.254.0.0 -169.254.255.255
					
					InetAddress addr = s.getInetAddress();
					String addrString = addr.toString();
					String myInetAddr = Settings.getClientHost().replace("::ffff:", "");
					boolean localNetworkAccess = java.util.regex.Pattern.matches("^((" + myInetAddr + ")|(localhost)|(127\\.)|(10\\.)|(192\\.168\\.)|(172\\.((1[6-9])|(2[0-9])|(3[0-1]))\\.)|(169\\.254\\.)|(::1)|(0:0:0:0:0:0:0:1)|(fc)|(fd)).*$", addr.getHostAddress());
					boolean apiServerAccess = Settings.isValidRPCServer(addr);

					if(!apiServerAccess && !allowNormalConnections) {
						Out.warning("Rejecting connection request during startup.");
						forceClose = true;						
					} else if(!apiServerAccess && !localNetworkAccess) {
						// connections from the API Server and the local network are not subject to the max connection limit or the flood control
						
						int maxConnections = Settings.getMaxConnections();
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
						
							// this flood control will stop clients from opening more than ten connections over a (roughly) five second floating window, and forcibly block them for 60 seconds if they do.
							FloodControlEntry fce = null;						
							synchronized(floodControlTable) {
								fce = floodControlTable.get(addrString);
								if(fce == null) {
									fce = new FloodControlEntry(addr);
									floodControlTable.put(addrString, fce);
								}
							}
						
							if(!fce.isBlocked()) {
								if(!fce.hit()) {
									Out.warning("Flood control activated for  " + addrString + " (blocking for 60 seconds)");
									forceClose = true;
								}
							}
							else {
								forceClose = true;
							}
						}
					}

					if(forceClose) {
						try { s.close(); } catch(Exception e) { /* LALALALALA */ }					
					}
					else {
						// all is well. keep truckin'
						HTTPSession hs = new HTTPSession(s, getNewConnId(), localNetworkAccess, this);
						sessions.add(hs);
						Stats.setOpenConnections(sessions.size());
						hs.handleSession();											
					}
				}
			}
		} catch(java.io.IOException e) {
			if(!client.isShuttingDown()) {
				Out.error("ServerSocket terminated unexpectedly!");
				HentaiAtHomeClient.dieWithError(e);
			} else {
				Out.info("ServerSocket was closed and will no longer accept new connections.");
			}

			ss = null;
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
				blocktime = nowtime + 60000;	// block this client from connecting for 60 seconds
				return false;
			}
			else {
				return true;
			}
		}
	}
}
