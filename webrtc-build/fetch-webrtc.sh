#!/bin/bash
set -e

# depot_tools PATH 설정
export PATH="/workspace/depot_tools:$PATH"

echo "📥 Fetching WebRTC source in Docker..."

# gclient 설정
cat > .gclient <<'EOF'
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
EOF

echo "⏬ Running gclient sync (this takes 1-2 hours)..."
gclient sync --no-history --shallow 2>&1 | tee /workspace/sync.log

echo "✅ WebRTC source checkout complete!"
ls -la src/
