require "xmlrpc/client"

client = XMLRPC::Client.new("localhost", "/exist/xmlrpc", 8080)
if $*.length < 1 then
  puts "usage: find.rb xpath-query"
  exit(0)
end

query = $*[0]
puts "Query: #{query}"

outputOptions = { "encoding" => "UTF-8", "indent" => "yes" }

begin
  handle = client.call("executeQuery", query)
  hits = client.call("getHits", handle)
  puts "Found #{hits} hits"

  for i in 1..10
	result = client.call("retrieve", handle, i, outputOptions)
	print "#{result}\n"
  end
rescue XMLRPC::FaultException => e
  puts "An error occurred:"
  puts e.faultCode
  puts e.faultString
end
