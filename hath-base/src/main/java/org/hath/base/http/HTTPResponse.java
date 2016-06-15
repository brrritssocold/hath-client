/*

Copyright 2008-2012 E-Hentai.org
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

import java.util.Hashtable;
import java.util.LinkedList;
import java.util.regex.Pattern;

import org.hath.base.HVFile;
import org.hath.base.HentaiAtHomeClient;
import org.hath.base.MiscTools;
import org.hath.base.Out;
import org.hath.base.Settings;
import org.hath.base.http.handlers.BaseHandler;

/**
 * Verifies the Request and then chooses the appropriate way to handle the
 * request. Sets status Codes.
 *
 */
public class HTTPResponse {
	private static final Pattern absoluteUriPattern = Pattern.compile("^http://[^/]+/", Pattern.CASE_INSENSITIVE);

	private BaseHandler session;

	private boolean requestHeadOnly, validRequest;
	private boolean servercmd;
	private int responseStatusCode;

	private HTTPResponseProcessor hpc;

	public enum Sensing {
		FILE_REQUEST_TOO_SHORT, FILE_REQUEST_INVALID_KEY, FILE_REQUEST_FILE_NOT_FOUND, FILE_REQUEST_FILE_LOCAL, FILE_REQUEST_FILE_STATIC, FILE_REQUEST_VALID_FILE_TOKEN, FILE_REQUEST_INVALID_FILE_TOKEN, FILE_REQUEST_FILE_NOT_LOCAL_OR_STATIC, SERVER_CMD_INVALID_RPC_SERVER, SERVER_CMD_MALFORMED_COMMAND, SERVER_CMD_KEY_INVALID, SERVER_CMD_KEY_VALID, PROXY_REQUEST_INVALID_GID_OR_PAGE_INTEGERS, PROXY_REQUEST_CHECK_PASSKEY, PROXY_REQUEST_GRANTED, PROXY_REQUEST_DENIED, PROXY_REQUEST_PASSKEY_NOT_REQUIRED, PROXY_REQUEST_PASSKEY_AS_EXPECTED, PROXY_REQUEST_PASSKEY_INVALID, PROXY_REQUEST_INVALID_ARGUMENTS, PROXY_REQUEST_LOCAL_FILE, PROXY_REQUEST_PROXY_FILE, PROXY_REQUEST_INVALID_GID_OR_PAGE, TEST_REQUEST_VALID, TEST_REQUEST_INVALID_KEY, TEST_REQUEST_EXPIRED_KEY, TEST_REQUEST_INVALID_REQUEST, ROBOTS, FAVICON, INVALID_REQUEST_LEN2, HTTP_REQUEST_NULL, HTTP_REQUEST_INVALID_LENGTH, HTTP_REQUEST_TYPE_AND_FORM_INVALID, HTTP_REQUEST_INVALID_URL
	}

	public LinkedList<Sensing> sensingPointsHit = new LinkedList<>();

	public HTTPResponse(BaseHandler session) {
		this.session = session;

		validRequest = false;
		servercmd = false;
		requestHeadOnly = false;

		responseStatusCode = 500;	// if nothing alters this, there's a bug somewhere
	}

	private void hitSensingPoint(Sensing point) {
		sensingPointsHit.add(point);
	}

