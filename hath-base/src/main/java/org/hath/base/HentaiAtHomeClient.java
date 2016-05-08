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

/*

- Moved log write synchronization to prevent rare errors when rotating logs.

- TCP sending buffers are now calculated dynamically. Note that for low-memory clients, making sure the throttle is set correctly (even if disabled) will avoid wasting RAM.

- When H@H is started with the --port=<num> argument, the port is no longer overridden by the startup sequence. This enables an easy hack to run H@H on port 80 on Linux as a non-privileged user by using a local forwarding rule, such as:

[code]-A INPUT -p tcp --dport 80 -j ACCEPT
-A INPUT -p tcp --dport 7777 -j ACCEPT
-A PREROUTING -t nat -i eth0 -p tcp --dport 80 -j REDIRECT --to-port 7777[/code]

- The startup check for static ranges will now only fail if the cache is empty and more than 20 ranges are set. (The absolute strictness could cause problems under some circumstances.)

- Added mime-type for webm.


[b]To update an existing client: shut it down, download [url=http://hentaiathome.net/get/HentaiAtHome_1.2.6.zip]Hentai@Home 1.2.6[/url], extract the archive, copy the jar files over the existing ones, then restart the client.[/b]

[b]For information on how to join Hentai@Home, check out [url=http://forums.e-hentai.org/index.php?showtopic=19795]The Hentai@Home Project FAQ[/url].[/b]

[b]Other download options can be found at [url=http://g.e-hentai.org/hentaiathome.php]the usual place[/url].[/b]

*/

package org.hath.base;

public class HentaiAtHomeClient implements Runnable {
	private InputQueryHandler iqh;
	private Out out;
	private ShutdownHook shutdownHook;
	private boolean shutdown, reportShutdown, fastShutdown;
	private HTTPServer httpServer;
	private ClientAPI clientAPI;
	private CacheHandler cacheHandler;
	private ServerHandler serverHandler;
	private GalleryDownloadManager galleryDownloadManager;
	private Thread myThread;
	private int threadSkipCounter;
	private long suspendedUntil;
	private String[] args;
	
	public HentaiAtHomeClient(InputQueryHandler iqh, String[] args) {
		this.iqh = iqh;
		this.args = args;
		shutdown = false;
		reportShutdown = false;
		
		myThread = new Thread(this);
		myThread.start();
	}

