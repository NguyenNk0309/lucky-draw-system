pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "lucky-draw"

include(
    "api-gateway",
    "campaign-service",
    "common-events",
    "order-service",
    "lucky-draw-service",
    "analytics-service",
    "notification-service",
    "reward-service",
)

