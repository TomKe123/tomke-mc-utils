#!/bin/bash
# Cloudflare Pages build script for resource pack
set -e

echo "Building TomkesTookit Resource Pack..."

if [ ! -d "resourcepack" ]; then
    echo "Error: resourcepack/ directory not found"
    exit 1
fi

mkdir -p public

# Use Python (available on CF Pages) to create zip with forward slashes
python3 -c "
import zipfile, os, hashlib

with zipfile.ZipFile('public/TomkesTookit-ResourcePack.zip', 'w', zipfile.ZIP_DEFLATED) as zf:
    for root, dirs, files in os.walk('resourcepack'):
        for f in files:
            full = os.path.join(root, f)
            arc = os.path.relpath(full, 'resourcepack')
            zf.write(full, arc)

sha1 = hashlib.sha1(open('public/TomkesTookit-ResourcePack.zip','rb').read()).hexdigest()
open('public/TomkesTookit-ResourcePack.zip.sha1','w').write(sha1)
print(f'  SHA1: {sha1}')
print(f'  Size: {os.path.getsize(\"public/TomkesTookit-ResourcePack.zip\")} bytes')
"

echo "Build complete!"
