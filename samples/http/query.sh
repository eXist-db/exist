#!/bin/bash

QUERY=`cat $1`

REQUEST=`cat <<ENDE
<?xml version="1.0" encoding="UTF-8"?> \
<query xmlns="http://exist.sourceforge.net/NS/exist" \
    start="1" max="20"> \
    <text><![CDATA[$QUERY]]></text>
</query>
ENDE
`

echo $REQUEST | curl -i --data @- http://localhost:8080/exist/rest/db
