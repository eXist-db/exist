#!/usr/bin/perl

use RPC::XML;
use RPC::XML::Client;
use Term::ReadLine;
use MIME::Base64;
use Getopt::Long;

$PRETTY = 1;
$CLIENTS = 5;
$QUERY_COUNT=5;
$HOWMANY = 10;
$NEXT_RECORD = 1;
@RESULT_SET = undef;
$COLLECTION = "/db";
$USER = "admin";
$PASS = "";

printNotice();

GetOptions("i|indent" => \$indent,
	   "t|test=s" => \$test,
	   "clients=i" => \$CLIENTS,
	   "count=i" => \$QUERY_COUNT,
	   "u|user=s" => \$USER,
	   "p|password=s" => \$PASS);
	   
$URL = "http://$USER:$PASS\@localhost:8081";
print "connecting to $URL...\n";
$client = new RPC::XML::Client $URL;

$PRETTY = ($indent ? 1 : 0);
if($test) {
  test();
  exit(0);
}

if(@ARGV > 0) {
  # execute query found on command line
  print "\n" . query($ARGV[0]) . "\n";
  exit(0);
}

# enter interactive mode

$term = new Term::ReadLine 'eXist perl-client';
$PRETTY = 1; # turn pretty printing on
$PROMPT = "[eXist $COLLECTION]\$ ";

print "Type help for help.\n";
while(defined($_ = $term->readline($PROMPT))) {
  next if($_ eq "");
  ($cmd, $args) = /(\w+)[\s]*(.*)/;
  if($cmd eq "find") {
    $hits = query($args);
    print "found " . $hits . " hits. Use show to retrieve results.\n"
      if($hits > -1);

  } elsif($cmd eq "summary") {
    summary($args);

  } elsif($cmd eq "show") {
    print show($args) . "\n";

  } elsif($cmd eq "quit") {
    last;

  } elsif($cmd eq "get") {
    print getDocument($args);

  } elsif($cmd eq "ls") {
    listDocuments();

  } elsif($cmd eq "cd") {
    if($args =~ /^\/.*/) {
      $tmpColl = $args;
    } else {
      $tmpColl = "$COLLECTION/$args";
    }
    $req = RPC::XML::request->new('getCollectionDesc', $tmpColl);
    $resp = $client->send_request($req);
    if($resp->is_fault) {
      print "collection $tmpColl not found!\n";
    } else {
      $COLLECTION = $tmpColl;
    }
  } elsif($cmd eq "createid") {
      $req = RPC::XML::request->new('createId', $COLLECTION);
      $resp = $client->send_request($req);
      if($resp->is_fault) {
          print $resp->string . "\n";
      } else {
          print $resp->value . "\n";
      }
  } elsif($cmd eq "put") {
    parse($args);

  } elsif($cmd eq "rm") {
    remove($args);

  } elsif($cmd eq "mkcol") {
    mkcol($args);

  } elsif($cmd eq "rmcol") {
      rmcol($args);
      
  } elsif($cmd eq "help") {
    printHelp();

  } elsif($cmd eq "set") {
    setProperty($args);

  } elsif($cmd eq "bench") {
    benchmark($args);

  } else {
    print "unknown command $cmd.\n";
    printHelp();
    next;
  }
  $PROMPT = "[eXist $COLLECTION]\$";
}

