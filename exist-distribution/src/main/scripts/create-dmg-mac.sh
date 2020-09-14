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

# Create a temporary Disk Image
/usr/bin/hdiutil create -fs HFS+ -srcfolder $final_app_dir -volname $2 -ov $tmp_dmg -format UDRW

# Attach the temporary image
/usr/bin/hdiutil attach $tmp_dmg.dmg -mountroot $tmp_dmg_mount

# Copy the background, the volume icon and DS_Store files
mkdir -p $tmp_dmg_mount/$2/.DropDMGBackground
cp $3 $tmp_dmg_mount/$2/.DropDMGBackground/
cp $4 $tmp_dmg_mount/$2/.VolumeIcon.icns
cp $5 $tmp_dmg_mount/$2/.DS_Store

# Indicate that we want a custom icon
if [[ -f "/Applications/Xcode.app/Contents/Developer/Tools/SetFile" ]]; then
    /Applications/Xcode.app/Contents/Developer/Tools/SetFile -a -c $tmp_dmg_mount/$2
else
    /usr/bin/SetFile -a -c $tmp_dmg_mount/$2
fi

# Add a symbolic link to the Applications directory
ln -s /Applications $tmp_dmg_mount/$2/Applications

# Detach the temporary image
/usr/bin/hdiutil detach $tmp_dmg_mount/$2

# Compress it to a new image
/usr/bin/hdiutil convert $tmp_dmg.dmg -format UDZO -o $6

# Delete the temporary image
rm $tmp_dmg.dmg

# Delete the mount point
rm -r $tmp_dmg_mount

# Delete the copied `volname`.app used for the DMG
rm -r $final_app_dir
