# Instala o APK no celular via ADB marcando a Play Store como installer
# (mesma técnica do AAAD: https://github.com/shmykelsa/AAAD).
# O Android Auto filtra apps de terceiros pela origem da instalação; com
# installer = com.android.vending o app aparece sem depender de "Fontes desconhecidas".
#
# Uso: .\scripts\install-as-play-store.ps1 caminho\do\app.apk
param([Parameter(Mandatory = $true)][string]$Apk)
$ErrorActionPreference = 'Stop'

$pkg = 'com.kododake.aabrowser'
$installer = 'com.android.vending'
$remote = '/data/local/tmp/androsbrowser.apk'

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { Write-Error 'adb não encontrado no PATH'; exit 1 }
adb get-state | Out-Null
if ($LASTEXITCODE -ne 0) { exit 1 }

adb push $Apk $remote
if ($LASTEXITCODE -ne 0) { exit 1 }
# -r: reinstala mantendo dados. Se falhar com INSTALL_FAILED_UPDATE_INCOMPATIBLE,
# a assinatura mudou (debug x release): rode "adb uninstall $pkg" e tente de novo.
adb shell pm install -r -i $installer $remote
$code = $LASTEXITCODE
adb shell rm -f $remote
if ($code -ne 0) { exit $code }

Write-Output '--- origem registrada ---'
adb shell dumpsys package $pkg | Select-String -Pattern 'installerPackageName|installInitiatingPackageName'