sub test {
  open(QIN, $test);
  @queries = <QIN>;
  close(QIN);

  print "forking $CLIENTS client processes ...\n";
  for($i = 0; $i < $CLIENTS; $i++) {
    if(!defined($child_pid = fork())) {
      die "unable to fork child process.";
    } elsif($child_pid) {
      # child process runs here
      $out_file = ">" . $child_pid . ".out";
      open(CHILD_OUT, $out_file);

      srand($child_pid);
      for($i = 0; $i < $QUERY_COUNT; $i++) {
        $r = rand(@queries);
        $qu = $queries[$r];
        print "[$child_pid] query $i: $qu\n";
        print CHILD_OUT "query: $qu\n";
        $start = time();
        $req = RPC::XML::request->new('query', $qu, "UTF-8", 
                          RPC::XML::int->new($HOWMANY),
                          RPC::XML::int->new(1),
                          RPC::XML::int->new($PRETTY)
                         );
        $resp = $client->send_request($req);
        if($resp->is_fault) {
          print "\nerror occured: " . $resp->string . "\n";
          print "($child_pid) query: $qu\n";
          return -1;
        } else {
          $result = decode_base64($resp->value);
          $hits = $resp->value;
          print CHILD_OUT $result;
          $t = (time() - $start);
          print "($child_pid) query: [$i] " . $qu . " took $t sec.\n";
          print CHILD_OUT "$qu took $t sec.\n";
        }
      }
      close(CHILD_OUT);
    }
  }
  $waitpid = 1;
  while($waitpid > -1) {
    $waitpid = wait();
  }
  exit(0);
}

sub query {
  my($query) = @_;
  $query = iso2utf8($query);
  $req = RPC::XML::request->new('executeQuery', 
    RPC::XML::base64->new($query), 'UTF-8');
  $resp = $client->send_request($req);
  if($resp->is_fault) {
    print "\nerror occured: " . $resp->string . "\n";
    return -1;
  }
  $RESULT_ID = $resp->value;
  $req = RPC::XML::request->new('getHits', RPC::XML::int->new($RESULT_ID));
  $resp = $client->send_request($req);
  if(!$resp->is_fault) {
    $HITS = $resp->value;
    return $HITS;
  } else {
    print "\nerror occured: " . $resp->string . "\n";
    return -1;
  }
}

sub show {
  my($args) = @_;
  my($start, $howmany, $xml);
  if($RESULT_ID < 0) {
    print "no query. Use find to do a query!\n";
    return;
  }
  if(!$args) {
    $start = $NEXT_RECORD;
    $howmany = $HOWMANY;
  } elsif($args =~ /\d+\s*,\s*\d+/) {
    ($start, $howmany) = $args =~ /(\d+)\s*,\s*(\d+)/;
  } else {
    $start = $args;
    $howmany = $HOWMANY;
  }

  if($start > $HITS) {
    print "start parameter out of range.\n";
    return;
  }
  $howmany = $HITS - $start + 1 if($start + $howmany > $HITS);

  for($i = $start - 1; $i < $start + $howmany - 1; $i++) {
    $req = RPC::XML::request->new('retrieve',
				  RPC::XML::int->new($RESULT_ID),
				  $i,
				  RPC::XML::int->new($PRETTY),
				  "ISO-8859-1");
    $resp = $client->send_request($req);
    if(!$resp->is_fault) {
      #$xml .= $resp->value;
      $xml .= decode_base64($resp->value);
    } else {
      print "\nerror occured: " . $resp->string . "\n";
      return $xml;
    }
  }
  $NEXT_RECORD = $start + $howmany;
  return $xml;
}

sub setProperty {
  my($args) = @_;
  ($key, $value) = $args =~ /(\w+)\s*=\s*(.*)/;
  if($key eq "indent") {
    $PRETTY = ($value eq "on") ? 1 : 0;
    print "$key = $value\n";
  } elsif($key eq "collection") {
    $COLLECTION = $value;
    print "$key = $value\n";
  } elsif($key eq "display") {
    $HOWMANY = $value;
    print "$key = $value\n";
  } else {
    print "unknown property: $key\n";
  }
}

sub escape {
  my($str) = @_;
  $str =~ s/&/&amp;/g;
  $str =~ s/</&lt;/g;
  $str =~ s/>/&gt;/g;
  print $str;
  return $str;
}

