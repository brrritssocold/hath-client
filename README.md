# HatH Client
[![Build Status](https://travis-ci.org/brrritssocold/hath-client.svg?branch=master)](https://travis-ci.org/brrritssocold/hath-client)
Make sure to read and understand the license for this program, found in the file LICENSE or at http://www.gnu.org/licenses/gpl.txt, before you start playing with it.

Note that this package only contains the Hentai@Home Client, which is coded specifically for E-Hentai Galleries. The server-side systems are highly dependent on the setup of a given site, and must be coded specially if it's to be used for other sites.

## Changes
 - Mavenized project
 - SQLite dependency managed with Maven
 - Split Base code and GUI related code into separate modules
 - Replaced most logging code with SLF4J and logback
 - Create shaded executable jar files
 - Minor code cleanup
 - Replaced custom HTTP server with embedded jetty
 - Split parsing code into several handlers
 - Replaced custom HTTP client with jetty-client
 - Additional logging code for debugging
 
## Building
In order to build Hentai@Home, you need the following:

- The Java(TM) SE JDK, version 8 or greater
- Maven 3 or later

To build run:
```mvn package```

## Running
```java -Dlogback.configurationFile=logback.xml -jar hath-base-1.2.6.jar```


