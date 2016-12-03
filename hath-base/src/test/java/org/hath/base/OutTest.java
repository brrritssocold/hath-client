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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Before;
import org.junit.Test;

public class OutTest {
	private static final String ERROR_LOG_FILE_NAME = "log_err";
	private static final String OUTPUT_LOG_FILE_NAME = "log_out";

	private Path testDirectory;

	@SuppressWarnings("deprecation")
	@Before
	public void setUp() throws Exception {
		testDirectory = Files.createTempDirectory("OutTest");
		Settings.setLogDir(testDirectory.toFile());
	}

	private Path createOldLogFile(String filename) throws IOException {
		Path oldLog = testDirectory.resolve(filename + ".old");
		Files.createFile(oldLog);

		return oldLog;
	}

	@Test
	public void testStartLoggersErrorLogCreated() throws Exception {
		Out.startLoggers();

		assertThat(Files.exists(testDirectory.resolve(ERROR_LOG_FILE_NAME)), is(true));
	}

	@Test
	public void testStartLoggersOutputLogCreated() throws Exception {
		Out.startLoggers();

		assertThat(Files.exists(testDirectory.resolve(OUTPUT_LOG_FILE_NAME)), is(true));
	}

	@Test
	public void testNoOutputLogCreatedWhenLoggingDisabled() throws Exception {
		Settings.updateSetting("disable_logging", "true");

		Out.startLoggers();

		assertThat(Files.exists(testDirectory.resolve(OUTPUT_LOG_FILE_NAME)), is(false));
	}

	@Test
	public void testMissingLogDirectory() throws Exception {
		Files.deleteIfExists(testDirectory);

		Out.startLoggers();

		assertThat(Out.isLogSetupFailed(), is(true));
	}

	@Test
	public void testOldLogFileDeleted() throws Exception {
		Path oldLog = createOldLogFile(OUTPUT_LOG_FILE_NAME);

		assertThat(Files.exists(oldLog), is(true)); // guard assert

		Out.startLogger(testDirectory.resolve(OUTPUT_LOG_FILE_NAME));

		assertThat(Files.exists(oldLog), is(false));
	}

	@Test
	public void testLogFileRotation() throws Exception {
		Path currentLog = testDirectory.resolve(OUTPUT_LOG_FILE_NAME);
		Files.createFile(currentLog);

		Path oldLog = testDirectory.resolve(OUTPUT_LOG_FILE_NAME + ".old");

		assertThat(Files.exists(oldLog), is(false)); // guard assert
		assertThat(Files.exists(currentLog), is(true)); // guard assert

		Out.startLogger(testDirectory.resolve(OUTPUT_LOG_FILE_NAME));

		assertThat(Files.exists(oldLog), is(true));
	}
}
