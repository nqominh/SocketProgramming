plugins {
    application
}

dependencies {
    implementation(project(":protocol"))
    testImplementation(project(":server"))
}

application {
    mainClass.set("ftp.client.Cli")
}

tasks.jar {
    archiveFileName.set("client-cli.jar")
    manifest {
        attributes("Main-Class" to application.mainClass.get())
    }
}
