#!/bin/bash
set -e

export PATH=/workspace/depot_tools:$PATH

cd /workspace/webrtc

# .gclient 설정
cat > .gclient << 'GCLIENT_EOF'
solutions = [
  {
    "name": "src",
    "url": "https://webrtc.googlesource.com/src.git",
    "deps_file": "DEPS",
    "managed": False,
    "custom_deps": {},
  },
]
target_os = ["android"]
GCLIENT_EOF

echo "=========================================="
echo "🚀 WebRTC Source Checkout Starting"
echo "=========================================="
echo "⏱️  Expected duration: 1-2 hours"
echo "📍 Working directory: $(pwd)"
echo "=========================================="

gclient sync --no-history --shallow 2>&1 | tee /output/sync.log

echo ""
echo "=========================================="
echo "✅ WebRTC Source Checkout Complete!"
echo "=========================================="

ls -la src/ > /output/structure.txt 2>&1 || echo "src/ directory not found" > /output/structure.txt

echo "📁 Source location: /workspace/webrtc/src"
echo "📄 Log saved to: /output/sync.log"
