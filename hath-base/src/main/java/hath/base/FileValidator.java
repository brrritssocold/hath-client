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

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;

public class FileValidator {
	private MessageDigest messageDigest;
	private ByteBuffer byteBuffer;

	public FileValidator() {
		try {
			messageDigest = MessageDigest.getInstance("SHA-1");
		}
		catch(java.security.NoSuchAlgorithmException e) {
			HentaiAtHomeClient.dieWithError(e);
		}

		byteBuffer = ByteBuffer.allocateDirect(65536);
	}
	
	public boolean validateFile(Path path, String expectedSHA1Value) throws java.io.IOException {
		FileChannel fileChannel = null;

		try {
			// usually byteBuffer would already be cleared and messageDigest would be reset at the end of the previous validation
			// however, if the last run encountered an exception, this is not guaranteed, so we do it explicitly to avoid weirdness
			messageDigest.reset();
			byteBuffer.clear();

			fileChannel = FileChannel.open(path, StandardOpenOption.READ);

			while(fileChannel.read(byteBuffer) != -1) {
				byteBuffer.flip();
				// guaranteed to consume the buffer
				messageDigest.update(byteBuffer);
				byteBuffer.clear();
			}

			return Tools.binaryToHex(messageDigest.digest()).equals(expectedSHA1Value);
		}
		finally {
			// achievement unlocked: used non-contrived try/finally without catch
			if(fileChannel != null) {
				try {
					fileChannel.close();
				} catch(Exception e) {}
			}
		}
	}
}