	// master thread for all regularly scheduled tasks
	// note that this function also does most of the program initialization, so that the GUI thread doesn't get locked up doing this when the program is launched through the GUI extension.
	public void run() {
		out = new Out();
		Out.overrideDefaultOutput();
		Out.info("Hentai@Home " + Settings.CLIENT_VERSION + " starting up");
		Out.info("");
		Out.info("Copyright (c) 2008-2014, E-Hentai.org - all rights reserved.");
		Out.info("This software comes with ABSOLUTELY NO WARRANTY. This is free software, and you are welcome to modify and redistribute it under the GPL v3 license.");
		Out.info("");
		
		System.setProperty("http.keepAlive", "false");

		Settings.setActiveClient(this);
		Settings.parseArgs(args);
		
		Stats.resetStats();
		Stats.setProgramStatus("Logging in to main server...");

		// processes commands from the server and interfacing code (like a GUI layer)
		clientAPI = new ClientAPI(this);

		if(Settings.loadClientLoginFromFile()) {
			Out.info("Loaded login settings from " + Settings.DATA_FILENAME_CLIENT_LOGIN);
		}

		if(!Settings.loginCredentialsAreSyntaxValid()) {
			Settings.promptForIDAndKey(iqh);
		}

		// handles notifications other communication with the hentai@home server
		serverHandler = new ServerHandler(this);
		serverHandler.loadClientSettingsFromServer();

		Stats.setProgramStatus("Initializing cache handler...");

		// manages the files in the cache
		try {
			cacheHandler = new CacheHandler(this);
			cacheHandler.initializeCacheHandler();
			cacheHandler.flushRecentlyAccessed();
		}
		catch(java.io.IOException ioe) {
			setFastShutdown();
			dieWithError(ioe);
			return;
		}

		Out.info("Calculating initial cache list file size...");
		cacheHandler.calculateStartupCachedFilesStrlen();
		Out.info("Calculated cacheListStrlen = " + cacheHandler.getStartupCachedFilesStrlen());

		Stats.setProgramStatus("Starting HTTP server...");

		// handles HTTP connections used to request images and receive commands from the server
		httpServer = new HTTPServer(this);
		if(!httpServer.startConnectionListener(Settings.getClientPort())) {
			setFastShutdown();
			dieWithError("Failed to initialize HTTPServer");
			return;
		}
		
		Stats.setProgramStatus("Sending startup notification...");
		
		Out.info("Notifying the server that we have finished starting up the client...");
		if(!serverHandler.notifyStart()) {
			setFastShutdown();
			Out.info("Startup notification failed.");
			return;
		}
		
		httpServer.allowNormalConnections();
		
		reportShutdown = true;
		shutdownHook = new ShutdownHook();
		java.lang.Runtime.getRuntime().addShutdownHook(shutdownHook);

		if(Settings.isWarnNewClient()) {
			String newClientWarning = "A new client version is available. Please download it from http://hentaiathome.net/ at your convenience.";
			Out.warning(newClientWarning);
			
			if(Settings.getActiveGUI() != null) {
				Settings.getActiveGUI().notifyWarning("New Version Available", newClientWarning);
			}
		}
		
		if(cacheHandler.getCacheCount() < 1) {
			Out.info("Important: Your cache does not yet contain any files.");
			Out.info("Because of this, you won't receive any traffic until the client has downloaded some files.");
			Out.info("This should usually happen within a few minutes, but traffic will take time to build up.");
			Out.info("The longer you run the client, the higher the utilization will become.");
		}
		
		// check if we're in an active schedule
		serverHandler.refreshServerSettings();

		Out.info("Activated.");
		Stats.resetBytesSentHistory();
		Stats.programStarted();

		cacheHandler.processBlacklist(259200, false);
		
		galleryDownloadManager = new GalleryDownloadManager(this);

		suspendedUntil = 0;
		threadSkipCounter = 1;
		
		long lastThreadTime = 0;
		
		System.gc();

		while(!shutdown) {
			try {
				Thread.sleep(Math.max(1000, 10000 - lastThreadTime));
			} catch(java.lang.InterruptedException e) {
				Out.debug("Master thread sleep interrupted");
			}

			long startTime = System.currentTimeMillis();
		
			if(!shutdown && suspendedUntil < System.currentTimeMillis()) {
				Stats.setProgramStatus("Running");

				if(suspendedUntil > 0) {
					resumeMasterThread();
				}
				
				if(threadSkipCounter % 30 == 0) {
					serverHandler.stillAliveTest();
				}

				if(threadSkipCounter % 6 == 2) {
					httpServer.pruneFloodControlTable();
				}

				if(threadSkipCounter % 30 == 15) {
					if( (int) (System.currentTimeMillis() / 1000) - Stats.getLastServerContact() < 360 ) {
						cacheHandler.pruneOldFiles();
					}
				}
				
				if(threadSkipCounter % 2160 == 2159) {
					cacheHandler.processBlacklist(43200, false);
				}
				
				cacheHandler.flushRecentlyAccessed();
				httpServer.nukeOldConnections(false);				
				Stats.shiftBytesSentHistory();

				++threadSkipCounter;
			}
			
			lastThreadTime = System.currentTimeMillis() - startTime;
		}
	}
	
	public boolean isSuspended() {
		return suspendedUntil > System.currentTimeMillis();
	}

	public boolean suspendMasterThread(int suspendTime) {
		if(suspendTime > 0 && suspendTime <= 86400 && suspendedUntil < System.currentTimeMillis()) {
			Stats.programSuspended();
			long suspendTimeMillis = suspendTime * 1000;
			suspendedUntil = System.currentTimeMillis() + suspendTimeMillis;
			Out.debug("Master thread suppressed for " + (suspendTimeMillis / 1000) + " seconds.");
			return serverHandler.notifySuspend();
		}
		else {
			return false;
		}
	}
	
