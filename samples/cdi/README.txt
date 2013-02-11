
__How to run__:

$AS_MAIN/bin/asadmin start-domain
$AS_MAIN/bin/asadmin deploy --force ./target/*war

Run application in yout browser.

__How to test__:

mvn test -DargLine="-Dtyrus.test.port=8080 -Dtyrus.test.host=localhost"