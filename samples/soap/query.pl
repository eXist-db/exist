#!/usr/bin/perl -w

use SOAP::Lite;
use MIME::Base64;

sub escapeXML {
    my($c) = @_;
    if($c eq "<") {
        return "&lt;";
    } elsif($c eq '>') {
        return "&gt;";
    } elsif($c eq "&") {
        return "&amp;";
    }
}

my $service = SOAP::Lite->service("http://localhost:8080/exist/services/Query?WSDL");

my $query = '';
while(<>) { 
#    s/[<>&]/escapeXML($&)/ge;
    $query .= $_;
}

print "query: $query\n";

print "connecting ...\n";
my $session = $service->connect("guest", "guest");

print "executing query ...\n";
my $result = $service->query($session, $query);

my $hits = $result->{'hits'};
my $queryTime = $result->{'queryTime'};
my @collections = @{$result->{'collections'}};

print "found $hits hits in $queryTime ms.\n";

# display hits 1 to 100
my $r = $service->retrieve($session, 1, 100, "true", "true", "elements");
foreach $result (@{$r}) {
	print $result . "\n";
}
