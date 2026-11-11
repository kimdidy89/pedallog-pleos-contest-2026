"""원본 해시, 한국어 글리프, 선택한 APK의 글꼴 탑재를 검증한다."""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path)
    args = parser.parse_args()
    manifest = json.loads((ROOT / "assets/third-party-manifest.json").read_text(encoding="utf-8"))
    for item in manifest["files"]:
        data = (ROOT / item["path"]).read_bytes()
        assert hashlib.sha256(data).hexdigest() == item["sha256"], item["path"]
    required = set(range(0xAC00, 0xD7A4)) | set(map(ord, "0123456789속도브레이크입력해제미확인동기화"))
    for path in (ROOT / "assets/fonts").iterdir():
        if path.suffix not in {".otf", ".woff2"}:
            continue
        with TTFont(path) as font:
            assert required <= font.getBestCmap().keys(), path.name
    if args.apk:
        with zipfile.ZipFile(args.apk) as archive:
            for name in ["Pretendard-Regular.otf", "Pretendard-SemiBold.otf", "Pretendard-Bold.otf"]:
                assert archive.read("assets/fonts/" + name) == (ROOT / "assets/fonts" / name).read_bytes(), name
            assert archive.read("assets/licenses/pretendard.txt") == (ROOT / "assets/licenses/pretendard.txt").read_bytes()
    print("에셋 원본 해시·글꼴 6개 한글 완성형·선택 APK 탑재 검증 통과")

if __name__ == "__main__":
    main()
