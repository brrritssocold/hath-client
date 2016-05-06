@if not exist org mkdir org
@cd src
@dir /s /b *.java > ../org/srcfiles.txt
@cd ..
javac -source 1.6 -target 1.6 -d . -classpath sqlite-jdbc-3.7.2 @org/srcfiles.txt
