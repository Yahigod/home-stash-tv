plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val developmentKeystoreFile = providers.environmentVariable("HOME_STASH_TV_KEYSTORE_FILE").orNull
val developmentKeystorePassword =
    providers.environmentVariable("HOME_STASH_TV_KEYSTORE_PASSWORD").orNull
val developmentKeyAlias = providers.environmentVariable("HOME_STASH_TV_KEY_ALIAS").orNull
val developmentKeyPassword = providers.environmentVariable("HOME_STASH_TV_KEY_PASSWORD").orNull
val developmentSigningValues = listOf(
    developmentKeystoreFile,
    developmentKeystorePassword,
    developmentKeyAlias,
    developmentKeyPassword,
)
val developmentSigningConfigured = developmentSigningValues.all { !it.isNullOrBlank() }
if (developmentSigningValues.any { !it.isNullOrBlank() } && !developmentSigningConfigured) {
    throw GradleException("Development signing configuration is incomplete.")
}

val productionKeystoreFile =
    providers.environmentVariable("HOME_STASH_TV_RELEASE_KEYSTORE_FILE").orNull
val productionKeystorePassword =
    providers.environmentVariable("HOME_STASH_TV_RELEASE_KEYSTORE_PASSWORD").orNull
val productionKeyAlias =
    providers.environmentVariable("HOME_STASH_TV_RELEASE_KEY_ALIAS").orNull
val productionKeyPassword =
    providers.environmentVariable("HOME_STASH_TV_RELEASE_KEY_PASSWORD").orNull
val productionSigningValues = listOf(
    productionKeystoreFile,
    productionKeystorePassword,
    productionKeyAlias,
    productionKeyPassword,
)
val productionSigningConfigured = productionSigningValues.all { !it.isNullOrBlank() }
if (productionSigningValues.any { !it.isNullOrBlank() } && !productionSigningConfigured) {
    throw GradleException("Production signing configuration is incomplete.")
}
if (
    developmentSigningConfigured &&
    productionSigningConfigured &&
    file(developmentKeystoreFile!!).canonicalFile == file(productionKeystoreFile!!).canonicalFile
) {
    throw GradleException("Development and production signing must use different keystores.")
}

val releaseVersionCode = providers.gradleProperty("homeStashTvVersionCode").get().toIntOrNull()
    ?: throw GradleException("homeStashTvVersionCode must be an integer.")
val releaseVersionName = providers.gradleProperty("homeStashTvVersionName").get().trim()
if (releaseVersionCode < 1) {
    throw GradleException("homeStashTvVersionCode must be positive.")
}
if (!releaseVersionName.matches(
        Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?"),
    )
) {
    throw GradleException("homeStashTvVersionName must be a semantic version.")
}

android {
    namespace = "com.yahigod.homestashtv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yahigod.homestashtv"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (developmentSigningConfigured) {
            create("development") {
                storeFile = file(developmentKeystoreFile!!)
                storePassword = developmentKeystorePassword!!
                keyAlias = developmentKeyAlias!!
                keyPassword = developmentKeyPassword!!
            }
        }
        if (productionSigningConfigured) {
            create("production") {
                storeFile = file(productionKeystoreFile!!)
                storePassword = productionKeystorePassword!!
                keyAlias = productionKeyAlias!!
                keyPassword = productionKeyPassword!!
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            if (developmentSigningConfigured) {
                signingConfig = signingConfigs.getByName("development")
            }
        }

        release {
            isMinifyEnabled = false
            if (productionSigningConfigured) {
                signingConfig = signingConfigs.getByName("production")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(libs.json)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
