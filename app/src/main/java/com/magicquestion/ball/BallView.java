package com.magicquestion.ball;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.Random;

/**
 * Рисует магический шар как 3D-сферу. Окошко с ответом и узор на поверхности
 * проецируются на сферу, поэтому при вращении они уходят за край и возвращаются.
 */
public class BallView extends View {

    public interface Listener {
        void onSpinFinished();
    }

    private static final long SPIN_DURATION_MS = 3000;
    private static final long REVEAL_DURATION_MS = 1500;
    private static final int TURNS = 5;          // сколько оборотов делает шар
    private static final float WINDOW_K = 0.45f; // радиус окошка относительно радиуса шара

    private final Paint ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint windowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glarePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint floorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path triangle = new Path();
    private final Path ballClip = new Path();

    // Узор на поверхности: звёздочки с долготой/широтой (радианы)
    private final float[] starLon = new float[22];
    private final float[] starLat = new float[22];
    private final float[] starSize = new float[22];

    private static final float ZOOM_SCALE = 1.2f; // во сколько раз шар увеличивается с ответом

    // Текущий поворот сферы: ось (единичный вектор) и угол вокруг неё
    private float axisX = 0f, axisY = 1f, axisZ = 0f;
    private float spinAngle = 0f;
    private float zoom = 1f;
    private float windowGrow = 0f;  // 0 — обычное окошко, 1 — окошко размером с шар
    private float windowAlpha = 1f; // непрозрачность круга окошка (0 — растворился)
    private final Random random = new Random();

    private String answer = "?";
    private float answerAlpha = 1f;
    private float answerScale = 1f;
    private boolean animating = false;
    private Listener listener;

    private StaticLayout cachedLayout;
    private String cachedText;
    private float cachedRadius;

