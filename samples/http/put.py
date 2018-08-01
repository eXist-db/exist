#!/usr/bin/python
from __future__ import print_function

import httplib
import sys
from string import rfind

collection = sys.argv[1]
file = sys.argv[2]

with open(file, 'r') as f:
    print("reading file {} ...".format(file))
    xml = f.read()
    f.close()

    p = rfind(file, '/')
    if p > -1:
        doc = file[p+1:]
    else:
        doc = file
        
    print(doc)
    print("storing document to collection {} ...".format(collection))
    
    con = httplib.HTTP('localhost:8080')
    con.putrequest('PUT', '/exist/rest/{col}/{doc}'.format(col=collection, doc=doc))
    con.putheader('Content-Type', 'application/xml')
    
    clen = len(xml)
    
    con.putheader('Content-Length', 'clen')
    con.endheaders()
    con.send(xml)

    errcode, errmsg, headers = con.getreply()

    if errcode != 200:
        f = con.getfile()
        print('An error occurred: {}'.format(errmsg))
    else:
        print("Ok.")
