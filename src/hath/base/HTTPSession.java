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

import java.util.Date;
import java.util.TimeZone;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.net.InetAddress;
import java.lang.Thread;
import java.lang.StringBuilder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import javax.net.ssl.SSLSocket;

public class HTTPSession implements Runnable {

	public static final String CRLF = "\r\n";

	private static final Pattern getheadPattern = Pattern.compile("^((GET)|(HEAD)).*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private SSLSocket socket;
	private HTTPServer httpServer;
	private int connId;
	private Thread myThread;
	private boolean localNetworkAccess;
	private long sessionStartTime, lastPacketSend;
	private HTTPResponse hr;

	public HTTPSession(SSLSocket socket, int connId, boolean localNetworkAccess, HTTPServer httpServer) {
		sessionStartTime = System.currentTimeMillis();
		this.socket = socket;
		this.connId = connId;
		this.localNetworkAccess = localNetworkAccess;
		this.httpServer = httpServer;
	}

	public void handleSession() {
		myThread = new Thread(this);
		myThread.start();
	}

	private void connectionFinished() {
		if(hr != null) {
			hr.requestCompleted();
		}

		httpServer.removeHTTPSession(this);
	}

	public void run() {
		// why are we back to input/output streams? because java has no SSLSocketChannel, using them with SSLEngine is stupidly complex, and all the middleware libraries for SSL over channels are either broken, outdated, or require a major code rewrite
		// may switch back to channels in the future if a decent library materializes, or I can be arsed to learn SSLEngine and implementing it does not require a major rewrite
		BufferedReader reader = null;
		DataOutputStream writer = null;
		HTTPResponseProcessor hpc = null;
		String info = this.toString() + " ";

		try {
			socket.setSoTimeout(10000);
			
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			writer = new DataOutputStream(socket.getOutputStream());

			// read the header and parse the request - this will also update the response code and initialize the proper response processor
			String request = null;
			int rcvdBytes = 0;

			// ignore every single line except for the request one. we SSL now, so if there is no end-of-line, just wait for the timeout
			do {
				String read = reader.readLine();

				if(read != null) {
					rcvdBytes += read.length();

					if(getheadPattern.matcher(read).matches()) {
						request = read.substring(0, Math.min(1000, read.length()));
					}
					else if(read.isEmpty()) {
						break;
					}
				}
				else {
					break;
				}
			} while(true);
			
			hr = new HTTPResponse(this);
			hr.parseRequest(request, localNetworkAccess);

			// get the status code and response processor - in case of an error, this will be a text type with the error message
			hpc = hr.getHTTPResponseProcessor();
			int statusCode = hr.getResponseStatusCode();
			int contentLength = hpc.getContentLength();

			// we'll create a new date formatter for each session instead of synchronizing on a shared formatter. (sdf is not thread-safe)
			SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", java.util.Locale.US);
			sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

			// build the header
			StringBuilder header = new StringBuilder(300);
			header.append(getHTTPStatusHeader(statusCode));
			header.append(hpc.getHeader());
			header.append("Date: " + sdf.format(new Date()) + " GMT" + CRLF);
			header.append("Server: Genetic Lifeform and Distributed Open Server " + Settings.CLIENT_VERSION + CRLF);
			header.append("Connection: close" + CRLF);
			header.append("Content-Type: " + hpc.getContentType() + CRLF);

			if(contentLength > 0) {
				header.append("Cache-Control: public, max-age=31536000" + CRLF);
				header.append("Content-Length: " + contentLength + CRLF);
			}

			header.append(CRLF);

			// write the header to the socket
			byte[] headerBytes = header.toString().getBytes(Charset.forName("ISO-8859-1"));

			if(request != null && contentLength > 0) {
				try {
					// buffer size might be limited by OS. for linux, check net.core.wmem_max
					int bufferSize = (int) Math.min(contentLength + headerBytes.length + 32, Math.min(Settings.isUseLessMemory() ? 131072 : 524288, Math.round(0.2 * Settings.getThrottleBytesPerSec())));
					socket.setSendBufferSize(bufferSize);
					//Out.debug("Socket size for " + connId + " is now " + socket.getSendBufferSize() + " (requested " + bufferSize + ")");
				}
				catch (Exception e) {
					Out.info(e.getMessage());
				}
			}

			HTTPBandwidthMonitor bwm = httpServer.getBandwidthMonitor();

			if(bwm != null && !localNetworkAccess) {
				bwm.waitForQuota(myThread, headerBytes.length);
			}

			writer.write(headerBytes, 0, headerBytes.length);
			
			//Out.debug("Wrote " +  headerBytes.length + " header bytes to socket for connId=" + connId + " with contentLength=" + contentLength);

			if(!localNetworkAccess) {
				Stats.bytesSent(headerBytes.length);
			}

			if(hr.isRequestHeadOnly()) {
				// if this is a HEAD request, we are done
				writer.flush();

				info += "Code=" + statusCode + " ";
				Out.info(info + (request == null ? "Invalid Request" : request));
			}
			else {
				// if this is a GET request, process the body if we have one
				info += "Code=" + statusCode + " Bytes=" + String.format("%1$-8s", contentLength) + " ";

				if(request != null) {
					// skip the startup message for error requests
					Out.info(info + request);
				}

				long startTime = System.currentTimeMillis();

				if(contentLength > 0) {
					int writtenBytes = 0;
					int lastWriteLen = 0;
					
					// bytebuffers returned by getPreparedTCPBuffer should never have a remaining() larger than Settings.TCP_PACKET_SIZE. if that happens due to some bug, we will hit an IndexOutOfBounds exception during the get below
					byte[] buffer = new byte[Settings.TCP_PACKET_SIZE];

					while(writtenBytes < contentLength) {
						lastPacketSend = System.currentTimeMillis();
						ByteBuffer tcpBuffer = hpc.getPreparedTCPBuffer();
						lastWriteLen = tcpBuffer.remaining();

						if(bwm != null && !localNetworkAccess) {
							bwm.waitForQuota(myThread, lastWriteLen);
						}
						
						tcpBuffer.get(buffer, 0, lastWriteLen);
						writer.write(buffer, 0, lastWriteLen);
						writtenBytes += lastWriteLen;
						
						//Out.debug("Wrote " + lastWriteLen + " content bytes to socket for connId=" + connId + " with contentLength=" + contentLength);

						if(!localNetworkAccess) {
							Stats.bytesSent(lastWriteLen);
						}
					}
				}

				writer.flush();

				// while the outputstream is flushed and empty, the bytes may not have made it further than the OS network buffers, so the time calculated here is approximate at best and widely misleading at worst, especially if the BWM is disabled
				long sendTime = System.currentTimeMillis() - startTime;
				DecimalFormat df = new DecimalFormat("0.00");
				Out.info(info + "Finished processing request in " + df.format(sendTime / 1000.0) + " seconds" + (sendTime >= 10 ? " (" + df.format(contentLength / (float) sendTime) + " KB/s)" : ""));
			}
		}
		catch(Exception e) {
			Out.debug(info + "The connection was interrupted or closed by the remote host.");
			Out.debug(e == null ? "(no exception)" : e.getMessage());
			//e.printStackTrace();
		}
		finally {
			if(hpc != null) {
				hpc.cleanup();
			}

			try { reader.close(); writer.close(); } catch(Exception e) {}
			try { socket.close(); } catch(Exception e) {}
		}

		connectionFinished();
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

	public boolean doTimeoutCheck() {
		long nowtime = System.currentTimeMillis();

		if(lastPacketSend < nowtime - 1000 && socket.isClosed()) {
			// the connecion was already closed and should be removed by the HTTPServer instance.
			// the lastPacketSend check was added to prevent spurious "Killing stuck session" errors
			return true;
		}
		else {
			int startTimeout = hr != null ? (hr.isServercmd() ? 1800000 : 180000) : 30000;

			if( (sessionStartTime > 0 && sessionStartTime < nowtime - startTimeout) || (lastPacketSend > 0 && lastPacketSend < nowtime - 30000) ) {
				return true;
			}
		}

		return false;
	}
	
	public void forceCloseSocket() {
		try {
			if(!socket.isClosed()) {
				Out.debug("Closing socket for session " + connId);
				socket.close();
				Out.debug("Closed socket for session " + connId);
			}
		} catch(Exception e) {
			Out.debug(e.toString());
		}
	}

	// accessors

	public HTTPServer getHTTPServer() {
		return httpServer;
	}

	public InetAddress getSocketInetAddress() {
		return socket.getInetAddress();
	}

	public boolean isLocalNetworkAccess() {
		return localNetworkAccess;
	}

	public String toString() {
		return "{" + connId + String.format("%1$-17s", getSocketInetAddress().toString() + "}");
	}

}
