__Configuration__

The following steps are needed prior you run the tests:

o Install the following browsers:

 - Firefox: http://www.mozilla.org/en-US/firefox/
 - Chrome: http://www.google.com/intl/en/chrome/browser/
 - Safari (installed on MacOS, for Windows platform: http://support.apple.com/kb/dl1531)
 - Internet Explorer

o Setup the Selenium drivers and extensions 

Firefox:
 - nothig to be done

Chrome:
 - download chromedriver from http://code.google.com/p/chromedriver/downloads/list
 - unpack it
 - either export CHROME_DRIVER=/path/to/chromedriver or set 
   java property via -DCHROME_DRIVER=/path/to/chromedriver in mvn test target

Internet Explorer:
 - download the driver from 
 - unpack it
 - set IE_DRIVER=/path/to/iedriver, e.g.: C:/Tools/IEDriverServer.exe or set 
   java property via -DIE_DRIVER=/path/to/iedriver in mvn test target

Safari:
 - Download the Safari extension from: https://docs.google.com/folder/d/0B5KGduKl6s6-c3dlMWVNcTJhLUk/edit
 - Open the Safari browser and drag-and-drop the extension into it, follow the steps

__How to run__:

$AS_MAIN/bin/asadmin start-domain
$AS_MAIN/bin/asadmin deploy --force browser-test/target/*war 
$AS_MAIN/bin/asadmin deploy --force ../../samples/chat/target/*war
$AS_MAIN/bin/asadmin deploy --force ../../samples/auction/target/*war 

__How to test__:


export CHROME_DRIVER=/path/to/chromedriver
mvn test -Dtest=BrowserTest  -DargLine="-Dtyrus.test.port=8080 -Dtyrus.test.host=localhost"

or specify selenium drivers via java properties:

mvn test -Dtest=BrowserTest  -DargLine="-Dtyrus.test.port=8080 -Dtyrus.test.host=localhost -DIE_DRIVER=C:/Tools/IEDriverServer.exe"

