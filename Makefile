all: 
	javac -O -target 1.1 com/jcraft/jogg/*.java
	javac -O -target 1.1 com/jcraft/jorbis/*.java
	javac -O -target 1.1 com/fluendo/codecs/*.java
	javac -O -target 1.1 com/fluendo/player/*.java
	javac -O -target 1.1 com/fluendo/examples/*.java
	javac -O -target 1.1 com/fluendo/utils/*.java
	javac -O -target 1.1 com/fluendo/jtheora/*.java

clean:
	rm -f com/jcraft/jogg/*.class
	rm -f com/jcraft/jorbis/*.class
	rm -f com/fluendo/codecs/*.class
	rm -f com/fluendo/player/*.class
	rm -f com/fluendo/examples/*.class
	rm -f com/fluendo/utils/*.class
	rm -f com/fluendo/jtheora/*.class

jar:
	jar cvf cortado.jar com/jcraft/jogg/*.class com/jcraft/jorbis/*.class com/fluendo/player/*.class com/fluendo/utils/*.class com/fluendo/jtheora/*.class

