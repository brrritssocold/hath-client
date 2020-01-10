/*

Copyright 2008-2016 E-Hentai.org
https://forums.e-hentai.org/
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

package hath.base;

import java.io.File;
import java.nio.file.Path;

public class HVFile {
	private String hash;
	private int size;
	private int xres;
	private int yres;
	private String type;

	private HVFile(String hash, int size, int xres, int yres, String type) {
		this.hash = hash;
		this.size = size;
		this.xres = xres;
		this.yres = yres;
		this.type = type;
	}
	
	public File getLocalFileRef() {
		return new File(Settings.getInstance().getCacheDir(),
				hash.substring(0, 2) + "/" + hash.substring(2, 4) + "/" + getFileid());
	}
	
	public Path getLocalFilePath() {
		return getLocalFileRef().toPath();
	}

	// accessors
	public String getMimeType() {
		if(type.equals("jpg")) {
			return Settings.CONTENT_TYPE_JPG;
		} else if(type.equals("png")) {
			return Settings.CONTENT_TYPE_PNG;
		} else if(type.equals("gif")) {
			return Settings.CONTENT_TYPE_GIF;
		} else if(type.equals("wbm")) {
			return Settings.CONTENT_TYPE_WEBM;
		} else {
			return Settings.CONTENT_TYPE_OCTET;
		}
	}

	public String getFileid() {
		return hash + "-" + size + "-" + xres + "-" + yres + "-" + type;
	}
	
	public String getHash() {
		return hash;
	}
	
	public int getSize() {
		return size;
	}
	
	public String getType() {
		return type;
	}
	
	public String getStaticRange() {
		return hash.substring(0, 4);
	}
	

	// static stuff
	public static boolean isValidHVFileid(String fileid) {
		return java.util.regex.Pattern.matches("^[a-f0-9]{40}-[0-9]{1,8}-[0-9]{1,5}-[0-9]{1,5}-((jpg)|(png)|(gif)|(wbm))$", fileid);
	}

	public static HVFile getHVFileFromFile(File file) {
		return getHVFileFromFile(file, null);
	}

	public static HVFile getHVFileFromFile(File file, FileValidator validator) {
		if(file.exists()) {
			String fileid = file.getName();

			try {
				HVFile hvFile = getHVFileFromFileid(fileid);
				
				if(hvFile == null) {
					return null;
				}
				
				if(file.length() != hvFile.getSize()) {
					return null;
				}
				
				if(validator != null) {
					if(!validator.validateFile(file.toPath(), fileid.substring(0, 40))) {
						return null;
					}
				}
				
				return hvFile;
			}
			catch(java.io.IOException e) {
				e.printStackTrace();
				Out.warning("Warning: Encountered IO error computing the hash value of " + file);
			}
		}
		
		return null;
	}
	
	public static HVFile getHVFileFromFileid(String fileid) {
		if(isValidHVFileid(fileid)) {
			try {
				String[] fileidParts = fileid.split("-");
				String hash = fileidParts[0];
				int size = Integer.parseInt(fileidParts[1]);
				int xres = Integer.parseInt(fileidParts[2]);
				int yres = Integer.parseInt(fileidParts[3]);
				String type = fileidParts[4];
				return new HVFile(hash, size, xres, yres, type);
			}
			catch(Exception e) {
				Out.warning("Failed to parse fileid \"" + fileid + "\" : " + e);
			}
		}
		else {
			Out.warning("Invalid fileid \"" + fileid + "\"");
		}
		
		return null;
	}
	
	public String toString() {
		return getFileid();
	}
}
