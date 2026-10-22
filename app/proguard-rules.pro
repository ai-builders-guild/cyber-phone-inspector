# kotlinx.serialization: keep generated serializers and the @Serializable classes' companions.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.cyberrick.phoneinspector.**$$serializer { *; }
-keepclassmembers class com.cyberrick.phoneinspector.** { *** Companion; }
-keepclasseswithmembers class com.cyberrick.phoneinspector.** { kotlinx.serialization.KSerializer serializer(...); }
