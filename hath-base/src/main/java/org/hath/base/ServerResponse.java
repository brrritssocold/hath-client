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

import java.net.URL;

public class ServerResponse {
	public static final int RESPONSE_STATUS_NULL = 0;
	public static final int RESPONSE_STATUS_OK = 1;
	public static final int RESPONSE_STATUS_FAIL = -1;

	private int responseStatus;
	private String[] responseText;
	private String failCode;

	private ServerResponse(int responseStatus, String[] responseText) {
		this.responseStatus = responseStatus;
		this.responseText = responseText;
		this.failCode = null;
	}

	private ServerResponse(int responseStatus, String failCode) {
		this.responseStatus = responseStatus;
		this.failCode = failCode;
		this.responseText = null;
	}

	public static ServerResponse getServerResponse(String act, ServerHandler retryhandler) {
		URL	serverConnectionURL = ServerHandler.getServerConnectionURL(act);
		return getServerResponse(serverConnectionURL, retryhandler, act);
	}
	
	public static ServerResponse getServerResponse(URL serverConnectionURL, ServerHandler retryhandler) {
		return getServerResponse(serverConnectionURL, retryhandler, null);
	}
	
	private static ServerResponse getServerResponse(URL serverConnectionURL, ServerHandler retryhandler, String retryact) {
		FileDownloader dler = new FileDownloader(serverConnectionURL, 3600000);
		String serverResponse = dler.getTextContent();

		if(serverResponse == null) {
			return new ServerResponse(RESPONSE_STATUS_NULL, "NO_RESPONSE");
		}
		else if(serverResponse.length() < 1) {
			return new ServerResponse(RESPONSE_STATUS_NULL, "NO_RESPONSE");
		}

		String[] split = serverResponse.split("\n");
		
		Out.debug("Received response: " + serverResponse);

		if(split.length < 1) {
			return new ServerResponse(RESPONSE_STATUS_NULL, "NO_RESPONSE");
		}
		else if(split[0].startsWith("Log Code") || split[0].startsWith("Database Error")) {
			return new ServerResponse(RESPONSE_STATUS_NULL, "SERVER_ERROR");
		}
		else if(split[0].startsWith("TEMPORARILY_UNAVAILABLE")) {
			return new ServerResponse(RESPONSE_STATUS_NULL, "TEMPORARILY_UNAVAILABLE");
		}
		else if(split[0].equals("OK")) {
			Stats.serverContact();
			return new ServerResponse(RESPONSE_STATUS_OK, java.util.Arrays.copyOfRange(split, 1, split.length));
		}
		else if(split[0].equals("KEY_EXPIRED") && retryhandler != null && retryact != null) {
			Out.warning("Server reported expired key; attempting to refresh time from server and retrying");
			retryhandler.refreshServerStat();
			return getServerResponse(ServerHandler.getServerConnectionURL(retryact), null);
		}
		else {
			return new ServerResponse(RESPONSE_STATUS_FAIL, split[0]);
		}	
	}

	public String toString() {
		java.lang.StringBuffer sb = new java.lang.StringBuffer();

		if(responseText != null) {
			for(String s : responseText) {
				sb.append(s + ",");
			}
		}

		return "ServerResponse {responseStatus=" + responseStatus + ", responseText=" + sb.toString() + ", failCode=" + failCode + "}";
	}

	public int getResponseStatus() {
		return responseStatus;
	}

	public String[] getResponseText() {
		return responseText;
	}

	public String getFailCode() {
		return failCode;
	}

}
