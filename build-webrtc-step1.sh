#!/bin/bash
set -e

echo "🚀 WebRTC Custom Build Script for Docker"
echo "=========================================="

# 1. Docker 이미지 빌드
echo "📦 Building Docker image..."
docker build --platform linux/amd64 -f Dockerfile.webrtc -t webrtc-builder .

# 2. Docker 컨테이너 실행 (현재 디렉토리 마운트)
echo "🐳 Starting Docker container..."
docker run -it --rm --platform linux/amd64 \
    -v "$(pwd)/webrtc-build:/workspace/webrtc" \
    -v "$(pwd)/app/src/main/cpp:/workspace/custom-adm" \
    webrtc-builder \
    /bin/bash -c '
    
echo "📥 Fetching WebRTC source..."
cd /workspace/webrtc

# .gclient 설정
cat > .gclient <<EOF
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

# WebRTC 소스 체크아웃 (shallow clone)
echo "⏬ Syncing WebRTC repositories (this may take 30-60 minutes)..."
fetch --nohooks webrtc_android
gclient sync --no-history

echo "✅ WebRTC source checkout complete!"
echo "📍 Source location: /workspace/webrtc/src"

# 다음 단계 안내
cat <<NEXT

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ WebRTC 소스 체크아웃 완료!

다음 단계:
1. 커스텀 AudioDeviceModule 코드를 src/에 추가
2. BUILD.gn 파일 수정
3. 빌드 실행

계속하려면 다음 스크립트를 실행하세요:
  ./build-webrtc-step2.sh
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
NEXT
'

echo "🎉 Step 1 Complete!"
