pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "lucky-draw"

include(
    "common-events",
    "order-service",
    "lucky-draw-write",
    "lucky-draw-relay",
    "lucky-draw-scheduler",
    "analytics-service",
    "notification-service",
    "reward-service",
)

