-keepattributes Signature

# Gson
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern

# OkHttp
-keep,allowobfuscation,allowshrinking class okhttp3.RequestBody
-keep,allowobfuscation,allowshrinking class okhttp3.ResponseBody

#Retrofit
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# Models
-keep class org.uniapk.upgrader.data.** { *; }

# These types are persisted or decoded with Gson; retain field names across releases.
-keep class org.uniapk.upgrader.glass.RuleSet { *; }
-keep class org.uniapk.upgrader.glass.FamilyRule { *; }
-keep class org.uniapk.upgrader.glass.InstallHistory { *; }
-keep class org.uniapk.upgrader.glass.PendingInstall { *; }
-keep class com.aurora.gplayapi.data.models.** { *; }

# Keep the decoder opaque to constant folding, while allowing its name to be obfuscated.
-keep,allowobfuscation class org.uniapk.upgrader.glass.SupportLink { *; }
