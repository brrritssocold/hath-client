/*

Copyright 2008-2016 E-Hentai.org
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

package org.hath.base.http.handlers;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.hath.base.MiscTools;
import org.hath.base.Out;
import org.hath.base.Settings;
import org.hath.base.http.HTTPRequestAttributes;
import org.hath.base.http.HTTPRequestAttributes.ClassAttributes;
import org.hath.base.http.HTTPRequestAttributes.IntegerAttributes;
import org.hath.base.http.HTTPResponseProcessorSpeedtest;

public class SpeedTestHandler extends AbstractHandler {

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		String[] urlparts = target.replace("%3d", "=").split("/");

		// sends a randomly generated file of a given length for speed testing
		// purposes
		if (urlparts.length < 4) {
			response.setStatus(HttpStatus.BAD_REQUEST_400);
			return;
		}
		int testsize = Integer.parseInt(urlparts[1]);
		int testtime = Integer.parseInt(urlparts[2]);
		String testkey = urlparts[3];

		String session = String.valueOf(HTTPRequestAttributes.getAttribute(request, IntegerAttributes.SESSION_ID));

		Out.debug("Sending threaded proxy test with testsize=" + testsize + " testtime=" + testtime + " testkey="
				+ testkey);

		if (Math.abs(testtime - Settings.getServerTime()) > Settings.MAX_KEY_TIME_DRIFT) {
			Out.warning(session + " Got a speedtest request with expired key");
			response.setStatus(HttpStatus.FORBIDDEN_403);
		} else if (!calculateTestKey(testsize, testtime).equals(testkey)) {
			Out.warning(session + " Got a speedtest request with invalid key");
			response.setStatus(HttpStatus.FORBIDDEN_403);
		} else {
			response.setStatus(HttpStatus.OK_200);
			request.setAttribute(ClassAttributes.HTTPResponseProcessor.toString(),
					new HTTPResponseProcessorSpeedtest(testsize));

			baseRequest.setHandled(true); //FIXME remove after basehandler is done
		}
	}

	public static String calculateTestKey(int testsize, int testtime) {
		return MiscTools.getSHAString("hentai@home-speedtest-" + testsize + "-" + testtime + "-"
				+ Settings.getClientID() + "-" + Settings.getClientKey());
	}
}
