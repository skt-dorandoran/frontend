// WebRTC 커스텀 AAR 통합 자동화 스크립트
// 1. webrtc-custom.aar을 app/libs로 복사
// 2. build.gradle에 implementation 추가
// 3. JNI 및 TTS 믹싱 API 연결

import java.io.File;

public class WebRtcAarIntegrator {
    public static void main(String[] args) {
        File src = new File("../webrtc-build/aar/webrtc-custom.aar");
        File dest = new File("./libs/webrtc-custom.aar");
        if (!src.exists()) {
            System.err.println("webrtc-custom.aar 파일이 존재하지 않습니다.");
            return;
        }
        try {
            java.nio.file.Files.copy(src.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("AAR 복사 완료: " + dest.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
