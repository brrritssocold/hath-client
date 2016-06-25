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

package org.hath.base.http;

import java.util.Random;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpStatus;
import org.hath.base.Stats;

public class HTTPResponseProcessorSpeedtest extends HTTPResponseProcessor {	
	int testsize = 0;
	Random rand;

	public HTTPResponseProcessorSpeedtest(int testsize) {
		this.testsize = testsize;
		rand = new Random();
	}

	@Override
	public void initialize(HttpServletResponse response) {
		Stats.setProgramStatus("Running speed tests...");
		response.setStatus(HttpStatus.OK_200);
	}

	@Override
	public int getContentLength() {
		return testsize;
	}
	
	@Override
	public byte[] getBytes() {
		return getRandomBytes(testsize);
	}
	
	@Override
	public byte[] getBytesRange(int len) {
		return getRandomBytes(len);
	}
	
	private byte[] getRandomBytes(int len) {
		// generate a random body the server can use to gauge the actual upload speed capabilities of this client
		byte[] random = new byte[len];
        rand.nextBytes(random);
        return random;
	}
}
