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
import org.hath.base.http.SessionTracker;
import org.hath.base.util.HandlerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This handler removes handled sessions from the session tracker.
 */
public class SessionRemovalHandler extends AbstractHandler {
	private static final Logger logger = LoggerFactory.getLogger(SessionRemovalHandler.class);
	private final SessionTracker sessionTracker;

	public SessionRemovalHandler(SessionTracker sessionTracker) {
		this.sessionTracker = sessionTracker;
	}

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {

		int session = HTTPRequestAttributes.getAttribute(request, IntegerAttributes.SESSION_ID);
		
		logger.trace("Removing session with id: {}, {}", session,
				HandlerUtils.handlerStatus(baseRequest, request, response));

		sessionTracker.remove(session);
	}
}
