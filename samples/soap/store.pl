#!/usr/bin/perl -w

use SOAP::Lite;
use utf8;

sub loadFile {
  my($file) = @_;
  if(!(-r $file)) {
	print "cannot read $file!\n";
	return;
  }
  print "reading $file ...\n";
  open(XIN, "<:bytes", $file);
  $xml = '';
  $pos = 0;
  $l = sysread(XIN, $xml, 4096, $pos);
  while(defined($l) && $l > 0) {
    $pos = $pos + $l;
    $l = sysread(XIN, $xml, 4096, $pos)
  }
  return $xml;
}

die("Usage: $0 file_or_directory target_collection\n") unless @ARGV == 2;

my $service = SOAP::Lite->service("http://localhost:8080/exist/services/Admin?WSDL");

print "Connecting ...\n";
$session = $service->connect("admin", "");

$collection = $ARGV[1];
print "Creating collection $collection ...\n";
$service->createCollection($session, $collection);

my $files;
if(-d $ARGV[0]) {
    # Scan the directory for files
    @files = glob("$ARGV[0]/*");
} else {
    @files = ($ARGV[0]);
}

foreach $f (@files) {
    if(-f $f) {
        $xml = loadFile($f);
        if($xml =~ /((<\?.*\?>)|(<\w.*>))/) {
            ($name = $f) =~ s#.*[/\\]##s;
            $path = "$collection/$name";
            print "Storing file $f to $path...\n";
            $service->store($session, SOAP::Data->type("base64" => $xml), 
                "UTF-8", $path, TRUE);
        } else {
            print "$f does not look like an XML file\n";
        }
    } 
}
$service->disconnect($session);
