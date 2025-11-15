![larex](https://github.com/OCR4all/LAREX/blob/master/documentation/larex.gif)
# [LAREX](https://github.com/OCR4all/LAREX)

[![Build verification with Maven](https://github.com/OCR4all/LAREX/actions/workflows/maven_build_verification.yml/badge.svg)](https://github.com/OCR4all/LAREX/actions/workflows/maven_build_verification.yml)

LAREX is a semi-automatic open-source tool for layout analysis on early printed books. 
It uses a rule based connected components approach which is very fast, 
easily comprehensible for the user and allows an intuitive manual correction if necessary. 
The PAGE XML format is used to support integration into existing OCR workflows. 
Evaluations showed that LAREX provides an efficient and flexible way to segment pages of early printed books.

Please feel free to visit the [tool homepage](https://www.uni-wuerzburg.de/zpd/larex/). A short user manual is available [here](https://www.ocr4all.org/guide/user-guide/workflow#segmentation-%E2%80%93-larex).

## Table of Contents
- [Installation](#installation)
  * [Docker](#docker)
  * [Quick Start (Gradle)](#quick-start-gradle)
  * [Linux](#linux)
  * [Windows](#windows)
  * [Mac OS X](#macos)
- [Usage](#usage)
- [Configuration](#configuration)
- [Related Publications](#related-publications)

_Additional information about developing for LAREX [see here](documentation/development.md)_

## Installation

### Docker
This guide uses [Docker](https://www.docker.com/) and allows a platform agnostic installation of LAREX
#### Production
* See: [LAREX Docker](https://github.com/maxnth/LAREX_Docker)

#### Development
* Configure `development/build.sh`, run `cd development` and `sh build.sh`

### Quick Start (Gradle)
**LAREX now uses Spring Boot 3 with embedded Tomcat and Gradle (Kotlin DSL) build system.**

#### Prerequisites
* Java 21 or higher
* Gradle is not required (Gradle wrapper is included)

#### Build and Run
```bash
# Clone the repository
git clone https://github.com/OCR4all/LAREX.git
cd LAREX

# Build the project
./gradlew build

# Run the application
./gradlew bootRun
```

The application will start on `http://localhost:8080`

#### Create executable JAR
```bash
# Build a standalone executable JAR
./gradlew bootJar

# The JAR will be created at: build/libs/Larex.jar
# Run it with:
java -jar build/libs/Larex.jar
```

#### Configuration
Create an `application.properties` file in the same directory as the JAR or use environment variables:

```properties
# Server port (default: 8080)
server.port=8080

# Book directory
larex.bookpath=/path/to/books

# Save settings
larex.localsave=none
larex.savedir=/path/to/save
larex.websave=true
```

Or use environment variables:
```bash
export LAREX_CONFIG=/path/to/custom/larex.properties
java -jar build/libs/Larex.jar
```

### Linux
This guide now uses the Gradle-based Spring Boot setup

* Install required packages: 
	`apt-get install openjdk-21-jdk git`
* Clone Repository: 
	`git clone https://github.com/OCR4all/LAREX.git`
* Build and run: 
	```bash
	cd LAREX
	./gradlew bootRun
	```
* Or build JAR and run:
	```bash
	./gradlew bootJar
	java -jar build/libs/Larex.jar
	```

### Windows
This guide uses [IntelliJ IDEA](https://www.jetbrains.com/idea/) or [Eclipse](https://www.eclipse.org/) to simplify the setup on Windows

#### Using IntelliJ IDEA (Recommended)
* Install IntelliJ IDEA Community Edition from the [official website](https://www.jetbrains.com/idea/download/)
* Install Java 21 JDK
* Clone Repository:
	* `File` -> `New` -> `Project from Version Control`
	-> Set `URL: https://github.com/OCR4all/LAREX.git` -> `Clone`
* The project will be automatically detected as a Gradle project
* Run the application:
	* Open `src/main/java/de/uniwue/LarexApplication.java`
	* Right click -> `Run 'LarexApplication'`

#### Using Eclipse
* Install _Eclipse IDE for Enterprise Java Developers_ from the [official website](https://www.eclipse.org/downloads/packages/)
* Install Java 21 JDK
* Clone Repository:
	* `File` -> `Import` -> `Git` -> `Projects from Git` -> `Clone URI`
	-> Set `URI: https://github.com/OCR4all/LAREX.git` -> `Next` -> `Import existing Gradle project` -> `Finish`
* Run the application:
	* Right click on `LarexApplication.java` -> `Run As` -> `Java Application`

### macOS
**Note: LAREX is mainly developed on Linux so the macOS build instructions may be outdated from time to time. If this is the case, feel free to contact us**

This guide uses homebrew (please adjust accordingly for your setup).
* Install Homebrew (see https://brew.sh/) and run `brew update`.
* Install required packages:
	* `brew install openjdk@21 git`
	* `sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk`
* Clone Repository:
	* `git clone https://github.com/OCR4all/LAREX.git`
* Build and run:
	```bash
	cd LAREX
	./gradlew bootRun
	```
* Or build JAR and run:
	```bash
	./gradlew bootJar
	java -jar build/libs/Larex.jar
	```


## Usage

### Access in browser
Go to `http://localhost:8080` (default port).

You can change the port by:
- Setting `server.port=<port>` in `application.properties`
- Using command line: `java -jar build/libs/Larex.jar --server.port=<port>`
- Using environment variable: `SERVER_PORT=<port> java -jar build/libs/Larex.jar`

### Using your own images and books
You can add your own books by copying them to `src/main/webapp/resources/books`

(Or an alternative direction set in the [config file](https://github.com/OCR4all/LAREX/blob/master/src/main/resources/application.properties). See section [*Configuration*](#configuration) for more information).

Book directories must have the following structure:
```
bookDir/
├── <book_name>/ 
│    ├── <page_name>.png 
│    └── <page_name>.xml
└── <book2_name>/
     └── …
```
### More information
Detailed information about the usage of LAREX can be found in the OCR4all [getting started](https://github.com/OCR4all/getting_started) guides.

See sections and chapters about _Segmentation_, _Ground Truth Correction_ and _Post Correction_.

## Configuration ##
LAREX contains a configuration file ([`src/main/resources/application.properties`](https://github.com/OCR4all/LAREX/blob/master/src/main/resources/application.properties)) with various settings.

### Spring Boot Configuration
Standard Spring Boot properties can be configured:
- `server.port`: Application port (default: 8080)
- `server.servlet.session.timeout`: Session timeout
- `spring.servlet.multipart.max-file-size`: Maximum file upload size

### LAREX Specific Configuration

### bookpath ###
The setting *larex.bookpath* sets the file path of the books folder.

e.g. `larex.bookpath=/home/user/books` (Linux)

e.g. `larex.bookpath=C:\\Users\\user\\Documents\\books` (Windows)

LAREX will load the books from this folder.

[default: src/main/webapp/resources/books]

### localsave ###
The setting *larex.localsave* tells the application how to handle results locally when saved.

_Please note_:  
To work properly in local mode it's required that the `Page@imageFilename`-attribute matches the actual filename (apart from the extension). This label will be used for local storage.

`<mode>=[bookpath|savedir|none]`

`bookpath`: save the result in the bookpath

`savedir`: save the result in a defined savedir

`none`: do not save the result locally [default]

e.g. `larex.localsave=bookpath`

### savedir ###
The setting *larex.savedir* is needed if localsave mode is set to "savedir".

e.g. `larex.savedir=/home/user/save` (Linux)

e.g. `larex.savedir=C:\\Users\\user\\Documents\\save` (Windows)

### websave ###
The setting *larex.websave* tells the application how to handle results on the browser side when saved.

`<value>=[true|false]`

`true`: download the result after saving [default]

`false`: no action after saving

e.g. `larex.websave=true`

### modes ###
Set the accessible modes in the LAREX GUI `<value>=[[segment][edit][lines][text]]`
A combination of the modes "segment", "edit", "lines" and "text" can be set as 
a space separated string. 
e.g. `larex.modes=segment lines`

The order of those modes in the string also determines which mode is opened
on startup, with the first in the list being opened as main mode.
The mode "segment" can be replaced with "edit" in order to hide all auto 
segmentation features. ("edit" will be ignored if both are present)

[Default] `larex.modes=segment lines text`


### directrequest ###
This setting enables or disables the direct open feature.

`<value>=[enable|disable]`

This feature allows users to load a book from everywhere on the servers drive as well as to alter the options *websave*,  *localsave* and *savedir*.

`enable`: enable direct request

`disable`: disable direct request [default]

e.g. `larex.directrequest=enable`

This feature should be used with caution but is very useful when using LAREX in a workflow with other web applications. (e.g. in Docker)

The easiest direct request would be via a html form with the values *bookpath*, *bookname*, *websave* (optional),  *localsave* (optional) and *savedir* (optional).
```html
<form action="http://localhost:8080/direct" method="POST">
	bookpath: <input type="text" name="bookpath"/><br>
	bookname: <input type="text" name="bookname"/><br>
	websave: <input type="text" name="websave"/><br>
	localsave: <input type="text" name="localsave"/><br>
	savedir: <input type="text" name="savedir"/><br>
	modes: <input type="text" name="modes"/><br>
	<input type="submit"/>
</form>
```

### OCR4all UI mode ###
This setting enables or disables OCR4all UI mode.

`<value>=[enable|disable]`

This setting allows displaying and/or hiding certain UI elements when LAREX is used in combination with OCR4all.

`enable`: enable OCR4all UI mode

`disable`: disable OCR4all UI mode [default]

e.g. `larex.ocr4all=enable`

### Environment Variables ###
Alternatively, you can use the `LAREX_CONFIG` environment variable to point to a custom properties file:
```bash
export LAREX_CONFIG=/path/to/custom/larex.properties
java -jar build/libs/Larex.jar
```


## Citing LAREX

If you are using LAREX please cite:

> Reul, C., Springmann, U., Puppe, F.: *Larex: A semi-automatic open-source tool for layout analysis and region extraction on early printed books* Proceedings of the 2nd International Conference on Digital Access to Textual Cultural Heritage (2017)
```
@inproceedings{reul2017larex,
  title={Larex: A semi-automatic open-source tool for layout analysis and region extraction on early printed books},
  author={Reul, Christian and Springmann, Uwe and Puppe, Frank},
  booktitle={Proceedings of the 2nd International Conference on Digital Access to Textual Cultural Heritage},
  pages={137--142},
  year={2017}
}
```
