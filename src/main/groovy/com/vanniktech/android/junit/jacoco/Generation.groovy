package com.vanniktech.android.junit.jacoco

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.testing.jacoco.tasks.JacocoReport

class Generation implements Plugin<Project> {
    @Override
    void apply(final Project rootProject) {
        rootProject.extensions.create('junitJacoco', JunitJacocoExtension)

        final def hasSubProjects = rootProject.subprojects.size() > 0

        if (hasSubProjects) {
            rootProject.subprojects { subProject ->
                afterEvaluate {
                    final def extension = rootProject.junitJacoco

                    addJacoco(subProject, extension)
                }
            }
        } else {
            rootProject.afterEvaluate {
                final def extension = rootProject.junitJacoco

                addJacoco(rootProject, extension)
            }
        }
    }

    protected static boolean addJacoco(final Project subProject, final JunitJacocoExtension extension) {
        if (!shouldIgnore(subProject, extension)) {
            if (isAndroidProject(subProject)) {
                addJacocoAndroid(subProject, extension)
                return true
            } else if (isJavaProject(subProject)) {
                addJacocoJava(subProject, extension)
                return true
            }
        }

        return false
    }

    private static void addJacocoJava(final Project subProject, final extension) {
        subProject.plugins.apply('jacoco')

        subProject.jacoco {
            toolVersion extension.jacocoVersion
        }

        subProject.jacocoTestReport {
            dependsOn 'test'

            group = 'Reporting'
            description = 'Generate Jacoco coverage reports.'

            reports {
                xml.enabled = true
                html.enabled = true
            }

            classDirectories = subProject.fileTree(
                    dir: 'build/classes/main/',
                    excludes: getExcludes()
            )

            final def coverageSourceDirs = [
                    'src/main/java',
            ]

            additionalSourceDirs = subProject.files(coverageSourceDirs)
            sourceDirectories = subProject.files(coverageSourceDirs)
            executionData = subProject.files("${subProject.buildDir}/jacoco/test.exec")
        }

        subProject.check.dependsOn 'jacocoTestReport'
    }

    private static void addJacocoAndroid(final Project subProject, final extension) {
        subProject.plugins.apply('jacoco')

        subProject.jacoco {
            toolVersion extension.jacocoVersion
        }

        final def buildTypes = subProject.android.buildTypes.collect { type -> type.name }

        buildTypes.each { buildTypeName ->
            final def taskName = "jacocoTestReport${buildTypeName.capitalize()}"
            final def testTaskName = "test${buildTypeName.capitalize()}UnitTest"

            subProject.task(taskName, type: JacocoReport, dependsOn: testTaskName) {
                group = 'Reporting'
                description = "Generate Jacoco coverage reports after running ${buildTypeName} tests."

                reports {
                    xml {
                        enabled = true
                        destination "${subProject.buildDir}/reports/jacoco/${buildTypeName}/jacoco.xml"
                    }
                    html {
                        enabled = true
                        destination "${subProject.buildDir}/reports/jacoco/${buildTypeName}"
                    }
                }

                classDirectories = subProject.fileTree(
                        dir: "build/intermediates/classes/${buildTypeName}",
                        excludes: getExcludes()
                )

                final def coverageSourceDirs = [
                        'src/main/java',
                        "src/$buildTypeName/java"
                ]

                additionalSourceDirs = subProject.files(coverageSourceDirs)
                sourceDirectories = subProject.files(coverageSourceDirs)
                executionData = subProject.files("${subProject.buildDir}/jacoco/${testTaskName}.exec")
            }

            subProject.check.dependsOn "${taskName}"
        }
    }

    private static ArrayList<String> getExcludes() {
        ['**/R.class',
         '**/R$*.class',
         '**/*$$*',
         '**/*$ViewInjector*.*',
         '**/*$ViewBinder*.*',
         '**/BuildConfig.*',
         '**/Manifest*.*',
         '**/*$Lambda$*.*', // Jacoco can not handle several "$" in class name.
         '**/*Dagger*.*', // Dagger auto-generated code.
         '**/*MembersInjector*.*', // Dagger auto-generated code.
         '**/*_Provide*Factory*.*' // Dagger auto-generated code.
        ]
    }

    protected static boolean isAndroidProject(final Project project) {
        final boolean isAndroidLibrary = project.plugins.hasPlugin('com.android.library')
        final boolean isAndroidApp = project.plugins.hasPlugin('com.android.application')
        return isAndroidLibrary || isAndroidApp
    }

    protected static boolean isJavaProject(final Project project) {
        return project.plugins.hasPlugin('org.gradle.java')
    }

    private static boolean shouldIgnore(final Project project, final JunitJacocoExtension extension) {
        return extension.ignoreProjects?.contains(project.name)
    }
}
