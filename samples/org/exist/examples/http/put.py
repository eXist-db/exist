#!/usr/bin/python

import httplib
import sys

file = sys.argv[1]
f = open(file, 'r')
print "reading file %s ..." % file
xml = f.read()
f.close()

con = httplib.HTTP('localhost:8088')
con.putrequest('PUT', '/db/test/%s' % file)
con.putheader('Accept', 'text/xml')
clen = len(xml)
con.putheader('Content-Length', `clen`)
con.endheaders()
con.send(xml)

errcode, errmsg, headers = con.getreply()

if errcode != 200:
    f = con.getfile()
    print 'An error occurred: %s' % f.read()
    f.close()
else:
    print "Ok."
