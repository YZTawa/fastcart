# FastCart (Fabric 1.21.1)

Features:
- Hold **W** to drive minecart at the selected gear speed.
- **PgUp** / **PgDn** to change gear (1~10).
- Works on any rail type (normal / powered / detector / activator).
- On joining a world: chat message "感谢您使用高速矿车模组\n作者:Ye-Nolta".

## Build
- Requires **JDK 21**
- If you don't have Gradle wrapper, generate it:
  - `gradle wrapper`
- Then build:
  - `./gradlew build` (Windows: `gradlew.bat build`)


## GitHub Actions
This repo includes a workflow that generates the Gradle wrapper on CI, then builds.
