#!/bin/bash
set -e

ROOT_DIR="/Users/jaeyong/skt-dorandoran/frontend"
SRC_DIR="$ROOT_DIR/webrtc-build/src"
OUT_DIR="$ROOT_DIR/webrtc-build/out"
AAR_OUT="$ROOT_DIR/webrtc-build/aar"
SDK_DIR="$ROOT_DIR/webrtc-build/android-sdk"

mkdir -p "$OUT_DIR" "$AAR_OUT" "$SDK_DIR"

echo "🐳 Building WebRTC AAR inside Docker..."

docker run --rm --platform linux/amd64 \
  -v "$SRC_DIR:/workspace/webrtc/src" \
  -v "$OUT_DIR:/workspace/webrtc/out" \
  -v "$AAR_OUT:/workspace/output" \
  -v "$SDK_DIR:/workspace/android-sdk" \
  webrtc-builder \
  bash -c '
    set -e
    export PATH=/workspace/depot_tools:$PATH
    export ANDROID_HOME=/workspace/android-sdk
    export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

    if ! command -v sdkmanager >/dev/null 2>&1; then
      echo "📦 Installing Android cmdline-tools..."
      apt-get update
      apt-get install -y unzip
      mkdir -p $ANDROID_HOME
      cd $ANDROID_HOME
      wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip -O cmdline-tools.zip
      mkdir -p cmdline-tools
      unzip -q cmdline-tools.zip -d cmdline-tools
      mkdir -p cmdline-tools/latest
      mv cmdline-tools/cmdline-tools/* cmdline-tools/latest/
      rm -f cmdline-tools.zip
      export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
    fi

    # Initialize depot_tools
    (cd /workspace/depot_tools && ./ensure_bootstrap)
    /workspace/depot_tools/gclient --version >/dev/null

    echo "📦 Installing Android SDK components..."
    yes | sdkmanager --licenses >/dev/null
    sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;25.2.9519653" "cmake;3.22.1"

    cd /workspace/webrtc/src

    build_one() {
      local abi=$1
      local cpu=$2
      local out=out/android_${cpu}

      gn gen $out --args="target_os=\"android\" target_cpu=\"${cpu}\" is_debug=false rtc_include_tests=false rtc_build_examples=false android_static_analysis=\"off\""
      ninja -C $out -j4 sdk/android:libwebrtc sdk/android:libjingle_peerconnection_so

      mkdir -p /workspace/output/aar-temp/jni/${abi}
      local so_path=$(find $out -name "libjingle_peerconnection_so.so" | head -1)
      if [ -z "$so_path" ]; then
        echo "❌ .so not found for ${abi}"
        exit 1
      fi
      cp -f "$so_path" /workspace/output/aar-temp/jni/${abi}/
    }

    rm -rf /workspace/output/aar-temp
    mkdir -p /workspace/output/aar-temp

    # Build for each ABI
    build_one arm64-v8a arm64
    build_one armeabi-v7a arm
    build_one x86_64 x64

    # Copy classes.jar and manifest
    cp -f out/android_arm64/lib.java/sdk/android/libwebrtc.jar /workspace/output/aar-temp/classes.jar
    cp -f sdk/android/AndroidManifest.xml /workspace/output/aar-temp/AndroidManifest.xml

    # Create AAR
    cd /workspace/output/aar-temp
    zip -r /workspace/output/webrtc-custom.aar .

    echo "✅ AAR created at /workspace/output/webrtc-custom.aar"
  '

echo "🎉 Done! Output: $AAR_OUT/webrtc-custom.aar"
