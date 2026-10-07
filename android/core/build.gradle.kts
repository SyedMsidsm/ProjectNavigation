plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies { testImplementation(libs.junit) }
sourceSets.test { resources.srcDir("../app/src/main/assets") }
tasks.test { useJUnit() }
