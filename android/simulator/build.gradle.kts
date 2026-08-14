plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":domain"))
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass.set("mx.reddeayuda.simulator.MainKt")
}

tasks.test {
    useJUnit()
}
