#!/usr/bin/perl -w

use SOAP::Lite;

die "Usage: $0 document\n" unless @ARGV == 1;

my $document = $ARGV[0];

my $service = SOAP::Lite->service("http://localhost:8080/exist/services/Admin?WSDL");

my $session = $service->connect("admin", "");

print "Removing document $document\n";

print $service->removeDocument($session, $document) . "\n";

$service->disconnect($session);
