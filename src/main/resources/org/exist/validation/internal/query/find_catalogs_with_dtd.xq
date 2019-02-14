(:
    Search for DTD in catalogs of database.

    Parameters:
    - $collection      top level collection to start searching
    - $PublicId        public identifier of DTD 

    Returns:
    Sequence of document-uris.

    $Id$    
:)
declare namespace ctlg='urn:oasis:names:tc:entity:xmlns:xml:catalog';
for $uri in collection($collection)//ctlg:catalog/ctlg:public[@publicId = $publicId]/@uri/root()
return document-uri($uri)