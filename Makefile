#
# Basic Makefile for Hentai@Home and Hentai@Home GUI

hath:
	chmod 755 make.sh
	./make.sh

jar: 
	chmod 755 makejar.sh
	./makejar.sh

all: hath jar

clean:
	rm -rf build
