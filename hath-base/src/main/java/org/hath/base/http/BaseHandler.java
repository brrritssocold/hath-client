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

package org.hath.base.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.hath.base.Out;
import org.hath.base.Settings;
import org.hath.base.Stats;

import com.google.common.net.HttpHeaders;

public class BaseHandler extends AbstractHandler implements Runnable {

	public static final String CRLF = "\r\n";

	private static final Pattern getheadPattern = Pattern.compile("^((GET)|(HEAD)).*", Pattern.CASE_INSENSITIVE);

	private Socket mySocket;
	private HTTPServer httpServer;
	private int connId;
	private Thread httpSession;
	private boolean localNetworkAccess;
	private long sessionStartTime, lastPacketSend;
	private HTTPResponse hr;
	private HTTPResponseFactory responseFactory;
	private int rcvdBytes = 0;
	private HTTPBandwidthMonitor bandwidthMonitor;

	/**
	 * Use the non socket version
	 */
	@Deprecated
	public BaseHandler(Socket s, int connId, boolean localNetworkAccess, HTTPServer httpServer,
			HTTPResponseFactory responseFactory) {
		sessionStartTime = System.currentTimeMillis();
		this.mySocket = s;
		this.connId = connId;
		this.httpServer = httpServer;
		this.bandwidthMonitor = httpServer.getBandwidthMonitor();
		this.localNetworkAccess = localNetworkAccess;
		this.responseFactory = responseFactory;
	}

	@Deprecated
	public BaseHandler(Socket socket, int connId, boolean localNetworkAccess, HTTPBandwidthMonitor bandwidthMonitor,
			HTTPResponseFactory responseFactory) {
		this(connId, localNetworkAccess, bandwidthMonitor, responseFactory);
		mySocket = socket;
	}

	public BaseHandler(int connId, boolean localNetworkAccess, HTTPBandwidthMonitor bandwidthMonitor,
			HTTPResponseFactory responseFactory) {
		sessionStartTime = System.currentTimeMillis();
		this.connId = connId;
		this.bandwidthMonitor = bandwidthMonitor;
		this.localNetworkAccess = localNetworkAccess;
		this.responseFactory = responseFactory;
	}

	public BaseHandler(Socket s, int connId, boolean localNetworkAccess, HTTPServer httpServer) {
		this(s, connId, localNetworkAccess, httpServer, new HTTPResponseFactory());
	}

	/**
	 * Use the http server to call
	 * {@link BaseHandler#handle(String, Request, HttpServletRequest, HttpServletResponse)}
	 */
	@Deprecated
	public void handleSession() {
		httpSession = new Thread(this);
		httpSession.setName("HTTP Session");
		httpSession.start();
	}

	@Deprecated
	private void connectionFinished() {
		httpServer.removeHTTPSession(this);
	}

	@Deprecated
	@Override
	public void run() {
	}

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		ServletOutputStream bs = response.getOutputStream();

		HTTPResponseProcessor hpc = null;
		String info = this.toString() + " ";		

		try {
			hr = responseFactory.create(this);
			
			// parse the request - this will also update the response code and initialize the proper response processor
			hr.parseRequest(target, localNetworkAccess);

			// get the status code and response processor - in case of an error, this will be a text type with the error message
			hpc = hr.getHTTPResponseProcessor();
			int statusCode = hr.getResponseStatusCode();
			int contentLength = hpc.getContentLength();

			createHeader(response, statusCode, contentLength);
		
			response.setBufferSize(524288);

			long startTime = System.currentTimeMillis();

			if (baseRequest.isHead()) {
				// if this is a HEAD request, we flush the socket and finish
				baseRequest.setHandled(true);
				info += "Code=" + statusCode + " ";
				Out.info(info + (target == null ? "Invalid Request" : target));
				printProcessingFinished(info, contentLength, startTime);
				return;
			}
				// if this is a GET request, process the pony if we have one
				info += "Code=" + statusCode + " Bytes=" + String.format("%1$-8s", contentLength) + " ";
				
				if(target != null) {
					// skip the startup message for error requests
					Out.info(info + target);
				}

				if(contentLength == 0) {
					// there is no pony to write (probably a redirect). flush the socket and finish.
					baseRequest.setHandled(true);
					printProcessingFinished(info, contentLength, startTime);
					return;
			}
					if(localNetworkAccess && (hpc instanceof HTTPResponseProcessorFile || hpc instanceof HTTPResponseProcessorProxy)) {
						Out.debug(this + " Local network access detected, skipping throttle.");
						
						if(hpc instanceof HTTPResponseProcessorProxy) {
							// split the request even though it is local. otherwise the system will stall waiting for the proxy to serve the request fully before any data at all is returned.
							int writtenBytes = 0;
							
							while(writtenBytes < contentLength) {
								// write a packet of data and flush. getBytesRange will block if new data is not yet available.

								int writeLen = Math.min(Settings.TCP_PACKET_SIZE_HIGH, contentLength - writtenBytes);
								bs.write(hpc.getBytesRange(writeLen), 0, writeLen);
								bs.flush();

								writtenBytes += writeLen;
							}
						}
						else {
							// dump the entire file and flush.
							bs.write(hpc.getBytes(), 0, contentLength);							
							bs.flush();
						}
					}
					else {
						// bytes written to the local network do not count against the bandwidth stats. these do, however.
						Stats.bytesRcvd(rcvdBytes);

						HTTPBandwidthMonitor bwm = this.bandwidthMonitor;
						boolean disableBWM = Settings.isDisableBWM();
						
						int packetSize = bwm.getActualPacketSize();
						int writtenBytes = 0;

						while(writtenBytes < contentLength) {
							// write a packet of data and flush.
							lastPacketSend = System.currentTimeMillis();

							int writeLen = Math.min(packetSize, contentLength - writtenBytes);
							bs.write(hpc.getBytesRange(writeLen), 0, writeLen);
							bs.flush();

							writtenBytes += writeLen;

							Stats.bytesSent(writeLen);
							
							if(!disableBWM) {
								bwm.synchronizedWait();
							}
						}
					}

				printProcessingFinished(info, contentLength, startTime);
		} catch(Exception e) {
			Out.info(info + "The connection was interrupted or closed by the remote host.");
			Out.debug(e == null ? "(no exception)" : e.getMessage());
		} finally {
			if(hpc != null) {
				hpc.cleanup();
			}
		}

