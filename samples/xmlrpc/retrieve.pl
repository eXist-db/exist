#!/usr/bin/perl

use RPC::XML;
use RPC::XML::Client;

$doc = $ARGV[0];
if(!$doc) {
    die "Please specify a document path as first argument!\n";
}

$URL = "http://guest:guest\@localhost:8080/exist/xmlrpc";
print "connecting to $URL...\n";
$client = new RPC::XML::Client $URL;

# Output options
$options = RPC::XML::struct->new(
    'indent' => 'yes', 
    'encoding' => 'UTF-8');
$req = RPC::XML::request->new("getDocument", $doc, $options);
$resp = $client->send_request($req);
if($resp->is_fault) {
    die "An error occurred: " . $resp->string . "\n";
}
print $resp->value . "\n";
