require "xmlrpc/client"

# Execute an XQuery through XML-RPC. Uses a single call to
# "query" to process the query and retrieve a specified number
# of items from the generated result set.
#
query = <<END
for \$speech in //SPEECH[ft:query(., \$query)]
order by \$speech/SPEAKER[1]
return
    <hit>
	{\$speech}
	</hit>
END

client = XMLRPC::Client.new("localhost", "/exist/xmlrpc", 8080)

puts "Query: #{query}"

vars = { "query" => "love*" }
parameters = { "variables" => vars }
outputOptions = { "encoding" => "UTF-8", "indent" => "yes",
	"variables" => vars
}

begin
  handle = client.call("executeQuery", XMLRPC::Base64.new(query), parameters)
  summary = client.call("summary", handle)
  hits = summary['hits']
  puts "Found #{hits} hits\n"

  summary = client.call("summary", "//SPEECH[ft:query(., 'love')]")
  hits = summary['hits']
  puts "Found #{hits} hits\n"
rescue XMLRPC::FaultException => e
  puts "An error occurred:"
  puts e.faultCode
  puts e.faultString
end
