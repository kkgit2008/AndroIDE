
# HOW TO BUILD


* Fork this repo.

* Run action 'Build APK' ~~(only on 'main' branch)~~

* If you want, you can edit app version code from 
<a href="./composite-builds/build-logic/common/src/main/java/com/itsaky/androidide/build/config/ProjectConfig.kt"> here </a> at line 118

* Or, to build all architectures from 
<a href="./composite-builds/build-logic/plugins/src/main/java/com/itsaky/androidide/plugins/conf/AndroidModuleConf.kt"> here </a> at line 48

> ~~BTW, app version name depends on the latest tag ('pre-release' tag is not included)~~


<p> </p>

> check all [SNAPSHOTS](https://s01.oss.sonatype.org/content/repositories/snapshots/com/itsaky/androidide/gradle-plugin/maven-metadata.xml)

<p> </p>

> check all [ide-gradle-plugin](https://repo.maven.apache.org/maven2/com/itsaky/androidide/gradle-plugin/maven-metadata.xml)


<p> </p>

# CHANGELOG

- [All changelogs](./RELEASE_INFO)


<p> </p>

# Download JDK & SDK & NDK from here:
- [JDK](https://github.com/kkgit2008/AndroIDE/releases/tag/JDK)
- [SDK](https://github.com/kkgit2008/AndroIDE/releases/tag/SDK)
- [NDK](https://github.com/kkgit2008/AndroIDE/releases/tag/NDK)


<p> </p>

# 🔥NOTICE

- Version of `Android Gradle Plugin` must between  `7.2.0` and `8.10.0`

- `Android API Lever` must be `34` or lower

- If you want to use Android API Lever 35 (or `platforms-android-35`) in your project, click [here](https://github.com/kkgit2008/AndroIDE-SDK-35-DOCS)





<br> </br>
<br> </br>







<p align="center">
  <a href="https://github.com/AndroidIDEOfficial/AndroidIDE"><img src="./images/icon.png" alt="AndroIDE" width="80" height="80"/></a>
</p>

<h2 align="center"><b>AndroIDE</b></h2>
<p align="center">
  An IDE to develop real, Gradle-based Android applications on Android devices.
<p><br>

<p align="center">
<!-- Latest release -->
<img src="https://img.shields.io/github/v/release/kkgit2008/AndroIDE?include_prereleases&amp;label=latest%20release" alt="Latest release">
<!-- Build and test -->
<img src="https://github.com/kkgit2008/AndroIDE/actions/workflows/build.yml/badge.svg" alt="Builds and tests">
<!-- CodeFactor -->
<img src="https://www.codefactor.io/repository/github/androidideofficial/androidide/badge/main" alt="CodeFactor">
<!-- Crowdin -->
<a href="https://crowdin.com/project/androidide"><img src="https://badges.crowdin.net/androidide/localized.svg" alt="Crowdin"></a>
<!-- License -->
<img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License"></p>

<p align="center">
  <a href="https://docs.androidide.com/">Explore the docs »</a> &nbsp; &nbsp;
</p>

<p align="center">
  <a href="https://github.com/kkgit2008/AndroIDE/issues/new?labels=feature&template=FEATURE.yml&title=%5BFeature%5D%3A+">Request a feature</a> &nbsp; &#8226; &nbsp;
</p>



## Features

- [x] Gradle support.
- [x] `JDK 17` and `JDK 21` available for use.
- [x] Terminal with necessary packages.
- [x] Custom environment variables (for Build & Terminal).
- [x] SDK Manager (Available via terminal).
- [x] API information for classes and their members (since, removed, deprecated).
- [x] Log reader (shows your app's logs in real-time)
- [ ] Language servers
    - [x] Java
    - [x] XML
    - [ ] Kotlin
- [ ] UI Designer
    - [x] Layout inflater
    - [x] Resolve resource references
    - [x] Auto-complete resource values when user edits attributes using the attribute editor
    - [x] Drag & Drop
    - [x] Visual attribute editor
    - [x] Android Widgets
- [ ] String Translator
- [ ] Asset Studio (Drawable & Icon Maker)
- [x] Git

## Installation

> _Please install AndroIDE from trusted sources only i.e._
> - [_GitHub Releases_](https://github.com/kkgit2008/AndroIDE/releases)
> - [_Documents_](https://androidide.com)



## Limitations

- For working with projects in AndroIDE, your project must use Android Gradle Plugin `v7.2.0` or
  newer. Projects with older AGP must be migrated to newer versions.
- SDK Manager is already included in Android SDK and is accessible in AndroidIDE via its Terminal.
  But, you cannot use it to install some tools (like `NDK`) because those tools are not built for
  Android.
- You can install `NDK` form here: [NDK](https://github.com/kkgit2008/AndroIDE/releases/tag/NDK) ~~No official NDK support because we haven't built the NDK for Android.~~

The app is still being developed actively. It's in beta stage and may not be stable. if you have any
issues using the app, please let us know.

## Contributing

See the [contributing guide](./CONTRIBUTING.md).

For translations, visit the [Crowdin project page](https://crowdin.com/project/androidide).

## Thanks to

- [Rosemoe](https://github.com/Rosemoe) for the
  awesome [CodeEditor](https://github.com/Rosemoe/sora-editor)
- [Termux](https://github.com/termux) for [Terminal Emulator](https://github.com/termux/termux-app)
- [Bogdan Melnychuk](https://github.com/bmelnychuk)
  for [AndroidTreeView](https://github.com/bmelnychuk/AndroidTreeView)
- [George Fraser](https://github.com/georgewfraser) for
  the [Java Language Server](https://github.com/georgewfraser/java-language-server)

Thanks to all the developers who have contributed to this project.

<p>This project is supported by:</p>
<p>
  <a href="https://m.do.co/c/54add371d1d7">
    <img src="https://opensource.nyc3.cdn.digitaloceanspaces.com/attribution/assets/SVG/DO_Logo_horizontal_blue.svg" width="201px">
  </a>
</p>

## Documents & Help

- [Website](https://m.androidide.com)


## License

```
AndroIDE is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

AndroIDE is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with AndroIDE.  If not, see <https://www.gnu.org/licenses/>.
```

Any violations to the license can be reported either by opening an issue or writing a mail to us
directly.

