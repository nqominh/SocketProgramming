plugins {
    application
}

dependencies {
    implementation(project(":protocol"))
}

application {
    mainClass.set("ftp.server.ControlChannelServer")
}

tasks.jar {
    archiveFileName.set("server.jar")
    manifest {
        attributes("Main-Class" to application.mainClass.get())
    }
}
