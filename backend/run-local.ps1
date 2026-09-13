# Loads backend/.env into this process's environment, then starts
# the backend. Use this instead of calling mvnw directly whenever a
# database is needed (Phase 5+) — plain `mvnw spring-boot:run` won't
# have DATABASE_URL/USERNAME/PASSWORD set otherwise.
$envFile = Join-Path $PSScriptRoot ".env"
if (-not (Test-Path $envFile)) {
    Write-Error "backend\.env not found. Copy .env.example to .env and fill in your real database credentials first."
    exit 1
}

Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
        [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2], "Process")
    }
}

& "$PSScriptRoot\mvnw.cmd" spring-boot:run
