#!/usr/bin/env bash
# Instala o APK no celular via ADB marcando a Play Store como installer
# (mesma técnica do AAAD: https://github.com/shmykelsa/AAAD).
# O Android Auto filtra apps de terceiros pela origem da instalação; com
# installer = com.android.vending o app aparece sem depender de "Fontes desconhecidas".
#
# Uso: scripts/install-as-play-store.sh caminho/do/app.apk
set -euo pipefail

APK="${1:?uso: $0 caminho/do/app.apk}"
PKG="com.kododake.aabrowser"
INSTALLER="com.android.vending"
REMOTE="/data/local/tmp/androsbrowser.apk"

command -v adb >/dev/null || { echo "adb não encontrado no PATH" >&2; exit 1; }
adb get-state >/dev/null

adb push "$APK" "$REMOTE"
# -r: reinstala mantendo dados. Se falhar com INSTALL_FAILED_UPDATE_INCOMPATIBLE,
# a assinatura mudou (debug x release): rode "adb uninstall $PKG" e tente de novo.
adb shell pm install -r -i "$INSTALLER" "$REMOTE"
adb shell rm -f "$REMOTE"

echo "--- origem registrada ---"
adb shell dumpsys package "$PKG" | grep -E "installerPackageName|installInitiatingPackageName" || true
