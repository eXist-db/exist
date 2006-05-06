(: not used yet :)
declare namespace catalogns='urn:oasis:names:tc:entity:xmlns:xml:catalog';
for $catalog in collection('$collection')/catalogns:catalog
return document-uri($catalog)