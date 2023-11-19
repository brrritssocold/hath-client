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
import java.util.Arrays;

public class ServerResponse {
	public static final int RESPONSE_STATUS_NULL = 0;
	public static final int RESPONSE_STATUS_OK = 1;
	public static final int RESPONSE_STATUS_FAIL = -1;

	private int responseStatus;
	private String[] responseText;
	private String failCode, failHost;

	private ServerResponse(int responseStatus, String[] responseText) {
		this.responseStatus = responseStatus;
		this.responseText = responseText;
		this.failCode = null;
	}

	private ServerResponse(int responseStatus, String failCode, String failHost) {
		this.responseStatus = responseStatus;
		this.failCode = failCode;
		this.failHost = failHost;
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
		FileDownloader dler = new FileDownloader(serverConnectionURL, 3600000, 3600000);
		String serverResponse = dler.getResponseAsString("ASCII");

		if(serverResponse == null) {
			return new ServerResponse(RESPONSE_STATUS_NULL, "NO_RESPONSE", serverConnectionURL.getHost().toLowerCase());
		}

		Out.debug("Received response: " + serverResponse);
		String[] split = serverResponse.split("\n");

		if(split.length < 1) {
			return new ServerResponse(RESPONSE_STATUS_NULL, "NO_RESPONSE", serverConnectionURL.getHost().toLowerCase());
		}
		else if(split[0].startsWith("TEMPORARILY_UNAVAILABLE")) {
			return new ServerResponse(RESPONSE_STATUS_NULL, "TEMPORARILY_UNAVAILABLE", serverConnectionURL.getHost().toLowerCase());
		}
		else if(split[0].equals("OK")) {
			return new ServerResponse(RESPONSE_STATUS_OK, Arrays.copyOfRange(split, 1, split.length));
		}
		else if(split[0].equals("KEY_EXPIRED") && retryhandler != null && retryact != null) {
			Out.warning("Server reported expired key; attempting to refresh time from server and retrying");
			retryhandler.refreshServerStat();
			return getServerResponse(ServerHandler.getServerConnectionURL(retryact), null);
		}
		else {
			return new ServerResponse(RESPONSE_STATUS_FAIL, split[0], serverConnectionURL.getHost().toLowerCase());
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
	
	public String getFailHost() {
		return failHost;
	}

}
