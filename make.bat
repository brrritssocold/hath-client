@if not exist build mkdir build
@cd src
@dir /s /b *.java > ../build/srcfiles.txt
@cd ..
javac -source 1.8 -target 1.8 -d ./build  @build/srcfiles.txt
