#!/usr/bin/perl -w

use SOAP::Lite;

sub loadFile {
  my($file) = @_;
  if(!(-r $file)) {
	print "cannot read $file!\n";
	return;
  }
  print "reading $file ...\n";
  open(XIN, $file);
  $xml = '';
  $pos = 0;
  while(($l = sysread(XIN, $xml, 57, $pos)) > 0) {
    $pos = $pos + $l;
  }
  return iso2utf8($xml);
}

sub iso2utf8 {
    my $buffer = shift;
    $buffer =~ s/([\x80-\xFF])/chr(0xC0|ord($1)>>6).chr(0x80|ord($1)&0x3F)/eg;
    return $buffer;
}

my $doc = "../shakespeare/hamlet.xml";
my $xml = loadFile($doc);
print "$xml\n";
my $service = SOAP::Lite->service("http://localhost:8080/exist/services/Admin?WSDL");
$resp = $service->store($xml, "/db/shakespeare/plays/hamlet.xml", "UTF-8", 1);

