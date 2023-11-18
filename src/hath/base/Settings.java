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

import java.io.File;
import java.net.InetAddress;
import java.util.*;
import java.lang.*;

public class Settings {
	public static final String NEWLINE = System.getProperty("line.separator");

	// the client build is among other things used by the server to determine the client's capabilities. any forks should use the build number as an indication of compatibility with mainline, rather than an internal build number.
	public static final int CLIENT_BUILD = 160;
	public static final int CLIENT_KEY_LENGTH = 20;
	public static final int MAX_KEY_TIME_DRIFT = 300;
	public static final int MAX_CONNECTION_BASE = 20;
	public static final int TCP_PACKET_SIZE = 1460;

	public static final String CLIENT_VERSION = "1.6.2";
	public static final String CLIENT_RPC_PROTOCOL = "http://";
	public static final String CLIENT_RPC_HOST = "rpc.hentaiathome.net";
	public static final String CLIENT_LOGIN_FILENAME = "client_login";
	public static final String CONTENT_TYPE_DEFAULT = "text/html; charset=iso-8859-1";

	private static HentaiAtHomeClient activeClient = null;
	private static HathGUI activeGUI = null;
	private static Object rpcServerLock = new Object();
	private static InetAddress rpcServers[] = null;
	private static String rpcServerCurrent = null, rpcServerLastFailed = null;
	private static Hashtable<String, Integer> staticRanges = null;
	private static File datadir = null, logdir = null, cachedir = null, tempdir = null, downloaddir = null;
	private static String clientKey = "", clientHost = "", dataDirPath = "data", logDirPath = "log", cacheDirPath = "cache", tempDirPath = "tmp", downloadDirPath = "download", rpcPath = "15/rpc?";

	private static int clientID = 0, clientPort = 0, throttle_bytes = 0, overrideConns = 0, serverTimeDelta = 0, maxAllowedFileSize = 1073741824, currentStaticRangeCount = 0;
	private static long disklimit_bytes = 0, diskremaining_bytes = 0, fileSystemBlocksize = 4096;
	private static boolean verifyCache = false, rescanCache = false, skipFreeSpaceCheck = false, warnNewClient = false, useLessMemory = false, disableBWM = false, disableDownloadBWM = false, disableLogs = false, flushLogs = false, disableIPOriginCheck = false, disableFloodControl = false;

	public static void setActiveClient(HentaiAtHomeClient client) {
		activeClient = client;
	}

	public static void setActiveGUI(HathGUI gui) {
		activeGUI = gui;
	}

	public static boolean loginCredentialsAreSyntaxValid() {
		return clientID > 0 && java.util.regex.Pattern.matches("^[a-zA-Z0-9]{" + CLIENT_KEY_LENGTH + "}$", clientKey);
	}

	public static boolean loadClientLoginFromFile() {
		File clientLogin = new File(getDataDir(), CLIENT_LOGIN_FILENAME);

		if(!clientLogin.exists()) {
			return false;
		}

		try {
			String filecontent = Tools.getStringFileContents(clientLogin);

			if(!filecontent.isEmpty()) {
				String[] split = filecontent.split("-", 2);

				if(split.length == 2) {
					clientID = Integer.parseInt(split[0]);
					clientKey = split[1];
					Out.info("Loaded login settings from " + CLIENT_LOGIN_FILENAME);

					return true;
				}
			}
		}
		catch(Exception e) {
			Out.warning("Encountered error when reading " + CLIENT_LOGIN_FILENAME + ": " + e);
		}

		return false;
	}

	public static void promptForIDAndKey(InputQueryHandler iqh) {
		Out.info("Before you can use this client, you will have to register it at https://e-hentai.org/hentaiathome.php");
		Out.info("IMPORTANT: YOU NEED A SEPARATE IDENT FOR EACH CLIENT YOU WANT TO RUN.");
		Out.info("DO NOT ENTER AN IDENT THAT WAS ASSIGNED FOR A DIFFERENT CLIENT UNLESS IT HAS BEEN RETIRED.");
		Out.info("After registering, enter your ID and Key below to start your client.");
		Out.info("(You will only have to do this once.)\n");

		clientID = 0;
		clientKey = "";

		do {
			try {
				clientID = Integer.parseInt(iqh.queryString("Enter Client ID").trim());
			}
			catch(java.lang.NumberFormatException nfe) {
				Out.warning("Invalid Client ID. Please try again.");
			}
		} while(clientID < 1000);

		do {
			clientKey = iqh.queryString("Enter Client Key").trim();

			if(!loginCredentialsAreSyntaxValid()) {
				Out.warning("Invalid Client Key, it must be exactly 20 alphanumerical characters. Please try again.");
			}
		} while(!loginCredentialsAreSyntaxValid());

		try {
			Tools.putStringFileContents(new File(getDataDir(), CLIENT_LOGIN_FILENAME), clientID + "-" + clientKey);
		}
		catch(java.io.IOException ioe) {
			Out.warning("Error encountered when writing " + CLIENT_LOGIN_FILENAME + ": " + ioe);
		}
	}

