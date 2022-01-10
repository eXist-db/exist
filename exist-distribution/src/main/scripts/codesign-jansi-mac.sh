#!/usr/bin/env bash
#
# Copyright (C) 2014, Evolved Binary Ltd
#
# This file was originally ported from FusionDB to eXist-db by
# Evolved Binary, for the benefit of the eXist-db Open Source community.
# Only the ported code as it appears in this file, at the time that
# it was contributed to eXist-db, was re-licensed under The GNU
# Lesser General Public License v2.1 only for use in eXist-db.
#
# This license grant applies only to a snapshot of the code as it
# appeared when ported, it does not offer or infer any rights to either
# updates of this source code or access to the original source code.
#
# The GNU Lesser General Public License v2.1 only license follows.
#
# ---------------------------------------------------------------------
#
# Copyright (C) 2014, Evolved Binary Ltd
#
# This library is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; version 2.1.
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
# $2 is the jansi version
# $3 is temp work directory
# $4 the mac codesign identity


set -e
#set -x  ## enable to help debug

# ensure a clean temp work directory
if [ -d "${3}/org" ]
then
  rm -rf "${3}/org"
fi

# for each native arch
archs=('arm64' 'x86' 'x86_64')
for arch in ${archs[@]}
do
  # create the temp output dirs
  mkdir -p "${3}/org/fusesource/jansi/internal/native/Mac/${arch}"

  # switch to temp output dir
  pushd "${3}"

  # extract the native files
  jar -xf "${1}/jansi-${2}.jar" "org/fusesource/jansi/internal/native/Mac/${arch}/libjansi.jnilib"

  # test if the file is unsigned, and sign if needed
  /usr/bin/codesign --verbose --test-requirement="=anchor trusted" --verify "org/fusesource/jansi/internal/native/Mac/${arch}/libjansi.jnilib" || /usr/bin/codesign --verbose --force --timestamp --sign "${4}" "org/fusesource/jansi/internal/native/Mac/${arch}/libjansi.jnilib"

  # overwrite the file in the jar
  jar -uf "${1}/jansi-${2}.jar" "org/fusesource/jansi/internal/native/Mac/${arch}/libjansi.jnilib"

  # switch back from temp output dir
  popd

done
