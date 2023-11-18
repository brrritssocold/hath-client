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

// note: this class can be invoked by local extentions to play with stuff

public class ClientAPI {
	public static final int API_COMMAND_CLIENT_START = 1;
	public static final int API_COMMAND_CLIENT_SUSPEND = 2;
	public static final int API_COMMAND_CLIENT_RESUME = 5;
	public static final int API_COMMAND_MODIFY_SETTING = 3;
	public static final int API_COMMAND_REFRESH_SETTINGS = 4;
	
	private HentaiAtHomeClient client;

	public ClientAPI(HentaiAtHomeClient client) {
		this.client = client;
	}
	
	// available hooks for controlling the client
	public ClientAPIResult clientSuspend(int suspendTime) {
		return new ClientAPIResult(API_COMMAND_CLIENT_SUSPEND, client.suspendMasterThread(suspendTime) ? "OK" : "FAIL");	
	}
	
	public ClientAPIResult clientResume() {
		return new ClientAPIResult(API_COMMAND_CLIENT_RESUME, client.resumeMasterThread() ? "OK" : "FAIL");	
	}
	
	public ClientAPIResult refreshSettings() {
		return new ClientAPIResult(API_COMMAND_REFRESH_SETTINGS, client.getServerHandler().refreshServerSettings() ? "OK" : "FAIL");		
	}
}
