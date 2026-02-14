cmake_minimum_required(VERSION 3.22.1)

project("audio_mixer")

# C++17 표준 사용
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 소스 파일
add_library(audio_mixer SHARED
    audio_mixer_jni.cpp
    audio_mixer.cpp
    tts_pcm_queue.cpp
)

# Android 로그 라이브러리 링크
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(audio_mixer
    ${log-lib}
    ${android-lib}
)
