/*

Copyright 2008-2016 E-Hentai.org
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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.hath.base.HentaiAtHomeClient;
import org.hath.base.Out;
import org.hath.base.Settings;
import org.hath.base.Stats;
import org.hath.base.http.HTTPRequestAttributes.IntegerAttributes;

import com.google.common.net.InetAddresses;

/**
 * Creates and tracks sessions and enforces limits.
 */
public class SessionTrackingHandler extends AbstractHandler {
	private boolean allowNormalConnections;
	private HentaiAtHomeClient client;
	private FloodControl floodControl;
	private int currentConnId;
	private SessionTracker sessionTracker;

	public SessionTrackingHandler(HentaiAtHomeClient client, FloodControl floodControl, SessionTracker sessionTracker) {
		this.client = client;
		this.floodControl = floodControl;
		this.sessionTracker = sessionTracker;

		allowNormalConnections = false;
		currentConnId = 0;
	}

	private synchronized int getNewConnId() {
		return ++currentConnId;
	}

	public void allowNormalConnections() {
		allowNormalConnections = true;
	}

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {

		InetAddress addr = InetAddresses.forString(baseRequest.getRemoteAddr());

		boolean forceClose = false;

		// private network: localhost, 127.x.y.z, 10.0.0.0 - 10.255.255.255,
		// 172.16.0.0 - 172.31.255.255, 192.168.0.0 - 192.168.255.255,
		// 169.254.0.0 -169.254.255.255

		String addrString = addr.toString();
		String myInetAddr = Settings.getClientHost().replace("::ffff:", "");
		boolean localNetworkAccess = java.util.regex.Pattern.matches(
				"^((" + myInetAddr
						+ ")|(localhost)|(127\\.)|(10\\.)|(192\\.168\\.)|(172\\.((1[6-9])|(2[0-9])|(3[0-1]))\\.)|(169\\.254\\.)|(::1)|(0:0:0:0:0:0:0:1)|(fc)|(fd)).*$",
				addr.getHostAddress());

		boolean apiServerAccess = Settings.isValidRPCServer(addr);

		request.setAttribute(HTTPRequestAttributes.LOCAL_NETWORK_ACCESS, localNetworkAccess);
		request.setAttribute(HTTPRequestAttributes.API_SERVER_ACCESS, apiServerAccess);

		if (!apiServerAccess && !allowNormalConnections) {
			Out.warning("Rejecting connection request during startup.");
			forceClose = true;
		} else if (!apiServerAccess && !localNetworkAccess) {
			// connections from the API Server and the local network are not
			// subject to the max connection limit or the flood control

			if (sessionTracker.isMaxSessionReached()) {
				Out.warning("Exceeded the maximum allowed number of incoming connections ("
						+ sessionTracker.getMaxSessions() + ").");
				forceClose = true;
			} else {
				if (sessionTracker.isOverloaded()) {
					// let the dispatcher know that we're close to the breaking
					// point. this will make it back off for 30 sec, and
					// temporarily turns down the dispatch rate to half.
					client.getServerHandler().notifyOverload();
				}

				forceClose = floodControl.hasExceededConnectionLimit(addrString);
			}
		}

		if (forceClose) {
			response.setStatus(HttpStatus.SERVICE_UNAVAILABLE_503);
			baseRequest.setHandled(true);
			return;
		}

		// all is well. keep truckin'
		int sessionID = getNewConnId();
		request.setAttribute(IntegerAttributes.SESSION_ID.toString(), sessionID);
		sessionTracker.add(sessionID, baseRequest);
		Stats.setOpenConnections((int) sessionTracker.activeSessions());
	}
}
