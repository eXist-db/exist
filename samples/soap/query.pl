#!/usr/bin/perl -w

use SOAP::Lite;

my $service = SOAP::Lite->service("http://localhost:8080/exist/services/Query?WSDL");

my $query = "//SPEAKER";
$query = $ARGV[0] unless @ARGV == 0;
print "query: " . $query . "\n";

my $result = $service->query($query);

my $resultSetId = $result->{'resultSetId'};
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
my $i = 1;
my $xml;

while($i < $hits && $i < 10) {
  $xml = $service->retrieve($resultSetId, $i, 'ISO-8859-1', 1);
  print $xml . "\n";
  $i++;
}

print $xml . "\n";
