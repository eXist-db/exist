require "xmlrpc/client"

client = XMLRPC::Client.new("localhost", "/exist/xmlrpc", 8080)
if $*.length < 1 then
  puts "usage: retrieve.rb path-to-document"
  exit(0)
end

doc = $*[0]
puts "Retrieving document #{doc}"
begin
  result = client.call("getDocument", 
  	doc, "UTF-8", 1)

  puts result

rescue XMLRPC::FaultException => e
  puts "An error occurred:"
  puts e.faultCode
  puts e.faultString
end
