param (
    [string]$CommitMessage = "Update app logic"
)

Write-Host "==> Checking connected Android devices via ADB..." -ForegroundColor Cyan
$deviceMatches = @(adb devices | Select-String "`tdevice$")
if ($deviceMatches.Count -eq 0) {
    Write-Warning "No active device detected. Check your connection."
    exit 1
}

$deviceId = $deviceMatches[0].Line.Split("`t")[0]
Write-Host "Connected target device: $deviceId" -ForegroundColor Green

Write-Host "==> Committing and pushing to trigger Cloud Build..." -ForegroundColor Cyan
git add .
$status = git status --porcelain
if ($status) {
    git commit -m "$CommitMessage"
    git push origin main
} else {
    gh workflow run build-apk.yml --ref main
}

Write-Host "==> Watching GitHub Actions build..." -ForegroundColor Cyan
Start-Sleep -Seconds 5
gh run watch --exit-status

if ($LASTEXITCODE -ne 0) {
    Write-Error "Build failed on GitHub Actions!"
    exit 1
}

Write-Host "==> Downloading compiled APK..." -ForegroundColor Cyan
$ArtifactDir = ".\dist"
Remove-Item -Recurse -Force $ArtifactDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $ArtifactDir -Force | Out-Null

gh run download --name app-debug --dir $ArtifactDir

$apkFiles = @(Get-ChildItem -Path$ArtifactDir -Filter "*.apk" -Recurse)
if ($apkFiles.Count -gt 0) {
    $ApkPath =$apkFiles[0].FullName
    Write-Host "==> Sideloading APK to $deviceId..." -ForegroundColor Cyan
    
    $installOutput = adb -s$deviceId install -r "$ApkPath" 2>&1
    if ($installOutput -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
        Write-Warning "Signature mismatch detected. Reinstalling cleanly..."
        adb -s $deviceId uninstall com.example.myfirstapp | Out-Null
        adb -s $deviceId install "$ApkPath"
    }
    
    Write-Host "==> Launching app..." -ForegroundColor Cyan
    adb -s $deviceId shell monkey -p com.example.myfirstapp -c android.intent.category.LAUNCHER 1
    Write-Host "==> App successfully deployed and running!" -ForegroundColor Green
} else {
    Write-Error "No APK found in downloaded artifacts."
}
