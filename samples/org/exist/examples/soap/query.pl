#!/usr/bin/perl -w

use SOAP::Lite;
use MIME::Base64;

my $service = SOAP::Lite->service("http://localhost:8080/exist/services/Query?WSDL");

my $query = "//SPEAKER";
$query = $ARGV[0] unless @ARGV == 0;
print "query: " . $query . "\n";

print "connecting ...\n";
my $session = $service->connect("guest", "guest");

my $result = $service->query($session, $query);

my $hits = $result->{'hits'};
my $queryTime = $result->{'queryTime'};
my @collections = @{$result->{'collections'}};

format STDOUT_TOP =
collection: @<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
$collectionName
======================================================================
.
format STDOUT = 
@<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<              @>>>>>>>>>>>
$name, $docHits
.

# print hits by collection and document
foreach $collection (@collections) {
  $collectionName = $collection->{"collectionName"};
  my @docs = @{$collection->{'documents'}};
  foreach $doc (@docs) {
    $name = $doc->{'documentName'};
    $docHits = $doc->{'hitCount'};
    write(STDOUT);
  }
}
print "found $hits hits in $queryTime ms.\n";

# display hits 1 to 10

my $r = $service->retrieve($session, 1, 100, "true", "true", "elements");
foreach $result (@{$r}) {
	print "$result" . "\n";
}
