VERSION = 0.1.0

configure:
	@./gen-Configure "Built using make."

all: configure
	javac -O -target 1.1 com/jcraft/jogg/*.java
	javac -O -target 1.1 com/jcraft/jorbis/*.java
	javac -O -target 1.1 com/fluendo/codecs/*.java
	javac -O -target 1.1 com/fluendo/player/*.java
	javac -O -target 1.1 com/fluendo/examples/*.java
	javac -O -target 1.1 com/fluendo/utils/*.java
	javac -O -target 1.1 com/fluendo/jheora/*.java
	javac -O -target 1.1 com/fluendo/plugin/*.java

clean:
	rm -f com/jcraft/jogg/*.class
	rm -f com/jcraft/jorbis/*.class
	rm -f com/fluendo/codecs/*.class
	rm -f com/fluendo/player/*.class
	rm -f com/fluendo/examples/*.class
	rm -f com/fluendo/utils/*.class
	rm -f com/fluendo/jheora/*.class
	rm -f cortado.jar 
	rm -f cortado.tgz 
	rm -rf build
	rm -rf dist

jar:
	jar cvf cortado.jar com/jcraft/jogg/*.class com/jcraft/jorbis/*.class com/fluendo/player/*.class com/fluendo/utils/*.class com/fluendo/jheora/*.class com/fluendo/codecs/*.class plugins.ini

dist:
	rm -f cortado-$(VERSION).tar.gz
	make cortado-$(VERSION).tar.gz

cortado-$(VERSION).tar.gz:
	rm -rf cortado-$(VERSION)
	mkdir cortado-$(VERSION)
	cp --parents \
		com/jcraft/jogg/*.java com/jcraft/jorbis/*.java \
		com/fluendo/player/*.java com/fluendo/utils/*.java \
		com/fluendo/jheora/*.java com/fluendo/codecs/*.java \
		com/fluendo/plugin/*.java \
		com/fluendo/examples/*.java \
		stubs/sun/audio/*.java \
		stubs/javax/sound/sampled/*.java \
		Makefile build.xml gen-Configure \
		LICENSE.cortado LICENSE.jheora LICENSE.smoke \
		ChangeLog README TODO play \
		cortado-$(VERSION)/
	tar cvzf $@ cortado-$(VERSION)
	rm -rf cortado-$(VERSION)

cortado-ovt-$(VERSION).jar:
	ant -Dexclude=MultiPart,JPEG,Smoke,Mulaw jar
	cp dist/applet/cortado.jar $@

cortado-mmjs-$(VERSION).jar:
	ant -Dexclude=Ogg,Theora,Vorbis jar
	cp dist/applet/cortado.jar $@

ovt:	cortado-ovt-$(VERSION).jar
mmjs:	cortado-mmjs-$(VERSION).jar
