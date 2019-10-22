LAREX: Development
==================
LAREX is a web application written in Java.
It uses Maven as building tool and is a Spring application.

Frameworks, tools and resources
-------------------------------
* [Maven](https://maven.apache.org/) - Project builder and [repository provider](https://mvnrepository.com/)
* [Spring Framework](https://spring.io/) - Base framework to create the Java web application
* [OpenPNP/OpenCV](TODO) - Image processing library
* [PrimA PageXML](TODO) - Input and output format for segmentation and ground truth production data 
* [SASS](https://sass-lang.com/) - Enhanced style sheet language to create css from scss files (enhanced css)
* [Materialize CSS](TODO) - CSS library for enhanced UI functionality


Important notes for development from source
-------------------------------------------
## SASS
LAREX can be developed by changing the sources and updating the installation as it is described in [_Installation_ in the README](../README.md#installation).
Only one change should be noted. In order to edit the style of the application SASS is needed.
The base compiled versions of the current [scss files](https://github.com/OCR4all/Larex/blob/master/scss/) can be found in the [css directory](https://github.com/OCR4all/Larex/blob/master/src/main/webapp/resources/css/).
In order to edit the css one must make the changes in the scss files and compile them into the css folder as described in the official [SASS guide](https://sass-lang.com/guide)

e.g. (inside the Variance-Viewer folder)
```
sass --watch scss:src/main/webapp/resources/css --style compressed
```

## PrimA PageXML
The base format of LAREX is PAGE xml from PrimA.
While LAREX uses maven as building and dependency providing tool, PrimA does not provide the official base library for page xml as a maven module.
LAREX has therefore added the compiled PrimA jars into a [local maven repository](https://github.com/OCR4all/Larex/blob/master/src/lib).
Updating the PrimA libraries requires therefore more changes than updating its version in the [pom.xml](https://github.com/OCR4all/Larex/blob/master/pom.xml#L125).
Adding new versions to the local repository can be done with a [predefined skript](https://github.com/OCR4all/Larex/blob/master/src/lib/add.sh) (`sh add.sh`).
The script guides the developer through the installation in the local repository, by asking for the file, prefered maven group id, artifact id and version.
Informations should mirror the module information in [pom.xml](https://github.com/OCR4all/Larex/blob/master/pom.xml#L125) with a new version number.


Spring
------
Spring Framework is a application framwork and hereby used to create a web application for a Tomcat Server.