sub parse {
  my($args) = @_;
  ($file, $doc) = split(/,\s*/, $args);
  if(-d $file) {
    print "reading files from directory $file\n";
    while($name = <$file/*.xml $file/*.XML>) {
      print "\nparsing $name:\n";
      print "-" x 25 . "\n";
      ($doc = $name) =~ s#.*/##s;
	parseFile($name, $doc); 
    }
  } else {
    if($doc eq '') {
      ($doc = $file) =~ s#.*/##s;
    }
    parseFile($file, $doc);
  }
}

sub parseFile {
  my($file, $doc) = @_;
  if(!(-r $file)) {
	print "cannot read $file!\n";
	return;
  }
  print "reading $file ...";
  open(XIN, $file);
  $xml = '';
  $pos = 0;
  $buf = '';
  while(($l = sysread(XIN, $xml, 57, $pos)) > 0) {
    $pos = $pos + $l;
  }
  $xml = iso2utf8($xml);
  $doc = "$COLLECTION/$doc" if($COLLECTION);
  print "ok.\nsending $doc to server ...\n";
  $req = RPC::XML::request->new('parse',
    RPC::XML::base64->new($xml),
    $doc);
  $resp = $client->send_request($req);
  if(!$resp->is_fault) {
    print "ok.\n";
  } else {
    print "\nerror occured: " . $resp->string. "\n";
  }
}

sub remove {
  my($args) = @_;
  if(!($args =~ /\/.*/)) {
    $args = "$COLLECTION/$args";
  }
  print "removing document $args ...\n";
  $resp = $client->send_request(RPC::XML::request->new('remove', $args));
  if(!$resp->is_fault) {
    print "ok.\n";
  } else {
    print "\nerror occured: " . $resp->string . "\n";
  }
}

sub mkcol {
  my($args) = @_;
  if(!($args =~ /\/.*/)) {
    $args = "$COLLECTION/$args";
  }
  print "creating collection $args";
  $resp = 
    $client->send_request(RPC::XML::request->new('createCollection', $args));
  if(!$resp->is_fault) {
    print "ok.\n";
  } else {
    print "\nerror occurred: " . $resp->string . "\n";
  }
}

sub rmcol {
  my($args) = @_;
  if(!($args =~ /\/.*/)) {
    $args = "$COLLECTION/$args";
  }
  print "removing collection $args\n";
  $resp = 
    $client->send_request(RPC::XML::request->new('removeCollection', $args));
  if(!$resp->is_fault) {
    if($resp->value == 0) {
      print "could not remove collection $args.\n";
    } else {
      print "removed collection $args.\n";
    }
  } else {
    print "\nerror occurred: " . $resp->string . "\n";
  }
}

sub benchmark {
  my($file) = @_;
  format STDOUT =
@<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<  @<<<<<
$q $t
.
  open(IN, $file);
  while(<IN>) {
    #$query = escape($_);
    $resp = $client->send_request(RPC::XML::request->new('querySummary', $query));
    if(!$resp->is_fault) {
      %struct = %{$resp->value->value};
      $q = $_;
      $t = $struct{"queryTime"};
      write(STDOUT);
    } else {
      print "error occured: $resp->value->string\n";
      return;
    }
  }
}

sub summary {
  my($query) = @_;
  if($RESULT_ID < 0) {
    print "no query. Use find to do a query!\n";
    return;
  }
  $resp = $client->send_request(RPC::XML::request->new('querySummary', $RESULT_ID));
  if(!ref($resp)) {
	print "error occured: $resp\n";
  } else {
	%struct = %{$resp->value->value};
	print "\nQuery result summary\n";
	print "--------------------\n";
	print "Hits: " . $struct{"hits"} . "\n";
	print "Query time: " . $struct{"queryTime"} . "\n\n";
	@docs = @{$struct{"documents"}};
	format STDOUT_TOP =
DocId    Document                          Hits
========================================================
.

	format STDOUT =
@<<<<<<  @<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<  @<<<<<<<<<<<<
$docId $docName $hits
.

	foreach $doc (@docs) {
	  ($docName, $docId, $hits) = @{$doc};
	  write(STDOUT);
	}
  }
}

