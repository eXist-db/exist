#!/bin/bash

if [ $# -le 1 ]
then
    echo "Usage: upload.sh filename collection [curl arguments]"
    echo "Example: upload.sh test.xml /db/my_collec --user admin:admin"
    exit 1
fi

DOC=`basename $1`
URL="http://localhost:8080/exist/rest$2/$DOC"

curl -i --upload-file $1 $URL $3 $4 $5 $6 $7 $8 $9
