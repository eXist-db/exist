require "xmlrpc/client"

# Execute an XQuery through XML-RPC. Uses a single call to
# "query" to process the query and retrieve a specified number
# of items from the generated result set.
#
query = <<END
for \$speech in //SPEECH[LINE &= \$query]
order by \$speech/SPEAKER[1]
return
    \$speech
END

client = XMLRPC::Client.new("localhost", "/exist/xmlrpc", 8080)

puts "Query: #{query}"

vars = { "query" => "love*" }
outputOptions = { "encoding" => "UTF-8", "indent" => "yes",
	"variables" => vars
}

begin
  result = client.call("query", XMLRPC::Base64.new(query), 20, 1, outputOptions)
  puts "#{result}\n"

rescue XMLRPC::FaultException => e
  puts "An error occurred:"
  puts e.faultCode
  puts e.faultString
end