sub getDocument {
  my($name) = @_;
  if(!($name =~ /\/.*/)) {
    $name = "$COLLECTION/$name";
  }
  $req = RPC::XML::request->new('getDocument', $name, "ISO-8859-1", 
				RPC::XML::int->new($PRETTY), '/db/shakespeare/plays/shakes.xsl');

  $resp =
    $client->send_request($req);

  if(!$resp->is_fault) {
    return decode_base64($resp->value);
  } else {
    print "error occured: ". $resp->string . "\n";
    return '';
  }
}

sub listDocuments {
  $req = RPC::XML::request->new('getCollectionDesc', $COLLECTION);
  $resp = $client->send_request($req);
  if(!$resp->is_fault) {
    %struct = %{$resp->value};
    @docs = @{$struct{"documents"}};
    @colls = @{$struct{"collections"}};
    foreach $coll (sort(@colls)) {
      print "$coll/\n";
    }
    foreach $_ (sort(@docs)) {
      /.*\/([^\/]*)$/;
      print "$1\n";
    }
    return true;
  } else {
    print "\nerror occured: " . $resp->string . "\n";
    return false;
  }
}

sub printNotice {
  print <<END;
eXist version 0.8, Copyright (C) 2001 Wolfgang M. Meier
eXist comes with ABSOLUTELY NO WARRANTY.
This is free software, and you are welcome to
redistribute it under certain conditions;
for details read the license file.

END
}

sub printHelp {
  print <<END;
parse xmlFile           Parse file xmlFile.
get docName             Get the document identified by docName.
find xpathQuery         Execute XPath query and print number of results found. 
                        Use show to display results.
show [start [, howmany] Retrieve howmany results from the last query, beginning
                        at start. With no argument, retrieves the 15 next results.
summary xpathQuery      Same as find but prints a summary of hits
                        by document.
remove docName          Remove document identified by docName from 
                        the database.
list                    List all documents contained in the database.
quit                    exit.

set option=value        Set an option. The following options are available:
  indent=[ on | off ]   Turn pretty printing of results on or off
  collection=x          Set current collection to x.
END
}

sub iso2utf8 {
    my $buffer = shift;
    $buffer =~ s/([\x80-\xFF])/chr(0xC0|ord($1)>>6).chr(0x80|ord($1)&0x3F)/eg;
    return $buffer;
}

__END__

=head1 NAME

xmlrpc.pl Perl command line client for eXist

=head1 SYNOPSIS

C<xmlrpc.pl>

Enter interactive mode.

C<xmlrpc.pl [-i | --indent] xpath>

Execute xpath expression given in C<xpath> and print results.

C<xmlrpc.pl [-t file | --test=file] [--clients=n] [--count=n]>

Enter test mode. In test mode, the skript will fork multiple parallel
clients. Each client will randomly select queries from a list, send it
to the server and retrieve the results. The list of possible queries
is read from the file identified by option C<--test>. The number of parallel
clients is defined by option C<--clients>. Each client will perfom
C<--count> queries before it stops.

Every client writes it's output into a file. The file's name
consists of the client's process-id and ends with C<.out>.

=head1 DESCRIPTION

B<xmlrpc.pl> is a Perl command line client for the eXist native XML
database. Communication with the eXist server is done by XML-RPC calls.

In interactive mode, type B<help> to get a list of available commands.

=head2 Commands in interactive mode

=over4

=item ls

Get a list of all documents contained in the database.

=item cd I<collection>

Change current collection to I<collection>

=item get I<xml document>

Retrieve I<xml document> from the database.

=item find I<xpath query>

Send I<xpath query> to the server and retrieve the list of hits. The number of
hits is displayed. You may then use command B<show> to display results.

=item show [I<start> [, I<howmany>]

Retrieve I<howmany> hits from the current result set, beginning at I<start>, 
and display them. If I<howmany> is omitted, 15 hits will be retrieved. Without
arguments, B<show> will display the next 15 hits, beginning at the current position
in the result set.

=back

=cut
