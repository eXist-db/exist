<result>
  {
    for $id in doc("report.xml")//xref/@xrefid
    return doc("report.xml")//topic[@topicid = $id]
  }
</result>
