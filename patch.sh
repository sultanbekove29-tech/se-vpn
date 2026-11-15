#!/usr/bin/env bash
# Переименовывает клиент в SE VPN. Запускается из корня репозитория.
set -euo pipefail
A="$PWD/sing-box-for-android"
G="$A/app/build.gradle.kts"
sed -i 's/applicationId = "io.nekohasekai.sfa"/applicationId = "ru.sevpn.app"/' "$G"
sed -i 's/include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")/include("armeabi-v7a", "arm64-v8a")/' "$G"
# название приложения во всех языках
find "$A/app/src" -name strings.xml -exec sed -i 's|<string name="app_name" translatable="false">[^<]*</string>|<string name="app_name" translatable="false">SE VPN</string>|' {} +
# значок
python3 - "$A" <<'PY'
import sys, os
from PIL import Image
a = sys.argv[1]
im = Image.open("assets/icon.png").convert("RGBA")
for d, s in {"mdpi":48,"hdpi":72,"xhdpi":96,"xxhdpi":144,"xxxhdpi":192}.items():
    p = f"{a}/app/src/main/res/mipmap-{d}"
    os.makedirs(p, exist_ok=True)
    r = im.resize((s, s), Image.LANCZOS)
    r.save(f"{p}/ic_launcher.png"); r.save(f"{p}/ic_launcher_round.png")
PY
rm -rf "$A/app/src/main/res/mipmap-anydpi-v26" "$A/app/src/main/res/mipmap-anydpi"
grep -n 'applicationId' "$G"

# ---- вход в аккаунт SE VPN ----
API_BASE="${API_BASE:-http://193.17.180.182:8080}"
PKG="$A/app/src/main/java/io/nekohasekai/sfa/sevpn"
mkdir -p "$PKG"
sed "s|__API_BASE__|$API_BASE|" sevpn/LoginActivity.kt > "$PKG/LoginActivity.kt"
python3 - "$A/app/src/main/AndroidManifest.xml" <<'PY'
import re, sys
p = sys.argv[1]
s = open(p).read()
# запуск теперь начинается с экрана входа
n = len(re.findall(r'<category android:name="android.intent.category.LAUNCHER" />', s))
assert n == 1, n
s = re.sub(r'\s*<intent-filter>\s*<action android:name="android.intent.action.MAIN" />\s*<category android:name="android.intent.category.LAUNCHER" />\s*</intent-filter>', '', s, count=1)
act = """
        <activity
            android:name=".sevpn.LoginActivity"
            android:exported="true"
            android:icon="@mipmap/ic_launcher"
            android:theme="@style/AppTheme">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
"""
s = s.replace('</application>', act + '    </application>', 1)
s = s.replace('<application', '<application android:usesCleartextTraffic="true"', 1)
open(p, 'w').write(s)
PY
grep -n "LoginActivity\|usesCleartext" "$A/app/src/main/AndroidManifest.xml"
