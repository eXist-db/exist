#!/usr/bin/env bash

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
/usr/bin/hdiutil internet-enable -yes $6

# Delete the temporary image
rm $tmp_dmg.dmg

# Delete the mount point
rm -r $tmp_dmg_mount

# Delete the copied `volname`.app used for the DMG
rm -r $final_app_dir