	private HTTPResponseProcessor processRemoteAPICommand(String command, String additional) {
		Hashtable<String,String> addTable = MiscTools.parseAdditional(additional);
		HentaiAtHomeClient client = session.getHTTPServer().getHentaiAtHomeClient();

		try {
			if(command.equalsIgnoreCase("still_alive")) {
				return new HTTPResponseProcessorText("I feel FANTASTIC and I'm still alive");
			} else if(command.equalsIgnoreCase("cache_list")) {
				return new HTTPResponseProcessorCachelist(client.getCacheHandler());
			} else if(command.equalsIgnoreCase("cache_files")) {
				return new HTTPResponseProcessorText(client.getServerHandler().downloadFilesFromServer(addTable));
			} else if(command.equalsIgnoreCase("proxy_test")) {
				String ipaddr = addTable.get("ipaddr");
				int port = Integer.parseInt(addTable.get("port"));
				String fileid = addTable.get("fileid");
				String keystamp = addTable.get("keystamp");
				return new HTTPResponseProcessorText(client.getServerHandler().doProxyTest(ipaddr, port, fileid, keystamp));
			} else if(command.equalsIgnoreCase("threaded_proxy_test")) {
				String ipaddr = addTable.get("ipaddr");
				int port = Integer.parseInt(addTable.get("port"));
				int testsize = Integer.parseInt(addTable.get("testsize"));
				int testcount = Integer.parseInt(addTable.get("testcount"));
				int testtime = Integer.parseInt(addTable.get("testtime"));
				String testkey = addTable.get("testkey");
				return new HTTPResponseProcessorText(client.getServerHandler().doThreadedProxyTest(ipaddr, port, testsize, testcount, testtime, testkey));
			} else if(command.equalsIgnoreCase("speed_test")) {
				String testsize = addTable.get("testsize");
				return new HTTPResponseProcessorSpeedtest(testsize != null ? Integer.parseInt(testsize) : 1000000);
			} else if(command.equalsIgnoreCase("refresh_settings")) {
				return new HTTPResponseProcessorText(client.getServerHandler().refreshServerSettings()+"");
			}
		} catch(Exception e) {
			e.printStackTrace();
			Out.warning(session + " Failed to process command");
		}

		return new HTTPResponseProcessorText("INVALID_COMMAND");
	}

	public void parseRequest(String request, boolean localNetworkAccess) {
		if(request == null) {
			responseStatusCode = 400;
			hitSensingPoint(Sensing.HTTP_REQUEST_NULL);
			return;		
		}
	
		String[] requestParts = request.trim().split(" ", 3);

		if(requestParts.length != 3) {
			Out.warning(session + " Invalid HTTP request form.");
			hitSensingPoint(Sensing.HTTP_REQUEST_INVALID_LENGTH);
		} else if( !(requestParts[0].equalsIgnoreCase("GET") || requestParts[0].equalsIgnoreCase("HEAD")) || !requestParts[2].startsWith("HTTP/") ) {
			Out.warning(session + " HTTP request is not GET or HEAD.");
			responseStatusCode = 405;
			// TODO add header "Allow", "GET,HEAD"
			hitSensingPoint(Sensing.HTTP_REQUEST_TYPE_AND_FORM_INVALID);
			return;
		} else {
			validRequest = true;
			requestHeadOnly = requestParts[0].equalsIgnoreCase("HEAD");
			
			// The request URI may be an absolute path or an absolute URI for GET/HEAD requests (see section 5.1.2 of RFC2616)
			requestParts[1] = absoluteUriPattern.matcher(requestParts[1]).replaceFirst("/");
			
			String[] urlparts = requestParts[1].replace("%3d", "=").split("/");

			if( (urlparts.length < 2) || !urlparts[0].equals("")) {
				Out.warning(session + " The requested URL is invalid or not supported.");
				hitSensingPoint(Sensing.HTTP_REQUEST_INVALID_URL);
			} else {
				if (urlparts[1].equals("servercmd")) {
					processServerCommand(urlparts);
					return;
				}
				else if(urlparts[1].equals("p")) {
					boolean requestHandled = processProxyRequest(urlparts);

					if (requestHandled) {
						return;
					}
				}
				else if(urlparts.length == 2) {
						Out.warning(session + " Invalid request type '" + urlparts[1] + "'.");
						hitSensingPoint(Sensing.INVALID_REQUEST_LEN2);
				}
				else {
					Out.warning(session + " Invalid request type '" + urlparts[1] + "'.");
				}
			}

			responseStatusCode = 404;
			return;
		}

		Out.warning(session + " Invalid HTTP request.");
		responseStatusCode = 400;
	}

