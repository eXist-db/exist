#!/usr/bin/perl

use RPC::XML;
use RPC::XML::Client;

# Execute an XQuery through XML-RPC. The query is passed
# to the "executeQuery" method, which returns a handle to
# the created result set. The handle can then be used to
# retrieve results.

$query = <<END;
for \$speech in //SPEECH[LINE &= \$query]
order by \$speech/SPEAKER[1]
return
    \$speech
END

# user-supplied variables
$vars = RPC::XML::struct->new('query' => 'corrupt*');
# Output options
$options = RPC::XML::struct->new(
    'indent' => 'yes', 
    'encoding' => 'UTF-8',
	'variables' => $vars
);

$URL = "http://guest:guest\@localhost:8080/exist/xmlrpc";
print "connecting to $URL...\n";
$client = new RPC::XML::Client $URL;

# Execute the query. The method call returns a handle
# to the created result set.
$req = RPC::XML::request->new("executeQuery", 
    RPC::XML::base64->new($query), 
	"UTF-8", $options);
$resp = process($req);
$result_id = $resp->value;

# Get the number of hits in the result set
$req = RPC::XML::request->new("getHits", $result_id);
$resp = process($req);
$hits = $resp->value;
print "Found $hits hits.\n";

# Retrieve query results 1 to 10
for($i = 1; $i < 10 && $i < $hits; $i++) {
    $req = RPC::XML::request->new("retrieve", $result_id, $i, $options);
    $resp = process($req);
    print $resp->value . "\n";
}

# We release the result set handle
$req = RPC::XML::request->new("releaseQueryResult", $result_id);
process($req);

# Send the request and check for errors
sub process {
    my($request) = @_;
    $response = $client->send_request($request);
    if($response->is_fault) {
        die "An error occurred: " . $response->string . "\n";
    }
    return $response;
}
