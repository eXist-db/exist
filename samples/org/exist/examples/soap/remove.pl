#!/usr/bin/perl -w

use SOAP::Lite;

my $doc = "/db/shakespeare/plays";
my $service = SOAP::Lite->service("http://localhost:8080/exist/services/Admin?WSDL");

my $session = $service->connect("admin", "");

$service->removeCollection($session, $doc);
