# Kibrary 

![version][version-image]
[![Java8][Java8-image]][Java8]

Library for waveform inversion.   
It bundles [ANISOtime](https://github.com/kensuke1984/Kibrary/wiki/ANISOtime) ([ANISOtime][ANISOtime]) package.<br>
A tutorial on how to use Kibrary for waveform inversion can be found [here](https://github.com/kensuke1984/Kibrary/wiki/Tutorial-for-waveform-inversion-using-Kibrary)


# INSTALLATION
## Java environment
 
Kibrary currently runs on *[Java SE Runtime Environment 8][JRE8]* or higher (Java 14 is strongly recommended).
If you are not sure about the version you have, 
click <a href="https://www.java.com/en/download/installed8.jsp" target="_blank">here</a> to check. 
 
You can download from [Oracle](https://www.oracle.com/technetwork/java/javase/downloads/index.html),
while you might want to manage by something like [sdkman](https://sdkman.io/).
If you are a macOS user and have [Homebrew](https://brew.sh) installed, then you can have the latest Java as below.
```bash
 % brew update
 % brew cask install java
```

## Install using Maven
1. [Install Apache Maven](https://maven.apache.org/download.cgi)
```bash
# On macOS, maven can be installed using brew
brew install maven
````
2. Clone the Kibrary respository to your local machine
```bash
git clone git@github.com:afeborgeaud/Kibrary.git
```
4. Change to the Kibrary directory, and build Kibrary using Maven
```
cd Kibrary
mvn package assembly:single
```
4. Add the generated JAR file ```kibrary-1.1a-jar-with-dependencies.jar``` to your CLASSPATH (in ~/.bashrc)
```bash
# replace /path/to/Kibrary/dir/ by the path to the Kibrary directory cloned in step 3
echo "# Kibrary\nexport CLASSPATH=/path/to/Kibrary/dir/target/kibrary-1.1a-jar-with-dependencies.jar:$CLASSPATH" >> ~/.bashrc
source ~/.bashrc
```
5. To check that the installation is succesfull, run:
```bash
java io.github.kensuke1984.kibrary.About
```


[release-image]:https://img.shields.io/badge/release-Shiva-pink.svg
[release]:https://en.wikipedia.org/wiki/Shiva
[version-image]:https://img.shields.io/badge/version-1.1a-yellow.svg

[alicense-image]: https://img.shields.io/badge/license-Apache--2-blue.svg?style=flat
[alicense]: https://www.apache.org/licenses/LICENSE-2.0

[olicense-image]: http://img.shields.io/badge/license-Oracle-blue.svg?style=flat
[olicense]: https://www.oracle.com/technetwork/licenses/bsd-license-1835287.html

[gplicense]: https://www.gnu.org/licenses/gpl-3.0.html
[gplicense-image]: http://img.shields.io/badge/license-GPL--3.0-blue.svg?style=flat


[ANISOtime]: http://www-solid.eps.s.u-tokyo.ac.jp/~dsm/anisotime.html
[Java8-image]:https://img.shields.io/badge/dependencies-JRE%208-brightgreen.svg


