# Sherlock Bot — R8 / ProGuard

-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# EncryptedFile / Tink
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }
