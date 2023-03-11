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
xquery version "3.1";

module namespace ist = "http://exist-db.org/xquery/image/scale/test";


import module namespace image = "http://exist-db.org/xquery/image";
import module namespace util = "http://exist-db.org/xquery/util";
import module namespace xmldb = "http://exist-db.org/xquery/xmldb";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare
    %test:setUp
function ist:setup() {
  let $_ := xmldb:create-collection("/db", "image-scale-test")
  let $img := file:read-binary("src/test/resources/h+p.jpeg")
  return
    xmldb:store-as-binary("/db/image-scale-test", "h+p.jpeg", $img)
};

declare
    %test:tearDown
function ist:tear-down() {
  xmldb:remove("/db/image-scale-test")
};

declare
    %test:assertEquals("/db/image-scale-test/h+p_scale_legacy_200.jpeg")
function ist:scale-down-legacy() {
  let $source-img := util:binary-doc("/db/image-scale-test/h+p.jpeg")
  let $scaled-img := image:scale($source-img, (200, 200), "image/jpeg")
  return
    xmldb:store-as-binary("/db/image-scale-test", "h+p_scale_legacy_200.jpeg", $scaled-img)
};

declare
    %test:assertEquals("/db/image-scale-test/h+p_scale_legacy_1000.jpeg")
function ist:scale-up-legacy() {
  let $source-img := util:binary-doc("/db/image-scale-test/h+p.jpeg")
  let $scaled-img := image:scale($source-img, (1000, 1000), "image/jpeg")
  return
    xmldb:store-as-binary("/db/image-scale-test", "h+p_scale_legacy_1000.jpeg", $scaled-img)
};

declare
    %test:assertEquals("/db/image-scale-test/h+p_scale_200_bicubic.jpeg")
function ist:scale-down-bicubic() {
  let $source-img := util:binary-doc("/db/image-scale-test/h+p.jpeg")
  let $scaled-img := image:scale(
        $source-img,
        map {
            "source": map {
                "media-type": "image/jpeg"
            },
            "destination": map {
                "max-width": 200,
                "max-height": 200,
                "rendering-hints": map {
                    $image:interpolation: $image:interpolation_bicubic
                }
            }
        }
  )
  return
    xmldb:store-as-binary("/db/image-scale-test", "h+p_scale_200_bicubic.jpeg", $scaled-img)
};

declare
    %test:assertEquals("/db/image-scale-test/h+p_scale_1000_bicubic.jpeg")
function ist:scale-up-bicubic() {
  let $source-img := util:binary-doc("/db/image-scale-test/h+p.jpeg")
  let $scaled-img := image:scale(
        $source-img,
        map {
            "source": map {
                "media-type": "image/jpeg"
            },
            "destination": map {
                "max-width": 1000,
                "max-height": 1000,
                "rendering-hints": map {
                    $image:interpolation: $image:interpolation_bicubic
                }
            }
        }
  )
  return
    xmldb:store-as-binary("/db/image-scale-test", "h+p_scale_1000_bicubic.jpeg", $scaled-img)
};

declare
    %test:assertEquals("/db/image-scale-test/h+p_scale_200_bilinear.jpeg")
function ist:scale-down-bilinear() {
  let $source-img := util:binary-doc("/db/image-scale-test/h+p.jpeg")
  let $scaled-img := image:scale(
        $source-img,
        map {
            "source": map {
                "media-type": "image/jpeg"
            },
            "destination": map {
                "max-width": 200,
                "max-height": 200,
                "rendering-hints": map {
                    $image:interpolation: $image:interpolation_bilinear
                }
            }
        }
  )
  return
    xmldb:store-as-binary("/db/image-scale-test", "h+p_scale_200_bilinear.jpeg", $scaled-img)
};

declare
    %test:assertEquals("/db/image-scale-test/h+p_scale_1000_bilinear.jpeg")
function ist:scale-up-bilinear() {
  let $source-img := util:binary-doc("/db/image-scale-test/h+p.jpeg")
  let $scaled-img := image:scale(
        $source-img,
        map {
            "source": map {
                "media-type": "image/jpeg"
            },
            "destination": map {
                "max-width": 1000,
                "max-height": 1000,
                "rendering-hints": map {
                    $image:interpolation: $image:interpolation_bilinear
                }
            }
        }
  )
  return
    xmldb:store-as-binary("/db/image-scale-test", "h+p_scale_1000_bilinear.jpeg", $scaled-img)
};
