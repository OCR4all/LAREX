LAREX: Development
==================
LAREX is a web application written in Java.
It uses Maven as building tool and is a Spring application.

Frameworks, tools and resources
-------------------------------
* [Maven](https://maven.apache.org/) - Project builder and [repository provider](https://mvnrepository.com/)
* [Spring Framework](https://spring.io/) - Base framework to create the Java web application
* [OpenPNP/OpenCV](https://github.com/openpnp/opencv) - "OpenCV Java bindings packaged with native libraries" for image processing
* [PRImA PAGE xml](https://www.primaresearch.org/tools/PAGELibraries) - Input and output format for segmentation and ground truth production data with the [official library](https://github.com/PRImA-Research-Lab/prima-core-libs)
* [Paper.js](http://paperjs.org/) - "The Swiss Army Knife of Vector Graphics Scripting" used to draw the interactive viewer"
* [Materialize CSS](https://materializecss.com/) - "A modern responsive front-end framework based on Material Design"
* [SASS](https://sass-lang.com/) - Enhanced style sheet language to create css from scss files (enhanced css)


Important notes for development from source
-------------------------------------------
### SASS
LAREX can be developed by changing the sources and updating the installation as it is described in [_Installation_ in the README](../README.md#installation).
Only one difference should be noted. In order to edit the style of the application SASS is required to create css files.
The base compiled versions of the current [scss files](https://github.com/OCR4all/Larex/blob/master/scss/) can be found in the [css directory](https://github.com/OCR4all/Larex/blob/master/src/main/webapp/resources/css/).
In order to edit the css one must make the changes in the scss files and compile them into the css folder as described in the official [SASS guide](https://sass-lang.com/guide)

e.g. (inside the LAREX folder)
```
sass --watch scss:src/main/webapp/resources/css --style compressed
```

### PRImA PAGE XML
The base format of LAREX is the PAGE xml from PRImA.
While LAREX uses maven as building and dependency providing tool, PRImA does not provide the official base library for PAGE XML as a maven module.
LAREX has therefore added the compiled PRImA jars into a local maven repository ([src/lib](https://github.com/OCR4all/LAREX/blob/master/src/lib)).
Updating the PRImA libraries therefore requires more changes than other maven dependencies in this project. One must update more than its version in the [pom.xml](https://github.com/OCR4all/LAREX/blob/master/pom.xml#L120).
Adding new versions to the local repository can be done with a [predefined script](https://github.com/OCR4all/LAREX/blob/master/src/lib/add.sh) (`sh add.sh`).
The script guides the developer through the installation in the local repository, by asking for the file, preferred maven group id, artifact id and version.
Information should mirror the module information in [pom.xml](https://github.com/OCR4all/LAREX/blob/master/pom.xml#L116) with a new version number.


Spring
------
Spring Framework is an application framework and hereby used to create a web application for a Tomcat Server.


