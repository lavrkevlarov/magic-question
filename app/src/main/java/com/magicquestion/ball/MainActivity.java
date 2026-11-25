package com.magicquestion.ball;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
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
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Random;

public class MainActivity extends Activity implements SensorEventListener {

    private static final String[] ANSWERS = {
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

    // Порог перегрузки (в g), после которого считаем, что телефон встряхнули
    private static final float SHAKE_THRESHOLD_G = 2.2f;
    private static final long SHAKE_COOLDOWN_MS = 1500;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private BallView ballView;
    private final Random random = new Random();
    private long lastShakeTime = 0;
    private int lastIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(10, 8, 30));

        ballView = new BallView(this);
        root.addView(ballView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView hint = new TextView(this);
        hint.setText("Задай вопрос и встряхни телефон\n(или коснись шара)");
        hint.setTextColor(Color.argb(170, 255, 255, 255));
        hint.setTextSize(16);
        hint.setGravity(Gravity.CENTER);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        hint.setPadding(pad, pad, pad, pad * 2);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(hint, lp);

        TextView title = new TextView(this);
        title.setText("Магический шар");
        title.setTextColor(Color.argb(220, 190, 180, 255));
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        title.setPadding(pad, pad * 2, pad, pad);
        root.addView(title, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));

        ballView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ask();
            }
        });

        setContentView(root);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
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
        int index;
        do {
            index = random.nextInt(ANSWERS.length);
        } while (index == lastIndex && ANSWERS.length > 1);
        lastIndex = index;
        vibrate();
        ballView.showAnswer(ANSWERS[index]);
    }

    @SuppressWarnings("deprecation")
    private void vibrate() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(80);
        }
    }
}
