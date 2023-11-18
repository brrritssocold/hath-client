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

import java.util.Random;
import java.nio.ByteBuffer;

public class HTTPResponseProcessorSpeedtest extends HTTPResponseProcessor {
	private int testsize = 0, writeoff = 0;
	private final int randomLength = 8192;
	private byte[] randomBytes;

	public HTTPResponseProcessorSpeedtest(int testsize) {
		this.testsize = testsize;
		Random rand = new Random();
		randomBytes = new byte[randomLength];
		rand.nextBytes(randomBytes);
	}

	public int getContentLength() {
		return testsize;
	}

	public ByteBuffer getPreparedTCPBuffer() throws Exception {
		int bytecount = Math.min(getContentLength() - writeoff, Settings.TCP_PACKET_SIZE);
		int startbyte = (int) Math.floor(Math.random() * (randomLength - bytecount));

		// making this read-only is probably not necessary, but doing so is almost free, and we don't want anything messing with our precious random bytes
		ByteBuffer buffer = ByteBuffer.wrap(randomBytes, startbyte, bytecount).asReadOnlyBuffer();
		writeoff += bytecount;

		// this was a wrap, so we do not flip
		return buffer;
	}
}
