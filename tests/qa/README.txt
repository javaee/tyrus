
__How to run__:

$AS_MAIN/bin/asadmin start-domain
$AS_MAIN/bin/asadmin deploy --force browser-test/target/*war 
$AS_MAIN/bin/asadmin deploy --force ../../samples/chat/target/*war
$AS_MAIN/bin/asadmin deploy --force ../../samples/auction/target/*war 

__How to test__:

mvn test -Dtest=BrowserTest  -DargLine="-Dtyrus.test.port=8080 -Dtyrus.test.host=localhost"

