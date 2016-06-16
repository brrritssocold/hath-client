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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.hath.base.HentaiAtHomeClient;
import org.hath.base.Out;
import org.hath.base.Settings;
import org.hath.base.Stats;
import org.hath.base.http.handlers.BaseHandler;
import org.hath.base.http.handlers.FaviconHandler;
import org.hath.base.http.handlers.FileHandler;
import org.hath.base.http.handlers.ProxyHandler;
import org.hath.base.http.handlers.RequestMethodCheckHandler;
import org.hath.base.http.handlers.RobotsHandler;
import org.hath.base.http.handlers.ServerCommandHandler;
import org.hath.base.http.handlers.SessionRemovalHandler;
import org.hath.base.http.handlers.SpeedTestHandler;
import org.hath.base.http.handlers.UnhandledSessionHandler;

public class HTTPServer {
	private static final int MAX_FLOOD_ENTRY_AGE_SECONDS = 60;
	private static final int REQUEST_TIMEOUT_SECONDS = 30;
	private static final double OVERLOAD_PERCENTAGE = 0.8;
	private static final HttpMethod[] allowedMethods = { HttpMethod.GET, HttpMethod.HEAD };

	private HentaiAtHomeClient client;
	@Deprecated
	private HTTPBandwidthMonitor bandwidthMonitor;
	private Server httpServer;
	@Deprecated
	private List<BaseHandler> sessions;
	private SessionTrackingHandler sessionTrackingHandler;
	
	public HTTPServer(HentaiAtHomeClient client) {
		this(client, new HTTPSessionFactory());
	}

	public HTTPServer(HentaiAtHomeClient client, HTTPSessionFactory factory) {
		this.client = client;
		bandwidthMonitor = new HTTPBandwidthMonitor();
		sessions = Collections.checkedList(new ArrayList<BaseHandler>(), BaseHandler.class);
	}
	
	public Handler setupHandlers() {
		HandlerList handlerList = new HandlerList();
		HandlerCollection handlerCollection = new HandlerCollection();

		SessionTracker sessionTracker = new SessionTracker(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS,
				Settings.getMaxConnections(), OVERLOAD_PERCENTAGE);
		FloodControl floodControl = new FloodControl(MAX_FLOOD_ENTRY_AGE_SECONDS, TimeUnit.SECONDS);

		sessionTrackingHandler = new SessionTrackingHandler(client, floodControl, sessionTracker);
		SessionRemovalHandler sessionRemovalHandler = new SessionRemovalHandler(sessionTracker);

		// process in-order until positive status or exception
		handlerList.addHandler(createContextHandlerCollection());
		handlerList.addHandler(new BaseHandler(new HTTPBandwidthMonitor()));
		handlerList.addHandler(new UnhandledSessionHandler(HttpStatus.NOT_FOUND_404));

		// these handlers will always be executed in-order
		handlerCollection.addHandler(sessionTrackingHandler);
		handlerCollection.addHandler(new RequestMethodCheckHandler(allowedMethods));
		handlerCollection.addHandler(handlerList);
		handlerCollection.addHandler(sessionRemovalHandler);

		return handlerCollection;
	}

	/**
	 * This {@link ContextHandlerCollection} is used to route the requests to
	 * the respective handlers.
	 */
	private ContextHandlerCollection createContextHandlerCollection() {
		ContextHandlerCollection handlerCollection = new ContextHandlerCollection();

		handlerCollection.addHandler(createContextHandler("/favicon.ico", new FaviconHandler()));
		handlerCollection.addHandler(createContextHandler("/robots.txt", new RobotsHandler()));

		handlerCollection.addHandler(createContextHandler("/t", new SpeedTestHandler()));
		handlerCollection.addHandler(createContextHandler("/h", new FileHandler(client.getCacheHandler())));
		handlerCollection.addHandler(createContextHandler("/p", new ProxyHandler(client)));
		handlerCollection.addHandler(createContextHandler("/servercmd", new ServerCommandHandler(client)));

		return handlerCollection;
	}

	private ContextHandler createContextHandler(String contextPath, Handler handler) {
		ContextHandler contextHandler = new ContextHandler(contextPath);
		contextHandler.setHandler(handler);

		return contextHandler;
	}

	public boolean startConnectionListener(int port) {
		try {
			Out.info("Starting up the internal HTTP Server...");
		
			if (httpServer != null && httpServer.isRunning()) {
				stopConnectionListener();
			}
			
			httpServer = new Server(port);
			httpServer.setStopTimeout(TimeUnit.MILLISECONDS.convert(15, TimeUnit.SECONDS));

			
			httpServer.setHandler(setupHandlers());
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
	
	@Deprecated
	public void nukeOldConnections(boolean killall) {
		synchronized(sessions) {
			// in some rare cases, the connection is unable to remove itself from the session list. if so, it will return true for doTimeoutCheck, meaning that we will have to clear it out from here instead
			List<BaseHandler> remove = Collections.checkedList(new ArrayList<BaseHandler>(), BaseHandler.class);
			
			for(BaseHandler session : sessions) {
				if(session.doTimeoutCheck(killall)) {
					remove.add(session);
				}
			}
			
			for(BaseHandler session : remove) {
				sessions.remove(session);
			}
		}
	}
	
	public void allowNormalConnections() {
		sessionTrackingHandler.allowNormalConnections();
	}

	@Deprecated
	public void removeHTTPSession(BaseHandler httpSession) {
		synchronized(sessions) {
			sessions.remove(httpSession);
			Stats.setOpenConnections(sessions.size());
		}
	}
	
	@Deprecated
	public HTTPBandwidthMonitor getBandwidthMonitor() {
		return bandwidthMonitor;
	}

	@Deprecated
	public HentaiAtHomeClient getHentaiAtHomeClient() {
		return client;
	}
}
