# HatH Client

[![Build Status](https://github.com/brrritssocold/hath-client/actions/workflows/maven.yml/badge.svg?branch=master)](https://github.com/brrritssocold/hath-client/actions/workflows/maven.yml)
[![codecov](https://codecov.io/gh/brrritssocold/hath-client/branch/master/graph/badge.svg?token=SmxgDI7wEC)](https://codecov.io/gh/brrritssocold/hath-client)
[![Codacy Badge](https://img.shields.io/codacy/grade/37cd1054934c4c689912992827cf5c3a/master.svg?maxAge=2592000)](https://www.codacy.com/app/brrritssocold/hath-client?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=brrritssocold/hath-client&amp;utm_campaign=Badge_Grade)

Make sure to read and understand the license for this program, found in the file LICENSE or at http://www.gnu.org/licenses/gpl.txt, before you start playing with it.

Note that this package only contains the Hentai@Home Client, which is coded specifically for E-Hentai Galleries. The server-side systems are highly dependent on the setup of a given site, and must be coded specially if it's to be used for other sites.

## Changes
 - Mavenized project
 - Split Base and GUI code into separate modules
 - Replaced logging code with SLF4J and logback
 - Create shaded executable jar files
 - Minor code cleanup
 - Additional logging code for debugging

## Known issues
 - Using SLF4J breaks logging output to GUI
 - Setting logback to rescan the configuration file will override the "Disable logging to disk" setting on the H@H page
 
## Building
In order to build Hentai@Home, you need the following:

- The Java(TM) SE JDK, version 11 or greater
- Maven 3 or later

To build run:
```mvn package```

## Running
```java -Dlogback.configurationFile=logback.xml -jar hath-base-1.6.2-0.1.0.jar```


