#!/bin/bash

if [ $# -ne 2 ]
then
    echo "Usage: upload.sh filename collection"
    exit 1
fi

DOC=`basename $1`
URL="http://localhost:8080/exist/servlet$2/$DOC"

curl -i --upload-file $1 $URL
