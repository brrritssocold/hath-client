/*

Copyright 2008-2023 E-Hentai.org
https://forums.e-hentai.org/
tenboro@e-hentai.org

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

package hath.base;

import java.net.URL;
import java.lang.StringBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ServerHandler {
	public static final String ACT_SERVER_STAT = "server_stat";
	public static final String ACT_GET_BLACKLIST = "get_blacklist";
	public static final String ACT_GET_CERTIFICATE = "get_cert";
	public static final String ACT_CLIENT_LOGIN = "client_login";
	public static final String ACT_CLIENT_SETTINGS = "client_settings";
	public static final String ACT_CLIENT_START = "client_start";
	public static final String ACT_CLIENT_SUSPEND = "client_suspend";
	public static final String ACT_CLIENT_RESUME = "client_resume";
	public static final String ACT_CLIENT_STOP = "client_stop";
	public static final String ACT_STILL_ALIVE = "still_alive";
	public static final String ACT_STATIC_RANGE_FETCH = "srfetch";
	public static final String ACT_DOWNLOADER_FETCH = "dlfetch";
	public static final String ACT_DOWNLOADER_FAILREPORT = "dlfails";
	public static final String ACT_OVERLOAD = "overload";

	private HentaiAtHomeClient client;
	private static boolean loginValidated = false;
	private long lastOverloadNotification;

	public ServerHandler(HentaiAtHomeClient client) {
		this.client = client;
		lastOverloadNotification = 0;
	}

	public static URL getServerConnectionURL(String act) {
		return getServerConnectionURL(act, "");
	}

	public static URL getServerConnectionURL(String act, String add) {
		URL serverConnectionURL = null;

		try {
			if(act.equals(ACT_SERVER_STAT)) {
				serverConnectionURL = new URL(Settings.CLIENT_RPC_PROTOCOL + Settings.getRPCServerHost() + "/" + Settings.getRPCPath() + "clientbuild=" + Settings.CLIENT_BUILD + "&act=" + act);
			}
			else {
				serverConnectionURL = new URL(Settings.CLIENT_RPC_PROTOCOL + Settings.getRPCServerHost() + "/" + Settings.getRPCPath() + getURLQueryString(act, add));
			}
		} catch(java.net.MalformedURLException e) {
			HentaiAtHomeClient.dieWithError(e);
		}

		return serverConnectionURL;
	}

	public static String getURLQueryString(String act, String add) {
		int correctedTime = Settings.getServerTime();
		String actkey = Tools.getSHA1String("hentai@home-" + act + "-" + add + "-" + Settings.getClientID() + "-" + correctedTime + "-" + Settings.getClientKey());
		return "clientbuild=" + Settings.CLIENT_BUILD + "&act=" + act + "&add=" + add + "&cid=" + Settings.getClientID() + "&acttime=" + correctedTime + "&actkey=" + actkey;
	}

	// communications that do not use additional variables can use this
	private boolean simpleNotification(String act, String humanReadable) {
		ServerResponse sr = ServerResponse.getServerResponse(act, this);

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_NULL) {
			Settings.markRPCServerFailure(sr.getFailHost());
		}

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_OK) {
			Out.debug(humanReadable + " notification successful.");
			return true;
		}
		else {
			Out.warning(humanReadable + " notification failed.");
			return false;
		}

	}


	// simple notifications

	public boolean notifySuspend() {
		return simpleNotification(ACT_CLIENT_SUSPEND, "Suspend");
	}

	public boolean notifyResume() {
		return simpleNotification(ACT_CLIENT_RESUME, "Resume");
	}

	public boolean notifyShutdown() {
		return simpleNotification(ACT_CLIENT_STOP, "Shutdown");
	}

	public boolean notifyOverload() {
		long nowtime = System.currentTimeMillis();

		if(lastOverloadNotification < nowtime - 30000) {
			lastOverloadNotification = nowtime;
			return simpleNotification(ACT_OVERLOAD, "Overload");
		}

		return false;
	}


	// these communcation methods are more complex, and have their own result parsing

	public boolean notifyStart() {
		ServerResponse sr = ServerResponse.getServerResponse(ACT_CLIENT_START, this);

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_NULL) {
			Settings.markRPCServerFailure(sr.getFailHost());
		}

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_OK) {
			Out.info("Start notification successful. Note that there may be a short wait before the server registers this client on the network.");
			Stats.serverContact();
			return true;
		}
		else {
			String failcode = sr.getFailCode();

			Out.warning("Startup Failure: " + failcode);
			Out.debug(sr.toString());

			if(failcode.startsWith("FAIL_CONNECT_TEST")) {
				Out.info("");
				Out.info("************************************************************************************************************************************");
				Out.info("The client has failed the external connection test.");
				Out.info("The server failed to verify that this client is online and available from the Internet.");
				Out.info("If you are behind a firewall, please check that port " + Settings.getClientPort() + " is forwarded to this computer.");
				Out.info("You might also want to check that " + Settings.getClientHost() + " is your actual public IP address.");
				Out.info("If you need assistance with forwarding a port to this client, locate a guide for your particular router at http://portforward.com/");
				Out.info("The client will remain running so you can run port connection tests.");
				Out.info("Use Program -> Exit in windowed mode or hit Ctrl+C in console mode to exit the program.");
				Out.info("************************************************************************************************************************************");
				Out.info("");

				return false;
			}
			else if(failcode.startsWith("FAIL_OTHER_CLIENT_CONNECTED")) {
				Out.info("");
				Out.info("************************************************************************************************************************************");
				Out.info("The server detected that another client was already connected from this computer or local network.");
				Out.info("You can only have one client running per public IP address.");
				Out.info("The program will now terminate.");
				Out.info("************************************************************************************************************************************");
				Out.info("");

				client.dieWithError("FAIL_OTHER_CLIENT_CONNECTED");
			}
			else if(failcode.startsWith("FAIL_CID_IN_USE")) {
				Out.info("");
				Out.info("************************************************************************************************************************************");
				Out.info("The server detected that another client is already using this client ident.");
				Out.info("If you want to run more than one client, you have to apply for additional idents.");
				Out.info("The program will now terminate.");
				Out.info("************************************************************************************************************************************");
				Out.info("");

				client.dieWithError("FAIL_CID_IN_USE");
			}
		}

		return false;
	}

	public String[] getBlacklist(long deltatime) {
		URL blacklistURL = getServerConnectionURL(ACT_GET_BLACKLIST, "" + deltatime);
		ServerResponse sr = ServerResponse.getServerResponse(blacklistURL, this);

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_NULL) {
			Settings.markRPCServerFailure(sr.getFailHost());
		}

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_OK) {
			return sr.getResponseText();
		}

		return null;
	}

	public void stillAliveTest(boolean resume) {
		CakeSphere cs = new CakeSphere(this, client);
		cs.stillAlive(resume);
	}

	// this MUST NOT be called after the client has started up, as it will clear out and reset the client on the server, leaving the client in a limbo until restart
	public void loadClientSettingsFromServer() {
		Stats.setProgramStatus("Loading settings from server...");
		Out.info("Connecting to the Hentai@Home Server to register client with ID " + Settings.getClientID() + "...");

		try {
			do {
				if(!refreshServerStat()) {
					HentaiAtHomeClient.dieWithError("Failed to get initial stat from server.");
				}

				Out.info("Reading Hentai@Home client settings from server...");
				ServerResponse sr = ServerResponse.getServerResponse(ServerHandler.ACT_CLIENT_LOGIN, this);

				if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_OK) {
					loginValidated = true;
					Out.info("Applying settings...");
					Settings.parseAndUpdateSettings(sr.getResponseText());
					Out.info("Finished applying settings");
				}
				else if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_NULL) {
					HentaiAtHomeClient.dieWithError("Failed to get a login response from server.");
				}
				else {
					Out.warning("\nAuthentication failed, please re-enter your Client ID and Key (Code: " + sr.getFailCode() + ")");
					Settings.promptForIDAndKey(client.getInputQueryHandler());
				}
			} while(!loginValidated);
		} catch(Exception e) {
			HentaiAtHomeClient.dieWithError(e);
		}
	}

	public boolean refreshServerSettings() {
		Out.info("Refreshing Hentai@Home client settings from server...");
		ServerResponse sr = ServerResponse.getServerResponse(ServerHandler.ACT_CLIENT_SETTINGS, this);

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_NULL) {
			Settings.markRPCServerFailure(sr.getFailHost());
		}

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_OK) {
			Settings.parseAndUpdateSettings(sr.getResponseText());
			Out.info("Finished applying settings");
			return true;
		}
		else {
			Out.warning("Failed to refresh settings");
			return false;
		}
	}

	public boolean refreshServerStat() {
		Stats.setProgramStatus("Getting initial stats from server...");
		// get timestamp and minimum client build from server
		ServerResponse sr = ServerResponse.getServerResponse(ServerHandler.ACT_SERVER_STAT, this);

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_NULL) {
			Settings.markRPCServerFailure(sr.getFailHost());
		}

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_OK) {
			Settings.parseAndUpdateSettings(sr.getResponseText());
			return true;
		}
		else {
			return false;
		}
	}

	public URL[] getStaticRangeFetchURL(String fileindex, String xres, String fileid) {
		URL requestURL = getServerConnectionURL(ACT_STATIC_RANGE_FETCH, fileindex + ";" + xres + ";" + fileid);
		ServerResponse sr = ServerResponse.getServerResponse(requestURL, this);

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_NULL) {
			Settings.markRPCServerFailure(sr.getFailHost());
		}

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_OK) {
			String[] response = sr.getResponseText();

			try {
				List<URL> urls = Collections.checkedList(new ArrayList<URL>(), URL.class);

				for(String s : response) {
					if(!s.equals("")) {
						urls.add(new URL(s));
					}
				}
				
				return urls.isEmpty() ? null : urls.toArray(new URL[urls.size()]);
			} catch(Exception e) {}
		}

		Out.info("Failed to request static range download link for " + fileid + ".");
		return null;
	}

	public URL getDownloaderFetchURL(int gid, int page, int fileindex, String xres, boolean forceImageServer) {
		URL requestURL = getServerConnectionURL(ACT_DOWNLOADER_FETCH, gid + ";" + page + ";" + fileindex + ";" + xres + ";" + (forceImageServer ? 1 : 0));
		ServerResponse sr = ServerResponse.getServerResponse(requestURL, this);

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_NULL) {
			Settings.markRPCServerFailure(sr.getFailHost());
		}

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_OK) {
			String[] response = sr.getResponseText();

			try {
				return new URL(response[0]);
			} catch(Exception e) {}
		}

		Out.info("Failed to request gallery file url for fileindex=" + fileindex + ".");
		return null;
	}

	public void reportDownloaderFailures(List<String> failures) {
		if(failures == null) {
			return;
		}

		int failcount = failures.size();

		if(failcount < 1 || failcount > 50) {
			// if we're getting a lot of distinct failures, it's probably a problem with this client
			return;
		}

		StringBuilder s = new StringBuilder(failcount * 30);
		int i = 0;

		for(String failure : failures) {
			s.append(failure);

			if(++i < failcount) {
				s.append(";");
			}
		}

		ServerResponse sr = ServerResponse.getServerResponse(getServerConnectionURL(ACT_DOWNLOADER_FAILREPORT, s.toString()), this);

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_NULL) {
			Settings.markRPCServerFailure(sr.getFailHost());
		}

		Out.debug("Reported " + failcount + " download failures with response " + (sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_OK ? "OK" : "FAIL"));
	}

	public static boolean isLoginValidated() {
		return loginValidated;
	}
}
