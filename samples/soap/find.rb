require "xmlrpc/client"

# Execute an XQuery through XML-RPC. Uses a single call to
# "query" to process the query and retrieve a specified number
# of items from the generated result set.
#
query = <<END
for \$speech in //SPEECH[LINE &= 'tear*']
order by \$speech/SPEAKER[1]
return
    \$speech
END

client = XMLRPC::Client.new("localhost", "/exist/xmlrpc", 8080)

puts "Query: #{query}"

outputOptions = { "encoding" => "UTF-8", "indent" => "yes" }

begin
  result = client.call("query", query, 20, 1, outputOptions)
  puts "#{result}\n"

rescue XMLRPC::FaultException => e
  puts "An error occurred:"
  puts e.faultCode
  puts e.faultString
end
