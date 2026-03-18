/**
 * SOVEREIGN VANTAGE V5.5.92 "ARTHUR EDITION"
 * Project Settings
 *
 * © 2025-2026 MiWealth Pty Ltd
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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
    
    // Reduce network retries to avoid CI timeouts
    // Default is 3 retries per dependency - we set to 1
    // This prevents 20+ minute stalls on missing dependencies
    rulesMode.set(RulesMode.FAIL_ON_PROJECT_RULES)
}

rootProject.name = "SovereignVantage"
include(":app")
