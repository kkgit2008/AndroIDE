/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.templates.base.root

import com.itsaky.androidide.templates.base.ProjectTemplateBuilder

internal fun ProjectTemplateBuilder.settingsGradleSrcStr(
    useAliyunMirror: Boolean
): String {

    val pluginRepos = if (useAliyunMirror) """
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    """.trimIndent() else """
        gradlePluginPortal()
        mavenCentral()
    """.trimIndent()

    val depRepos = if (useAliyunMirror) """
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
    """.trimIndent() else """
        mavenCentral()
        google()
    """.trimIndent()

    val includeBlock = modules.joinToString(separator = "\n") { "include(\":${it.name}\")" }

    return """
pluginManagement {
    repositories {
        $pluginRepos
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        $depRepos
    }
}

rootProject.name = "${data.name}"

$includeBlock
    """.trimIndent()
}