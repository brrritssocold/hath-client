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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Before;
import org.junit.Test;

import hath.base.Settings;

public class SettingsTest {
	private static final String LOG_OUT_FILE_NAME = "log_out";
	private static final String LOG_ERR_FILE_NAME = "log_err";

	private Path logDir;
	private Settings cut;

	@SuppressWarnings("deprecation")
	@Before
	public void setUp() throws Exception {
		logDir = Files.createTempDirectory("SettingsTest");
		Settings.getInstance().setLogDir(logDir.toFile());
		cut = Settings.getInstance();
	}

	@Test
	public void testLogDirectoryPath() throws Exception {
		assertThat(cut.getLogDir(), is(logDir.toFile()));
	}

	@Test
	public void testGetOutputLogPath() throws Exception {
		assertThat(Paths.get(cut.getOutputLogPath()), is(logDir.resolve(LOG_OUT_FILE_NAME)));
	}

	@Test
	public void testGetErrorLogPath() throws Exception {
		assertThat(Paths.get(cut.getErrorLogPath()), is(logDir.resolve(LOG_ERR_FILE_NAME)));
	}
}
