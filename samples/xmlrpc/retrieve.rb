require "xmlrpc/client"

client = XMLRPC::Client.new("localhost", "/exist/xmlrpc", 8080)
if $*.length < 1 then
  puts "usage: retrieve.rb path-to-document"
  exit(0)
end

doc = $*[0]
puts "Retrieving document #{doc}"
options = { "indent" => "yes", "encoding" => "ISO-8859-1",
    "expand-xincludes" => "yes" }
begin
  result = client.call("getDocument", 
  	doc, options)

  puts result

rescue XMLRPC::FaultException => e
  puts "An error occurred:"
  puts e.faultCode
  puts e.faultString
end
