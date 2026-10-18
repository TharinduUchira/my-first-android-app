param (
    [string]$CommitMessage = "Update app logic"
)

Write-Host "==> Checking connected Android devices via ADB..." -ForegroundColor Cyan
$device = (adb devices | Select-String -Pattern "`tdevice$")
if (-not $device) {
    Write-Warning "No device detected. Connect your phone with USB Debugging enabled."
    exit 1
}
Write-Host "Device detected: $($device.Line.Split("`t")[0])" -ForegroundColor Green

Write-Host "==> Committing and pushing to trigger Cloud Build..." -ForegroundColor Cyan
git add .
git commit -m "$CommitMessage"
git push origin main

Write-Host "==> Watching GitHub Actions build..." -ForegroundColor Cyan
Start-Sleep -Seconds 4
gh run watch --exit-status

if ($LASTEXITCODE -ne 0) {
    Write-Error "Build failed on GitHub Actions! Run 'gh run view --log-failed' to inspect."
    exit 1
}

Write-Host "==> Downloading compiled APK..." -ForegroundColor Cyan
$ArtifactDir = "$PSScriptRoot\dist"
if (Test-Path $ArtifactDir) { Remove-Item -Recurse -Force $ArtifactDir }
gh run download --name app-debug --dir $ArtifactDir

$ApkPath = Get-ChildItem -Path $ArtifactDir -Filter "*.apk" -Recurse | Select-Object -ExpandProperty FullName -First 1

if ($ApkPath) {
    Write-Host "==> Sideloading APK to phone via ADB..." -ForegroundColor Cyan
    adb install -r $ApkPath
    Write-Host "==> App successfully deployed and running!" -ForegroundColor Green
} else {
    Write-Error "No APK found in downloaded artifacts."
}
