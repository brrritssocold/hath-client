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
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.hath.base.CacheHandler;
import org.hath.base.HVFile;
import org.hath.base.HentaiAtHomeClient;
import org.hath.base.MiscTools;
import org.hath.base.Out;
import org.hath.base.Settings;
import org.hath.base.http.HTTPRequestAttributes;
import org.hath.base.http.HTTPRequestAttributes.BooleanAttributes;
import org.hath.base.http.HTTPRequestAttributes.IntegerAttributes;
import org.hath.base.http.HTTPResponseProcessorFile;
import org.hath.base.http.HTTPResponseProcessorProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileHandler extends AbstractHandler {
	private static final Logger logger = LoggerFactory.getLogger(FileHandler.class);
	private final CacheHandler cacheHandler;

	private boolean isKeystampValid(String hvfile, Hashtable<String, String> additional) {
		String[] keystampParts = additional.get("keystamp").split("-");

		if (keystampParts.length == 2) {
			try {
				long keystampTime = Integer.parseInt(keystampParts[0]);

				if (Math.abs(Settings.getServerTime() - keystampTime) < 900) {
					if (keystampParts[1].equalsIgnoreCase(calculateKeystamp(hvfile, keystampTime))) {
						return true;
					}
				}
			} catch (Exception e) {
			}
		}
		return false;
	}

	public static String calculateKeystamp(String hvfile, long keystampTime) {
		return MiscTools.getSHAString(keystampTime + "-" + hvfile + "-" + Settings.getClientKey() + "-hotlinkthis")
				.substring(0, 10);
	}

	public FileHandler(CacheHandler cacheHandler) {
		this.cacheHandler = cacheHandler;
	}

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		logger.trace("Handling file request ", request);
		// TODO replace with util method
		String[] urlparts = target.replace("%3d", "=").split("/");

		if (urlparts.length < 3) {
			logger.trace("Request was too short! {}", target);
			response.setStatus(HttpStatus.BAD_REQUEST_400);
			baseRequest.setHandled(true);
			return;
		}

		// new url type for H@H.. we don't really do anything new, but having
		// the filename at the end will make browsers using this as filename per
		// default.
		// we also put in an extension that allows us to add additional
		// arguments to the request url without messing with old clients.

		String hvfile = urlparts[1];
		HentaiAtHomeClient client = HTTPRequestAttributes.getClient(request);
		boolean localNetworkAccess = HTTPRequestAttributes.getAttribute(request,
				BooleanAttributes.LOCAL_NETWORK_ACCESS);

		int session = HTTPRequestAttributes.getAttribute(request, IntegerAttributes.SESSION_ID);

		HVFile requestedHVFile = cacheHandler.getHVFile(hvfile, !localNetworkAccess);

		Hashtable<String, String> additional = MiscTools.parseAdditional(urlparts[2]);
		// urlparts[4] will contain the filename, but we don't actively use this

		if (!isKeystampValid(hvfile, additional)) {
			logger.trace("Rejected file request due to invalid key: {}", request);
			response.setStatus(HttpStatus.FORBIDDEN_403);
		} else if (requestedHVFile == null) {
			Out.warning(session + " The requested file was invalid or not found in cache.");
			response.setStatus(HttpStatus.NOT_FOUND_404);
		} else {
			String fileid = requestedHVFile.getFileid();

			if (requestedHVFile.getLocalFileRef().exists()) {
				logger.trace("Sending requested file {}", fileid);
				HTTPRequestAttributes.setResponseProcessor(request, new HTTPResponseProcessorFile(requestedHVFile));
			} else if (Settings.isStaticRange(fileid)) {
				// non-existent file in a static range. do an on-demand request
				// of the file from the image servers
				logger.trace("Requested file is in the static range");
				List<String> requestTokens = new ArrayList<String>();
				requestTokens.add(fileid);

				Hashtable<String, String> tokens = client.getServerHandler().getFileTokens(requestTokens);

				if (tokens.containsKey(fileid)) {
					logger.trace("Creating proxy for static range file request: {}", fileid);
					HTTPRequestAttributes.setResponseProcessor(request,
							new HTTPResponseProcessorProxy(client, fileid, tokens.get(fileid), 1, 1, "ondemand"));
				} else {
					logger.trace("Could not find static range request in file tokens: {}", fileid);
					response.setStatus(HttpStatus.NOT_FOUND_404);
				}
			} else {
				// file does not exist, and is not in one of the client's static
				// ranges
				logger.trace("Could not find requested file {}", requestedHVFile.getFileid());
				response.setStatus(HttpStatus.NOT_FOUND_404);
			}
		}
	}
}
