# 서명 키 (mint-and-friends · todayletters와 같은 방식)

- `sideload.jks.gpg`: 시험용 APK 키 (별칭 `sideload`). Secrets `SIDELOAD_PASSPHRASE`로 GPG 잠금.
  CI 테스트 APK가 늘 같은 키로 서명되어 폰에 덮어 설치되고, Firebase 디지털 지문(SHA)이 고정된다.
- `upload.jks.gpg`: Google Play 업로드 키 (별칭 `upload`). Secrets `ANDROID_UPLOAD_PASSPHRASE`로 GPG 잠금.
- 둘 다 **Android signing key** 워크플로(`.github/workflows/android-signing-key.yml`)가 GitHub 안에서 만든다.
  잠그지 않은 키와 암호는 어디에도 남지 않고, 실행 요약(Summary)에 공개해도 되는 SHA-1 · SHA-256만 찍힌다.
- 키가 없으면 빌드는 실행기의 임시 디버그 키로 서명된다(지문이 매번 바뀜).
