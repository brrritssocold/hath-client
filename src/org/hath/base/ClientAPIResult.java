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

package org.hath.base;

public class ClientAPIResult {

	private int command;
	private String resultText;

	public ClientAPIResult(int command, String resultText) {
		this.command = command;
		this.resultText = resultText;
	}
	
	public String getResultText() {
		return resultText;
	}

	public String toString() {
		return "{ClientAPIResult: command=" + command + ",  resultText=" + resultText + "}";
	}
}
