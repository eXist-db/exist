#!/usr/bin/perl -w

use SOAP::Lite;

my $collection = "/db/shakespeare/plays";
my $doc = "/db/shakespeare/plays/hamlet.xml";
$doc = $ARGV[0] unless @ARGV == 0;
my $service = SOAP::Lite->service("http://localhost:8080/exist/services/Query?WSDL");

print "Collection contents for $collection:\n";
my $resp = $service->listCollection($collection);
my @resources = @{$resp->{'resources'}};
foreach $r (@resources) {
  print "\t$r\n";
}
print "Retrieving document $doc ...\n";
$resp = $service->getResource("/db/shakespeare/plays/hamlet.xml", "UTF-8", 1);
print $resp;
