#!/bin/sh
# Test redirecting from CGI
# $Id: redirect.sh,v 1.1 2001/07/14 20:04:47 bretts Exp $
echo "Status: 302 Moved"
echo "Location: http://${HTTP_HOST}/"
echo
