#!/bin/bash
# Cloudflare Pages build script for resource pack
set -e

echo "Building TomkesTookit Resource Pack..."

# Check if resourcepack directory exists
if [ ! -d "resourcepack" ]; then
    echo "Error: resourcepack/ directory not found"
    exit 1
fi

# Create output directory
mkdir -p public

# Zip the resource pack (use zip command for proper forward slashes)
cd resourcepack
zip -r ../public/TomkesTookit-ResourcePack.zip . -x '*.git*'
cd ..

# Calculate SHA1
SHA1=$(sha1sum public/TomkesTookit-ResourcePack.zip | cut -d' ' -f1)
echo "$SHA1" > public/TomkesTookit-ResourcePack.zip.sha1

echo "Build complete!"
echo "  File: public/TomkesTookit-ResourcePack.zip"
echo "  SHA1: $SHA1"
echo "  Size: $(stat -c%s public/TomkesTookit-ResourcePack.zip 2>/dev/null || stat -f%z public/TomkesTookit-ResourcePack.zip) bytes"
