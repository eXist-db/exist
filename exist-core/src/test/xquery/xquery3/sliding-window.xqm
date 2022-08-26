(:
 : eXist-db Open Source Native XML Database
 : Copyright (C) 2001 The eXist-db Authors
 :
 : info@exist-db.org
 : http://www.exist-db.org
 :
 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; either
 : version 2.1 of the License, or (at your option) any later version.
 :
 : This library is distributed in the hope that it will be useful,
 : but WITHOUT ANY WARRANTY; without even the implied warranty of
 : MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 : Lesser General Public License for more details.
 :
 : You should have received a copy of the GNU Lesser General Public
 : License along with this library; if not, write to the Free Software
 : Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 :)
xquery version "3.0";

module namespace swt = "http://exist-db.org/xquery/test/sliding-window";

declare namespace test = "http://exist-db.org/xquery/xqsuite";


declare
    %test:assertEquals("<window>2 4 6</window>", "<window>4 6 8</window>", "<window>6 8 10</window>", "<window>8 10 12</window>", "<window>10 12 14</window>")
function swt:windows-of-three-items() {
  for sliding window $w in (2, 4, 6, 8, 10, 12, 14)
  start at $s when fn:true()
  only end at $e when $e - $s eq 2
  return
    <window>{ $w }</window>
};

declare
    %test:assertEquals(4, 6, 8, 10, 12)
function swt:moving-averages-of-three-items() {
  for sliding window $w in (2, 4, 6, 8, 10, 12, 14)
  start at $s when fn:true()
  only end at $e when $e - $s eq 2
  return
    avg($w)
};

declare
    %test:assertEquals("<window>2 4 6</window>", "<window>4 6 8</window>", "<window>6 8 10</window>", "<window>8 10 12</window>", "<window>10 12 14</window>", "<window>12 14</window>", "<window>14</window>")
function swt:overlapping-windows-of-three-items() {
  for sliding window $w in (2, 4, 6, 8, 10, 12, 14)
  start at $s when fn:true()
  end at $e when $e - $s eq 2
  return
    <window>{ $w }</window>
};

declare
  %test:assertEquals(
      '<window><OrderRequest billTo="ACME2" date="2006-01-01T11:00:00-00:00" orderID="OID02" total="100" type="new"><Item partID="ID2" quantity="10" unitPrice="10"/></OrderRequest><ConfirmationRequest confirmID="C1" date="2006-01-01T18:00:00-00:00" orderID="OID02" status="reject"/><time date="2006-01-02T00:00:00-00:00"/></window>',
      '<window><ConfirmationRequest confirmID="C1" date="2006-01-01T18:00:00-00:00" orderID="OID02" status="reject"/><time date="2006-01-02T00:00:00-00:00"/><ConfirmationRequest confirmID="C1" date="2006-01-02T08:00:00-00:00" orderID="OID01" status="accept"/><OrderRequest billTo="ACME1" date="2006-01-02T14:00:00-00:00" orderID="OID03" shipTo="ACME1" total="10000" type="new"><Item partID="ID3" quantity="100" unitPrice="100"/></OrderRequest><ConfirmationRequest confirmID="C1" date="2006-01-02T16:00:00-00:00" orderID="OID03" status="accept"/><time date="2006-01-03T00:00:00-00:00"/><time date="2006-01-04T00:00:00-00:00"/><time date="2006-01-05T00:00:00-00:00"/><ShipNotice date="2006-01-05T08:00:00-00:00" orderID="OID01"/><ShipNotice date="2006-01-05T09:00:00-00:00" orderID="OID03"/><time date="2006-01-06T00:00:00-00:00"/><OrderRequest billTo="ACME2" date="2006-01-06T08:00:00-00:00" orderID="OID04" total="100" type="new"><Item partID="ID2" quantity="10" unitPrice="10"/></OrderRequest><time date="2006-01-07T00:00:00-00:00"/></window>',
      '<window><ConfirmationRequest confirmID="C1" date="2006-01-02T16:00:00-00:00" orderID="OID03" status="accept"/><time date="2006-01-03T00:00:00-00:00"/><time date="2006-01-04T00:00:00-00:00"/><time date="2006-01-05T00:00:00-00:00"/><ShipNotice date="2006-01-05T08:00:00-00:00" orderID="OID01"/></window>',
      '<window><time date="2006-01-07T00:00:00-00:00"/></window>'
  )