	public static boolean parseAndUpdateSettings(String[] settings) {
		if(settings == null) {
			return false;
		}

		for(String s : settings) {
			if(s != null) {
				String[] split = s.split("=", 2);

				if(split.length == 2) {
					updateSetting(split[0].toLowerCase(), split[1]);
				}
			}
		}

		return true;
	}

	// note that these settings will currently be overwritten by any equal ones read from the server, so it should not be used to override server-side settings.
	public static boolean parseArgs(String[] args) {
		if(args == null) {
			return false;
		}

		for(String s : args) {
			if(s != null) {
				if(s.startsWith("--")) {
					String[] split = s.substring(2).split("=", 2);

					if(split.length == 2) {
						updateSetting(split[0].toLowerCase(), split[1]);
					}
					else {
						updateSetting(split[0].toLowerCase(), "true");
					}
				}
				else {
					Out.warning("Invalid command argument: " + s);
				}
			}
		}

		return true;
	}

	public static boolean updateSetting(String setting, String value) {
		setting = setting.replace("-", "_");

		try {
			if(setting.equals("min_client_build")) {
				if(Integer.parseInt(value) > CLIENT_BUILD) {
					HentaiAtHomeClient.dieWithError("Your client is too old to connect to the Hentai@Home Network. Please download the new version of the client from http://hentaiathome.net/");
				}
			}
			else if(setting.equals("cur_client_build")) {
				if(Integer.parseInt(value) > CLIENT_BUILD) {
					warnNewClient = true;
				}
			}
			else if(setting.equals("server_time")) {
				serverTimeDelta = Integer.parseInt(value) - (int) (System.currentTimeMillis() / 1000);
				Out.debug("Setting altered: serverTimeDelta=" + serverTimeDelta);

				return true;
			}
			else if(setting.equals("rpc_server_ip")) {
				synchronized(rpcServerLock) {
					String[] split = value.split(";");
					rpcServers = new java.net.InetAddress[split.length];
					int i = 0;
					for(String s : split) {
						rpcServers[i++] = java.net.InetAddress.getByName(s);
					}
					
					rpcServerCurrent = null;
				}
			}
			else if(setting.equals("rpc_path")) {
				rpcPath = value;
			}
			else if(setting.equals("host")) {
				clientHost = value;
			}
			else if(setting.equals("port")) {
				if( clientPort == 0 ) {
					clientPort = Integer.parseInt(value);
				}
			}
			else if(setting.equals("throttle_bytes")) {
				// THIS SHOULD NOT BE ALTERED BY THE CLIENT AFTER STARTUP. Using the website interface will update the throttle value for the dispatcher first, and update the client on the first stillAlive test.
				throttle_bytes = Integer.parseInt(value);
			}
			else if(setting.equals("disklimit_bytes")) {
				long newLimit = Long.parseLong(value);

				if(newLimit >= disklimit_bytes) {
					disklimit_bytes = newLimit;
				}
				else {
					Out.warning("The disk limit has been reduced. However, this change will not take effect until you restart your client.");
				}
			}
			else if(setting.equals("diskremaining_bytes")) {
				diskremaining_bytes = Long.parseLong(value);
			}
			else if(setting.equals("filesystem_blocksize")) {
				fileSystemBlocksize = Long.parseLong(value);
				
				if(fileSystemBlocksize < 0 || fileSystemBlocksize > 65536) {
					Out.warning("A filesystem blocksize of " + fileSystemBlocksize + " bytes is not sane. Using the default of 4096 bytes.");
					fileSystemBlocksize = 4096;
				}
			}
			else if(setting.equals("rescan_cache")) {
				rescanCache = value.equals("true");
			}
			else if(setting.equals("verify_cache")) {
				verifyCache = value.equals("true");
				rescanCache = value.equals("true");
			}
			else if(setting.equals("use_less_memory")) {
				useLessMemory = value.equals("true");
			}
			else if(setting.equals("disable_logging")) {
				disableLogs = value.equals("true");
				Out.disableLogging();
			}
			else if(setting.equals("disable_bwm")) {
				disableBWM = value.equals("true");
				disableDownloadBWM = value.equals("true");
			}
			else if(setting.equals("disable_download_bwm")) {
				disableDownloadBWM = value.equals("true");
			}
			else if(setting.equals("disable_ip_origin_check")) {
				disableIPOriginCheck = value.equals("true");
			}
			else if(setting.equals("disable_flood_control")) {
				disableFloodControl = value.equals("true");
			}
			else if(setting.equals("skip_free_space_check")) {
				skipFreeSpaceCheck = value.equals("true");
			}
			else if(setting.equals("max_connections")) {
				overrideConns = Integer.parseInt(value);
			}
			else if(setting.equals("max_allowed_filesize")) {
				maxAllowedFileSize = Integer.parseInt(value);
			}
			else if(setting.equals("static_ranges")) {
				staticRanges = new Hashtable<String,Integer>((int) (value.length() * 0.3));
				currentStaticRangeCount = 0;

				for(String s : value.split(";")) {
					if(s.length() == 4) {
						++currentStaticRangeCount;
						staticRanges.put(s, 1);
					}
				}
			}
			else if(setting.equals("cache_dir")) {
				cacheDirPath = value;
			}
			else if(setting.equals("temp_dir")) {
				tempDirPath = value;
			}
			else if(setting.equals("data_dir")) {
				dataDirPath = value;
			}
			else if(setting.equals("log_dir")) {
				logDirPath = value;
			}
			else if(setting.equals("download_dir")) {
				downloadDirPath = value;
			}
			else if(setting.equals("flush_logs")) {
				flushLogs = value.equals("true");
			}
			else if(!setting.equals("silentstart")) {
				// don't flag errors if the setting is handled by the GUI
				Out.warning("Unknown setting " + setting + " = " + value);
				return false;
			}

			Out.debug("Setting altered: " + setting +"=" + value);
			return true;
		} catch(Exception e) {
			Out.warning("Failed parsing setting " + setting + " = " + value);
		}

		return false;
	}

