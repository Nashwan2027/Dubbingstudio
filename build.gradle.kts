// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    // تم اعتماد الإصدارات الأحدث
    id("com.android.application") version "8.9.1" apply false
    id("com.android.library") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    
    // إضافة بلجن Safe Args
    id("androidx.navigation.safeargs.kotlin") version "2.7.7" apply false 
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
