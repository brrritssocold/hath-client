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

import java.lang.Thread;

public class CakeSphere implements Runnable {
	private Thread myThread;
	private ServerHandler handler;
	private HentaiAtHomeClient client;
	private boolean doResume;
	
	public CakeSphere(ServerHandler handler, HentaiAtHomeClient client) {
		myThread = new Thread(this);
		this.handler = handler;
		this.client = client;
	}
	
	public void stillAlive(boolean resume) {
		// Cake and grief counseling will be available at the conclusion of the test.
		doResume = resume;
		myThread.start();
	}
	
	public void run() {
		ServerResponse sr = ServerResponse.getServerResponse(ServerHandler.getServerConnectionURL(ServerHandler.ACT_STILL_ALIVE, doResume ? "resume" : ""), handler);

		if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_OK) {
			Out.debug("Successfully performed a stillAlive test for the server.");
			Stats.serverContact();
		}
		else if(sr.getResponseStatus() == ServerResponse.RESPONSE_STATUS_NULL) {
			Settings.markRPCServerFailure(sr.getFailHost());
			Out.warning("Failed to connect to the server for the stillAlive test. This is probably a temporary connection problem.");
		}
		else if(sr.getFailCode().startsWith("TERM_BAD_NETWORK")) {
			client.dieWithError("Client is shutting down since the network is misconfigured; correct firewall/forwarding settings then restart the client.");
		}
		else {
			Out.warning("Failed stillAlive test: (" + sr.getFailCode() + ") - will retry later");		
		}	
	}
}
