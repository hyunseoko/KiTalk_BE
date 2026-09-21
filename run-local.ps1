$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-home'
& (Join-Path $PSScriptRoot 'gradlew.bat') bootRun --args='--spring.profiles.active=local'
