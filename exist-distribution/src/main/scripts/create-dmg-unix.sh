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


# $1 is .app dir
# $2 is app name e.g. VolName
# $3 is background image
# $4 is Volume icons
# $5 is DS_Store
# $6 is the output DMG file path and name

set -e
set -x

if [ ! -e /sbin/mkfs.hfsplus ]
then
	>&2 echo "ERROR: Skipping DMG creation because /sbin/mkfs.hfsplus is missing!"
	>&2 echo "       To install it, you might run:"
	>&2 echo "       * CentOS & co.: sudo yum install hfsutils hfsplus-tools"
	>&2 echo "       * Debian, Ubuntu & co.: sudo apt-get install hfsprogs hfsplus"
	exit 1
fi

# cleanup any previous DMG before creating a new DMG
if [[ -f "${6}" ]]; then
    echo "Removing previous DMG"
    rm -v "${6}"
fi

tmp_dmg=/tmp/$2-dmg-tmp
tmp_dmg_mount=$tmp_dmg-mount

final_app_dir="$(dirname "$1")/$2.app"

# Copy the produced .app to `volname`.app
cp -r $1 $final_app_dir

# Calculate the size for an image and add 10MB to ensure there is enough space!
base_size=$(du -sm $1 | sed 's/\([0-9]*\).*/\1/')
img_size=$(($base_size + 10))

# Create a temporary Disk Image
dd if=/dev/zero of=$tmp_dmg.dmg bs=1M count=$img_size
/sbin/mkfs.hfsplus -v $2 $tmp_dmg.dmg

# Attach the temporary image
username=$(whoami)
mkdir -p $tmp_dmg_mount
sudo mount -o loop,uid=$username $tmp_dmg.dmg $tmp_dmg_mount

# Copy the app into the image
cp -r $final_app_dir $tmp_dmg_mount

# Copy the background, the volume icon and DS_Store files
mkdir -p $tmp_dmg_mount/$2/.DropDMGBackground
cp $3 $tmp_dmg_mount/$2/.DropDMGBackground/
cp $4 $tmp_dmg_mount/$2/.VolumeIcon.icns
cp $5 $tmp_dmg_mount/$2/.DS_Store

# Add a symbolic link to the Applications directory
ln -s /Applications $tmp_dmg_mount/$2/Applications

# Detach the temporary image
sudo umount $tmp_dmg_mount

# Copy it to a new image
cp $tmp_dmg.dmg $6

# Delete the temporary image
rm $tmp_dmg.dmg

# Delete the mount point
rm -r $tmp_dmg_mount

# Delete the copied `volname`.app used for the DMG
rm -r $final_app_dir
