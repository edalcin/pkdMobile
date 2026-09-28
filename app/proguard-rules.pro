# Regras específicas do pkdMobile entram aqui conforme necessário.
# Regras padrão do Android/Compose já vêm de proguard-android-optimize.txt.

# Editor rico (ADR 0002): o WebView chama o bridge AndroidEditor por nome via reflection
# (window.AndroidEditor.onChange(...) etc.) — R8 não pode renomear/remover esses métodos.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
