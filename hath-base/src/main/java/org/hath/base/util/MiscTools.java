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

package org.hath.base.util;

import java.io.*;
import java.util.*;

import org.hath.base.HentaiAtHomeClient;
import org.hath.base.Out;

public class MiscTools {

	// these two functions are used to process servercmd type GETs
	public static Hashtable<String,String> parseAdditional(String additional) {
		Hashtable<String,String> addTable = new Hashtable<String,String>();

		if(additional != null) {
			if(!additional.isEmpty()) {
				String[] keyValuePairs = additional.trim().split(";");

				for(String kvPair : keyValuePairs) {
					if(kvPair.length() > 2) {
						String[] kvPairParts = kvPair.trim().split("=", 2);

						if(kvPairParts.length == 2) {
							addTable.put(kvPairParts[0].trim(), kvPairParts[1].trim());
						}
						else {
							Out.warning("Invalid kvPair: " + kvPair);
						}
					}
				}
			}
		}

		return addTable;
	}

	public static String getSHAString(String from) {
		return getSHAString(from.getBytes());
	}

	public static String getSHAString(File from) throws java.io.IOException {
		return getSHAString(FileTools.getFileContents(from));
	}

	public static String getSHAString(byte[] bytes) {
		java.lang.StringBuffer sb = null;

		try {
			java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA");
			byte[] keybytes = md.digest(bytes);
			sb = new java.lang.StringBuffer(keybytes.length * 2);

			for(byte b : keybytes) {
				String s = Integer.toHexString((int) b & 0xff);
				sb.append((s.length() < 2 ? "0" : "") + s);
			}
			
			// for some reason this doesn't appear to be releasing properly, so we'll do it manually...
			md.reset();
			md = null;
			bytes = null;
			keybytes = null;
		} catch(java.security.NoSuchAlgorithmException e) {
			HentaiAtHomeClient.dieWithError(e);
		}

		return sb == null ? null : sb.toString().toLowerCase();
	}



}