	public static void initializeDirectories() throws java.io.IOException {
		Out.debug("Using --data-dir=" + dataDirPath);
		datadir = Tools.checkAndCreateDir(new File(dataDirPath));

		Out.debug("Using --log-dir=" + logDirPath);
		logdir = Tools.checkAndCreateDir(new File(logDirPath));

		Out.debug("Using --cache-dir=" + cacheDirPath);
		cachedir = Tools.checkAndCreateDir(new File(cacheDirPath));

		Out.debug("Using --temp-dir=" + tempDirPath);
		tempdir = Tools.checkAndCreateDir(new File(tempDirPath));

		Out.debug("Using --download-dir=" + downloadDirPath);
		downloaddir = Tools.checkAndCreateDir(new File(downloadDirPath));
	}

	// accessor methods
	public static File getDataDir() {
		return datadir;
	}

	public static File getLogDir() {
		return logdir;
	}

	public static File getCacheDir() {
		return cachedir;
	}

	public static File getTempDir() {
		return tempdir;
	}

	public static File getDownloadDir() {
		return downloaddir;
	}

	public static int getClientID() {
		return clientID;
	}

	public static String getClientKey() {
		return clientKey;
	}

	public static String getClientHost() {
		return clientHost;
	}

	public static int getClientPort() {
		return clientPort;
	}

	public static int getThrottleBytesPerSec() {
		return throttle_bytes;
	}

	public static int getMaxAllowedFileSize() {
		return maxAllowedFileSize;
	}

	public static long getDiskLimitBytes() {
		return disklimit_bytes;
	}

	public static long getDiskMinRemainingBytes() {
		return diskremaining_bytes;
	}

	public static long getFileSystemBlockSize() {
		return fileSystemBlocksize;
	}

