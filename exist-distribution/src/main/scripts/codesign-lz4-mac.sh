#!/usr/bin/env bash
#
# eXist-db Open Source Native XML Database
# Copyright (C) 2001 The eXist-db Authors
#
# info@exist-db.org
# http://www.exist-db.org
#
# This library is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 2.1 of the License, or (at your option) any later version.
#
# This library is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public
# License along with this library; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
#


# $1 is .app/Contents/Java dir
# $2 is the lz4-java version
# $3 is temp work directory
# $4 is the mac codesign identity


set -e
#set -x  ## enable to help debug

jar="${1}/lz4-java-${2}.jar"

# ensure a clean temp work directory
if [ -d "${3}/net" ]
then
  rm -rf "${3}/net"
fi

# lz4-java's darwin natives live under net/jpountz/util/darwin/<arch>/liblz4-java.dylib
archs=('aarch64' 'x86_64')
for arch in "${archs[@]}"
do
  mkdir -p "${3}/net/jpountz/util/darwin/${arch}"

  pushd "${3}"

  # extract the native file
  jar -xf "${jar}" "net/jpountz/util/darwin/${arch}/liblz4-java.dylib"

  # test if signed; sign if not (mirrors codesign-jansi-mac.sh pattern)
  /usr/bin/codesign --verbose --test-requirement="=anchor trusted" \
                    --verify "net/jpountz/util/darwin/${arch}/liblz4-java.dylib" || \
                    /usr/bin/codesign --verbose --force --timestamp --sign "${4}" \
                    "net/jpountz/util/darwin/${arch}/liblz4-java.dylib"

  # overwrite the file in the jar
  jar -uf "${jar}" "net/jpountz/util/darwin/${arch}/liblz4-java.dylib"

  popd
done
