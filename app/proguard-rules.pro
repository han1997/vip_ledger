## VipLedger ProGuard 规则（M0 占位，随里程碑补充）
# 保留 Room 生成的实现（M1 起生效）
-keep class * extends androidx.room.RoomDatabase
# 保留 kotlinx.serialization 生成的序列化器（M5/M6 起生效）
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
