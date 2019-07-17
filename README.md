# LAREX

[![Build Status](https://travis-ci.org/OCR4all/LAREX.svg?branch=master)](https://travis-ci.org/OCR4all/LAREX)

LAREX is a semi-automatic open-source tool for layout analysis on early printed books. 
It uses a rule based connected components approach which is very fast, 
easily comprehensible for the user and allows an intuitive manual correction if necessary. 
The PageXML format is used to support integration into existing OCR workflows. 
Evaluations showed that LAREX provides an efficient and flexible way to segment pages of early printed books.

Please feel free to visit the [tool homepage](https://go.uniwue.de/larex) and the [web application](http://www.larex-webapp.informatik.uni-wuerzburg.de/). A short user manual is available [here](http://www.is.informatik.uni-wuerzburg.de/fileadmin/10030600/Mitarbeiter/Reul_Christian/Projects/Layout_Analysis/LAREX_Quick_Guide.pdf).

## Installing

### Linux (Ubuntu)
For this guide tomcat version 7 is used.

#### Packages
`apt-get install tomcat7`

`apt-get install maven`

`apt-get install openjdk-8-jdk`

#### Clone Repository
`git clone https://github.com/OCR4all/LAREX.git`

#### Compile
run `mvn clean install -f LAREX/pom.xml`.

#### Copy or link the created war file to tomcat
Either: `sudo ln -s $PWD/LAREX/target/Larex.war /var/lib/tomcat7/webapps/Larex.war`

or `cp LAREX/target/Larex.war /var/lib/tomcat7/webapps/Larex.war`

#### Start Tomcat
`systemctl start tomcat7`

to restart `systemctl restart tomcat7`

to start automatically at system boot `systemctl enable tomcat7`

### Windows
It is recommended to use Eclipse.

#### Java EE for Web Developer
In Eclipse go to Help -> Install New Software -> Work with neon -> Install Web, XML, Java EE and OSGi Enterprise Development

#### Maven
Install Maven as seen above and build the project.

#### Apache Tomcat
Download the most recent version under http://tomcat.apache.org/download-90.cgi.

Select the web perspective and add the Tomcat server.

### Mac OS X

#### Homebrew
Install homebrew (see https://brew.sh/).

Afterwards install all required packages (java, Tomcat, git, and maven):
`brew cask install java`
`brew install tomcat git maven`

To verify the Tomcat installation use homebrew’s services utility. Tomcat should now be listed here:
`brew services list`

#### Clone Repository
Run in your desired project directory
`git clone https://github.com/OCR4all/LAREX.git`
to clone the repository.

#### Compile
run `mvn clean install -f LAREX/pom.xml`.

#### Copy or link the created war file to tomcat
Either: `sudo ln -s $PWD/LAREX/target/Larex.war /usr/local/Cellar/tomcat/[version]/libexec/webapps/Larex.war`

or `cp LAREX/target/Larex.war /usr/local/Cellar/tomcat/[version]/libexec/webapps/Larex.war`

#### Start Tomcat
`brew services start tomcat`

to restart `brew services restart tomcat`


## Running
### Access in browser
Go to `localhost:8080/Larex`.

### Using your own images
You can add your own books by copying them to src/webapp/resources/books

(Or an alternative direction set in the config file. See *Configuration* for more information).

## Configuration ##
Larex contains a configuration file (src/webapp/WEB-INF/larex.config) with a few settings that can be set before running the application.

### bookpath ###
The setting *bookpath* sets the file path of the books folder.

e.g. bookpath:/home/user/books (Linux)

e.g. bookpath:C:\Users\user\Documents\books (Windows)

Larex will load the books from this folder.

[default <Larex>/src/main/webapp/resources/books]

### localsave ###
The setting *localsave* tells the application how to handle results locally when saved.

\<mode\>=[bookpath|savedir|none]

bookpath: save the result in the bookpath

savedir: save the result in a defined savedir

none: do not save the result locally [default]

e.g. localsave:bookpath

### savedir ###
The setting *savedir* is needed if localsave mode is set to "savedir".

e.g. savedir:/home/user/save (Linux)

e.g. savedir:C:\Users\user\Documents\save (Windows)

### websave ###
The setting *websave* tells the application how to handle results on the browser side when saved.

\<value\>=[true|false]

true: download the result after saving [default]

false: no action after saving

e.g. websave:true

### modes ###
Set the accessible modes in the Larex gui `<value>=[[segment][edit][lines][text]]`
A combination of the modes "segment", "edit", "lines" and "text" can be set as 
a space separated string. 
e.g. modes:segment lines

The order of those modes in the string also determines which mode is opened
on startup, with the first in the list being opened as main mode.
The mode "segment" can be replaced with "edit" in order to hide all auto 
segmentation features. ("edit" will be ignored if both are present)

[Default]modes:segment lines text


### directrequest ###
This setting enables or disables the direct open feature.

\<value\>=[enable|disable]

This feature allows users to load a book from everywhere on the servers drive aswell as to alter the options *websave*,  *localsave* and *savedir*.

enable: enable direct request

disable: disable direct request [default]

e.g. directrequest:enable

This feature should be used with caution but is very useful when using Larex in a workflow with other web applications. (e.g. in docker)

The easiest direct request would be via a html form with the values *bookpath*, *bookname*, *websave* (optional),  *localsave* (optional) and *savedir* (optional).
```html
<form action="http://localhost:8080/Larex/direct" method="POST">
	bookpath: <input type="text" name="bookpath"/><br>
	bookname: <input type="text" name="bookname"/><br>
	websave: <input type="text" name="websave"/><br>
	localsave: <input type="text" name="localsave"/><br>
	savedir: <input type="text" name="savedir"/><br>
	modes: <input type="text" name="modes"/><br>
	<input type="submit"/>
</form>
```

## Related Publications:
Reul, Christian; Springmann, Uwe; Puppe, Frank: LAREX – A semi-automatic open-source Tool for Layout Analysis and Region Extraction on Early Printed Books. In Proceedings of the 2nd International Conference on Digital Access to Textual Cultural Heritage (2017). [ACM](https://dl.acm.org/citation.cfm?id=3078097). Draft available at [arXiv](https://arxiv.org/abs/1701.07396).

Reul, Christian; Dittrich, Marco; Gruner, Martin: Case Study of a highly automated Layout Analysis and OCR of an incunabulum: ‘Der Heiligen Leben’ (1488). In Proceedings of the 2nd International Conference on Digital Access to Textual Cultural Heritage (2017). [ACM](https://dl.acm.org/citation.cfm?id=3078098). Draft available at [arXiv](https://arxiv.org/abs/1701.07395).
