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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

public class InputQueryHandlerCLI implements InputQueryHandler {
	private BufferedReader cmdreader;

	private InputQueryHandlerCLI() {
		cmdreader = new BufferedReader(new InputStreamReader(System.in));
	}
	
	public static InputQueryHandlerCLI getIQHCLI() throws IOException {
		return new InputQueryHandlerCLI();
	}
	
	public String queryString(String querytext) {
		System.out.print(querytext + ": ");
		String s = null;
		
		try {
			s = cmdreader.readLine();
		} catch(IOException e) {}
		
		if(s == null) {
			System.out.print("Interrupted");
			Settings.getActiveClient().shutdown();
		}
		
		return s;
	}
}
