#!/bin/sh

cd build
jar cvfm HentaiAtHome.jar ../src/hath/base/HentaiAtHome.manifest hath/base
jar cvfm HentaiAtHomeGUI.jar ../src/hath/gui/HentaiAtHomeGUI.manifest ../src/hath/gui/*.png hath/gui
cd ..
