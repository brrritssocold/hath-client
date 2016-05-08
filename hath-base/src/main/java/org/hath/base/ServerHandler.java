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

package org.hath.base;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

public class ServerHandler {
	public static final String ACT_SERVER_STAT = "server_stat";
	public static final String ACT_GET_BLACKLIST = "get_blacklist";
	public static final String ACT_CLIENT_LOGIN = "client_login";
	public static final String ACT_CLIENT_SETTINGS = "client_settings";
	public static final String ACT_CLIENT_START = "client_start";
	public static final String ACT_CLIENT_SUSPEND = "client_suspend";
	public static final String ACT_CLIENT_RESUME = "client_resume";
	public static final String ACT_CLIENT_STOP = "client_stop";
	public static final String ACT_STILL_ALIVE = "still_alive";
	public static final String ACT_FILE_UNCACHE = "file_uncache";
	public static final String ACT_FILE_REGISTER = "file_register";
	public static final String ACT_MORE_FILES = "more_files";
	public static final String ACT_FILE_TOKENS = "download_list";
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
				serverConnectionURL = new java.net.URL(Settings.CLIENT_API_URL + "clientbuild=" + Settings.CLIENT_BUILD + "&act=" + act);
			}
			else {
				int correctedTime = Settings.getServerTime();
				String actkey = MiscTools.getSHAString("hentai@home-" + act + "-" + add + "-" + Settings.getClientID() + "-" + correctedTime + "-" + Settings.getClientKey());
				serverConnectionURL = new java.net.URL(Settings.CLIENT_API_URL + "clientbuild=" + Settings.CLIENT_BUILD + "&act=" + act + "&add=" + add + "&cid=" + Settings.getClientID() + "&acttime=" + correctedTime + "&actkey=" + actkey);
			}
		} catch(java.net.MalformedURLException e) {
			HentaiAtHomeClient.dieWithError(e);
		}

		return serverConnectionURL;
	}

	// communications that do not use additional variables can use this
	private boolean simpleNotification(String act, String humanReadable) {
		ServerResponse sr = ServerResponse.getServerResponse(act, this);

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

	public boolean notifyMoreFiles() {
		return simpleNotification(ACT_MORE_FILES, "More Files");
	}


	// these communcation methods are more complex, and have their own result parsing

	public boolean notifyStart() {
		ServerResponse sr = ServerResponse.getServerResponse(ACT_CLIENT_START, this);

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_OK) {
			Out.info("Start notification successful. Note that there may be a short wait before the server registers this client on the network.");
			return true;
		}
		else {
			String failcode = sr.getFailCode();
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
			else if(failcode.startsWith("FAIL_STARTUP_FLOOD")) {
				Out.info("");
				Out.info("************************************************************************************************************************************");
				Out.info("Flood control is in effect.");
				Out.info("The client will automatically retry connecting in 90 seconds.");
				Out.info("************************************************************************************************************************************");
				Out.info("");

				try {
					Thread.sleep(90000);
				} catch(Exception e) {}

				return notifyStart();
			}
			else if(failcode.startsWith("FAIL_OTHER_CLIENT_CONNECTED")) {
				Out.info("");
				Out.info("************************************************************************************************************************************");
				Out.info("The server detected that another client was already connected from this computer or local network.");
				Out.info("You can only have one client running per public IP address.");
				Out.info("The program will now terminate.");
				Out.info("************************************************************************************************************************************");
				Out.info("");

				HentaiAtHomeClient.dieWithError("FAIL_OTHER_CLIENT_CONNECTED");
			}
			else if(failcode.startsWith("FAIL_CID_IN_USE")) {
				Out.info("");
				Out.info("************************************************************************************************************************************");
				Out.info("The server detected that another client is already using this client ident.");
				Out.info("If you want to run more than one client, you have to apply for additional idents.");
				Out.info("The program will now terminate.");
				Out.info("************************************************************************************************************************************");
				Out.info("");

				HentaiAtHomeClient.dieWithError("FAIL_CID_IN_USE");
			}
			else if(failcode.startsWith("FAIL_RESET_SUSPENDED")) {
				Out.info("");
				Out.info("************************************************************************************************************************************");
				Out.info("This client ident has been revoked for having too many cache resets.");
				Out.info("The program will now terminate.");
				Out.info("************************************************************************************************************************************");
				Out.info("");

				HentaiAtHomeClient.dieWithError("FAIL_RESET_SUSPENDED");
			}
		}

		return false;
	}

	public void notifyUncachedFiles(List<HVFile> deletedFiles) {
		// note: as we want to avoid POST, we do this as a long GET. to avoid exceeding certain URL length limitations, we uncache at most 50 files at a time
		int deleteCount = deletedFiles.size();

		if(deleteCount > 0) {
			Out.debug("Notifying server of " + deleteCount + " uncached files...");

			do {
				StringBuffer sb = new StringBuffer();
				int limiter = 0;

				while(deleteCount > 0 && ++limiter <= 50) {
					sb.append((limiter != 1 ? ";" : "") + deletedFiles.remove(--deleteCount).getFileid());
				}

				URL uncacheURL = getServerConnectionURL(ACT_FILE_UNCACHE, sb.toString());
				ServerResponse sr = ServerResponse.getServerResponse(uncacheURL, this);

				if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_OK) {
					Out.debug("Uncache notification successful.");
				}
				else {
					Out.warning("Uncache notification failed.");
				}
			} while(deleteCount > 0);
		}
	}

	public void notifyRegisterFiles(List<HVFile> pendingRegister) {
		int registerCount = pendingRegister.size();

		Out.debug("Notifying server of " + registerCount + " registered files...");

		StringBuffer sb = new StringBuffer();
		while(registerCount > 0) {
			sb.append((sb.length() > 0 ? ";" : "") + pendingRegister.remove(--registerCount).getFileid());
		}

		URL registerURL = getServerConnectionURL(ACT_FILE_REGISTER, sb.toString());
		ServerResponse sr = ServerResponse.getServerResponse(registerURL, this);

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_OK) {
			Out.debug("Register notification successful.");
		}
		else {
			Out.warning("Register notification failed.");
		}
	}

	public String[] getBlacklist(long deltatime) {
		URL blacklistURL = getServerConnectionURL(ACT_GET_BLACKLIST, "" + deltatime);
		ServerResponse sr = ServerResponse.getServerResponse(blacklistURL, this);

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_OK) {
			return sr.getResponseText();
		} else {
			return null;
		}
	}

	public void stillAliveTest() {
		CakeSphere cs = new CakeSphere(this, client);
		cs.stillAlive();
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

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_OK) {
			Settings.parseAndUpdateSettings(sr.getResponseText());
			Out.info("Finished applying settings");
			//client.getCacheHandler().recheckFreeDiskSpace();  - we're not bothering to recheck the free space as the client doesn't accept live reductions of disk space
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

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_OK) {
			Settings.parseAndUpdateSettings(sr.getResponseText());
			return true;
		}
		else {
			return false;
		}
	}

	public Hashtable<String,String> getFileTokens(List<String> requestTokens) {
		String tokens = "";

		for(String token : requestTokens) {
			tokens = tokens.concat(token + ";");
		}

		URL tokenURL = getServerConnectionURL(ACT_FILE_TOKENS, tokens);
		ServerResponse sr = ServerResponse.getServerResponse(tokenURL, this);

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_OK) {
			Hashtable<String,String> tokenTable = new Hashtable<String,String>();
			String[] split = sr.getResponseText();

			for(String s : split) {
				if(! s.isEmpty()) {
					String[] s2 = s.split(" ", 2);
					tokenTable.put(s2[0], s2[1]);
				}
			}

			return tokenTable;
		} else {
			Out.info("Could not grab token list - most likely the client has not been qualified yet. Will retry in a few minutes.");
			return null;
		}
	}

	public static boolean isLoginValidated() {
		return loginValidated;
	}


	// these functions do not communicate with the RPC server, but are actions triggered by it through servercmd

	public String downloadFilesFromServer(Hashtable<String,String> files) {
		StringBuffer returnText = new StringBuffer();
		Enumeration<String> fileids = files.keys();

		try {
			while(fileids.hasMoreElements()) {
				String file = fileids.nextElement();
				String key = files.get(file);

				String[] s = file.split(":");
				String fileid = s[0];
				String host = s[1];

				// verify that we have valid ID and Key before we build an URL from it, in case the server has been compromised somehow...
				if(HVFile.isValidHVFileid(fileid) && key.matches("^[0-9]{6}-[a-z0-9]{40}$")) {
					URL source = new URL("http", host, 80, "/image.php?f=" + fileid + "&t=" + key);

					if(downloadAndCacheFile(source, fileid)) {
						returnText.append(fileid + ":OK\n");
					}
					else {
						returnText.append(fileid + ":FAIL\n");
					}
				}
				else {
					returnText.append(fileid + ":INVALID\n");
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
			Out.warning("Encountered error " + e + " when downloading image files from server. Will not retry.");
		}

		return returnText.toString();
	}

	public String doThreadedProxyTest(String ipaddr, int port, int testsize, int testcount, int testtime, String testkey) {
		int successfulTests = 0;
		long totalTimeMillis = 0;
		
		Out.debug("Running threaded proxy test against ipaddr=" + ipaddr + " port=" + port + " testsize=" + testsize + " testcount=" + testcount + " testtime=" + testtime + " testkey=" + testkey);

		try {
			List<FileDownloader> testfiles = Collections.checkedList(new ArrayList<FileDownloader>(), FileDownloader.class);

			for(int i=0; i<testcount; i++) {
				URL source = new URL("http", ipaddr, port, "/t/" + testsize + "/" + testtime + "/" + testkey + "/" + (int) Math.floor(Math.random() * Integer.MAX_VALUE));
				Out.debug("Test thread: " + source);
				FileDownloader dler = new FileDownloader(source, 10000, 60000);
				testfiles.add(dler);
				dler.startAsyncDownload();
			}

			for(FileDownloader dler : testfiles) {
				if(dler.waitAsyncDownload()) {
					successfulTests += 1;
					totalTimeMillis += dler.getDownloadTimeMillis();
				}
			}
		} catch(java.net.MalformedURLException e) {
			HentaiAtHomeClient.dieWithError(e);
		}

		return "OK:" + successfulTests + "-" + totalTimeMillis;
	}

	public String doProxyTest(String ipaddr, int port, String fileid, String keystamp) {
		if(!HVFile.isValidHVFileid(fileid)) {
			Out.error("Encountered an invalid fileid in doProxyTest: " + fileid);
			return fileid + ":INVALID-0";
		}

		try {
			URL source = new URL("http", ipaddr, port, "/h/" + fileid + "/keystamp=" + keystamp + "/test.jpg");
			Out.info("Running a proxy test against " + source + ".");

			// determine the approximate ping time to the other client (if available, done on a best-effort basis). why isn't there a built-in ping in java anyway?
			int pingtime = 0;

			// juuuuuust in case someone manages to inject a faulty IP address, we don't want to pass that unsanitized to an exec
			if(!ipaddr.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
				Out.warning("Invalid IP address: " + ipaddr);
			}
			else {
				// make an educated guess on OS to access the built-in ping utility
				String pingcmd = null;
				String whichOS = System.getProperty("os.name");

				if(whichOS != null) {
					if(whichOS.toLowerCase().indexOf("windows") > -1) {
						// windows style
						pingcmd = "ping -n 3 " + ipaddr;
					}
				}

				if(pingcmd == null) {
					// linux/unix/bsd/macos style
					pingcmd = "ping -c 3 " + ipaddr;
				}

				Process p = null;
				InputStreamReader isr = null;
				BufferedReader br = null;
				int pingresult = 0;
				int pingcount = 0;

				try {
					p = java.lang.Runtime.getRuntime().exec(pingcmd);
					isr = new InputStreamReader(p.getInputStream());
					br = new BufferedReader(isr);

					String read = null;

					while((read = br.readLine()) != null) {
						// try to parse the ping result and extract the result. this will work as long as the time is enclosed between "time=" and "ms", which it should be both in windows and linux. YMMV.
						int indexTime = read.indexOf("time=");

						if(indexTime >= 0) {
							int indexNumStart = indexTime + 5;
							int indexNumEnd = read.indexOf("ms", indexNumStart);

							if(indexNumStart > 0 && indexNumEnd > 0) {
								// parsing as double then casting, since linux gives a decimal number while windows doesn't
								pingresult += (int) Double.parseDouble(read.substring(indexNumStart, indexNumEnd).trim());
								++pingcount;
							}
						}
					}

					if(pingcount > 0) {
						pingtime = pingresult / pingcount;
					}
				} catch(Exception e) {
					Out.debug("Encountered exception " + e + " while trying to ping remote client");
				} finally {
					try { br.close(); isr.close(); p.destroy(); } catch(Exception e) {}
				}
			}

			if(pingtime > 0) {
				Out.debug("Approximate latency determined as ~" + pingtime + " ms");
			}
			else {
				Out.debug("Could not determine latency, conservatively guessing 20ms");
				pingtime = 20;	// little to no compensation
			}

			long startTime = System.currentTimeMillis();

			if(downloadAndCacheFile(source, fileid)) {
				// this is mostly trial-and-error. we cut off 3 times the ping directly for TCP overhead (TCP three-way handshake + request/1st byte delay) , as well as cut off a factor of (1 second - pingtime) . this is capped to 200ms ping.
				long dlMillis = System.currentTimeMillis() - startTime;
				pingtime = Math.min(200, pingtime);
				double dlTime = Math.max(0, ((dlMillis * (1.0 - pingtime / 1000.0) - pingtime * 3) / 1000.0));
				Out.debug("Clocked a download time of " + dlMillis + " ms. Ping delay fiddling reduced estimate to " + dlTime + " seconds.");
				return fileid + ":OK-" + dlTime;
			}
		} catch(Exception e) {
			Out.warning("Encountered error " + e + " when doing proxy test against " + ipaddr + ":" + port + " on file " + fileid + ". Will not retry.");
		}

		return fileid + ":FAIL-0";
	}

	// used by doProxyTest and downloadFilesFromServer
	private boolean downloadAndCacheFile(URL source, String fileid) {
		if(HVFile.isValidHVFileid(fileid)) {
			CacheHandler ch = client.getCacheHandler();
			File tmpfile = new File(CacheHandler.getTmpDir(), fileid);

			if(tmpfile.exists()) {
				tmpfile.delete();
			}

			FileDownloader dler = new FileDownloader(source, 10000, 30000);

			if(dler.saveFile(tmpfile)) {
				HVFile hvFile = HVFile.getHVFileFromFile(tmpfile, true);

				if(hvFile != null) {
					if(!hvFile.getLocalFileRef().exists()) {
						if(ch.moveFileToCacheDir(tmpfile, hvFile)) {
							ch.addFileToActiveCache(hvFile);
							Out.info("The file " + fileid + " was successfully downloaded and inserted into the active cache.");
						}
						else {
							Out.warning("Failed to insert " + fileid + " into cache.");
							tmpfile.delete();
							// failed to move, but didn't exist.. so we'll fail
							return false;
						}
					}
					else {
						Out.info("The file " + fileid + " was successfully downloaded, but already exists in the cache.");
						tmpfile.delete();
					}

					// if the file was inserted, or if it exists, we'll call it a success
					Stats.fileRcvd();
					return true;
				}
				else {
					Out.warning("Downloaded file " + fileid + " failed hash verification. Will not retry.");
				}
			}
			else {
				Out.warning("Failed downloading file " + fileid + " from " + source + ". Will not retry.");
			}

			if(tmpfile.exists()) {
				tmpfile.delete();
			}
		}
		else {
			Out.warning("Encountered invalid fileid " + fileid);
		}

		return false;
	}
}
