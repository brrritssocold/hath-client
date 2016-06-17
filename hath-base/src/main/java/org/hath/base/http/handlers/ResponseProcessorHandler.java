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

package org.hath.base.http.handlers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.hath.base.Out;
import org.hath.base.Settings;
import org.hath.base.Stats;
import org.hath.base.http.HTTPBandwidthMonitor;
import org.hath.base.http.HTTPRequestAttributes;
import org.hath.base.http.HTTPRequestAttributes.BooleanAttributes;
import org.hath.base.http.HTTPRequestAttributes.IntegerAttributes;
import org.hath.base.http.HTTPResponseProcessor;
import org.hath.base.http.HTTPResponseProcessorFile;
import org.hath.base.http.HTTPResponseProcessorProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.HttpHeaders;

/**
 * Writes data from {@link HTTPResponseProcessor} into the response. Handles
 * requests based on local or external origin and enforces the bandwidth limit.
 */
public class ResponseProcessorHandler extends AbstractHandler {
	private static final Logger logger = LoggerFactory.getLogger(ResponseProcessorHandler.class);
	private int connId;
	private boolean localNetworkAccess;
	private long sessionStartTime, lastPacketSend; //TODO replace with guava stopwatch
	private HTTPBandwidthMonitor bandwidthMonitor;

	public ResponseProcessorHandler(HTTPBandwidthMonitor bandwidthMonitor) {
		sessionStartTime = System.currentTimeMillis();
		this.bandwidthMonitor = bandwidthMonitor;
	}

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		logger.trace("Handling response request");
		ServletOutputStream bs = response.getOutputStream();

		HTTPResponseProcessor hpc = null;
		String info = info(request.getRemoteAddr()) + " ";

		try {
			connId = HTTPRequestAttributes.getAttribute(request, IntegerAttributes.SESSION_ID);
			localNetworkAccess = HTTPRequestAttributes.getAttribute(request, BooleanAttributes.LOCAL_NETWORK_ACCESS);
			
			hpc = HTTPRequestAttributes.getResponseProcessor(request);

			if (hpc == null) {
				Out.warning("Got request without ResponseProcessor: " + request.toString());
				logger.trace("Status: {}, isHandled: {}", response.getStatus(), baseRequest.isHandled());
				return;
			}

			hpc.initialize(response);
			int contentLength = hpc.getContentLength();
			int statusCode = response.getStatus();
			response.setContentType(hpc.getContentType());

			createHeader(response, contentLength);
		
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
				logger.trace("Response content length is 0");
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
				Stats.bytesRcvd(target.getBytes(StandardCharsets.ISO_8859_1).length);

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

			baseRequest.setHandled(true);
				printProcessingFinished(info, contentLength, startTime);
		} catch(Exception e) {
			Out.info(info + "The connection was interrupted or closed by the remote host.");
			Out.debug(e == null ? "(no exception)" : e.getMessage());
			baseRequest.setHandled(true);
		} finally {
			if(hpc != null) {
				hpc.cleanup();
			}
		}
	}

	private void printProcessingFinished(String info, int contentLength, long startTime) {
		long sendTime = System.currentTimeMillis() - startTime;
		DecimalFormat df = new DecimalFormat("0.00");
		Out.info(info + "Finished processing request in " + df.format(sendTime / 1000.0) + " seconds (" + (sendTime > 0 ? df.format(contentLength / (float) sendTime) : "-.--") + " KB/s)");
	}

	protected void createHeader(HttpServletResponse response, int contentLength) {
		// we'll create a new date formatter for each session instead of synchronizing on a shared formatter. (sdf is not thread-safe)
		// TODO replace with DateTimeFormatter (thread-safe)
		SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", java.util.Locale.US);
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

		// build the header
		response.setHeader(HttpHeaders.DATE, sdf.format(new Date()) + " GMT");
		response.setHeader(HttpHeaders.SERVER,
				"Genetic Lifeform and Distributed Open Server " + Settings.CLIENT_VERSION);
		response.setHeader(HttpHeaders.CONNECTION, "close");

		if(contentLength > 0) {
			response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000");
			response.setContentLength(contentLength);
		}
	}

	private String info(String remoteIp) {
		return "{" + connId + "/" + String.format("%1$-17s", remoteIp + "}");
	}
}
