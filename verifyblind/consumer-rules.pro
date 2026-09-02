# VerifyBlind SDK — entegre eden uygulamaya OTOMATİK uygulanan R8 kuralları.
#
# Bu dosya AAR'ın içinde taşınır: partner R8'i açtığında Gradle bunları kendi kural
# setine ekler. Partner'ın hiçbir şey yazmasına gerek kalmaz — SDK kendi doğru
# çalışması için gerekeni kendi taşımalıdır.
#
# NEDEN KRİTİK: model sınıflarımız @SerializedName KULLANMIYOR, Gson doğrudan alan
# adlarına dayanıyor. Obfuscation `public_key` alanını `a` yaparsa partner backend'ine
# {"a": "..."} gider. Dahası, bu sınıflara yalnızca Gson yansımayla dokunduğu için R8
# onları "ölü kod" sayıp tamamen siler.
#
# Bu kurallar olmadan ölçülen gerçek sonuç (R8 açık bir tüketici uygulamasında):
#   PopResultResponse      -> sınıf tamamen silindi
#   encrypted_response     -> silindi
#   enc_key                -> silindi
# Yani doğrulama sonucu hiç çözülemez. Sessiz kırılma: derleme başarılı, çalışma
# zamanında sonuç boş döner.
#
# Bu Şubat 2027'ye kadar her partner'ı ilgilendirecek: Play'in DEX code optimization
# gerekliliği R8'i fiilen zorunlu kılıyor.

# ── Gson modelleri: alan adları JSON sözleşmesidir, aynen korunmalı ──
-keep class com.verifyblind.sdk.model.** { *; }

# ── Gson'ın kendisi HİÇ consumer kuralı taşımıyor (jar'ı açıp doğrulandı) ──
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*, RuntimeVisibleAnnotations
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn sun.misc.Unsafe

# ── Retrofit + R8 full mode ──
# SDK Retrofit 2.11 ile derleniyor ve 2.11 bu kuralları kendi taşıyor. Yine de burada
# tekrarlanıyor: partner'ın bağımlılık çözümlemesi Retrofit'i 2.10 ALTINA düşürürse
# (başka bir kütüphane eski sürüm dayatabilir) kurallar kaybolur ve her SDK çağrısı
# ClassCastException ile patlar — ana uygulamada 2.9.0 ile üretimde yaşandı.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
