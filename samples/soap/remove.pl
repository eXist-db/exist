#!/usr/bin/perl -w

use SOAP::Lite;

my $doc = "/db/shakespeare/plays/hamlet.xml";
my $service = SOAP::Lite->service("http://localhost:8080/exist/services/Admin?WSDL");
$resp = $service->removeDocument($doc);
