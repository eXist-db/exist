#!/usr/bin/perl -w

use XMLRPC::Lite;

$client = XMLRPC::Lite->proxy('http://localhost:8081');

open(XIN, $ARGV[0]);
$xml = '';
$pos = 0;
while(($l = sysread(XIN, $buf, 57)) > 0) {
    $pos = $pos + $l;
    $xml = $xml . $buf;
}
$xml = iso2utf8($xml);
print "$xml\n";
$doc = '/db/test/test.xml';
print "ok.\nsending $doc to server ...\n";
$resp = $client->parse(SOAP::Data->type(base64 => $xml), $doc);
if(!$resp->fault) {
    print "ok.\n";
} else {
    print "\nerror occured: " . $resp->faultstring. "\n";
}

sub iso2utf8 {
    my $buffer = shift;
    $buffer =~ s/([\x80-\xFF])/chr(0xC0|ord($1)>>6).chr(0x80|ord($1)&0x3F)/eg;
    return $buffer;
}
