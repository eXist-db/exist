#!/bin/bash
#
# This shell script uses the REST-style HTTP interface to talk
# to the database. It is based on curl, a standard utility 
# which should be available on most Linux systems.
#
# Author: Wolfgang Meier
#
# $Id$

url=http://localhost:8080/exist/rest

usage() {
    cat << END

Usage: $0 parameter-list [file-list]

Parameters:
    --collection|-c collection  The collection to use. If --collection
                                is specified as the only argument, shows
                                the contents of the collection.

    --store|-s                  Interprets all following parameters as
                                file names and uploads them to the
                                collection.

    --query|-q xquery-file      Load XQuery from file and execute it. 

	--start						The first item to retrieve from the
								query result.
	
	--max						The maximum number of items to retrieve
								from the query result set.

    --get|-g document           Retrieve document from collection.

    --remove|-r document or collection
                                Remove the specified document or child-
                                collection from the collection.

    --xupdate|-x xupdate-file   Process the XUpdate request read from the
                                specified file.

    --indent|-i                 Enable pretty-printing of returned XML.

    --noxsl                     Disable processing of XSL stylesheet
                                processing instructions.

    --binary|-b                 In combinations with --store, stores the
                                file[s] as binary resource.
END
}

action=none
collection=/db
indent=no
contentType=text/xml
binmode=no
offset=1
max=20

while [ -n "$1" ]
do
    case $1 in
        --help|-h)
            usage
            exit 0
            ;;
        --query|-q)
            action=query
            if [ -z $2 ]
            then
                echo "--query requires an argument"
                exit 1
            fi
            queryFile=$2
            shift
            ;;
        --store|-s)
            action=store
            shift
            break
            ;;
        --collection|-c)
            if [ -z $2 ]
            then
                echo "--collection requires an argument"
                exit 1
            fi
            collection=$2
            shift
            ;;
        --xupdate|-x)
            if [ -z $2 ]
            then
                echo "--xupdate requires an argument"
                exit 1
            fi
            action=xupdate
            xupdate=$2
            shift
            ;;
        --get|-g)
            if [ -z $2 ]
            then
                echo "--get requires an argument"
                exit 1
            fi
            action=get
            doc=$2
            shift
            ;;
        --remove|-r)
            action=delete
            if [ -z $2 ]
            then
                echo "--remove requires an argument"
                exit 1
            fi
            delete=$2
            shift
            ;;
        --indent|-i)
            indent=yes
            ;;
        --noxsl)
            xsl=no
            ;;
        --binary|-b)
            binmode=yes
            ;;
		--start)
			if [ -z $2 ]
            then
                echo "--start requires an argument"
                exit 1
            fi
            offset=$2
            shift
            ;;
		--max)
			if [ -z $2 ]
			then
				echo "--max requires an argument"
				exit 1
			fi
			max=$2
			shift
			;;
        *)
            echo "Unknown parameter: $1"
            exit 1
            ;;
    esac
    shift
done

case $action in
    get)
        u="$url$collection/$doc?_indent=$indent"
        if [ -n $xsl ]
        then
            u="$u&_xsl=$xsl"
        fi
        curl -i $u
        ;;
    delete)
        curl -i -X delete "$url$collection/$delete"
        ;;
    store)
        while [ -n "$1" ]
        do
            file=$1
            doc=`basename $file`
            options="-i --upload-file $file"
            if [ $binmode = 'yes' ]
            then
                contentType='application/octet-stream'
            fi
            options="$options -H Content-Type:$contentType"
            curl $options "$url$collection/$doc"
            shift
        done
        ;;
    query)
        if [ $queryFile = '-' ]
        then
            query=`cat <&0`
        else
            if [ ! -f $queryFile ]
            then
                echo "Unable to read query file $queryFile"
                exit 1
            fi
            query=`cat $queryFile`
        fi
        request=`cat <<END
<?xml version="1.0" encoding="UTF-8"?> \
<query xmlns="http://exist.sourceforge.net/NS/exist" \
    start="$offset" max="$max"> \
    <text><![CDATA[$query]]></text>
</query>
END
`
        echo $request | curl -H 'content-type:text/xml' -i --data-binary @- "$url$collection"
        ;;
    xupdate)
        if [ ! -f $xupdate ]
        then
            echo "Cannot read XUpdate file: $xupdate"
            exit 1
        fi
        curl -i --data "@$xupdate" "$url$collection"
        ;;
    *)
        curl -i "$url$collection"
        ;;
esac
echo
