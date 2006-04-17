declare namespace ctlg='urn:oasis:names:tc:entity:xmlns:xml:catalog';
for $schema in fn:document($catalog)/ctlg:catalog/ctlg:uri[@name = $targetNamespace]/@uri
return $schema