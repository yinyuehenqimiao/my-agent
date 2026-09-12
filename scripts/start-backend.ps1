param(
    [string]$EnvFile = (Join-Path $PSScriptRoot "..\.env"),
    [string]$Profile = "enterprise"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "Environment file not found: $EnvFile. Copy .env.example to .env and configure an API key."
}

Get-Content -LiteralPath $EnvFile -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith("#")) {
        return
    }
    $match = [regex]::Match($line, "^(?<name>[A-Za-z_][A-Za-z0-9_]*)=(?<value>.*)$")
    if (-not $match.Success) {
        throw "Invalid .env line: $line"
    }
    [Environment]::SetEnvironmentVariable($match.Groups["name"].Value, $match.Groups["value"].Value, "Process")
}

$provider = if ($env:AGENT_CHAT_PROVIDER) { $env:AGENT_CHAT_PROVIDER } else { "siliconflow" }
switch ($provider) {
    "siliconflow" {
        if ([string]::IsNullOrWhiteSpace($env:SILICONFLOW_API_KEY)) {
            throw "AGENT_CHAT_PROVIDER=siliconflow but SILICONFLOW_API_KEY is empty."
        }
    }
    "bailian" {
        if ([string]::IsNullOrWhiteSpace($env:BAILIAN_API_KEY)) {
            throw "AGENT_CHAT_PROVIDER=bailian but BAILIAN_API_KEY is empty."
        }
    }
    default { throw "Unsupported AGENT_CHAT_PROVIDER: $provider" }
}

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$jar = Join-Path $projectRoot "bootstrap\target\bootstrap-0.0.1-SNAPSHOT.jar"
if (-not (Test-Path -LiteralPath $jar)) {
    throw "Backend JAR not found: $jar. Run mvn -pl bootstrap -am package -DskipTests first."
}

& java -jar $jar "--spring.profiles.active=$Profile"
