# ProGuard / R8 rules for OpenMpesaTracker
# Keep Room database annotations and entity schemas
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
