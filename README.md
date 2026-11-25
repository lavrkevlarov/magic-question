# Магический шар

Android-приложение: задай вопрос, встряхни телефон (или коснись шара), и шар даст ответ:
«Да», «Нет», «Спроси позже», «Действуй», «Несомненно» и ещё два десятка фраз.

## Установка

Готовый APK лежит в [`apk/MagicBall.apk`](apk/MagicBall.apk). Скачай его на телефон и
открой. Android попросит разрешить установку из неизвестных источников.
Работает на Android 5.0 и новее.

## Сборка

Gradle не нужен. Хватит JDK и инструментов из apt (Ubuntu/Debian):

```bash
sudo apt-get install aapt dalvik-exchange zipalign apksigner
./build.sh            # результат: build/MagicBall.apk
```

`android.jar` (API 30) скрипт скачивает сам. Другой путь можно задать через `ANDROID_JAR`.

Фразы задаются в массиве `ANSWERS` в
`app/src/main/java/com/magicquestion/ball/MainActivity.java`.
