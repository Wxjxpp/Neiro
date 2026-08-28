# Project uses runtime-loaded LX/QuickJS scripts and JSON-based model bridges.
# Keep app entry points and public model fields used by reflection/serialization.
# Keep only app components and types accessed by Room/JSON/reflection.
-keep class com.wxjxpp.neiro.app.MusicPlayerApp { *; }
-keep class com.wxjxpp.neiro.core.userapi.** { *; }
-keep class com.wxjxpp.neiro.core.source.** { *; }
-keep class com.wxjxpp.neiro.core.model.** { *; }
-keep class com.wxjxpp.neiro.core.db.** { *; }
-keep class com.mocharealm.accompanist.lyrics.core.model.** { *; }
-keep class com.mocharealm.accompanist.lyrics.core.parser.** { *; }
-keep class androidx.media3.** { *; }
-keep class androidx.room.** { *; }
