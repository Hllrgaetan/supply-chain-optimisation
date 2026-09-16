#Requires -Version 5.1
<#
    Démarre Gantt Simulator sous Windows (à lancer via start\start.bat).
    Installe si nécessaire, sans droits administrateur :
      - Java 17 (Eclipse Temurin) dans .runtime\
      - JavaFX SDK 17.0.17 dans lib\
    puis compile les sources et lance l'interface.
#>

param(
    # Passé par start.bat, qui gère lui-même la pause en cas d'erreur
    [switch]$FromLauncher
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$JavaFxVersion = '17.0.17'
$Root = Split-Path -Parent $PSScriptRoot
$RuntimeDir = Join-Path $Root '.runtime'
$JavaFxDir = Join-Path $Root "lib\openjfx-$($JavaFxVersion)_windows-x64_bin-sdk"
$JavaFxLib = Join-Path $JavaFxDir "javafx-sdk-$JavaFxVersion\lib"
$JdkUrl = 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse'
$JavaFxUrl = "https://download2.gluonhq.com/openjfx/$JavaFxVersion/openjfx-$($JavaFxVersion)_windows-x64_bin-sdk.zip"

function Write-Step([string]$Message) {
    Write-Host "==> $Message" -ForegroundColor Cyan
}

# Lance un exécutable natif. Les arguments sont passés via un tableau de chaînes : Windows PowerShell 5.1
# découpe sinon les arguments du type -Dfile.encoding=UTF-8 au niveau du point, et la sortie d'erreur
# des outils Java ne doit pas être transformée en exception.
function Invoke-Native([string]$Exe, [string[]]$Arguments) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $Exe $Arguments | Out-Host
        return $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
}

function Get-JavaMajor([string]$Javac) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $Javac -version 2>&1 | Out-String
    } catch {
        return 0
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($output -match 'javac\s+(\d+)') { return [int]$Matches[1] }
    return 0
}

function Find-Jdk {
    $local = Get-ChildItem -Path $RuntimeDir -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($local) { return $local.FullName }

    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $javacCommand = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($javacCommand) { $candidates += Split-Path (Split-Path $javacCommand.Source) }

    foreach ($jdkHome in $candidates) {
        $javac = Join-Path $jdkHome 'bin\javac.exe'
        if ((Test-Path $javac) -and ((Get-JavaMajor $javac) -ge 17)) { return $jdkHome }
    }
    return $null
}

function Install-Zip([string]$Url, [string]$Destination) {
    $zip = Join-Path $env:TEMP ("gantt-" + [guid]::NewGuid().ToString() + ".zip")
    Write-Host "    Téléchargement de $Url"
    try {
        Invoke-WebRequest -Uri $Url -OutFile $zip -UseBasicParsing
        Write-Host "    Extraction dans $Destination"
        New-Item -ItemType Directory -Force -Path $Destination | Out-Null
        if (Get-Command tar.exe -ErrorAction SilentlyContinue) {
            tar.exe -xf $zip -C $Destination
            if ($LASTEXITCODE -ne 0) { throw "Échec de l'extraction de $Url" }
        } else {
            Expand-Archive -Path $zip -DestinationPath $Destination -Force
        }
    } finally {
        Remove-Item $zip -Force -ErrorAction SilentlyContinue
    }
}

try {
    Set-Location $Root

    # 1. Java 17+
    $jdk = Find-Jdk
    if (-not $jdk) {
        Write-Step 'Java 17 introuvable : installation locale (une seule fois, ~190 Mo)'
        Install-Zip $JdkUrl $RuntimeDir
        $jdk = Find-Jdk
        if (-not $jdk) { throw "L'installation de Java a échoué." }
    }
    Write-Step "Java : $jdk"

    # 2. JavaFX
    if (-not (Test-Path (Join-Path $JavaFxLib 'javafx.controls.jar'))) {
        Write-Step "JavaFX $JavaFxVersion introuvable : installation locale (une seule fois, ~40 Mo)"
        Install-Zip $JavaFxUrl $JavaFxDir
        if (-not (Test-Path (Join-Path $JavaFxLib 'javafx.controls.jar'))) { throw "L'installation de JavaFX a échoué." }
    }
    Write-Step "JavaFX : $JavaFxLib"

    # 3. Compilation
    $javac = Join-Path $jdk 'bin\javac.exe'
    $java = Join-Path $jdk 'bin\java.exe'
    $sources = @(Get-ChildItem -Path $Root -Filter '*.java' | ForEach-Object { $_.FullName })
    Write-Step 'Compilation des sources'
    $code = Invoke-Native $javac (@('-encoding', 'UTF-8', '--module-path', $JavaFxLib,
            '--add-modules', 'javafx.controls', '-d', $Root) + $sources)
    if ($code -ne 0) { throw "La compilation a échoué (code $code)." }

    # 4. Lancement
    Write-Step 'Lancement de Gantt Simulator (cette fenêtre reste ouverte tant que le logiciel tourne)'
    $code = Invoke-Native $java @('-Dfile.encoding=UTF-8', '--module-path', $JavaFxLib,
            '--add-modules', 'javafx.controls', '-cp', $Root, 'GanttSimulator')
    if ($code -ne 0) { throw "Gantt Simulator s'est arrêté avec le code $code." }
    exit 0
} catch {
    Write-Host ''
    Write-Host "ERREUR : $($_.Exception.Message)" -ForegroundColor Red
    if (-not $FromLauncher) {
        Write-Host ''
        Read-Host 'Appuyez sur Entrée pour fermer'
    }
    exit 1
}
