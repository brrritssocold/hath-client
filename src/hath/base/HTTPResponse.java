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

import java.util.*;
import java.util.regex.Pattern;
import java.net.URL;

public class HTTPResponse {
	private static final Pattern absoluteUriPattern = Pattern.compile("^http://[^/]+/", Pattern.CASE_INSENSITIVE);

	private HTTPSession session;

	private boolean requestHeadOnly;
	private boolean servercmd;
	private int responseStatusCode;

	private HTTPResponseProcessor hpc;

	public HTTPResponse(HTTPSession session) {
		this.session = session;
		servercmd = false;
		requestHeadOnly = false;
		responseStatusCode = 500;	// if nothing alters this, there's a bug somewhere
	}

	private HTTPResponseProcessor processRemoteAPICommand(String command, String additional) {
		Hashtable<String,String> addTable = Tools.parseAdditional(additional);
		HentaiAtHomeClient client = session.getHTTPServer().getHentaiAtHomeClient();

		try {
			if(command.equalsIgnoreCase("still_alive")) {
				return new HTTPResponseProcessorText("I feel FANTASTIC and I'm still alive");
			}
			else if(command.equalsIgnoreCase("threaded_proxy_test")) {
				return processThreadedProxyTest(addTable);
			}
			else if(command.equalsIgnoreCase("speed_test")) {
				String testsize = addTable.get("testsize");
				return new HTTPResponseProcessorSpeedtest(testsize != null ? Integer.parseInt(testsize) : 1000000);
			}
			else if(command.equalsIgnoreCase("refresh_settings")) {
				client.getServerHandler().refreshServerSettings();
				return new HTTPResponseProcessorText("");
			}
			else if(command.equalsIgnoreCase("start_downloader")) {
				client.startDownloader();
				return new HTTPResponseProcessorText("");
			}
			else if(command.equalsIgnoreCase("refresh_certs")) {
				client.setCertRefresh();
				return new HTTPResponseProcessorText("");
			}
		}
		catch(Exception e) {
			e.printStackTrace();
			Out.warning(session + " Failed to process command");
		}

		return new HTTPResponseProcessorText("INVALID_COMMAND");
	}
	
	private HTTPResponseProcessorText processThreadedProxyTest(Hashtable<String,String> addTable) {
		String hostname = addTable.get("hostname");
		String protocol = addTable.get("protocol");
		int port = Integer.parseInt(addTable.get("port"));
		int testsize = Integer.parseInt(addTable.get("testsize"));
		int testcount = Integer.parseInt(addTable.get("testcount"));
		int testtime = Integer.parseInt(addTable.get("testtime"));
		String testkey = addTable.get("testkey");
		
		Out.debug("Running speedtest against hostname=" + hostname + " protocol=" + protocol + " port=" + port + " testsize=" + testsize + " testcount=" + testcount + " testtime=" + testtime + " testkey=" + testkey);

		int successfulTests = 0;
		long totalTimeMillis = 0;

		try {
			List<FileDownloader> testfiles = Collections.checkedList(new ArrayList<FileDownloader>(), FileDownloader.class);

			for(int i=0; i<testcount; i++) {
				URL source = new URL(protocol == null ? "http" : protocol, hostname, port, "/t/" + testsize + "/" + testtime + "/" + testkey + "/" + (int) Math.floor(Math.random() * Integer.MAX_VALUE));
				//Out.debug("Test thread: " + source);
				FileDownloader dler = new FileDownloader(source, 10000, 60000, true);
				testfiles.add(dler);
				dler.startAsyncDownload();
			}

			for(FileDownloader dler : testfiles) {
				if(dler.waitAsyncDownload()) {
					successfulTests += 1;
					totalTimeMillis += dler.getDownloadTimeMillis();
				}
			}
		}
		catch(java.net.MalformedURLException e) {
			HentaiAtHomeClient.dieWithError(e);
		}

		Out.debug("Ran speedtest against hostname=" + hostname + " testsize=" + testsize + " testcount=" + testcount + ", reporting successfulTests=" + successfulTests + " totalTimeMillis=" + totalTimeMillis);

		return new HTTPResponseProcessorText("OK:" + successfulTests + "-" + totalTimeMillis);
	}

	public void parseRequest(String request, boolean localNetworkAccess) {
		if(request == null) {
			Out.debug(session + " Client did not send a request.");
			responseStatusCode = 400;
			return;		
		}
	
		String[] requestParts = request.trim().split(" ", 3);

		if(requestParts.length != 3) {
			Out.debug(session + " Invalid HTTP request form.");
			responseStatusCode = 400;
			return;
		}
		
		if( !(requestParts[0].equalsIgnoreCase("GET") || requestParts[0].equalsIgnoreCase("HEAD")) || !requestParts[2].startsWith("HTTP/") ) {
			Out.debug(session + " HTTP request is not GET or HEAD.");
			responseStatusCode = 405;
			return;
		}

		// The request URI may be an absolute path or an absolute URI for GET/HEAD requests (see section 5.1.2 of RFC2616)
		requestParts[1] = absoluteUriPattern.matcher(requestParts[1]).replaceFirst("/");
		String[] urlparts = requestParts[1].replace("%3d", "=").split("/");

		if( (urlparts.length < 2) || !urlparts[0].equals("")) {
			Out.debug(session + " The requested URL is invalid or not supported.");
			responseStatusCode = 404;
			return;
		}
		
		requestHeadOnly = requestParts[0].equalsIgnoreCase("HEAD");

		if(urlparts[1].equals("h")) {
			// form: /h/$fileid/$additional/$filename
			
			if(urlparts.length < 4) {
				responseStatusCode = 400;
				return;
			}

			String fileid = urlparts[2];
			HVFile requestedHVFile = HVFile.getHVFileFromFileid(fileid);
			Hashtable<String,String> additional = Tools.parseAdditional(urlparts[3]);
			boolean keystampRejected = true;

			try {
				String[] keystampParts = additional.get("keystamp").split("-");
				
				if(keystampParts.length == 2) {
					int keystampTime = Integer.parseInt(keystampParts[0]);

					if(Math.abs(Settings.getServerTime() - keystampTime) < 900) {
						if( keystampParts[1].equalsIgnoreCase(Tools.getSHA1String(keystampTime + "-" + fileid + "-" + Settings.getClientKey() + "-hotlinkthis").substring(0, 10)) ) {
							keystampRejected = false;
						}
					}
				}
			} catch(Exception e) {}
			
			String fileindex = additional.get("fileindex");
			String xres = additional.get("xres");
			
			if(keystampRejected) {
				responseStatusCode = 403;
			}
			else if(requestedHVFile == null || fileindex == null || xres == null || !Pattern.matches("^\\d+$", fileindex) || !Pattern.matches("^org|\\d+$", xres)) {
				Out.debug(session + " Invalid or missing arguments.");
				responseStatusCode = 404;
			}
			else if(requestedHVFile.getLocalFileRef().exists()) {	
				// hpc will update responseStatusCode
				hpc = new HTTPResponseProcessorFile(requestedHVFile);
				session.getHTTPServer().getHentaiAtHomeClient().getCacheHandler().markRecentlyAccessed(requestedHVFile);
			}
			else if(Settings.isStaticRange(fileid)) {
				// non-existent file. do an on-demand request of the file directly from the image servers
				URL[] sources = session.getHTTPServer().getHentaiAtHomeClient().getServerHandler().getStaticRangeFetchURL(fileindex, xres, fileid);
				
				if(sources == null) {
					Out.debug(session + " Sources was empty for fileindex=" + fileindex + " xres=" + xres + " fileid=" + fileid);
					responseStatusCode = 404;
				}
				else {
					// hpc will update responseStatusCode
					hpc = new HTTPResponseProcessorProxy(session, fileid, sources);
				}
			}
			else {
				// file does not exist, and is not in one of the client's static ranges
				Out.debug(session + " File is not in static ranges for fileindex=" + fileindex + " xres=" + xres + " fileid=" + fileid);
				responseStatusCode = 404;
			}						

			return;
		}
		else if(urlparts[1].equals("servercmd")) {
			// form: /servercmd/$command/$additional/$time/$key

			if(!Settings.isValidRPCServer(session.getSocketInetAddress())) {
				Out.debug(session + " Got a servercmd from an unauthorized IP address");
				responseStatusCode = 403;
				return;
			}
			
			if(urlparts.length < 6) {
				Out.debug(session + " Got a malformed servercmd");
				responseStatusCode = 403;
				return;
			}

			String command = urlparts[2];
			String additional = urlparts[3];
			int commandTime = Integer.parseInt(urlparts[4]);
			String key = urlparts[5];

			if( (Math.abs(commandTime - Settings.getServerTime()) > Settings.MAX_KEY_TIME_DRIFT) || !Tools.getSHA1String("hentai@home-servercmd-" + command + "-" + additional + "-" + Settings.getClientID() + "-" + commandTime + "-" + Settings.getClientKey()).equals(key) ) {
				Out.debug(session + " Got a servercmd with expired or incorrect key");
				responseStatusCode = 403;
				return;
			}
			
			responseStatusCode = 200;
			servercmd = true;
			hpc = processRemoteAPICommand(command, additional);
			return;
		}
		else if(urlparts[1].equals("t")) {
			// form: /t/$testsize/$testtime/$testkey
			
			if(urlparts.length < 5) {
				responseStatusCode = 400;
				return;
			}

			// send a randomly generated file of a given length for speed testing purposes
			int testsize = Integer.parseInt(urlparts[2]);
			int testtime = Integer.parseInt(urlparts[3]);
			String testkey = urlparts[4];
			
			if(Math.abs(testtime - Settings.getServerTime()) > Settings.MAX_KEY_TIME_DRIFT) {
				Out.debug(session + " Got a speedtest request with expired key");
				responseStatusCode = 403;
				return;
			}
			
			if(!Tools.getSHA1String("hentai@home-speedtest-" + testsize + "-" + testtime + "-" + Settings.getClientID() + "-" + Settings.getClientKey()).equals(testkey)) {
				Out.debug(session + " Got a speedtest request with invalid key");
				responseStatusCode = 403;
				return;
			}

			Out.debug("Sending threaded proxy test with testsize=" + testsize + " testtime=" + testtime + " testkey=" + testkey);
			
			responseStatusCode = 200;
			hpc = new HTTPResponseProcessorSpeedtest(testsize);
			return;
		}				
		else if(urlparts.length == 2) {
			if(urlparts[1].equals("favicon.ico")) {
				// Redirect to the main website icon (which should already be in the browser cache).
				hpc = new HTTPResponseProcessorText("");
				hpc.addHeaderField("Location", "https://e-hentai.org/favicon.ico");
				responseStatusCode = 301; // Moved Permanently
				return;
			}
			else if(urlparts[1].equals("robots.txt")) {
				// Bots are not welcome.
				hpc = new HTTPResponseProcessorText("User-agent: *\nDisallow: /", "text/plain");
				responseStatusCode = 200; // Found
				return;
			}
		}

		Out.debug(session + " Invalid request type '" + urlparts[1]);
		responseStatusCode = 404;
		return;
	}

	public HTTPResponseProcessor getHTTPResponseProcessor() {
		if(hpc == null) {
			hpc = new HTTPResponseProcessorText("An error has occurred. (" + responseStatusCode + ")");
			
			if(responseStatusCode == 405) {
				hpc.addHeaderField("Allow", "GET,HEAD");
			}
		}
		else if(hpc instanceof HTTPResponseProcessorFile) {
			responseStatusCode = hpc.initialize();
		}
		else if(hpc instanceof HTTPResponseProcessorProxy) {
			responseStatusCode = hpc.initialize();
		}
		else if(hpc instanceof HTTPResponseProcessorSpeedtest) {
			Stats.setProgramStatus("Running speed tests...");
		}

		return hpc;
	}
	
	public void requestCompleted() {
		hpc.requestCompleted();
	}

	// accessors

	public int getResponseStatusCode() {
		return responseStatusCode;
	}

	public boolean isRequestHeadOnly() {
		return requestHeadOnly;
	}

	public boolean isServercmd() {
		return servercmd;
	}
}
