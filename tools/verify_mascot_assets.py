"""포즈 7종의 원본 해시·투명도·APK 탑재를 검증한다."""
from pathlib import Path
import argparse,hashlib,json,zipfile
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk',type=Path)
    args=parser.parse_args()
    manifest=json.loads((ROOT/'assets/mascot/manifest.json').read_text(encoding='utf-8'))
    assert len(manifest['files'])==7
    archive=zipfile.ZipFile(args.apk) if args.apk else None
    try:
        for entry in manifest['files']:
            path=ROOT/entry['path'];raw=path.read_bytes()
            assert hashlib.sha256(raw).hexdigest()==entry['sha256'],entry['pose']
            with Image.open(path) as im:
                assert im.mode=='RGBA' and im.getchannel('A').getextrema()==(0,255),entry['pose']
                assert im.width>=512 and im.height>=512,entry['pose']
                assert im.size==(entry['width'],entry['height'])
            if archive:assert archive.read(entry['path'])==raw,entry['pose']
        print('MASCOT_ASSETS_PASS: 7 poses, hashes, transparency, APK bytes')
    finally:
        if archive:archive.close()

if __name__=='__main__':main()
