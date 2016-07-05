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

package org.hath.base.event;

import java.util.Collections;
import java.util.Map;

public class RequestEvent {
	private final RequestType requestType;
	private final String request;
	private final Map<String, String> additionals;

	public RequestEvent(RequestType requestType, String request, Map<String, String> additionals) {
		this.requestType = requestType;
		this.request = request;
		this.additionals = additionals;
	}

	public RequestEvent(RequestType requestType, String request) {
		this(requestType, request, Collections.emptyMap());
	}

	public RequestType getRequestType() {
		return requestType;
	}

	public String getRequest() {
		return request;
	}

	public synchronized Map<String, String> getAdditionals() {
		return additionals;
	}
}