		connectionFinished();
	}

	private void printProcessingFinished(String info, int contentLength, long startTime) {
		long sendTime = System.currentTimeMillis() - startTime;
		DecimalFormat df = new DecimalFormat("0.00");
		Out.info(info + "Finished processing request in " + df.format(sendTime / 1000.0) + " seconds (" + (sendTime > 0 ? df.format(contentLength / (float) sendTime) : "-.--") + " KB/s)");
	}

	protected void createHeader(HttpServletResponse response, int statusCode,
			int contentLength) {
		// we'll create a new date formatter for each session instead of synchronizing on a shared formatter. (sdf is not thread-safe)
		SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", java.util.Locale.US);
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

		// build the header
		response.setStatus(statusCode);
		response.setHeader(HttpHeaders.DATE, sdf.format(new Date()) + " GMT");
		response.setHeader(HttpHeaders.SERVER,
				"Genetic Lifeform and Distributed Open Server " + Settings.CLIENT_VERSION);
		response.setHeader(HttpHeaders.CONNECTION, "close");

		if(contentLength > 0) {
			response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000");
			response.setContentLength(contentLength);
		}
	}

	protected String readRequest(BufferedReader br) throws IOException {
		// http "parser" follows... might wanna replace this with a more compliant one eventually ;-)

		String read = null;
		String request = null;

		// utterly ignore every single line except for the request one.
		do {
			read = br.readLine();

			if(read != null) {
				rcvdBytes += read.length();

				if(getheadPattern.matcher(read).matches()) {
					request = read.substring(0, Math.min(Settings.MAX_REQUEST_LENGTH, read.length()));
				}
				else if(read.isEmpty()) {
					break;
				}
			}
			else {
				break;
			}
		} while(true);
		return request;
	}

	protected void setSendBufferSize(int contentLength, byte[] headerBytes) {
		if(contentLength <= 0) {
			return;
		}
		
		try {
			// buffer size might be limited by OS. for linux, check net.core.wmem_max
			int bufferSize = (int) Math.min(contentLength + headerBytes.length + 32, Math.min(Settings.isUseLessMemory() ? 131072 : 524288, Math.round(0.2 * Settings.getThrottleBytesPerSec())));
			mySocket.setSendBufferSize(bufferSize);
			//Out.debug("Socket size for " + connId + " is now " + mySocket.getSendBufferSize() + " (requested " + bufferSize + ")");
		} catch (Exception e) {
			Out.info(e.getMessage());
		}
	}

	private String getHTTPStatusHeader(int statuscode) {
		switch(statuscode) {
			case 200: return "HTTP/1.1 200 OK" + CRLF;
			case 301: return "HTTP/1.1 301 Moved Permanently" + CRLF;
			case 400: return "HTTP/1.1 400 Bad Request" + CRLF;
			case 403: return "HTTP/1.1 403 Permission Denied" + CRLF;
			case 404: return "HTTP/1.1 404 Not Found" + CRLF;
			case 405: return "HTTP/1.1 405 Method Not Allowed" + CRLF;
			case 418: return "HTTP/1.1 418 I'm a teapot" + CRLF;
			case 501: return "HTTP/1.1 501 Not Implemented" + CRLF;
			case 502: return "HTTP/1.1 502 Bad Gateway" + CRLF;
			default: return "HTTP/1.1 500 Internal Server Error" + CRLF;
		}
	}

	public boolean doTimeoutCheck(boolean forceKill) {
		if(mySocket.isClosed()) {
			//  the connecion was already closed and should be removed by the HTTPServer instance.
			return true;
		}
		else {
			long nowtime = System.currentTimeMillis();
			int startTimeout = hr != null ? (hr.isServercmd() ? 1800000 : 180000) : 30000;

			if(forceKill || (sessionStartTime > 0 && sessionStartTime < nowtime - startTimeout) || (lastPacketSend > 0 && lastPacketSend < nowtime - 30000)) {
				// DIE DIE DIE
				//Out.info(this + " The connection has exceeded its time limits: timing out.");
				try {
					mySocket.close();
				} catch(Exception e) {
					Out.debug(e.toString());
				}
			}
		}

		return false;
	}

	// accessors

	public HTTPServer getHTTPServer() {
		return httpServer;
	}

	public void setHttpServer(HTTPServer httpServer) {
		this.httpServer = httpServer;
	}

	public InetAddress getSocketInetAddress() {
		return mySocket.getInetAddress();
	}

	public boolean isLocalNetworkAccess() {
		return localNetworkAccess;
	}

	public String toString() {
		return "{" + connId + String.format("%1$-17s", getSocketInetAddress().toString() + "}");
	}

}
