(:
    Search for XML schemas in database.

    Parameters:
    - $collection      top level collection to start searching
    - $targetNamespace target namespace of schema 

    Returns:
    Sequence of document-uris.
    
    $Id$
:)
for $schema in collection($collection)//xs:schema[@targetNamespace = $targetNamespace ]/root() 
return document-uri($schema)