//
// NOTE: At the moment this buildscript is for development building only.
// To create a production installer, run the python buildscript using build.sh or build.bat
//

plugins {
    java
}

java{
    sourceCompatibility = org.gradle.api.JavaVersion.VERSION_1_8
}

group = "org.vivecraft"
version = "1.0-SNAPSHOT"

val mcVersion = "1.16.5"
val os = "windows"



repositories {
    mavenCentral()
    maven { url = uri( "https://jitpack.io") }
}

sourceSets.main{
    java {
        outputDir = file("mcp940/bin")
        srcDirs("mcp940/src/minecraft")
    }

    resources{
        srcDir("mcp940/src/resources")
    }
}

tasks.register("testing"){
    println("Hello World")
}

task("run", type = JavaExec::class){
    workingDir("mcp940/jars")
    classpath = sourceSets["main"].runtimeClasspath
    main = "Start"

    val natives = listOf(
        projectDir.resolve("lib/$mcVersion/natives/$os")
    )

    jvmArgs("-Djava.library.path=" +
             natives.joinToString(";","\"","\""){
                 it.absolutePath
             }
    )

}

dependencies {
    // local
    implementation( fileTree("lib/$mcVersion"){ include("*.jar") })

    // maven central
    implementation ("org.java-websocket:Java-WebSocket:1.5.1")
    implementation ("com.google.code.gson:gson:2.8.6")

    // jitpack
    implementation ("com.github.bhaptics:tact-java:0.1.4")
    implementation ("com.github.feduni:caliko:9953ac3")

    // compile
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}


