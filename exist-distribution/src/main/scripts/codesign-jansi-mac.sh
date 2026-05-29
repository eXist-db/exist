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
# $5 is the jline classifier (e.g. jdk11)


set -e
#set -x  ## enable to help debug

# for each jar file
for jar in "${1}/jansi-${2}.jar" "${1}/jline-${2}-${5}.jar"
do
  # ensure a clean temp work directory for each jar
  if [ -d "${3}/org" ]
  then
    rm -rf "${3}/org"
  fi

  # for each native arch
  archs=('arm64' 'x86' 'x86_64')
  for arch in "${archs[@]}"
  do
    # create the temp output dirs
    mkdir -p "${3}/org/jline/nativ/Mac/${arch}"

    # switch to temp output dir
    pushd "${3}"

    # extract the native files
    jar -xf "${jar}" "org/jline/nativ/Mac/${arch}/libjlinenative.jnilib"

    # test if the file is unsigned, and sign if needed
    /usr/bin/codesign --verbose --test-requirement="=anchor trusted" \
                      --verify "org/jline/nativ/Mac/${arch}/libjlinenative.jnilib" || \
                      /usr/bin/codesign --verbose --force --timestamp --sign "${4}" \
                      "org/jline/nativ/Mac/${arch}/libjlinenative.jnilib"

    # overwrite the file in the jar
    jar -uf "${jar}" "org/jline/nativ/Mac/${arch}/libjlinenative.jnilib"

    # switch back from temp output dir
    popd

  done
done
