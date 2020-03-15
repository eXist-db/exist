#!/usr/bin/python
#
# eXist-db Open Source Native XML Database
# Copyright (C) 2001 The eXist-db Authors
#
# info@exist-db.org
# http://www.exist-db.org
#
# This library is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 2.1 of the License, or (at your option) any later version.
#
# This library is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public
# License along with this library; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
#

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
