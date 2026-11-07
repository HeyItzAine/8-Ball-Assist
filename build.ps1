$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$bt = Join-Path $sdk 'build-tools/36.0.0'
$android = Join-Path $sdk 'platforms/android-35/android.jar'
function Check { if ($LASTEXITCODE -ne 0) { throw "Build command failed: $LASTEXITCODE" } }
New-Item -ItemType Directory -Force build/classes,build/dex,build/generated,dist | Out-Null
$workspacePath = (Resolve-Path -LiteralPath $PSScriptRoot).Path
foreach ($relative in @('build/classes','build/dex','build/generated')) {
 $targetPath = (Resolve-Path -LiteralPath (Join-Path $workspacePath $relative)).Path
 if (!$targetPath.StartsWith($workspacePath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Build cleanup escaped workspace' }
 Get-ChildItem -LiteralPath $targetPath -Force | Remove-Item -Recurse -Force
}
& "$bt/aapt.exe" package -f -m -M AndroidManifest.xml -S res -I $android -J build/generated -F build/base.zip
Check
$sources = @(Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName)
$sources += @(Get-ChildItem build/generated -Recurse -Filter *.java | ForEach-Object FullName)
& javac -encoding UTF-8 --release 8 -classpath $android -d build/classes @sources
Check
$classes = @(Get-ChildItem build/classes -Recurse -Filter *.class | ForEach-Object FullName)
& "$bt/d8.bat" --lib $android --min-api 26 --output build/dex @classes
Check
Copy-Item build/base.zip build/unsigned.zip -Force
Push-Location build/dex
try { & "$bt/aapt.exe" add ../unsigned.zip classes.dex; Check } finally { Pop-Location }
& "$bt/zipalign.exe" -f 4 build/unsigned.zip build/aligned.zip
Check
if (!(Test-Path build/local.keystore)) {
 & keytool -genkeypair -keystore build/local.keystore -storepass android -keypass android -alias local -dname 'CN=Rail Guide' -keyalg RSA -keysize 2048 -validity 10000
 Check
}
& "$bt/apksigner.bat" sign --ks build/local.keystore --ks-pass pass:android --key-pass pass:android --out build/RailGuide.pending build/aligned.zip
Check
& "$bt/apksigner.bat" verify --verbose build/RailGuide.pending
Check
Move-Item -LiteralPath build/RailGuide.pending -Destination 'dist/8 Ball Assist.apk' -Force
# Preserve previous packages as archives; keep one installable APK.
Get-ChildItem -LiteralPath dist -Filter '*.apk' -File | Where-Object Name -ne '8 Ball Assist.apk' | ForEach-Object { Move-Item -LiteralPath $_.FullName -Destination (Join-Path (Resolve-Path build) ($_.BaseName + '.previous.zip')) -Force }
Write-Output 'Built dist/8 Ball Assist.apk'
