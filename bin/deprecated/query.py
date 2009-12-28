#!/usr/bin/python

# eXist xml document repository and xpath implementation
# Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
#
# This library is free software; you can redistribute it and/or
# modify it under the terms of the GNU Library General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This library is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Library General Public License for more details.
#
# You should have received a copy of the GNU Library General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
#
 
import httplib, getopt, sys, readline
from string import split, replace, atoi, rfind
import re, time

class eXistClient:

	host = '127.0.0.1:8088'
	requestFile = ''
	xslStyle = ''
	display = 1
	start = 1
	howmany = 15
	outfile = ''
	indent = 'true'
	
	def __init__(self, args):
		optlist, args  = getopt.getopt(args[1:], 'hqis:b:p:')
		quiet = 0
		for i in optlist:
			if i[0] == '-h':
				self.printUsage()
				sys.exit(0)
			elif i[0] == '-s':
				self.host = i[1]
			elif i[0] == '-b':
				self.benchmark(i[1])
				return
			elif i[0] == '-q':
				self.quiet = 1
			elif i[0] == '-i':
				self.indent = 'false'
			elif i[0] == '-p':
				self.parse(i[1], args)
				return
			
		if not quiet:
			self.printBanner()
			
		if len(args) < 1:
			self.interactive()
			return
		else:
			try:
				freq = open(args[0], 'r')
			except IOError:
				print 'Unable to open file ', args[0]
				sys.exit(0)
			else:
				req = freq.read()
				freq.close()
		
		self.doQuery(req)

	def interactive(self):
		print '\npress h or ? for help on available commands.'
		while 1:
			s = raw_input('exist> ')
			line = split(s, ' ', 1)
			if line[0] in ('find', 'f'):
				r = self.queryRequest(line[1], 1, self.howmany)
				print r
				resp = self.doQuery(r)
				if self.outfile:
					o = open(self.outfile, 'w')
					o.write(resp)
					print 'output written to ', self.outfile
				else:
					print '\nserver responded:\n'
					print resp
			elif line[0] in ('get', 'g'):
				args = split(line[1])
				if len(args) > 1:
					self.getRequest(args[0], args[1])
				else:
					self.getRequest(args[0])
			elif line[0] in ('url', 'u'):
				self.host = line[1]
				print 'set host address = %s' % self.host
			elif line[0] in ('display', 'd'):
				self.setDisplay(split(line[1]))
			elif line[0] in ('output', 'o'):
				self.outfile = line[1]
				print 'set output file = %s' % self.outfile
			elif line[0] in ('bench', 'b'):
				self.benchmark(line[1])
			elif line[0] in ('remove', 'r'):
				self.remove(line[1])
			elif line[0] in ('parse', 'p'):
				args = split(line[1], ' ', 1)
				if len(args) > 1:
					self.parse(args[0], [ args[1] ])
				else:
					self.parse(args[0], [])
			elif line[0] in ('help', '?', 'h'):
				self.getHelp()
			elif line[0] in ('quit', 'q'):
				break
			else:
				print 'unknown command: ' + `line[0]`

	def setDisplay(self, line):
		self.display = 1
		for i in line:
			self.setArg(i)

	def setArg(self, arg):
		if arg in ('summary', 's'):
			self.display = 0
			print 'summarize = %i' % self.display
		elif arg in ('all', 'a'):
			self.display = 1
			print 'summarize = %i' % self.display
		else:
			self.howmany = atoi(arg)
			print 'howmany = %s' % self.howmany

	def getRequest(self, document, gid = ""):
		if gid != "":
			gid = 'id="%s"'
		temp = """
		<exist:request xmlns:exist="http://exist.sourceforge.net/NS/exist">
		    <exist:display indent="%s"/>
		<exist:get document="%s" %s/>
        </exist:request>
		"""
		req = temp % (self.indent, document, gid)
		print req
		resp = self.doQuery(req)
		if self.outfile:
			o = open(self.outfile, 'w')
			o.write(resp)
			print 'output written to ', self.outfile
		else:
			print '\nserver responded:\n'
			print resp

	def queryRequest(self, query, start, howmany):
		temp = """
		<exist:request xmlns:exist="http://exist.sourceforge.net/NS/exist">
		<exist:query>%s</exist:query>
		<exist:%s indent="%s" howmany="%i" start="%i"/>
        </exist:request>
		"""
		if self.display:
			disp = "display"
		else:
			disp = "summarize"
		return temp % ( self.escape(query), disp, self.indent, howmany, start)

	def remove(self, doc):
		temp = """
		<exist:request xmlns:exist="http://exist.sourceforge.net/NS/exist">
		    <exist:remove document="%s"/>
	    </exist:request>
		"""
		req = temp % ( doc )
		print req
		resp = self.doQuery(req)
		print resp
	
	
	def escape(self, str):
		n = ''
		for c in str:
			if c == '&':
				n = n + '&amp;'
			elif c == '<':
				n = n + '&lt;'
			elif c == '>':
				n = n + '&gt;'
			else:
				n = n + c
		return n

	def parse(self, file, args):
		p = rfind(file, '/')
		if p > -1:
			doc = file[p+1:]
		else:
			doc = file
		if(len(args) > 0):
			doc = args[0] + "/" + doc
		f = open(file, 'r')
		print "reading file %s ..." % file
		xml = f.read()
		f.close()
		print "ok.\nsending %s to server ..." % doc
		con = httplib.HTTP(self.host)
		con.putrequest('PUT', doc)
		con.putheader('Accept', 'text/xml')
		clen = len(xml)
		con.putheader('Content-Length', `clen`)
		con.endheaders()
		con.send(xml)

		errcode, errmsg, headers = con.getreply()
		
		if errcode != 200:
			print 'an error occurred: %s' % errmsg
		else:
			print "ok."
	
	def doQuery(self, request):
		con = httplib.HTTP(self.host)
		con.putrequest('POST', '/')
		con.putheader('Accept', 'text/xml')
		clen = len(request)
		con.putheader('Content-Length', `clen`)
		con.endheaders()
		print 'Sending request ...\n'
		con.send(request)
		
		errcode, errmsg, headers = con.getreply()
		
		if errcode != 200:
			print 'an error occurred: %s' % errmsg
			return
		f = con.getfile()
		data = f.read()
		f.close()
		return data

	def benchmark(self, benchfile):
		bench = open(benchfile, 'r')
		o = open('benchmark.out', 'w')
		queries = bench.readlines()
		print '%-10s | %-10s    | %-50s' % ("query", "retrieve", "query string")
		print '=' * 75
                qt = 0.0
                rt = 0.0
                i = 1
		for qu in queries:
			start = time.clock()
			req = self.queryRequest(qu, 1, 20)
			data = self.doQuery(req)
			queryTime = re.search('queryTime="([0-9]+)"', data).group(1)
			#retrTime = re.search('retrieveTime="([0-9]+)"', data).group(1)
			retrTime = 0
			print '%-10s | %-10s ==> %-47s' % (queryTime, retrTime, qu[0:50])
                        i = i + 1
		bench.close()
		
	def getHelp(self):
		print """
Available commands:
  h|help           print this help message

  g|get docName    retrieve document docName from the database

  r|remove docName remove document docName from the database

  p|parse file [collection]
                   parse and store file to the repository
  
  f|find expr      create query request with expr as query argument
  
  d|display [ [a|all] | [s|summary] ] [howmany]
                   all :    return the actual content of matching nodes
                   summary: just return a short summary of hits per document
                   howmany: howmany nodes should be returned at maximum
			  
  o|output file    write server response to file

  u|url host:port  set server address to host:port

  b|bench file     execute queries contained in file and print statistics
        """

	def printUsage(self):
		print """
Usage: query.py [-h] [-s server] [-b benchmark-file] request-file
  -h   Display this message
  -s   Server address (e.g. localhost:8088)
  -b   Benchmark: Execute queries from benchmark-file and print statistics
  -i   Switch off indentation of results
        """

	def printBanner(self):
		print """
eXist version 0.5, Copyright (C) 2001 Wolfgang M. Meier
eXist comes with ABSOLUTELY NO WARRANTY.
This is free software, and you are welcome to
redistribute it under certain conditions;
for details read the license file.
        """

c = eXistClient(sys.argv)
