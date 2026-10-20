# Snishaper Android proguard rules.
#
# Stage 2 keeps minify disabled; this file only documents the rules that will be
# needed once R8 is enabled for release builds:
#  - gomobile Java surface (com.snishaper.core.mobile.App / Listener) and go.Seq
#  - kotlinx.serialization generated serializers for the bridge JSON DTOs
#  - DataStore / Navigation reflection-free, no extra rules needed
#
# -keep class com.snishaper.core.mobile.** { *; }
# -keep class go.** { *; }
# -keep class kotlinx.serialization.** { *; }
# -keepclassmembers class **$$serializer { *; }
