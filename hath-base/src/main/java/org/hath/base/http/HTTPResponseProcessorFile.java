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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;

import org.hath.base.HVFile;
import org.hath.base.Out;
import org.hath.base.Settings;
import org.hath.base.Stats;

// this class provides provides a buffered interface to read a file in chunks

public class HTTPResponseProcessorFile extends HTTPResponseProcessor {

	private HVFile requestedHVFile;
	private BufferedInputStream bis;

	public HTTPResponseProcessorFile(HVFile requestedHVFile) {
		this.requestedHVFile = requestedHVFile;
	}

	@Override
	public int initialize() {
		int responseStatusCode = 0;

		File file = requestedHVFile.getLocalFileRef();

		try {
			bis = new BufferedInputStream(new FileInputStream(file), Settings.isUseLessMemory() ? 8192 : 65536);
			responseStatusCode = 200;
			Stats.fileSent();
		} catch(java.io.IOException e) {
			Out.warning("Failed reading content from " + file);
			responseStatusCode = 500;
		}

		return responseStatusCode;
	}
	
	@Override
	public void cleanup() {
		if(bis != null) {
			try {
				bis.close();
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public String getContentType() {
		return requestedHVFile.getMimeType();
	}

	@Override
	public int getContentLength() {
		if(bis != null) {
			return requestedHVFile.getSize();
		}
		else {
			return 0;
		}
	}

	@Override
	public byte[] getBytes() {
		return getBytesRange(requestedHVFile.getSize());
	}

	@Override
	public byte[] getBytesRange(int len) {
		byte[] range = null;
		
		try {
			range = new byte[len];
			bis.read(range);
		} catch(Exception e) {
			e.printStackTrace();
		}

		return range;
	}
}
