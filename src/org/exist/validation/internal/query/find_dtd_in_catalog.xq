declare namespace ctlg='urn:oasis:names:tc:entity:xmlns:xml:catalog';
for $dtd in fn:document($catalog)/ctlg:catalog/ctlg:public[@publicId = $publicId]/@uri 
return $dtd