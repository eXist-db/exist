declare namespace f="my-functions.uri";
declare function f:one_level ($p as element()) as element()
{
    <part partid="{$p/@partid}" name="{$p/@name}">
        {
            for $s in doc("parts.xml")//part
            where $s/@partof = $p/@partid
            return f:one_level($s)
        }
    </part>
};

<parttree>
  {
    for $p in doc("parts.xml")//part[empty(@partof)]
    return f:one_level($p)
  }
</parttree>

