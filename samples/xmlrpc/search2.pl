#!/usr/bin/perl

# Execute an XQuery through XML-RPC. Uses a single method
# call to process the query and retrieve a specified number
# of items from the generated result set.
#
use RPC::XML;
use RPC::XML::Client;

$query = <<END;
for \$speech in //SPEECH[LINE &= 'tear*']
order by \$speech/SPEAKER[1]
return
    \$speech
END

$URL = "http://guest:guest\@localhost:8080/exist/xmlrpc";
print "connecting to $URL...\n";
$client = new RPC::XML::Client $URL;

# Output options
$options = RPC::XML::struct->new(
    'indent' => 'yes', 
    'encoding' => 'UTF-8',
    'highlight-matches' => 'none');

$req = RPC::XML::request->new("query", RPC::XML::base64->new($query), 20, 1, $options);

$response = $client->send_request($req);
if($response->is_fault) {
    die "An error occurred: " . $response->string . "\n";
}
print $response->value;