	protected boolean processProxyRequest(String[] urlparts) {
		// new proxy request type, used implicitly when the password field is set
		// form: /p/fileid=asdf;token=asdf;gid=123;page=321;passkey=asdf/filename

		// we allow access depending on the proxy mode retrieved from the server when the client is first started.
		// 0 = disabled
		// 1 = local networks open
		// 2 = external networks open
		// 3 = local network protected
		// 4 = external network protected

		int proxymode = Settings.getRequestProxyMode();
		boolean enableProxy = proxymode > 0;
		boolean requirePasskey = proxymode == 3 || proxymode == 4;
		boolean requireLocalNetwork = proxymode == 1 || proxymode == 3;
		boolean requestHandled = false;

		if( !enableProxy || (requireLocalNetwork && !session.isLocalNetworkAccess()) ) {
			Out.warning(session + " Proxy request denied for remote client.");
			hitSensingPoint(Sensing.PROXY_REQUEST_DENIED);
		} else {
			Hashtable<String,String> parsedRequest = MiscTools.parseAdditional(urlparts[2]);

			String fileid	= parsedRequest.get("fileid");
			String token	= parsedRequest.get("token");
			String szGid	= parsedRequest.get("gid");
			String szPage	= parsedRequest.get("page");
			String passkey	= parsedRequest.get("passkey");
			String filename	= urlparts[3];

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
				try {
					int gid = Integer.parseInt(szGid);
					int page = Integer.parseInt(szPage);

					if(gid > 0 && page > 0) {
						HVFile requestedHVFile = session.getHTTPServer().getHentaiAtHomeClient().getCacheHandler().getHVFile(fileid, true);

						if( (requestedHVFile != null) && (requestedHVFile.getLocalFileRef().exists()) ) {
							hpc = new HTTPResponseProcessorFile(requestedHVFile);
							hitSensingPoint(Sensing.PROXY_REQUEST_LOCAL_FILE);
							requestHandled = true;
						}
						else {
							hpc = new HTTPResponseProcessorProxy(session, fileid, token, gid, page, filename);
							hitSensingPoint(Sensing.PROXY_REQUEST_PROXY_FILE);
							requestHandled = true;
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
		return requestHandled;
	}

	protected String calculateProxyKey(String fileid) {
		return MiscTools.getSHAString(
			fileid + "I think we can put our differences behind us." + MiscTools.getSHAString(Settings.getClientKey() + "For science.").substring(0, 10) + "You monster."
		).substring(0, 10);
	}

	protected void processServerCommand(String[] urlparts) {
		// form: /servercmd/$command/$additional/$time/$key

		if(!Settings.isValidRPCServer(session.getSocketInetAddress())) {
			Out.warning(session + " Got a servercmd from an unauthorized IP address: Denied");
			responseStatusCode = 403;
			hitSensingPoint(Sensing.SERVER_CMD_INVALID_RPC_SERVER);
			return;
		}
		else if(urlparts.length < 6) {
			Out.warning(session + " Got a malformed servercmd: Denied");
			responseStatusCode = 403;
			hitSensingPoint(Sensing.SERVER_CMD_MALFORMED_COMMAND);
			return;
		}
		else {
			String command = urlparts[2];
			String additional = urlparts[3];
			int commandTime = Integer.parseInt(urlparts[4]);
			String key = urlparts[5];

			int correctedTime = Settings.getServerTime();

			if((Math.abs(commandTime - correctedTime) < Settings.MAX_KEY_TIME_DRIFT) && calculateServercmdKey(command, additional, commandTime).equals(key)) {
				responseStatusCode = 200;
				servercmd = true;
				hpc = processRemoteAPICommand(command, additional);
				hitSensingPoint(Sensing.SERVER_CMD_KEY_VALID);
				return;
			}
			else {
				Out.warning(session + " Got a servercmd with expired or incorrect key: Denied");
				responseStatusCode = 403;
				hitSensingPoint(Sensing.SERVER_CMD_KEY_INVALID);
				return;
			}
		}
	}

	protected String calculateServercmdKey(String command, String additional, int commandTime) {
		return MiscTools.getSHAString("hentai@home-servercmd-" + command + "-" + additional + "-" + Settings.getClientID() + "-" + commandTime + "-" + Settings.getClientKey());
	}

	// accessors

	public int getResponseStatusCode() {
		return responseStatusCode;
	}

	public boolean isValidRequest() {
		return validRequest;
	}

	public boolean isRequestHeadOnly() {
		return requestHeadOnly;
	}

	public boolean isServercmd() {
		return servercmd;
	}
}