	public boolean resumeMasterThread() {
		suspendedUntil = 0;
		threadSkipCounter = 0;
		Stats.programResumed();
		return serverHandler.notifyResume();
	}

	public InputQueryHandler getInputQueryHandler() {
		return iqh;
	}

	public HTTPServer getHTTPServer() {
		return httpServer;
	}

	public CacheHandler getCacheHandler() {
		return cacheHandler;
	}

	public ServerHandler getServerHandler() {
		return serverHandler;
	}

	public ClientAPI getClientAPI() {
		return clientAPI;
	}

	// static crap

	public static void dieWithError(Exception e) {
		e.printStackTrace();
		dieWithError(e.toString());
	}

	public static void dieWithError(String error) {
		Out.error("Critical Error: " + error);
		Stats.setProgramStatus("Died");
		Settings.getActiveClient().shutdown(false, error);
	}
	
	public void setFastShutdown() {
		Out.flushLogs();
		fastShutdown = true;
	}
	
	public void shutdown() {
		shutdown(false, null);
	}
	
	private void shutdown(String error) {
		shutdown(false, error);
	}
	
	private void shutdown(boolean fromShutdownHook, String shutdownErrorMessage) {
		Out.flushLogs();

		if(!shutdown) {
			shutdown = true;
			Out.info("Shutting down...");

			if(reportShutdown && serverHandler != null) {
				serverHandler.notifyShutdown();
			}
			
			if(!fastShutdown && httpServer != null) {
				httpServer.stopConnectionListener();
				Out.info("Shutdown in progress - please wait 25 seconds");

				try {
					Thread.sleep(25000);
				} catch(java.lang.InterruptedException e) {}
				
				if(Stats.getOpenConnections() > 0) {
					httpServer.nukeOldConnections(true);		
					Out.info("All connections cleared.");			
				}			
			}
			
			if(cacheHandler != null) {
				cacheHandler.flushRecentlyAccessed();
				cacheHandler.terminateDatabase();
			}
			
			if(myThread != null) {
				myThread.interrupt();
			}

			if(Math.random() > 0.99) {
				Out.info(
"                             .,---.\n" +
"                           ,/XM#MMMX;,\n" +
"                         -%##########M%,\n" +
"                        -@######%  $###@=\n" +
"         .,--,         -H#######$   $###M:\n" +
"      ,;$M###MMX;     .;##########$;HM###X=\n" +
"    ,/@##########H=      ;################+\n" +
"   -+#############M/,      %##############+\n" +
"   %M###############=      /##############:\n" +
"   H################      .M#############;.\n" +
"   @###############M      ,@###########M:.\n" +
"   X################,      -$=X#######@:\n" +
"   /@##################%-     +######$-\n" +
"   .;##################X     .X#####+,\n" +
"    .;H################/     -X####+.\n" +
"      ,;X##############,       .MM/\n" +
"         ,:+$H@M#######M#$-    .$$=\n" +
"              .,-=;+$@###X:    ;/=.\n" +
"                     .,/X$;   .::,\n" +
"                         .,    ..    \n"   
);
			}
			else {
				String[] sd = {"I don't hate you", "Whyyyyyyyy...", "No hard feelings", "Your business is appreciated", "Good-night"};
				Out.info(sd[(int) Math.floor(Math.random() * sd.length)]);
			}
			
			if(shutdownErrorMessage != null) {
				if(Settings.getActiveGUI() != null) {
					Settings.getActiveGUI().notifyError(shutdownErrorMessage);
				}			
			}
			
			Out.disableLogging();
		}
		
		if(!fromShutdownHook) {
			System.exit(0);
		}
	}
	
	public boolean isShuttingDown() {
		return shutdown;
	}

	public static void main(String[] args) {
		InputQueryHandler iqh = null;

		try {
			iqh = InputQueryHandlerCLI.getIQHCLI();
			new HentaiAtHomeClient(iqh, args);
		} catch(Exception e) {
			Out.error("Failed to initialize InputQueryHandler");
		}
	}

	private class ShutdownHook extends Thread {
		public void run() {
			shutdown(true, null);
		}
	}
}