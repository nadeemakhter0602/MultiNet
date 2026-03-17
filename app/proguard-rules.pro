-keep class com.multinet.database.** { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
