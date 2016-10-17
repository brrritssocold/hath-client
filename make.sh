#!/bin/sh

if [ ! -d org ]; then
mkdir org
fi

cd src
find . -type f -name "*.java" -printf "$PWD/%h/%f\n" > ../org/srcfiles.txt
cd ..
javac -source 1.7 -target 1.7 -d . -classpath "sqlitejdbc-v056.jar" "@org/srcfiles.txt"
