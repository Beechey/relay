# Named only as a string in the manifest's Cast meta-data, so R8 can't see it's used
-keep class com.avetero.relay.player.CastOptions { <init>(); }
# Stack traces in crash reports stay readable
-keepattributes SourceFile,LineNumberTable
