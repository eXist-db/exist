#!/usr/bin/perl -w

use SOAP::Lite;

die "Usage: $0 collection document\n" unless @ARGV == 2;

my $collection = $ARGV[0];
my $doc = $ARGV[1];

my $service = 
    SOAP::Lite->service("http://localhost:8080/exist/services/Query?WSDL");
$service->on_fault(sub { my($soap, $res) = @_;
    die ref $res ? $res->faultdetail : $soap->transport->status, "\n"; });

print "Connecting ...\n";
my $session = $service->connect("guest", "guest");

print "Collection contents for $collection:\n";
my $resp = $service->listCollection($session, $collection);
my @resources = @{$resp->{'resources'}};
foreach $r (@resources) {
  print "\t$r\n";
}

$doc = "$collection/$doc";
print "Retrieving document $doc ...\n";

$resp = $service->getResourceData($session, $doc, TRUE, FALSE, FALSE);
print $resp;

$service->disconnect($session);
