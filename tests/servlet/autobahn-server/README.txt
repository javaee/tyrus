
__How to run__:

$AS_MAIN/bin/asadmin start-domain
$AS_MAIN/bin/asadmin deploy --force ./target/*war

wstest -d -m fuzzingclient -s glassfish-config.json

__Cleanup__:

$AS_MAIN/bin/asadmin stop-domain

__Links__:

http://autobahn.ws/testsuite
http://autobahn.ws/testsuite/installation
http://autobahn.ws/testsuite/manual


