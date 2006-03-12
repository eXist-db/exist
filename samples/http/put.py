#!/usr/bin/python

import httplib
import sys
from string import rfind

collection = sys.argv[1]
file = sys.argv[2]

f = open(file, 'r')
print "reading file %s ..." % file
xml = f.read()
f.close()

p = rfind(file, '/')
if p > -1:
    doc = file[p+1:]
else:
    doc = file
print doc
print "storing document to collection %s ..." % collection
con = httplib.HTTP('localhost:8080')
con.putrequest('PUT', '/exist/rest/%s/%s' % (collection, doc))
con.putheader('Content-Type', 'text/xml')
clen = len(xml)
con.putheader('Content-Length', `clen`)
con.endheaders()
con.send(xml)

errcode, errmsg, headers = con.getreply()

if errcode != 200:
    f = con.getfile()
    print 'An error occurred: %s' % errmsg
    f.close()
else:
    print "Ok."
