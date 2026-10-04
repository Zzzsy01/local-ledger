param(
    [string]$ApkPath = "$PSScriptRoot/../app/build/outputs/apk/debug/app-debug.apk",
    [Parameter(Mandatory)][string]$NotesFile,
    [Parameter(Mandatory)][string]$BuildToolsPath,
    [switch]$PrepareOnly
)

$ErrorActionPreference = 'Stop'
$repository = 'Zzzsy01/local-ledger'
$ApkPath = (Resolve-Path -LiteralPath $ApkPath).Path
$NotesFile = (Resolve-Path -LiteralPath $NotesFile).Path
$badging = & (Join-Path $BuildToolsPath 'aapt.exe') dump badging $ApkPath
if ($LASTEXITCODE -ne 0) { throw '无法读取 APK 版本。' }
$package = [regex]::Match(($badging -join "`n"), "(?m)^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'")
$sdk = [regex]::Match(($badging -join "`n"), "(?m)^sdkVersion:'(\d+)'")
if (!$package.Success -or !$sdk.Success -or $package.Groups[1].Value -ne 'com.localledger.app') {
    throw '请选择随手账的实际安装包。'
}
$versionCode = [long]$package.Groups[2].Value
$version = $package.Groups[3].Value
if ($version -notmatch '^[0-9A-Za-z][0-9A-Za-z._-]*$') { throw '版本名不能用于发布文件名。' }
$certificates = & (Join-Path $BuildToolsPath 'apksigner.bat') verify --print-certs $ApkPath
if ($LASTEXITCODE -ne 0) { throw 'APK 签名验证失败。' }
$signer = [regex]::Match(($certificates -join "`n"), 'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)')
# 保持已有用户的覆盖升级路径；私钥保存在发布者本机，不提交到 GitHub。
if (!$signer.Success -or $signer.Groups[1].Value -ne '447144514e8fb7d24c34a515f8aa70d014cebb4599629947f88302dc839956c9') {
    throw '签名与已交付版本不同，无法保留原账本覆盖升级。'
}
$tag = "v$version"
$directory = Join-Path $PSScriptRoot "../build/releases/$tag"
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$assetName = "ledger-$version.apk"
$asset = Join-Path $directory $assetName
Copy-Item -LiteralPath $ApkPath -Destination $asset -Force
$manifestPath = Join-Path $directory 'update.json'
$manifest = [ordered]@{
    versionCode = $versionCode
    versionName = $version
    apkUrl = "https://github.com/$repository/releases/download/$tag/$assetName"
    sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $asset).Hash.ToLowerInvariant()
    minSdk = [int]$sdk.Groups[1].Value
    notes = (Get-Content -LiteralPath $NotesFile -Raw -Encoding UTF8).Trim()
}
[IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
if ($PrepareOnly) {
    $manifest | ConvertTo-Json
    Write-Output "发布文件：$asset"
    return
}

Push-Location (Join-Path $PSScriptRoot '../..')
try {
    $commit = git rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw '无法确定源码提交；请先提交并推送本次版本。' }
    gh release create $tag $asset $manifestPath --repo $repository --target $commit --title "随手账 $version" --notes-file $NotesFile --draft
    if ($LASTEXITCODE -ne 0) { throw '创建草稿版本失败，未发布更新。' }
    gh release edit $tag --repo $repository --draft=false --latest
    if ($LASTEXITCODE -ne 0) { throw '发布失败，版本仍是草稿；请检查 GitHub 状态。' }
} finally {
    Pop-Location
}
