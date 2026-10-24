#!/bin/sh
# Stage the Live2D Cubism Native SDK into third_party/cubism/.
# The SDK is not vendored: it needs a (free) account and manual download.
# Usage: scripts/fetch_cubism.sh [/path/to/CubismSdkForNative-*.zip]
set -eu

DEST="$(cd "$(dirname "$0")/.." && pwd)/third_party/cubism"
ZIP="${1:-}"

if [ ! -x "$(command -v unzip 2>/dev/null)" ]; then
    echo "unzip is required but not installed" >&2
    exit 1
fi

if [ -f "$DEST/Core/include/Live2DCubismCore.hpp" ] && [ -d "$DEST/Framework" ]; then
    echo "Cubism SDK already present in $DEST"
    exit 0
fi

if [ -z "$ZIP" ]; then
    cat <<'EOF'
Cubism Native SDK not found in third_party/cubism/.

1. Register and download "Cubism SDK for Native" (free for small businesses):
   https://www.live2d.com/en/sdk/download/native/
2. Re-run this script with the downloaded zip:
   scripts/fetch_cubism.sh ~/Downloads/CubismSdkForNative-*.zip

The zip contains Core/ (prebuilt libLive2DCubismCore.a + headers), Framework/
and Samples/. Only Core and Framework are needed. After extracting, wire
NDK/CMake (TODO in app/build.gradle.kts) and implement Live2DRenderer.
EOF
    exit 1
fi

[ -f "$ZIP" ] || { echo "No such file: $ZIP" >&2; exit 1; }
mkdir -p "$DEST"
unzip -q -o "$ZIP" -d "$DEST"
echo "Extracted $ZIP into $DEST"
echo "Next: wire NDK/CMake (TODO in app/build.gradle.kts) and implement Live2DRenderer."