	public static int getServerTime() {
		return (int) (System.currentTimeMillis() / 1000) + serverTimeDelta;
	}
	
	public static int getServerTimeDelta() {
		return serverTimeDelta;
	}

	public static String getOutputLogPath() {
		return getLogDir().getPath() + "/log_out";
	}

	public static String getErrorLogPath() {
		return getLogDir().getPath() + "/log_err";
	}
	
	public static boolean isFlushLogs() {
		return flushLogs;
	}

	public static boolean isRescanCache() {
		return rescanCache;
	}

	public static boolean isVerifyCache() {
		return verifyCache;
	}

	public static boolean isUseLessMemory() {
		return useLessMemory;
	}

	public static boolean isSkipFreeSpaceCheck() {
		return skipFreeSpaceCheck;
	}

	public static boolean isWarnNewClient() {
		return warnNewClient;
	}

	public static boolean isDisableBWM() {
		return disableBWM;
	}

	public static boolean isDisableDownloadBWM() {
		return disableDownloadBWM;
	}

	public static boolean isDisableLogs() {
		return disableLogs;
	}
	
	public static boolean isDisableIPOriginCheck() {
		return disableIPOriginCheck;
	}

	public static boolean isDisableFloodControl() {
		return disableFloodControl;
	}

	public static HentaiAtHomeClient getActiveClient() {
		return activeClient;
	}

	public static HathGUI getActiveGUI() {
		return activeGUI;
	}

	public static boolean isValidRPCServer(InetAddress compareTo) {
		if(disableIPOriginCheck) {
			return true;
		}
		
		synchronized(rpcServerLock) {
			if(rpcServers == null) {
				return false;
			}

			for(InetAddress i : rpcServers) {
				if(i.equals(compareTo)) {
					return true;
				}
			}

			return false;
		}
	}

	public static String getRPCPath() {
		return rpcPath;
	}

	public static String getRPCServerHost() {
		synchronized(rpcServerLock) {
			if(rpcServerCurrent == null) {
				if(rpcServers == null) {
					return Settings.CLIENT_RPC_HOST;
				}

				if(rpcServers.length < 1) {
					return Settings.CLIENT_RPC_HOST;
				}

				if(rpcServers.length == 1) {
					rpcServerCurrent = rpcServers[0].getHostAddress().toLowerCase();
				}
				else {
					int rpcServerSelector = (int) (Math.random() * rpcServers.length);
					int scanDirection = Math.random() < 0.5 ? -1 : 1;

					while(true) {
						String candidate = rpcServers[(rpcServers.length + rpcServerSelector) % rpcServers.length].getHostAddress().toLowerCase();

						if(rpcServerLastFailed != null) {
							if(candidate.equals(rpcServerLastFailed)) {
								Out.debug(rpcServerLastFailed + " was marked as last failed");
								rpcServerSelector += scanDirection;
								continue;
							}
						}

						rpcServerCurrent = candidate;
						Out.debug("Selected rpcServerCurrent=" + rpcServerCurrent);
						break;
					}
				}
			}

			return rpcServerCurrent;
		}
	}

	public static void clearRPCServerFailure() {
		synchronized(rpcServerLock) {
			if(rpcServerLastFailed != null) {
				// to avoid long-term uneven loads on the RPC servers in case one of them goes down for a bit, we run this occasionally to clear the failure
				Out.debug("Cleared rpcServerLastFailed");
				rpcServerLastFailed = null;
				rpcServerCurrent = null;
			}
		}
	}
	
	public static void markRPCServerFailure(String failHost) {
		synchronized(rpcServerLock) {
			if(rpcServerCurrent != null) {
				Out.debug("Marking " + failHost + " as rpcServerLastFailed");
				rpcServerLastFailed = failHost;
				rpcServerCurrent = null;
			}
		}
	}

	public static int getMaxConnections() {
		if(overrideConns > 0) {
			return overrideConns;
		} else {
			// throttle_bytes was changed to a required value several years ago
			return MAX_CONNECTION_BASE + Math.min(480, (int) (throttle_bytes / 10000));
		}
	}

	public static boolean isStaticRange(String fileid) {
		if(staticRanges != null) {
			// hashtable is thread-safe
			return staticRanges.containsKey(fileid.substring(0, 4));
		}

		return false;
	}

	public static int getStaticRangeCount() {
		return currentStaticRangeCount;
	}
}
