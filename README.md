# LAREX

LAREX is a semi-automatic open-source tool for layout analysis on early printed books. 
It uses a rule based connected components approach which is very fast, 
easily comprehensible for the user and allows an intuitive manual correction if necessary. 
The PageXML format is used to support integration into existing OCR workflows. 
Evaluations showed that LAREX provides an efficient and flexible way to segment pages of early printed books.

Please feel free to visit the [tool homepage](https://go.uniwue.de/larex) and the [web application](http://www.larex-webapp.informatik.uni-wuerzburg.de/).

## Current Status
In the last few weeks a new, browser based GUI has been built from scratch. 
This also required some substantial changes within the rest of the code. 
We are now working on a step by step adaption and integration of the existing functionality.
Nevertheless, feel free to start testing right away but please keep in mind that it's work in progress.

## Installing

### OpenCV
To run LAREX the image processing library [OpenCV](http://opencv.org/) including java bindings is required. We recommend using version 2.4.9. 
The usage of newer versions (3.X) is possible but some adapations within the code will be necessary.
LAREX expects the corresponding .jar to be located in /src/main/WEB-INF/lib and to be called "opencv.jar".

### Linux (Ubuntu)
#### Packages
`apt-get install tomcat7` (or use tomcat8)

`apt-get install maven`

`apt-get install libopencv-dev`

`apt-get install openjdk-8-jdk`

#### Link the opencv.jar
cd *your git rep*/Larex/src/main/WEB-INF

mkdir lib && cd lib

ln -s /usr/share/java/opencv.jar

#### Compile
run `mvn clean install` in the root dir. This dir contains the `pom.xml`.

#### Copy or link the created war file to tomcat
Either:
`sudo ln -s $PWD/target/Larex.war /var/lib/tomcat7/webapps/Larex.war`

or

`cp target/Larex.war /var/lib/tomcat7/webapps/Larex.war`


### Windows
In is recommended to use Eclipse.

#### OpenCV
Rename your .jar to "opencv.jar" and the corresponding .ddl to "opencv.dll" and copy it to /src/main/WEB-INF/lib.

#### Java EE for Web Developer
In Eclipse go to Help -> Install New Software -> Work with neon -> Install Web, XML, Java EE and OSGi Enterprise Development

#### Maven
Install Maven as seen above and build the project.

#### Apache Tomcat
Download the most recent version under http://tomcat.apache.org/download-90.cgi.

Select the web perspective and add the Tomcat server.

## Running
### Access in browser
Go to `localhost:8080/Larex`.

The WebApp was developed and tested using Chrome. We observed some strange behavour when using Firefox. These issues will be addressed in the near future.

### Using your own images
You can add your own books by copying them to src/webapp/resources/books.

.tif support and the PageXML export will be added in the very near future.

## Related Publications:
Reul, C., Springmann, U., and Puppe, F.: LAREX - A semi-automatic open-source Tool for Layout
Analysis and Region Extraction on Early Printed Books. Accepted for oral presentation at [DATeCH 2017](http://ddays.digitisation.eu/). 
Draft available at [arXiv](https://arxiv.org/abs/1701.07396).

Reul, C., Dittrich, M., and Gruner, M.: Case Study of a highly automated Layout Analysis and OCR of an incunabulum: 
‘Der Heiligen Leben’ (1488).. Accepted for oral presentation at [DATeCH 2017](http://ddays.digitisation.eu/). 
Draft available at [arXiv](https://arxiv.org/abs/1701.07395).
