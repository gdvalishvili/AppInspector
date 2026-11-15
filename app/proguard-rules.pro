# Enum names are stored in settings and in the database (e.g. "THIS"/"WORK"): keep them stable
-keepclassmembers enum com.gdvalishvili.appinspector.** { *; }

# Glance creates refresh-button callbacks by class name
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }