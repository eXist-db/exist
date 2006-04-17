declare namespace ctlg='urn:oasis:names:tc:entity:xmlns:xml:catalog';
for $dtd in collection($collection)/ctlg:catalog/ctlg:public[@publicId = $publicid]/@uri
return document-uri($dtd)