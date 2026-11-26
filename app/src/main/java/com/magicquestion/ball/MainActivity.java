package com.magicquestion.ball;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity implements SensorEventListener {

    private static final String[] ANSWERS_RU = {
            // Положительные
            "Да",
            "Несомненно",
            "Действуй",
            "Определённо да",
            "Без сомнений",
            "Можешь быть уверен",
            "Мне кажется — да",
            "Вероятнее всего",
            "Знаки говорят — да",
            "Хорошие перспективы",
            "Конечно!",
            "Звёзды за тебя",
            // Нейтральные
            "Спроси позже",
            "Пока не ясно",
            "Лучше не рассказывать",
            "Сейчас нельзя предсказать",
            "Сконцентрируйся и спроси опять",
            "Подожди немного",
            "Всё может быть",
            // Отрицательные
            "Нет",
            "Даже не думай",
            "Мой ответ — нет",
            "По моим данным — нет",
            "Перспективы не очень",
            "Весьма сомнительно",
            "Не сейчас",
            "Лучше откажись",
    };

    private static final String[] ANSWERS_EN = {
            // Positive
            "Yes",
            "Without a doubt",
            "Go for it",
            "Definitely yes",
            "No doubt about it",
            "You can count on it",
            "I think so",
            "Most likely",
            "Signs say yes",
            "Looks good",
            "Of course!",
            "The stars are with you",
            // Neutral
            "Ask later",
            "Not clear yet",
            "Better not tell you now",
            "Can't say right now",
            "Focus and ask again",
            "Wait a little",
            "Anything is possible",
            // Negative
            "No",
            "Don't even think about it",
            "My answer is no",
            "My sources say no",
            "Not looking great",
            "Very doubtful",
            "Not now",
            "Better let it go",
    };

    private static final String LANG_RU = "ru";
    private static final String LANG_EN = "en";
    private static final String PREFS = "settings";
    private static final String KEY_LANG = "lang";
    private static final String KEY_LIGHT = "light_theme";

    // Порог перегрузки (в g), после которого считаем, что телефон встряхнули
    private static final float SHAKE_THRESHOLD_G = 2.2f;
    private static final long SHAKE_COOLDOWN_MS = 1500;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private BallView ballView;
    private FrameLayout root;
    private TextView hint;
    private ImageButton settingsButton;
    private final Random random = new Random();
    private long lastShakeTime = 0;
    private int lastIndex = -1;

    private SharedPreferences prefs;
    private String lang;
    private boolean lightTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String systemLang = Locale.getDefault().getLanguage();
        lang = prefs.getString(KEY_LANG, LANG_RU.equals(systemLang) ? LANG_RU : LANG_EN);
        lightTheme = prefs.getBoolean(KEY_LIGHT, false);

        float density = getResources().getDisplayMetrics().density;
        root = new FrameLayout(this);

        ballView = new BallView(this);
        root.addView(ballView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        hint = new TextView(this);
        hint.setTextSize(16);
        hint.setGravity(Gravity.CENTER);
        int pad = (int) (24 * density);
        hint.setPadding(pad, pad, pad, pad * 2);
        root.addView(hint, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        // Кнопка настроек в правом верхнем углу
        settingsButton = new ImageButton(this);
        settingsButton.setImageResource(R.drawable.ic_settings);
        settingsButton.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        int btn = (int) (48 * density);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(btn, btn, Gravity.TOP | Gravity.END);
        blp.setMargins(0, (int) (12 * density), (int) (12 * density), 0);
        root.addView(settingsButton, blp);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettings();
            }
        });

        ballView.setListener(new BallView.Listener() {
            @Override
            public void onSpinFinished() {
                vibrate(40);
            }
        });
        ballView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ask();
            }
        });

        setContentView(root);
        applyTheme();
        applyLanguage();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    private boolean isRu() {
        return LANG_RU.equals(lang);
    }

    private void applyLanguage() {
        hint.setText(isRu()
                ? "Задай вопрос и встряхни телефон\n(или коснись шара)"
                : "Ask a question and shake your phone\n(or tap the ball)");
        settingsButton.setContentDescription(isRu() ? "Настройки" : "Settings");
    }

    private void applyTheme() {
        int bg = lightTheme ? Color.rgb(233, 238, 248) : Color.rgb(6, 8, 20);
        root.setBackgroundColor(bg);
        hint.setTextColor(lightTheme ? Color.argb(190, 40, 52, 82) : Color.argb(170, 255, 255, 255));
        settingsButton.setColorFilter(lightTheme ? Color.argb(200, 40, 52, 82) : Color.argb(200, 255, 255, 255));
        int rippleColor = lightTheme ? Color.argb(40, 0, 0, 0) : Color.argb(50, 255, 255, 255);
        settingsButton.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor), null, null));
        ballView.setLightTheme(lightTheme);

        Window w = getWindow();
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = lightTheme ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0;
            if (Build.VERSION.SDK_INT >= 26 && lightTheme) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            w.getDecorView().setSystemUiVisibility(flags);
        }
    }

    /** Окно настроек: язык и тема. Изменения применяются сразу и сохраняются. */
    private void showSettings() {
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (24 * density);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, (int) (8 * density), pad, 0);

        box.addView(sectionLabel(isRu() ? "Язык" : "Language"));
        final RadioGroup langGroup = new RadioGroup(this);
        RadioButton ru = radio("Русский", 1);
        RadioButton en = radio("English", 2);
        langGroup.addView(ru);
        langGroup.addView(en);
        langGroup.check(isRu() ? 1 : 2);
        box.addView(langGroup);

        box.addView(sectionLabel(isRu() ? "Тема" : "Theme"));
        final RadioGroup themeGroup = new RadioGroup(this);
        themeGroup.addView(radio(isRu() ? "Тёмная" : "Dark", 3));
        themeGroup.addView(radio(isRu() ? "Светлая" : "Light", 4));
        themeGroup.check(lightTheme ? 4 : 3);
        box.addView(themeGroup);

        int dialogTheme = lightTheme
                ? android.R.style.Theme_Material_Light_Dialog_Alert
                : android.R.style.Theme_Material_Dialog_Alert;
        final AlertDialog dialog = new AlertDialog.Builder(this, dialogTheme)
                .setTitle(isRu() ? "Настройки" : "Settings")
                .setView(box)
                .setPositiveButton(isRu() ? "Готово" : "Done", null)
                .create();

        langGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                lang = checkedId == 1 ? LANG_RU : LANG_EN;
                prefs.edit().putString(KEY_LANG, lang).apply();
                applyLanguage();
                reopen(dialog);
            }
        });
        themeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                lightTheme = checkedId == 4;
                prefs.edit().putBoolean(KEY_LIGHT, lightTheme).apply();
                applyTheme();
                reopen(dialog);
            }
        });
        dialog.show();
    }

    /** Перерисовывает окно настроек, чтобы подписи и цвета сменились вместе с выбором. */
    private void reopen(final AlertDialog dialog) {
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                showSettings();
            }
        });
        dialog.dismiss();
    }

    private TextView sectionLabel(String text) {
        float density = getResources().getDisplayMetrics().density;
        TextView t = new TextView(this);
        t.setText(text.toUpperCase());
        t.setTextSize(12);
        t.setLetterSpacing(0.08f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(lightTheme ? Color.rgb(70, 100, 190) : Color.rgb(140, 180, 255));
        t.setPadding(0, (int) (16 * density), 0, (int) (4 * density));
        return t;
    }

    private RadioButton radio(String text, int id) {
        RadioButton b = new RadioButton(this);
        b.setId(id);
        b.setText(text);
        b.setTextSize(16);
        b.setTextColor(lightTheme ? Color.rgb(30, 36, 50) : Color.rgb(235, 240, 255));
        b.setButtonTintList(ColorStateList.valueOf(lightTheme ? Color.rgb(70, 100, 190) : Color.rgb(140, 180, 255)));
        int p = (int) (8 * getResources().getDisplayMetrics().density);
        b.setPadding(p, p, p, p);
        return b;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float gx = event.values[0] / SensorManager.GRAVITY_EARTH;
        float gy = event.values[1] / SensorManager.GRAVITY_EARTH;
        float gz = event.values[2] / SensorManager.GRAVITY_EARTH;
        double gForce = Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (gForce > SHAKE_THRESHOLD_G) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime > SHAKE_COOLDOWN_MS) {
                lastShakeTime = now;
                ask();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void ask() {
        if (ballView.isAnimating()) return;
        String[] answers = isRu() ? ANSWERS_RU : ANSWERS_EN;
        int index;
        do {
            index = random.nextInt(answers.length);
        } while (index == lastIndex && answers.length > 1);
        lastIndex = index;
        vibrate(80);
        ballView.showAnswer(answers[index]);
    }

    @SuppressWarnings("deprecation")
    private void vibrate(long ms) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(ms);
        }
    }
}
