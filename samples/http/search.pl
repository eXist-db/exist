require LWP::UserAgent;

$URL = 'http://localhost:8080/exist/rest/db/';
$QUERY = <<END;
<?xml version="1.0" encoding="UTF-8"?>
<query xmlns="http://exist.sourceforge.net/NS/exist"
    start="1" max="20">
    <text><![CDATA[
        for \$speech in //SPEECH[LINE &= 'corrupt*']
        order by \$speech/SPEAKER[1]
        return
            <hit>{\$speech}</hit>
    ]]></text>
    <properties>
        <property name="indent" value="yes"/>
    </properties>
</query>
END

$ua = LWP::UserAgent->new();
$req = HTTP::Request->new(POST => $URL);
$req->content_type('text/xml');
$req->content($QUERY);

$res = $ua->request($req);
if($res->is_success) {
    print $res->content . "\n";
} else {
    print "Error:\n\n" . $res->status_line . "\n";
}
