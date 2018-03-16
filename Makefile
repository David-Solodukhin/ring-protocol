all: jarbuild

jarbuild: compile
	cd src && jar -cevf com.company.Main ./ringo.jar ./com/company/*.class && cd ..
	mv src/ringo.jar .

compile:
	javac -cp src/com/company ./src/com/company/*.java

clean:
	rm ./ringo.jar
	rm ./src/com/company/*.class
