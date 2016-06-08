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

package org.hath.base.http;

import javax.servlet.http.HttpServletRequest;

public class HTTPRequestAttributes {
	/**
	 * Use {@link BooleanAttributes#LOCAL_NETWORK_ACCESS} instead
	 */
	@Deprecated
	public static final String LOCAL_NETWORK_ACCESS = "org.hath.base.http.localNetworkAccess";
	/**
	 * Use {@link BooleanAttributes#API_SERVER_ACCESS} instead
	 */
	@Deprecated
	public static final String API_SERVER_ACCESS = "org.hath.base.http.apiServerAccess";

	public enum BooleanAttributes {
		LOCAL_NETWORK_ACCESS("org.hath.base.http.localNetworkAccess"), API_SERVER_ACCESS(
				"org.hath.base.http.apiServerAccess");
		
		private final String attributeName;

		private BooleanAttributes(final String attributeName) {
			this.attributeName = attributeName;
		}

		@Override
		public String toString() {
			return this.attributeName;
		}
	};

	/**
	 * Get the value of the boolean attribute.
	 * 
	 * @param request
	 *            the request containing the attribute
	 * @param attribute
	 *            to be read
	 * @return the value of the attribute, or false if not set
	 */
	public static boolean getAttribute(HttpServletRequest request, BooleanAttributes attribute) {
		Object attr = request.getAttribute(attribute.toString());

		if (attr == null) {
			return false;
		}

		return (boolean) attr;
	}
}
