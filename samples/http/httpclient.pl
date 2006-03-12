require LWP::UserAgent;
use Getopt::Long;
use Pod::Usage;

$COLLECTION = "/db";
$USER = "guest";
$PASS = "guest";
$HELP = 0;
$PUT = 0;
$QUERY = 0;
$BINARY = 0;

GetOptions(
	"u|user=s" => \$USER,
	"p|password=s" => \$PASS,
    "c|collection=s" => \$COLLECTION,
    "g|get=s" => \$GET,
    "q|query" => \$QUERY,
    "s|store" => \$PUT,
    "r|remove=s" => \$REMOVE,
    "x|xupdate" => \$XUPDATE,
    "b|binary" => \$BINARY,
    "h|help" => \$HELP
) or pod2usage(1);

pod2usage(1) if $HELP;

$URL = "http://$USER:$PASS\@localhost:8080/exist/rest";
$ua = LWP::UserAgent->new();
if($QUERY) {
    query();
} elsif($REMOVE) {
    remove($REMOVE)
} elsif($PUT) {
    store();
} elsif($XUPDATE) {
    xupdate();
} elsif($GET) {
    get($GET);
}

sub store {
    foreach $name (@ARGV) {    
        my $data = readFile($name);

        ($doc = $name) =~ s#.*/##s;
        print "Storing document as $doc ...\n";
     
        $req = HTTP::Request->new(PUT => "$URL$COLLECTION/$doc");
        if($BINARY) {
            $req->content_type('application/octet-stream');
        } else {
            $req->content_type('text/xml');
        }
        $req->content($data);

        $res = $ua->request($req);
        if($res->is_success) {
            print $res->content . "\n";
        } else {
            print "Error:\n\n" . $res->status_line . "\n";
        }
    }
}

sub remove {
    my($resource) = @_;

    print "Removing resource $resource ...\n";
    if($resource =~ /\/.*/) {
        $u = "$URL$resource";
    } else {
        $u = "$URL$COLLECTION/$resource";
    }
    print "$u\n";
    my $req = HTTP::Request->new(DELETE => $u);
    my $res = $ua->request($req);
    if($res->is_success) {
        print $res->content . "\n";
    } else {
        print "Error:\n\n" . $res->status_line . "\n";
    }
}

sub query {
    my $query = "";
    while(<STDIN>) {
        $query .= $_;
    }
    my $xq = <<END;
<?xml version="1.0" encoding="UTF-8"?>
<query xmlns="http://exist.sourceforge.net/NS/exist"
    start="1" max="20">
    <text><![CDATA[
        $query
    ]]></text>
    <properties>
        <property name="indent" value="yes"/>
    </properties>
</query>
END
    print "Executing query: $query\n";
    my $req = HTTP::Request->new(POST => $URL);
    $req->content_type('text/xml');
    $req->content($xq);

    my $res = $ua->request($req);
    if($res->is_success) {
        print $res->content . "\n";
    } else {
        print "Error:\n\n" . $res->status_line . "\n";
    }
}

sub xupdate {
    my $xupdate = "";
    while(<STDIN>) {
        $xupdate .= $_;
    }
    print "Executing XUpdate request: $xupdate\n";
    my $req = HTTP::Request->new(POST => $URL);
    $req->content_type('text/xml');
    $req->content($xupdate);

    my $res = $ua->request($req);
    if($res->is_success) {
        print $res->content . "\n";
    } else {
        print "Error:\n\n" . $res->status_line . "\n";
    }
}

sub get {
    my($resource) = @_;
    my $u = $resource =~ /\/.*/ ? "$URL$resource" :
        "$URL$COLLECTION/$resource";
    my $req = HTTP::Request->new(GET => $u);
    my $res = $ua->request($req);
    if($res->is_success) {
        print $res->content . "\n";
    } else {
        print "Error:\n\n" . $res->status_line . "\n";
    }
}

sub readFile {
    my($file) = @_;
    open(XIN, $file);
    binmode XIN;
    my $pos = 0;
    while(($l = sysread(XIN, $xml, 4096, $pos)) > 0) {
        $pos = $pos + $l;
    }
    close(XIN);
    return $xml;
}

__END__

=head1 NAME

A simple Perl client which uses the REST interface to communicate
with an eXist database.

=head1 SYNOPSIS

httpclient [options] [file]

=head1 OPTIONS

=over 8

=item B<-h|--help>

Print this help and exit.

=item B<-c|--collection>

The base collection to use.

=item B<-u|--user>

Connect to the database with specified user.

=item B<-p|--password>

Password for the connection.

=item B<-q|--query>

Execute an XQuery: the query is read from standard input.

=item B<-x|--xupdate>

Process XUpdate modifications: the XUpdate document is read
from standard input.

=item B<-s|--store>

Store an XML document into the database.

=item B<-b|--binary>

Store a file as binary resource.

=item B<-r|--remove>

Remove a resource (document or collection).

=back

=cut
