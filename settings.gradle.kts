pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GitaKraft"
include(":app")

// Kraft Foundation — the shared design system, the core utilities, and the standard that
// says how both are to be used. Local composite build, no publishing.
// See github.com/kedharsairam/kraft-foundation.
//
// The first of the nine apps to move. Spacing, type, radius, motion and touch targets now
// come from here; the accent stays local, because an app's accent is its own.
includeBuild("../kraft-foundation") {
    dependencySubstitution {
        substitute(module("com.kraft:kraft-ui")).using(project(":kraft-ui"))
        substitute(module("com.kraft:kraft-core")).using(project(":kraft-core"))
    }
}
