# Sync docs/wiki → GitHub Wiki
#
# First time only: open https://github.com/Hanter1/Sherlock/wiki
# and click "Create the first page" (any content), then run:
#   powershell -File scripts/publish_wiki.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$src = Join-Path $root "docs\wiki"
$tmp = Join-Path $env:TEMP "Sherlock.wiki"

$credInput = "protocol=https`nhost=github.com`n`n"
$token = ([regex]::Match(($credInput | & git credential fill 2>&1 | Out-String), "password=(.+)")).Groups[1].Value.Trim()
if (-not $token) { throw "No GitHub token from git credential. Run: gh auth login" }
$env:GH_TOKEN = $token

Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
git clone "https://x-access-token:${token}@github.com/Hanter1/Sherlock.wiki.git" $tmp
Copy-Item (Join-Path $src "*.md") $tmp -Force

# Sidebar for Wiki navigation
@"
**Sherlock Bot**

- [[Home]]
- [[Installation]]
- [[Usage]]
- [[Catalog]]
- [[Privacy and Ethics|Privacy-and-Ethics]]
- [[Building]]
"@ | Set-Content -Encoding utf8 (Join-Path $tmp "_Sidebar.md")

Set-Location $tmp
git add -A
git -c user.email="docs@sherlock.bot" -c user.name="Sherlock Docs" commit -m "Sync wiki from docs/wiki" --allow-empty
git push origin HEAD
Write-Host "Wiki updated: https://github.com/Hanter1/Sherlock/wiki"
