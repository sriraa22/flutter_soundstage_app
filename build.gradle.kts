plugins {
    id("com.android.application") version "9.1.0" apply false
    // AGP 9 compiles Kotlin itself; declaring KGP here only picks the Kotlin version it uses,
    // which must match the Compose compiler plugin below
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
}