    public BallView(Context context) {
        super(context);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(Color.rgb(45, 45, 70));
        starPaint.setColor(Color.rgb(170, 150, 255));
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        textPaint.setShadowLayer(18f, 0f, 0f, Color.rgb(140, 110, 255)); // мягкое свечение

        Random r = new Random(8);
        for (int i = 0; i < starLon.length; i++) {
            starLon[i] = (float) (Math.PI * 0.5 + r.nextFloat() * Math.PI * 1.0); // в основном сзади
            if (i % 3 == 0) starLon[i] = (float) (r.nextFloat() * Math.PI * 2);
            starLat[i] = (float) ((r.nextFloat() - 0.5) * Math.PI * 0.85);
            starSize[i] = 0.02f + r.nextFloat() * 0.03f;
            // не ставим звёзды поверх окошка
            if (Math.abs(starLat[i]) < 0.6 && Math.cos(starLon[i]) > 0.75) {
                starLon[i] += Math.PI;
            }
        }
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public boolean isAnimating() {
        return animating;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float r = radius();
        float cx = w / 2f, cy = h / 2f;
        ballPaint.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.45f,
                new int[]{Color.rgb(85, 82, 110), Color.rgb(22, 20, 32), Color.BLACK},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        // затемнение у края — объём поверх подвижных деталей
        shadePaint.setShader(new RadialGradient(cx - r * 0.25f, cy - r * 0.3f, r * 1.35f,
                new int[]{Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(200, 0, 0, 0)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        glarePaint.setShader(new RadialGradient(cx - r * 0.38f, cy - r * 0.48f, r * 0.4f,
                new int[]{Color.argb(140, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                null, Shader.TileMode.CLAMP));
        floorPaint.setShader(new RadialGradient(0, 0, 1f,
                new int[]{Color.argb(150, 80, 60, 200), Color.argb(0, 80, 60, 200)},
                null, Shader.TileMode.CLAMP));
        ringPaint.setStrokeWidth(r * 0.03f);
        ballClip.reset();
        ballClip.addCircle(cx, cy, r, Path.Direction.CW);
    }

    private float radius() {
        return Math.min(getWidth(), getHeight()) * 0.40f;
    }

    /**
     * Поворачивает точку сферы (долгота, широта) вокруг текущей оси на spinAngle
     * (формула Родрига) и возвращает {x, y, z}; z > 0 — к зрителю.
     */
    private void project(float lon, float lat, float[] out) {
        double x = Math.cos(lat) * Math.sin(lon);
        double y = Math.sin(lat);
        double z = Math.cos(lat) * Math.cos(lon);
        double c = Math.cos(spinAngle), s = Math.sin(spinAngle);
        double dot = axisX * x + axisY * y + axisZ * z;
        double cx = axisY * z - axisZ * y;
        double cy = axisZ * x - axisX * z;
        double cz = axisX * y - axisY * x;
        out[0] = (float) (x * c + cx * s + axisX * dot * (1 - c));
        out[1] = (float) (y * c + cy * s + axisY * dot * (1 - c));
        out[2] = (float) (z * c + cz * s + axisZ * dot * (1 - c));
    }

    /** Случайная ось вращения: в основном в плоскости экрана, чтобы окошко уходило за край. */
    private void pickRandomAxis() {
        double dir = random.nextDouble() * Math.PI * 2;
        double depth = (random.nextDouble() - 0.5) * 0.8; // немного «подкрутки» к зрителю
        double x = Math.cos(dir), y = Math.sin(dir), z = depth;
        double len = Math.sqrt(x * x + y * y + z * z);
        axisX = (float) (x / len);
        axisY = (float) (y / len);
        axisZ = (float) (z / len);
    }

    private final float[] p = new float[3];

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = radius();

        // свечение-тень под шаром
        canvas.save();
        canvas.translate(cx, cy + r * 1.08f);
        canvas.scale(r * 0.8f, r * 0.14f);
        canvas.drawCircle(0, 0, 1f, floorPaint);
        canvas.restore();

        canvas.drawCircle(cx, cy, r, ballPaint);

        canvas.save();
        canvas.clipPath(ballClip);

        // звёздочки на поверхности
        for (int i = 0; i < starLon.length; i++) {
            project(starLon[i], starLat[i], p);
            if (p[2] <= 0) continue;
            float sx = cx + p[0] * r;
            float sy = cy - p[1] * r;
            starPaint.setAlpha((int) (200 * p[2]));
            drawOnSphere(canvas, sx, sy, p, starSize[i] * r, false);
        }

        // окошко с ответом
        project(0, 0, p);
        if (p[2] > 0) {
            float k = WINDOW_K + (1f - WINDOW_K) * windowGrow;
            float d = r * (float) Math.sqrt(Math.max(0f, 1 - k * k));
            float sx = cx + p[0] * d;
            float sy = cy - p[1] * d;
            drawOnSphere(canvas, sx, sy, p, r * k, true);
        }

        canvas.drawCircle(cx, cy, r, shadePaint);
        canvas.restore();

        canvas.drawCircle(cx - r * 0.38f, cy - r * 0.48f, r * 0.4f, glarePaint);
    }

    /** Рисует плоский элемент, «приклеенный» к сфере: сжимаем его по направлению к краю. */
    private void drawOnSphere(Canvas canvas, float sx, float sy, float[] n, float size, boolean window) {
        double angle = Math.toDegrees(Math.atan2(-n[1], n[0]));
        canvas.save();
        canvas.translate(sx, sy);
        canvas.rotate((float) angle);
        canvas.scale(Math.max(n[2], 0.001f), 1f);
        canvas.rotate((float) -angle);
        if (window) {
            drawWindow(canvas, size, n[2]);
        } else {
            drawStar(canvas, size);
        }
        canvas.restore();
    }

    private void drawStar(Canvas canvas, float s) {
        triangle.reset();
        triangle.moveTo(0, -s);
        triangle.quadTo(0, 0, s, 0);
        triangle.quadTo(0, 0, 0, s);
        triangle.quadTo(0, 0, -s, 0);
        triangle.quadTo(0, 0, 0, -s);
        canvas.drawPath(triangle, starPaint);
    }

    private void drawWindow(Canvas canvas, float wr, float facing) {
        int wa = (int) (255 * windowAlpha);
        if (wa > 0) {
            windowPaint.setShader(new RadialGradient(0, 0, wr,
                    new int[]{Color.rgb(28, 28, 80), Color.rgb(5, 5, 22)}, null, Shader.TileMode.CLAMP));
            windowPaint.setAlpha(wa);
            ringPaint.setAlpha(wa);
            canvas.drawCircle(0, 0, wr, windowPaint);
            canvas.drawCircle(0, 0, wr, ringPaint);
        }

        int alpha = (int) (255 * answerAlpha * Math.min(1f, facing * 1.5f));
        if (alpha <= 0) return;

        // Текст свёрстан под полный радиус шара и масштабируется вместе с окошком
        float r = radius();
        StaticLayout layout = layoutFor(r);
        float scale = wr / r * answerScale;
        canvas.save();
        canvas.scale(scale, scale);
        textPaint.setAlpha(alpha);
        canvas.translate(-layout.getWidth() / 2f, -layout.getHeight() / 2f);
        layout.draw(canvas);
        canvas.restore();
    }

    private StaticLayout layoutFor(float r) {
        String text = answer.toUpperCase();
        if (cachedLayout != null && text.equals(cachedText) && cachedRadius == r) {
            return cachedLayout;
        }
        int width = (int) (r * 1.45f);
        float size = r * 0.36f;
        StaticLayout layout;
        while (true) {
            textPaint.setTextSize(size);
            layout = makeLayout(text, width);
            if ((layout.getHeight() <= r * 1.1f && fitsWords(text, width)) || size < 8) break;
            size *= 0.92f;
        }
        cachedLayout = layout;
        cachedText = text;
        cachedRadius = r;
        return layout;
    }

    private boolean fitsWords(String text, int width) {
        for (String word : text.split(" ")) {
            if (textPaint.measureText(word) > width) return false;
        }
        return true;
    }

    @SuppressWarnings("deprecation")
    private StaticLayout makeLayout(String text, int width) {
        return new StaticLayout(text, textPaint, width, Layout.Alignment.ALIGN_CENTER,
                0.95f, 0f, false);
    }

    /** Крутит шар 3 секунды, затем проявляет новый ответ. */
    public void showAnswer(final String newAnswer) {
        animating = true;
        pickRandomAxis();
        final float startZoom = zoom;
        final float startGrow = windowGrow;
        final float startWindowAlpha = windowAlpha;

        // ответ быстро гаснет в начале вращения
        ValueAnimator spin = ValueAnimator.ofFloat(0f, 1f);
        spin.setDuration(SPIN_DURATION_MS);
        spin.setInterpolator(new DecelerateInterpolator(1.8f));
        final float startAlpha = answerAlpha;
        spin.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float t = (float) a.getAnimatedValue();
                float time = a.getCurrentPlayTime() / (float) SPIN_DURATION_MS;
                spinAngle = (float) (t * TURNS * 2 * Math.PI);
                answerAlpha = Math.max(0f, startAlpha * (1 - time * 6));
                // в начале вращения шар возвращается к обычному размеру
                float k = Math.min(1f, time * 5);
                zoom = startZoom + (1f - startZoom) * k;
                // окошко снова сжимается и проявляется
                windowGrow = startGrow * (1f - k);
                windowAlpha = startWindowAlpha + (1f - startWindowAlpha) * k;
                float bounce = 1f + 0.04f * (float) Math.sin(time * Math.PI * 6) * (1 - t);
                setScaleX(zoom * bounce);
                setScaleY(zoom * bounce);
                invalidate();
            }
        });
        spin.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                spinAngle = 0f;
                zoom = 1f;
                setScaleX(1f);
                setScaleY(1f);
                windowGrow = 0f;
                windowAlpha = 1f;
                answer = newAnswer;
                if (listener != null) listener.onSpinFinished();
            }
        });

        // Окошко разъезжается до размеров шара, круг растворяется, остаётся фраза
        ValueAnimator reveal = ValueAnimator.ofFloat(0f, 1f);
        reveal.setDuration(REVEAL_DURATION_MS);
        reveal.setInterpolator(new LinearInterpolator());
        final DecelerateInterpolator ease = new DecelerateInterpolator(2f);
        final OvershootInterpolator overshoot = new OvershootInterpolator(1.5f);
        reveal.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float u = (float) a.getAnimatedValue();
                float grow = ease.getInterpolation(Math.min(1f, u / 0.5f));
                windowGrow = grow;
                answerAlpha = Math.min(1f, u / 0.3f);
                answerScale = 1f;
                float dissolve = Math.max(0f, Math.min(1f, (u - 0.4f) / 0.6f));
                windowAlpha = 1f - dissolve * dissolve * (3 - 2 * dissolve);
                // шар увеличивается вместе с появлением ответа
                zoom = 1f + (ZOOM_SCALE - 1f) * overshoot.getInterpolation(Math.min(1f, u / 0.6f));
                setScaleX(zoom);
                setScaleY(zoom);
                invalidate();
            }
        });

        AnimatorSet set = new AnimatorSet();
        set.playSequentially(spin, reveal);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animating = false;
            }
        });
        set.start();
    }
}
