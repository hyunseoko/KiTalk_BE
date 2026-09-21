$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-home'
& (Join-Path $PSScriptRoot 'gradlew.bat') test --no-daemon --max-workers=1
