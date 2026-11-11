# Copies the LOCAL Sijill database (the sijill-postgres Docker container) into the Render database.
# Everything is copied: people, files, issues, scores and the saved Google connection. The Render database is
# wiped and replaced. Run it from PowerShell:   .\scripts\copy-db-to-render.ps1
#
# Before: in Render open the web service "sijill" > Suspend (so nothing is connected while the data is replaced).
# After:  Resume the service (or Manual Deploy > Restart).
param([string]$RenderUrl)

$ErrorActionPreference = 'Stop'

if (-not $RenderUrl) {
    $RenderUrl = Read-Host 'Paste the Render "External Database URL" (sijill-db > Connections)'
}
$RenderUrl = $RenderUrl.Trim()
if ($RenderUrl -notmatch '^postgres(ql)?://') { throw 'That does not look like a Postgres URL.' }
if ($RenderUrl -notmatch 'sslmode=') {
    $RenderUrl += $(if ($RenderUrl.Contains('?')) { '&' } else { '?' }) + 'sslmode=require'
}

# The dump goes to the temp folder, never into the project, because it holds real emails and Google tokens.
$dump = Join-Path $env:TEMP 'sijill-backup.sql'

Write-Host '1/3 Dumping the local database...'
docker exec sijill-postgres pg_dump -U sijill -d sijill --no-owner --no-privileges --clean --if-exists -f /tmp/sijill.sql
if ($LASTEXITCODE -ne 0) { throw 'The dump failed. Is the sijill-postgres container running?' }
docker cp sijill-postgres:/tmp/sijill.sql $dump
if ($LASTEXITCODE -ne 0) { throw 'Could not copy the dump out of the container.' }

Write-Host '2/3 Restoring into Render (this can take a minute)...'
docker run --rm -v "${env:TEMP}:/d" postgres:17-alpine psql $RenderUrl -v ON_ERROR_STOP=1 -q -f /d/sijill-backup.sql
$ok = ($LASTEXITCODE -eq 0)

Remove-Item $dump -Force -ErrorAction SilentlyContinue
if (-not $ok) { throw 'The restore failed. Read the message above, and send it to me if you need help.' }

Write-Host '3/3 Done. Now Resume (or Restart) the "sijill" web service in Render.'