function swt:overlapping-windows-start-prev-end-next-conditions() {
  let $data := document {
      <sequence>
        <time date="2006-01-01T00:00:00-00:00"/>
        <OrderRequest billTo="ACME1" date="2006-01-01T10:00:00-00:00"
          orderID="OID01" shipTo="ACME1" total="1100" type="new">
          <Item partID="ID1" quantity="10" unitPrice="100"/>
          <Item partID="ID2" quantity="10" unitPrice="10"/>
        </OrderRequest>
        <OrderRequest billTo="ACME2" date="2006-01-01T11:00:00-00:00"
          orderID="OID02" total="100" type="new">
          <Item partID="ID2" quantity="10" unitPrice="10"/>
        </OrderRequest>
        <ConfirmationRequest confirmID="C1" date="2006-01-01T18:00:00-00:00"
          orderID="OID02" status="reject"/>
        <time date="2006-01-02T00:00:00-00:00"/>
        <ConfirmationRequest confirmID="C1" date="2006-01-02T08:00:00-00:00"
          orderID="OID01" status="accept"/>
        <OrderRequest billTo="ACME1" date="2006-01-02T14:00:00-00:00"
          orderID="OID03" shipTo="ACME1" total="10000" type="new">
          <Item partID="ID3" quantity="100" unitPrice="100"/>
        </OrderRequest>
        <ConfirmationRequest confirmID="C1" date="2006-01-02T16:00:00-00:00"
          orderID="OID03" status="accept"/>
        <time date="2006-01-03T00:00:00-00:00"/>
        <time date="2006-01-04T00:00:00-00:00"/>
        <time date="2006-01-05T00:00:00-00:00"/>
        <ShipNotice date="2006-01-05T08:00:00-00:00" orderID="OID01"/>
        <ShipNotice date="2006-01-05T09:00:00-00:00" orderID="OID03"/>
        <time date="2006-01-06T00:00:00-00:00"/>
        <OrderRequest billTo="ACME2" date="2006-01-06T08:00:00-00:00"
          orderID="OID04" total="100" type="new">
          <Item partID="ID2" quantity="10" unitPrice="10"/>
        </OrderRequest>
        <time date="2006-01-07T00:00:00-00:00"/>
      </sequence>
  }
  return
    for sliding window $w in $data/sequence/*
        start previous $wSPrev when $wSPrev[self::OrderRequest]
        end next $wENext when $wENext/@orderID eq $wSPrev/@orderID
    return
      <window>{$w}</window>
};

declare
  %test:assertEquals(
      '<window s="1" x="1" sp="" sn="2" e="10" y="10" ep="9" en="">1 2 3 4 5 6 7 8 9 10</window>',
      '<window s="2" x="2" sp="1" sn="3" e="10" y="10" ep="9" en="">2 3 4 5 6 7 8 9 10</window>',
      '<window s="3" x="3" sp="2" sn="4" e="10" y="10" ep="9" en="">3 4 5 6 7 8 9 10</window>',
      '<window s="4" x="4" sp="3" sn="5" e="10" y="10" ep="9" en="">4 5 6 7 8 9 10</window>',
      '<window s="5" x="5" sp="4" sn="6" e="10" y="10" ep="9" en="">5 6 7 8 9 10</window>',
      '<window s="6" x="6" sp="5" sn="7" e="10" y="10" ep="9" en="">6 7 8 9 10</window>',
      '<window s="7" x="7" sp="6" sn="8" e="10" y="10" ep="9" en="">7 8 9 10</window>',
      '<window s="8" x="8" sp="7" sn="9" e="10" y="10" ep="9" en="">8 9 10</window>',
      '<window s="9" x="9" sp="8" sn="10" e="10" y="10" ep="9" en="">9 10</window>',
      '<window s="10" x="10" sp="9" sn="" e="10" y="10" ep="9" en="">10</window>'
  )
function swt:all-vars() {
  for sliding window $w in (1 to 10)
      start $s at $x previous $sp next $sn when fn:true()
      end $e at $y previous $ep next $en when fn:false()
  return
    <window s="{$s}" x="{$x}" sp="{$sp}" sn="{$sn}" e="{$e}" y="{$y}" ep="{$ep}" en="{$en}">{ $w }</window>
};