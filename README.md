# FooterMilter
Java implementation of the Sendmail Milter protocol based on the project of org.nightcode.jmilter from dmitry@nightcode.org to **insert a footer/disclaimer at the end of the body of an email**.

## DokuWiki - Detailed description
The following DokuWiki contains detailed instructions for installation and configuration. It describes the use under [CentOS](https://www.centos.org/) **7** in combination with the MTA [Postfix](http://www.postfix.org/). (The current documentation inside the DokuWiki is **only available in German language**.)

[Tachtler's DokuWiki - Postfix CentOS 7 - FooterMilter einbinden (footermilter)](https://dokuwiki.tachtler.net/doku.php?id=tachtler:postfix_centos_7_-_footermilter_einsetzen_footermilter)

## Installation and Configuration
The description of the installation and configuration of **FooterMilter** is divided into the following parts sections:

 1. [Prerequisites](README.md#prerequisites)
 2. [Download](README.md#download)
 3. [Installation](README.md#installation)

### Prerequisites
There is only one dependency for the execution of FooterMilter
 * Running **Java** installation e.g. [OpenJDK](https://openjdk.java.net/) **from version 1.8** or higher
 
### Download
Under the following link, you can download the executable **Java** - `FooterMilter.jar` archive file **and the required dependencies as a package**:
 * [GitHub - tachtler/FooterMilter](https://github.com/tachtler/FooterMilter)

### Installation
To install the **FooterMilter and the required dependencies as a package**, the following instructions must be followed:

:exclamation: **Note** - The FooterMilter and its required dependencies are installed in the following example in the 
 * `/opt/FooterMilter`
 
directory.

Download the **FooterMilter and the required dependencies as a package** using following command into the `/tmp` directory:

` # wget -P /tmp https://github.com/tachtler/FooterMilter/archive/master.zip`
