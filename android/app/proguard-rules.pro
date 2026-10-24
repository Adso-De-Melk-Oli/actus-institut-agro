# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class io.github.adsodemelkoli.actus.data.**$$serializer { *; }
-keepclassmembers class io.github.adsodemelkoli.actus.data.** { *** Companion; }
-keepclasseswithmembers class io.github.adsodemelkoli.actus.data.** { kotlinx.serialization.KSerializer serializer(...); }
