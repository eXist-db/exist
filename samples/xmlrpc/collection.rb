require "xmlrpc/client"

class Collection

    attr_reader :name
    attr_reader :owner
    attr_reader :group
    attr_reader :permissions
    
    def initialize(client, collectionName)
        @client = client
        load(collectionName)
    end

    def to_s()
        return "#{@permissions} #{@owner} #{@group} #{@name}"
    end

    def documents
        @documents.each { |d| yield d }
    end

    def [](key)
        @documents.each { |d|
            return d if key == d.name 
        }
        return nil
    end

protected

    def load(collectionName)
        begin
            resp = @client.call("getCollectionDesc", collectionName)
            @name = resp['name']
            @owner = resp['owner']
            @group = resp['group']
            @permissions = resp['permissions']
            @childCollection = resp['collections']
            
            @documents = Array.new
            docs = resp['documents']
            docs.each { |d| @documents.push(Document.new(@client, d)) }
        rescue XMLRPC::FaultException => e
            error(e)
        end
    end

end

class Document

    attr_reader :path
    attr_reader :name
    attr_reader :owner
    attr_reader :group
    attr_reader :permissions

    def initialize(client, hash)
        @client = client
        @path = hash['name']
        @name = @path[/[^\/]+$/]
        @owner = hash['owner']
        @group = hash['group']
        @permissions = hash['permissions']
    end

    def to_s
        return "#{@permissions} #{@owner} #{@group} #{@name}"
    end

    def content
        begin 
            options = { "indent" => "yes", "encoding" => "ISO-8859-1",
                "expand-xincludes" => "yes" }
            return @client.call("getDocument", @path, options)
        rescue XMLRPC::FaultException => e
            error(e)
        end
    end
end

client = XMLRPC::Client.new("localhost", "/exist/xmlrpc", 8080)

if $*.length < 1 then
  puts "usage: collections.rb collection-path"
  exit(0)
end

path = $*[0]
collection = Collection.new(client, path)
puts collection.to_s
collection.documents { |d| puts d.to_s }

doc = collection['hamlet.xml']
if doc == nil 
    error("document not found")
end
puts doc.content
