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

my $doc = "samples/shakespeare/hamlet.xml";
$doc = $ARGV[0] unless @ARGV == 0;

my $xml = loadFile($doc);
print "$xml\n";

my $service = SOAP::Lite->service("http://localhost:8080/exist/services/Admin?WSDL");

print "Connecting ...\n";
$session = $service->connect("admin", "");

print "Storing file ...\n";
$resp = $service->store($session, SOAP::Data->type("base64" => iso2utf8($xml)), 
	"UTF-8", "/db/shakespeare/plays/hamlet.xml", TRUE);
$service->disconnect($session);
