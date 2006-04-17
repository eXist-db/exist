for $schema in collection($collection)//xs:schema[@targetNamespace = $targetNamespace ] 
return document-uri($schema)