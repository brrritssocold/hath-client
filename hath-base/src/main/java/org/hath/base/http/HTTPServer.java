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

package org.hath.base.http;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.hath.base.HentaiAtHomeClient;
import org.hath.base.Out;
import org.hath.base.Settings;
import org.hath.base.Stats;

import com.google.common.net.InetAddresses;

public class HTTPServer extends AbstractHandler {
	private static final int MAX_FLOOD_ENTRY_AGE_SECONDS = 60;

	private HentaiAtHomeClient client;
	private HTTPBandwidthMonitor bandwidthMonitor;
	private Server httpServer;
	private List<HTTPSession> sessions;
	private int currentConnId;	
	private boolean allowNormalConnections;
	private FloodControl floodControl;
	private HTTPSessionFactory sessionFactory;
	
	public HTTPServer(HentaiAtHomeClient client) {
		this(client, new HTTPSessionFactory());
	}

	public HTTPServer(HentaiAtHomeClient client, HTTPSessionFactory factory) {
		this.client = client;
		this.sessionFactory = factory;
		bandwidthMonitor = new HTTPBandwidthMonitor();
		sessions = Collections.checkedList(new ArrayList<HTTPSession>(), HTTPSession.class);
		currentConnId = 0;
		allowNormalConnections = false;
		floodControl = new FloodControl(MAX_FLOOD_ENTRY_AGE_SECONDS, TimeUnit.SECONDS);
	}
	
	public boolean startConnectionListener(int port) {
		try {
			Out.info("Starting up the internal HTTP Server...");
		
			if (httpServer != null && httpServer.isRunning()) {
				stopConnectionListener();
			}
			
			httpServer = new Server(port);
			httpServer.setStopTimeout(TimeUnit.MILLISECONDS.convert(15, TimeUnit.SECONDS));
			httpServer.setHandler(this);
			httpServer.start();

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
		Out.info("Shutting down the internal HTTP Server...");

		if (httpServer != null) {
			try {
				httpServer.stop();
			} catch (Exception e) {
				Out.error("Failed to stop internal HTTP Server: " + e);
			}
		}
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

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		processConnection(InetAddresses.forString(baseRequest.getRemoteAddr()));
	}

	protected void processConnection(Socket socket) {
		InetAddress addr = socket.getInetAddress();
		boolean forceClose = processConnection(addr);

		if (forceClose) {
			try { socket.close(); } catch(Exception e) { /* LALALALALA */ }
		}
	}

	protected boolean processConnection(InetAddress addr) {
		synchronized(sessions) {
			boolean forceClose = false;

			//  private network: localhost, 127.x.y.z, 10.0.0.0 - 10.255.255.255, 172.16.0.0 - 172.31.255.255,  192.168.0.0 - 192.168.255.255, 169.254.0.0 -169.254.255.255
			
			String addrString = addr.toString();
			String myInetAddr = Settings.getClientHost().replace("::ffff:", "");
			boolean localNetworkAccess = java.util.regex.Pattern.matches("^((" + myInetAddr + ")|(localhost)|(127\\.)|(10\\.)|(192\\.168\\.)|(172\\.((1[6-9])|(2[0-9])|(3[0-1]))\\.)|(169\\.254\\.)|(::1)|(0:0:0:0:0:0:0:1)|(fc)|(fd)).*$", addr.getHostAddress());
			boolean apiServerAccess = Settings.isValidRPCServer(addr);

			if(!apiServerAccess && !allowNormalConnections) {
				Out.warning("Rejecting connection request during startup.");
				forceClose = true;						
			} else if (!apiServerAccess && !localNetworkAccess) {
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
				
					forceClose = floodControl.hasExceededConnectionLimit(addrString);
				}
			}

			if(forceClose) {
				return true;
				// FIXME set request handled
			}
			else {
				// all is well. keep truckin'
				HTTPSession hs = sessionFactory.create(getNewConnId(), localNetworkAccess, bandwidthMonitor,
						new HTTPResponseFactory());
				sessions.add(hs);
				Stats.setOpenConnections(sessions.size());
				hs.handleSession();
				return false;
			}
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
