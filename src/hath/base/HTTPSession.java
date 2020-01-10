/*

Copyright 2008-2016 E-Hentai.org
https://forums.e-hentai.org/
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

import java.util.Date;
import java.util.TimeZone;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.net.InetAddress;
import java.lang.Thread;
import java.lang.StringBuilder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class HTTPSession implements Runnable {

	public static final String CRLF = "\r\n";

	private static final Pattern getheadPattern = Pattern.compile("^((GET)|(HEAD)).*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private SocketChannel socketChannel;
	private HTTPServer httpServer;
	private int connId;
	private Thread myThread;
	private boolean localNetworkAccess;
	private long sessionStartTime, lastPacketSend;
	private HTTPResponse hr;

	public HTTPSession(SocketChannel socketChannel, int connId, boolean localNetworkAccess, HTTPServer httpServer) {
		sessionStartTime = System.currentTimeMillis();
		this.socketChannel = socketChannel;
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

	private String readHeader(ReadableByteChannel channel) throws java.io.IOException {
		int rcvdBytesTotal = 0, totalWaitTime = 0;

		// if the request exceeds 1000 bytes, it's almost certainly not valid
		// the request header itself can still be larger than 1000 bytes, as the GET/HEAD part will always be the first line of the request header
		byte[] buffer = new byte[1000];
		ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

		do {
			int rcvdBytes = channel.read(byteBuffer);

			if(rcvdBytes < 0 ) {
				Out.debug("Premature EOF while reading request header");
				return null;
			}
			else if(rcvdBytes == 0) {
				if(totalWaitTime > 5000) {
					Out.debug("Request header read timeout");
					return null;
				}
				else {
					try {
						totalWaitTime += 10;
						Thread.sleep(10);
					}
					catch(InterruptedException e) {
						Out.debug("Request header read interrupted");
						return null;
					}
				}
			}
			else {
				rcvdBytesTotal += rcvdBytes;

				if(!localNetworkAccess) {
					Stats.bytesRcvd(rcvdBytes);
				}

				String currentFullHeader = new String(buffer, 0, rcvdBytesTotal);
				Matcher matcher = getheadPattern.matcher(currentFullHeader);
				boolean isValid = matcher.matches();
				
				if(isValid || matcher.hitEnd()) {
					if(isValid) {
						for(int i = 1; i < rcvdBytesTotal; i++) {
							if(buffer[i] == '\n' && buffer[i - 1] == '\r') {
								// only return the first line with the request string, sans the CRLF
								return new String(buffer, 0, i - 1);
							}
						}
					}
				}
				else {
					Out.debug("Malformed request header");
					//Out.debug(currentFullHeader);
					return null;
				}

				Out.debug("Request incomplete; looping");
				//Out.debug(currentFullHeader);
			}

			if(!byteBuffer.hasRemaining()) {
				Out.debug("Oversize request");
				return null;
			}
		} while (true);
	}

	public void run() {
		HTTPResponseProcessor hpc = null;
		String info = this.toString() + " ";

		try {
			String request = readHeader(socketChannel);
			socketChannel.shutdownInput();
			
			hr = new HTTPResponse(this);

			// parse the request - this will also update the response code and initialize the proper response processor
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

			if(contentLength > 0) {
				try {
					// buffer size might be limited by OS. for linux, check net.core.wmem_max
					int bufferSize = (int) Math.min(contentLength + headerBytes.length + 32, Math.min(Settings.isUseLessMemory() ? 131072 : 524288, Math.round(0.2 * Settings.getThrottleBytesPerSec())));
					socketChannel.socket().setSendBufferSize(bufferSize);
					//Out.debug("Socket size for " + connId + " is now " + socketChannel.socket().getSendBufferSize() + " (requested " + bufferSize + ")");
				}
				catch (Exception e) {
					Out.info(e.getMessage());
				}
			}

			// we try to feed the SocketChannel buffers in chunks of 1460 bytes, to make the TCP/IP packets fit neatly into the 1500 byte Ethernet MTU
			// because of this, we record the number of remainder bytes after the header has been written, and limit the first packet to 1460 sans that count
			// we do not have any guarantees that the header won't get shipped off in a packet by itself, but generally this should improve fillrate and prevent fragmentation
			int lingeringBytes = headerBytes.length % Settings.TCP_PACKET_SIZE;

			// wrap and write the header
			HTTPBandwidthMonitor bwm = httpServer.getBandwidthMonitor();
			ByteBuffer tcpBuffer = ByteBuffer.wrap(headerBytes);
			int lastWriteLen = 0;

			if(bwm != null && !localNetworkAccess) {
				bwm.waitForQuota(myThread, tcpBuffer.remaining());
			}

			while(tcpBuffer.hasRemaining()) {
				lastWriteLen += socketChannel.write(tcpBuffer);
			}
			
			//Out.debug("Wrote " + lastWriteLen + " header bytes to socketChannel for connId=" + connId);

			if(!localNetworkAccess) {
				Stats.bytesSent(lastWriteLen);
			}

			if(hr.isRequestHeadOnly()) {
				// if this is a HEAD request, we are done
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

					while(writtenBytes < contentLength) {
						lastPacketSend = System.currentTimeMillis();
						tcpBuffer = hpc.getPreparedTCPBuffer(lingeringBytes);
						lingeringBytes = 0;
						lastWriteLen = 0;

						if(bwm != null && !localNetworkAccess) {
							bwm.waitForQuota(myThread, tcpBuffer.remaining());
						}

						while(tcpBuffer.hasRemaining()) {
							lastWriteLen += socketChannel.write(tcpBuffer);

							// we should be blocking, but if we're not, loop until the buffer is empty
							if(tcpBuffer.hasRemaining()) {
								myThread.sleep(1);
							}
						}
						
						//Out.debug("Wrote " + lastWriteLen + " content bytes to socketChannel for connId=" + connId);

						writtenBytes += lastWriteLen;

						if(!localNetworkAccess) {
							Stats.bytesSent(lastWriteLen);
						}
					}
				}

				long sendTime = System.currentTimeMillis() - startTime;
				DecimalFormat df = new DecimalFormat("0.00");
				Out.info(info + "Finished processing request in " + df.format(sendTime / 1000.0) + " seconds" + (sendTime >= 10 ? " (" + df.format(contentLength / (float) sendTime) + " KB/s)" : ""));
			}
		}
		catch(Exception e) {
			Out.info(info + "The connection was interrupted or closed by the remote host.");
			Out.debug(e == null ? "(no exception)" : e.getMessage());
			//e.printStackTrace();
		}
		finally {
			if(hpc != null) {
				hpc.cleanup();
			}

			try {
				socketChannel.close(); 
			} catch(Exception e) {}
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

	public boolean doTimeoutCheck(boolean forceKill) {
		long nowtime = System.currentTimeMillis();

		if(lastPacketSend < nowtime - 1000 && !socketChannel.isOpen()) {
			// the connecion was already closed and should be removed by the HTTPServer instance.
			// the lastPacketSend check was added to prevent spurious "Killing stuck session" errors
			return true;
		}
		else {
			int startTimeout = hr != null ? (hr.isServercmd() ? 1800000 : 180000) : 30000;

			if(forceKill || (sessionStartTime > 0 && sessionStartTime < nowtime - startTimeout) || (lastPacketSend > 0 && lastPacketSend < nowtime - 30000)) {
				// DIE DIE DIE
				//Out.info(this + " The connection has exceeded its time limits: timing out.");
				try {
					socketChannel.close();
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

	public InetAddress getSocketInetAddress() {
		return socketChannel.socket().getInetAddress();
	}

	public boolean isLocalNetworkAccess() {
		return localNetworkAccess;
	}

	public String toString() {
		return "{" + connId + String.format("%1$-17s", getSocketInetAddress().toString() + "}");
	}

}
