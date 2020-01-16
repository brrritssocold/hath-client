/*

Copyright 2008-2019 E-Hentai.org
https://forums.e-hentai.org/
ehentai@gmail.com

This file is part of Hentai@Home GUI.

Hentai@Home GUI is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Hentai@Home GUI is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Hentai@Home GUI.  If not, see <http://www.gnu.org/licenses/>.

*/

package hath.gui;
import hath.base.HentaiAtHomeClient;

public class GUIThreaded implements Runnable {
	public static final int ACTION_SHUTDOWN = 1;
	
	private HentaiAtHomeClient client;
	private Thread myThread;
	private int action;

	public GUIThreaded(HentaiAtHomeClient client, int action) {
		this.client = client;
		this.action = action;
		myThread = new Thread(this, GUIThreaded.class.getSimpleName());
		myThread.start();
	}

	public void run() {
		if(action == ACTION_SHUTDOWN) {
			client.shutdown();
		}
	}
}

