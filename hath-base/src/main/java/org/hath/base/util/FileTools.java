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

public class FileTools {

	public static File checkAndCreateDir(File dir) throws IOException {
		if(dir.isFile()) {
			dir.delete();
		}

		if(!dir.isDirectory()) {
			dir.mkdirs();
		}
	
		return dir;
	}

	public static byte[] getFileContents(File file) throws IOException {
		byte[] bytes = new byte[(int) file.length()];
		java.io.FileInputStream fis = new java.io.FileInputStream(file);
		fis.read(bytes);
		fis.close();
		return bytes;
	}
	
	public static String getStringFileContents(File file) throws IOException {
		char[] cbuf = new char[(int) file.length()];
		java.io.FileReader fr = new java.io.FileReader(file);
		fr.read(cbuf);
		fr.close();
		return new String(cbuf);
	}
	
	public static String getStringFileContentsUTF8(File file) throws IOException {
		int fileLength = (int) file.length();
		char[] cbuf = new char[fileLength];
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF8"));
		br.read(cbuf, 0, fileLength);
		br.close();
		return new String(cbuf);
	}
	
	public static void putFileContents(File file, byte[] content) throws IOException {
		java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
		fos.write(content);
		fos.close();
	}
	
	public static void putStringFileContents(File file, String content) throws IOException {
		java.io.FileWriter fw = new java.io.FileWriter(file);
		fw.write(content);
		fw.close();
	}
	
	public static void putStringFileContentsUTF8(File file, String content) throws IOException {
		int fileLength = (int) content.length();
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF8"));
		bw.write(content, 0, fileLength);
		bw.close();
	}	
	
	public static String getFileExtension(File file) {
		String[] filenameParts = file.getName().split("(-|\\.)");
		
		if(filenameParts.length > 1) {
			return filenameParts[filenameParts.length - 1];
		}
		else {
			return null;
		}
	}
	
	public static boolean copy(File fromFile, File toFile) {
		boolean success = false;
	
		FileInputStream from = null;
	    FileOutputStream to = null;

		try {
			from = new FileInputStream(fromFile);
			to = new FileOutputStream(toFile);
			byte[] buffer = new byte[4096];
			int bytesRead;

			while ((bytesRead = from.read(buffer)) != -1) {
				to.write(buffer, 0, bytesRead);
			}
			
			success = true;
		} catch(Exception e) {
			System.err.println(e.toString());
		} finally {
			try {
				from.close();
			} catch (IOException e) {}

			try {
				to.close();
			} catch (IOException e) {}
		}
		
		return success;
	}
}
