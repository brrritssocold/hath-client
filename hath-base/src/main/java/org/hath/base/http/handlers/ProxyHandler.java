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

import java.util.Hashtable;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
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

public class ProxyHandler extends AbstractHandler {
	private static final Logger logger = LoggerFactory.getLogger(ProxyHandler.class);
	private HentaiAtHomeClient client;
	private Pattern rawRequestParser;
	public LinkedList<Sensing> sensingPointsHit = new LinkedList<>();

	public enum Sensing {
		PROXY_REQUEST_DENIED, PROXY_REQUEST_GRANTED, PROXY_REQUEST_PASSKEY_NOT_REQUIRED, PROXY_REQUEST_CHECK_PASSKEY, PROXY_REQUEST_PASSKEY_AS_EXPECTED, PROXY_REQUEST_PASSKEY_INVALID, PROXY_REQUEST_INVALID_ARGUMENTS, PROXY_REQUEST_LOCAL_FILE, PROXY_REQUEST_PROXY_FILE, PROXY_REQUEST_INVALID_GID_OR_PAGE, PROXY_REQUEST_INVALID_GID_OR_PAGE_INTEGERS
	}

	private void hitSensingPoint(Sensing point) {
		sensingPointsHit.add(point);
	}

	public ProxyHandler(HentaiAtHomeClient client) {
		this.client = client;
		this.rawRequestParser = Pattern.compile("^(?:Request\\(GET.*\\/p)(\\/.*)(?:\\)@[\\d\\w]*)$");
	}

	public static String calculateProxyKey(String fileid) {
		return MiscTools.getSHAString(fileid + "I think we can put our differences behind us."
				+ MiscTools.getSHAString(Settings.getClientKey() + "For science.").substring(0, 10) + "You monster.")
				.substring(0, 10);
	}

	protected Matcher reparse(String rawRequest) {
		return rawRequestParser.matcher(rawRequest);
	}

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
		logger.trace("Handling proxy request");
	// new proxy request type, used implicitly when the password field is set
	// form: /p/fileid=asdf;token=asdf;gid=123;page=321;passkey=asdf/filename

		String rawRequest = request.toString();

		Matcher matcher = reparse(rawRequest);

		if (matcher.matches()) {
			String reparse = matcher.group(1);
			logger.trace("Reparsing proxy request. target: {}, raw: {}, reparse: {}", target, rawRequest, reparse);
			target = reparse;
		}
	// we allow access depending on the proxy mode retrieved from the server when the client is first started.
	// 0 = disabled
	// 1 = local networks open
	// 2 = external networks open
	// 3 = local network protected
	// 4 = external network protected

		// TODO replace with util method
		String[] urlparts = target.replace("%3d", "=").split("/");

	int proxymode = Settings.getRequestProxyMode();
	boolean enableProxy = proxymode > 0;
	boolean requirePasskey = proxymode == 3 || proxymode == 4;
	boolean requireLocalNetwork = proxymode == 1 || proxymode == 3;
		int session = HTTPRequestAttributes.getAttribute(request, IntegerAttributes.SESSION_ID);
		boolean isLocalNetworkAccess = HTTPRequestAttributes.getAttribute(request,
				BooleanAttributes.LOCAL_NETWORK_ACCESS);

		if (!enableProxy || (requireLocalNetwork && !isLocalNetworkAccess)) {
		Out.warning(session + " Proxy request denied for remote client.");
			hitSensingPoint(Sensing.PROXY_REQUEST_DENIED);
	} else {
			Hashtable<String, String> parsedRequest = MiscTools.parseAdditional(urlparts[1]);

		String fileid	= parsedRequest.get("fileid");
		String token	= parsedRequest.get("token");
		String szGid	= parsedRequest.get("gid");
		String szPage	= parsedRequest.get("page");
		String passkey	= parsedRequest.get("passkey");
			String filename = urlparts[2];

		boolean acceptPasskey = false;

			hitSensingPoint(Sensing.PROXY_REQUEST_GRANTED);

		if(!requirePasskey) {
				hitSensingPoint(Sensing.PROXY_REQUEST_PASSKEY_NOT_REQUIRED);
			acceptPasskey = true;
		} else if(passkey != null) {
				hitSensingPoint(Sensing.PROXY_REQUEST_CHECK_PASSKEY);
			// The client's passkey is generated by passing the client key through a SHA-1 function together with an additional string. This passkey is then entered under My Settings.
			// The request passkey is generated by passing the client passkey through an additional SHA-1 operation, which also includes the fileid of the requested file. This gives an unique passkey for each request.

			String expectedPasskey = calculateProxyKey(fileid);

			if(expectedPasskey.equals(passkey)) {
					hitSensingPoint(Sensing.PROXY_REQUEST_PASSKEY_AS_EXPECTED);
				acceptPasskey = true;
			}
		}

		if(!acceptPasskey) {
			Out.warning(session + " Invalid passkey");
				hitSensingPoint(Sensing.PROXY_REQUEST_PASSKEY_INVALID);
			// TODO move security and validation code to own
			// class
		} else if( !(HVFile.isValidHVFileid(fileid) && token.matches("^\\d+-[a-z0-9]{40}$") && szGid.matches("^\\d+$") && szPage.matches("^\\d+$") && filename.matches("^(([a-zA-Z0-9])|(\\.)|(_))*$")) ) {
			Out.warning(session + " Failed argument validation");
				hitSensingPoint(Sensing.PROXY_REQUEST_INVALID_ARGUMENTS);
		} else {
				// TODO add correct error codes
			try {
				int gid = Integer.parseInt(szGid);
				int page = Integer.parseInt(szPage);

				if(gid > 0 && page > 0) {

						HVFile requestedHVFile = client.getCacheHandler().getHVFile(fileid, true);

					if( (requestedHVFile != null) && (requestedHVFile.getLocalFileRef().exists()) ) {
							logger.trace("Creating file response for local file");
							HTTPRequestAttributes.setResponseProcessor(request,
									new HTTPResponseProcessorFile(requestedHVFile));
							hitSensingPoint(Sensing.PROXY_REQUEST_LOCAL_FILE);
					}
					else {
							logger.trace("Creating proxy response for remote file");
							HTTPRequestAttributes.setResponseProcessor(request,
									new HTTPResponseProcessorProxy(client, fileid, token, gid, page, filename));
							hitSensingPoint(Sensing.PROXY_REQUEST_PROXY_FILE);
					}
				}
				else {
					Out.warning(session + " gid and/or page are <= 0");
						hitSensingPoint(Sensing.PROXY_REQUEST_INVALID_GID_OR_PAGE);
				}
			} catch(Exception e) {
				Out.warning(session + " gid and/or page are not valid integers");
					hitSensingPoint(Sensing.PROXY_REQUEST_INVALID_GID_OR_PAGE_INTEGERS);
			}
		}
	}
	}
}
