#!/usr/bin/perl

use RPC::XML;
use RPC::XML::Client;

$query = "document(*)//character";
#$query = "document(*)//OPERAT";
$client = new RPC::XML::Client "http://localhost:8080/exist/xmlrpc";
$req = RPC::XML::request->new("executeQuery", RPC::XML::base64->new($query), 
	"latin1");
$resp = $client->send_request($req);
$result_id = $resp->value;
$req = RPC::XML::request->new("retrieve", $result_id, 1, 1, "latin1");
$resp = $client->send_request($req);
print $resp->value . "\n";
