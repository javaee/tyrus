__How to run__:

$AS_MAIN/bin/asadmin start-domain
$AS_MAIN/bin/asadmin deploy --force ./target/*war

Run application in your browser.

Once first user enters the auction, the auction is started (time countdown starts at the predefined time). User can bid on the item. After each bid the auction timer
is set to predefined time again. The auction finishes once the predefined time runs out.
