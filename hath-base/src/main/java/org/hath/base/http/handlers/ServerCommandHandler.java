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

package org.hath.base.http.handlers;

import java.io.IOException;
import java.util.Hashtable;
import java.util.LinkedList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.hath.base.HentaiAtHomeClient;
import org.hath.base.MiscTools;
import org.hath.base.Out;
import org.hath.base.Settings;
import org.hath.base.http.HTTPRequestAttributes;
import org.hath.base.http.HTTPRequestAttributes.IntegerAttributes;
import org.hath.base.http.HTTPResponseProcessor;
import org.hath.base.http.HTTPResponseProcessorCachelist;
import org.hath.base.http.HTTPResponseProcessorSpeedtest;
import org.hath.base.http.HTTPResponseProcessorText;
import org.hath.base.util.HandlerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.InetAddresses;

public class ServerCommandHandler extends AbstractHandler {
	private static final Logger logger = LoggerFactory.getLogger(ServerCommandHandler.class);
	public final LinkedList<Sensing> sensingPointsHit = new LinkedList<>();
	private final HentaiAtHomeClient client;

	public enum Sensing {
		SERVER_CMD_INVALID_RPC_SERVER, SERVER_CMD_MALFORMED_COMMAND, SERVER_CMD_KEY_VALID, SERVER_CMD_KEY_INVALID
	}
	
	public ServerCommandHandler(HentaiAtHomeClient client) {
		this.client = client;
	}

	private void hitSensingPoint(Sensing point) {
		sensingPointsHit.add(point);
	}

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		logger.trace("Handling server command, {}", HandlerUtils.handlerStatus(baseRequest, request, response));

		// form: /servercmd/$command/$additional/$time/$key

		int session = HTTPRequestAttributes.getAttribute(request, IntegerAttributes.SESSION_ID);
		// TODO replace with util method
		String[] urlparts = target.replace("%3d", "=").split("/");

		if (!Settings.isValidRPCServer(InetAddresses.forString(request.getRemoteAddr()))) {
			Out.warning(session + " Got a servercmd from an unauthorized IP address: Denied");
			response.setStatus(HttpStatus.FORBIDDEN_403);
			baseRequest.setHandled(true);
			hitSensingPoint(Sensing.SERVER_CMD_INVALID_RPC_SERVER);
			return;
		} else if (urlparts.length < 5) {
			Out.warning(session + " Got a malformed servercmd: Denied");
			response.setStatus(HttpStatus.FORBIDDEN_403);
			baseRequest.setHandled(true);
			hitSensingPoint(Sensing.SERVER_CMD_MALFORMED_COMMAND);
			return;
		} else {
			String command = urlparts[1];
			String additional = urlparts[2];
			int commandTime = Integer.parseInt(urlparts[3]);
			String key = urlparts[4];

			int correctedTime = Settings.getServerTime();

			if (!isCommandTimeValid(commandTime, correctedTime)) {
				Out.warning(session + " Got a servercmd with expired key: Denied (was "
						+ keyTimeDrift(commandTime, correctedTime) + ", allowed: " + Settings.MAX_KEY_TIME_DRIFT + ")");
				response.setStatus(HttpStatus.FORBIDDEN_403);
				baseRequest.setHandled(true);
				hitSensingPoint(Sensing.SERVER_CMD_KEY_INVALID);
				return;
			}

			if (!isCommandKeyValid(command, additional, commandTime, key)) {
				Out.warning(session + " Got a servercmd with incorrect key: Denied (expected "
						+ calculateServercmdKey(command, additional, commandTime) + " but got " + key);
				logger.trace("command: {} additional: {} commandTime: {}", command, additional, commandTime);
				
				response.setStatus(HttpStatus.FORBIDDEN_403);
				baseRequest.setHandled(true);
				hitSensingPoint(Sensing.SERVER_CMD_KEY_INVALID);
				return;
			}

			HTTPRequestAttributes.setResponseProcessor(request, processRemoteAPICommand(command, additional, session));
			hitSensingPoint(Sensing.SERVER_CMD_KEY_VALID);
		}
	}

	private boolean isCommandKeyValid(String command, String additional, int commandTime, String key) {
		return calculateServercmdKey(command, additional, commandTime).equals(key);
	}

	private boolean isCommandTimeValid(int commandTime, int correctedTime) {
		return keyTimeDrift(commandTime, correctedTime) < Settings.MAX_KEY_TIME_DRIFT;
	}

	private int keyTimeDrift(int commandTime, int correctedTime) {
		return Math.abs(commandTime - correctedTime);
	}

	private HTTPResponseProcessor processRemoteAPICommand(String command, String additional, int session) {
		Hashtable<String, String> addTable = MiscTools.parseAdditional(additional);

		try {
			if (command.equalsIgnoreCase("still_alive")) {
				return new HTTPResponseProcessorText("I feel FANTASTIC and I'm still alive");
			} else if (command.equalsIgnoreCase("cache_list")) {
				return new HTTPResponseProcessorCachelist(client.getCacheHandler());
			} else if (command.equalsIgnoreCase("cache_files")) {
				return new HTTPResponseProcessorText(client.getServerHandler().downloadFilesFromServer(addTable));
			} else if (command.equalsIgnoreCase("proxy_test")) {
				String ipaddr = addTable.get("ipaddr");
				int port = Integer.parseInt(addTable.get("port"));
				String fileid = addTable.get("fileid");
				String keystamp = addTable.get("keystamp");
				return new HTTPResponseProcessorText(
						client.getServerHandler().doProxyTest(ipaddr, port, fileid, keystamp));
			} else if (command.equalsIgnoreCase("threaded_proxy_test")) {
				String ipaddr = addTable.get("ipaddr");
				int port = Integer.parseInt(addTable.get("port"));
				int testsize = Integer.parseInt(addTable.get("testsize"));
				int testcount = Integer.parseInt(addTable.get("testcount"));
				int testtime = Integer.parseInt(addTable.get("testtime"));
				String testkey = addTable.get("testkey");
				return new HTTPResponseProcessorText(client.getServerHandler().doThreadedProxyTest(ipaddr, port,
						testsize, testcount, testtime, testkey));
			} else if (command.equalsIgnoreCase("speed_test")) {
				String testsize = addTable.get("testsize");
				return new HTTPResponseProcessorSpeedtest(testsize != null ? Integer.parseInt(testsize) : 1000000);
			} else if (command.equalsIgnoreCase("refresh_settings")) {
				return new HTTPResponseProcessorText(client.getServerHandler().refreshServerSettings() + "");
			}
		} catch (Exception e) {
			e.printStackTrace();
			Out.warning(session + " Failed to process command");
		}

		return new HTTPResponseProcessorText("INVALID_COMMAND");
	}

	public static String calculateServercmdKey(String command, String additional, int commandTime) {
		return MiscTools.getSHAString("hentai@home-servercmd-" + command + "-" + additional + "-"
				+ Settings.getClientID() + "-" + commandTime + "-" + Settings.getClientKey());
	}
}
