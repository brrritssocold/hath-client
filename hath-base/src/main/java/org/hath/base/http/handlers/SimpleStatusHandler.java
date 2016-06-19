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

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.hath.base.http.HTTPRequestAttributes;
import org.hath.base.http.HTTPRequestAttributes.IntegerAttributes;
import org.hath.base.util.HandlerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle requests with the given response code.
 */
public class SimpleStatusHandler extends AbstractHandler {
	private static final Logger logger = LoggerFactory.getLogger(SimpleStatusHandler.class);
	private final int responseCode;

	public SimpleStatusHandler(int responseCode) {
		this.responseCode = responseCode;
	}

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		logger.trace("Handling request with simple status code");
		logger.trace("Session {}, {}", HTTPRequestAttributes.getAttribute(request, IntegerAttributes.SESSION_ID),
				HandlerUtils.handlerStatus(baseRequest, request, response));

		response.setStatus(responseCode);
		baseRequest.setHandled(true);
	}
}
