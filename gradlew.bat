@echo off
REM Windows helper. For GitHub Actions this project uses ./gradlew on Ubuntu.
REM On Windows, install Gradle 8.1.1 or open the project in Android Studio.
gradle %*
