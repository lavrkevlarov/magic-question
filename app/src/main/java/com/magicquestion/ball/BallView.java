package com.magicquestion.ball;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.Random;

/**
 * Магический шар как 3D-сфера. Окошко с ответом и звёзды
 * проецируются на поверхность через касательный базис, поэтому честно поворачиваются
 * и сжимаются у края при вращении вокруг любой оси. Сцена живёт непрерывно: шар
 * парит, слегка покачивается, ореол дышит, на фоне плывут огоньки, при быстром
 * вращении детали оставляют размытый след.
 */
public class BallView extends View {

    public interface Listener {
        void onSpinFinished();
    }

    private static final long SPIN_DURATION_MS = 3000;
    private static final long REVEAL_DURATION_MS = 1500;
    private static final int TURNS = 5;            // сколько оборотов делает шар
    private static final float WINDOW_K = 0.45f;   // радиус окошка относительно радиуса шара
    private static final float ZOOM_SCALE = 1.2f;  // во сколько раз шар увеличивается с ответом
    private static final int GHOSTS = 5;           // кадров в следе размытия
    private static final int LUT_SIZE = 512;

    private final Paint ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint windowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bouncePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crescentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bokehPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path crescent = new Path();
    private final Path ballClip = new Path();
    private final Matrix surface = new Matrix();
    private final Matrix textMatrix = new Matrix();
    private final float[] matrixValues = new float[9];

    // Звёздочки на поверхности (долгота/широта в радианах)
    private final float[] starLon = new float[22];
    private final float[] starLat = new float[22];
    private final float[] starSize = new float[22];
    private final float[] starPhase = new float[22];

    // Огоньки на фоне
    private static final int PARTICLES = 26;
    private final float[] partX = new float[PARTICLES];
    private final float[] partY = new float[PARTICLES];
    private final float[] partSize = new float[PARTICLES];
    private final float[] partSpeed = new float[PARTICLES];
    private final float[] partPhase = new float[PARTICLES];
    private final float[] partAlpha = new float[PARTICLES];

    private final float[] spinLut = new float[LUT_SIZE + 1];
    private final Random random = new Random();

    // Поворот: сначала лёгкое покачивание в покое, затем вращение вокруг случайной оси
    private float axisX = 0f, axisY = 1f, axisZ = 0f;
    private float spinAngle = 0f;
    private float spinVelocity = 0f;   // рад/с, для размытия
    private float idleAxisX = 1f, idleAxisY = 0f, idleAngle = 0f;

    private float zoom = 1f;
    private float bounce = 1f;
    private float windowGrow = 0f;  // 0 — обычное окошко, 1 — окошко размером с шар
    private float windowAlpha = 1f; // непрозрачность круга окошка (0 — растворился)
    private float reveal = 0f;      // насколько показан ответ 0..1 (усиливает ореол)
    private float highlight = 1f;   // яркость белого блика 0..1
    private boolean lightTheme = false;

    private String answer = "?";
    private float answerAlpha = 1f;
    private float pendingTextAlpha = 0f;
    private float pendingTextScale = 1f;
    private boolean animating = false;
    private Listener listener;

    private long lastFrameMs = 0;
    private float time = 0f;
    private boolean running = true;

    private StaticLayout cachedLayout;
    private String cachedText;
    private float cachedRadius;

    // рабочие векторы касательного базиса: нормаль, «восток», «север»
    private final float[] vn = new float[3];
    private final float[] ve = new float[3];
    private final float[] vu = new float[3];

    public BallView(Context context) {
        super(context);
        textPaint.setColor(Color.rgb(232, 240, 255));
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        starPaint.setColor(Color.rgb(170, 190, 255));

        Random r = new Random(8);
        for (int i = 0; i < starLon.length; i++) {
            starLon[i] = (float) (r.nextFloat() * Math.PI * 2);
            starLat[i] = (float) ((r.nextFloat() - 0.5) * Math.PI * 0.85);
            starSize[i] = 0.015f + r.nextFloat() * 0.022f;
            starPhase[i] = r.nextFloat() * 6.28f;
            // не ставим звёзды поверх окошка
            double c = Math.cos(starLon[i]);
            if (Math.abs(starLat[i]) < 0.65 && c > 0.7) {
                starLon[i] += (float) (Math.PI / 2);
            }
        }
        for (int i = 0; i < PARTICLES; i++) {
            partX[i] = r.nextFloat();
            partY[i] = r.nextFloat();
            partSize[i] = 0.006f + r.nextFloat() * 0.022f;
            partSpeed[i] = 0.006f + r.nextFloat() * 0.018f;
            partPhase[i] = r.nextFloat() * 6.28f;
            partAlpha[i] = 0.3f + r.nextFloat() * 0.7f;
        }

        // Кривая вращения: плавный разгон за первые ~8% и долгое торможение
        double acc = 0;
        spinLut[0] = 0;
        for (int i = 1; i <= LUT_SIZE; i++) {
            double t = (i - 0.5) / LUT_SIZE;
            acc += smoothstep((float) (t / 0.08)) * Math.pow(1 - t, 1.7);
            spinLut[i] = (float) acc;
        }
        for (int i = 0; i <= LUT_SIZE; i++) spinLut[i] /= (float) acc;

        updateBokehShader();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public boolean isAnimating() {
        return animating;
    }

    /** Светлая тема: ореол слабее, под шаром мягкая тень, огоньки темнее. */
    public void setLightTheme(boolean light) {
        lightTheme = light;
        updateBokehShader();
        invalidate();
    }

    private void updateBokehShader() {
        int c = lightTheme ? Color.rgb(80, 110, 200) : Color.rgb(140, 175, 255);
        bokehPaint.setShader(new RadialGradient(0, 0, 1f,
                new int[]{c, Color.argb(0, Color.red(c), Color.green(c), Color.blue(c))},
                null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        running = visibility == VISIBLE;
        lastFrameMs = 0;
        if (running) postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float r = radius();
        // Все шейдеры шара — в локальных координатах с центром шара в (0, 0)
        ballPaint.setShader(new RadialGradient(0, -r * 0.6f, r * 1.6f,
                new int[]{Color.rgb(52, 64, 95), Color.rgb(13, 18, 34), Color.BLACK},
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        shadePaint.setShader(new RadialGradient(-r * 0.25f, -r * 0.3f, r * 1.35f,
                new int[]{Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(200, 0, 0, 0)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        auraPaint.setShader(new RadialGradient(0, 0, r * 1.3f,
                new int[]{Color.rgb(90, 130, 230), Color.rgb(90, 130, 230), Color.argb(0, 90, 130, 230)},
                new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        bouncePaint.setShader(new LinearGradient(0, r * 0.55f, 0, r,
                Color.argb(0, 90, 130, 230), Color.argb(77, 90, 130, 230), Shader.TileMode.CLAMP));
        crescentPaint.setShader(new LinearGradient(0, -r, 0, -r * 0.35f,
                Color.argb(191, 215, 230, 255), Color.argb(0, 215, 230, 255), Shader.TileMode.CLAMP));
        crescent.reset();
        crescent.setFillType(Path.FillType.EVEN_ODD);
        crescent.addCircle(0, 0, r, Path.Direction.CW);
        crescent.addCircle(0, r * 0.085f, r * 0.995f, Path.Direction.CW);
        specPaint.setShader(new RadialGradient(0, 0, r * 0.18f,
                Color.argb(230, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP));
        shadowPaint.setShader(new RadialGradient(0, 0, r * 0.85f,
                Color.argb(70, 20, 30, 60), Color.argb(0, 20, 30, 60), Shader.TileMode.CLAMP));

        textPaint.setShadowLayer(r * 0.12f, 0f, 0f, Color.argb(191, 140, 180, 255));
        ballClip.reset();
        ballClip.addCircle(0, 0, r, Path.Direction.CW);
        cachedLayout = null;
    }

    private float radius() {
        return Math.min(getWidth(), getHeight()) * 0.40f;
    }

    // ---------- 3D ----------

    private static void rotate(float[] v, float ax, float ay, float az, float ang) {
        if (ang == 0f) return;
        double c = Math.cos(ang), s = Math.sin(ang);
        double x = v[0], y = v[1], z = v[2];
        double dot = ax * x + ay * y + az * z;
        double cx = ay * z - az * y;
        double cy = az * x - ax * z;
        double cz = ax * y - ay * x;
        v[0] = (float) (x * c + cx * s + ax * dot * (1 - c));
        v[1] = (float) (y * c + cy * s + ay * dot * (1 - c));
        v[2] = (float) (z * c + cz * s + az * dot * (1 - c));
    }

    private void orient(float[] v, float angle) {
        rotate(v, idleAxisX, idleAxisY, 0f, idleAngle);
        rotate(v, axisX, axisY, axisZ, angle);
    }

    /** Касательный базис точки сферы после поворота на angle. */
    private void basis(float lon, float lat, float angle) {
        double sl = Math.sin(lon), cl = Math.cos(lon), sp = Math.sin(lat), cp = Math.cos(lat);
        vn[0] = (float) (cp * sl); vn[1] = (float) sp; vn[2] = (float) (cp * cl);
        ve[0] = (float) cl; ve[1] = 0f; ve[2] = (float) -sl;
        vu[0] = (float) (-sp * sl); vu[1] = (float) cp; vu[2] = (float) (-sp * cl);
        orient(vn, angle);
        orient(ve, angle);
        orient(vu, angle);
    }

    /** Ортографическая проекция касательной плоскости, смещённой на d вдоль нормали. */
    private void surfaceMatrix(Matrix m, float d) {
        matrixValues[0] = ve[0];
        matrixValues[1] = -vu[0];
        matrixValues[2] = vn[0] * d;
        matrixValues[3] = -ve[1];
        matrixValues[4] = vu[1];
        matrixValues[5] = -vn[1] * d;
        matrixValues[6] = 0f;
        matrixValues[7] = 0f;
        matrixValues[8] = 1f;
        m.setValues(matrixValues);
    }

    private float spinCurve(float t) {
        float x = Math.max(0f, Math.min(1f, t)) * LUT_SIZE;
        int i = (int) x;
        if (i >= LUT_SIZE) return 1f;
        float f = x - i;
        return spinLut[i] + (spinLut[i + 1] - spinLut[i]) * f;
    }

    // ---------- отрисовка ----------

    @Override
    protected void onDraw(Canvas canvas) {
        long now = AnimationUtils.currentAnimationTimeMillis();
        float dt = lastFrameMs == 0 ? 0f : Math.min(0.05f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;
        time += dt;

        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r = radius();

        drawParticles(canvas, dt, w, h);

        // покой: шар парит и чуть покачивается
        float floatY = (float) Math.sin(time * 1.1f) * r * 0.025f;
        idleAngle = 0.09f * (float) Math.sin(time * 0.9f);
        idleAxisX = (float) Math.cos(time * 0.37f);
        idleAxisY = (float) Math.sin(time * 0.37f);
        float scale = zoom * bounce;

        if (lightTheme) {
            float near = 1f + floatY / r * 0.8f;
            canvas.save();
            canvas.translate(cx, cy + r * 1.08f * scale);
            canvas.scale(scale * near, scale * 0.14f * near);
            shadowPaint.setAlpha((int) (255 * Math.min(1f, near)));
            canvas.drawCircle(0, 0, r * 0.85f, shadowPaint);
            canvas.restore();
        }

        canvas.save();
        canvas.translate(cx, cy + floatY);
        canvas.scale(scale, scale);

        float breathe = 0.92f + 0.08f * (float) Math.sin(time * 1.4f);
        float aura = lightTheme ? 0.10f + 0.22f * reveal : 0.22f + 0.15f * reveal;
        auraPaint.setAlpha((int) (255 * Math.min(1f, aura * breathe)));
        canvas.drawCircle(0, 0, r * 1.3f, auraPaint);

        canvas.drawCircle(0, 0, r, ballPaint);

        canvas.save();
        canvas.clipPath(ballClip);

        // след размытия при быстром вращении
        float span = Math.min(0.7f, Math.abs(spinVelocity) / 60f * 2.2f);
        if (span > 0.03f) {
            for (int i = 0; i < GHOSTS; i++) {
                float off = span * (GHOSTS - i) / GHOSTS;
                drawSurface(canvas, r, spinAngle - off, 0.4f * (i + 1) / (GHOSTS + 1), false);
            }
        }
        drawSurface(canvas, r, spinAngle, 1f, true);

        canvas.drawCircle(0, 0, r, shadePaint);
        canvas.drawRect(-r, -r, r, r, bouncePaint);

        // фраза поверх затемнения, чтобы её было хорошо видно
        if (pendingTextAlpha > 0f) {
            canvas.save();
            canvas.concat(textMatrix);
            drawText(canvas, r);
            canvas.restore();
        }

        // белый блик: серп сверху и яркая точка, гаснут при раскрытии слова
        int ha = (int) (255 * highlight);
        if (ha > 2) {
            crescentPaint.setAlpha(ha);
            canvas.drawPath(crescent, crescentPaint);
            specPaint.setAlpha(ha);
            canvas.save();
            canvas.translate(0, -r * 0.8f);
            canvas.scale(1f, 0.35f);
            canvas.drawCircle(0, 0, r * 0.18f, specPaint);
            canvas.restore();
        }
        canvas.restore();
        canvas.restore();

        if (running) postInvalidateOnAnimation();
    }

    private void drawParticles(Canvas canvas, float dt, float w, float h) {
        float maxAlpha = lightTheme ? 70f : 90f;
        float unit = Math.min(w, h);
        for (int i = 0; i < PARTICLES; i++) {
            partY[i] -= partSpeed[i] * dt;
            if (partY[i] < -0.05f) {
                partY[i] = 1.05f;
                partX[i] = random.nextFloat();
            }
            float twinkle = 0.55f + 0.45f * (float) Math.sin(time * (0.8f + partSpeed[i] * 40f) + partPhase[i]);
            bokehPaint.setAlpha((int) (maxAlpha * twinkle * partAlpha[i]));
            float x = partX[i] * w + (float) Math.sin(time * 0.3f + partPhase[i]) * unit * 0.015f;
            canvas.save();
            canvas.translate(x, partY[i] * h);
            float s = partSize[i] * unit;
            canvas.scale(s, s);
            canvas.drawCircle(0, 0, 1f, bokehPaint);
            canvas.restore();
        }
    }

    /** Рисует детали поверхности при повороте angle; alphaMul < 1 — для следа размытия. */
    private void drawSurface(Canvas canvas, float r, float angle, float alphaMul, boolean main) {
        // звёздочки
        for (int i = 0; i < starLon.length; i++) {
            basis(starLon[i], starLat[i], angle);
            if (vn[2] <= 0f) continue;
            float twinkle = 0.65f + 0.35f * (float) Math.sin(time * 2.1f + starPhase[i]);
            starPaint.setAlpha((int) (150 * vn[2] * twinkle * alphaMul));
            surfaceMatrix(surface, r);
            canvas.save();
            canvas.concat(surface);
            canvas.drawCircle(0, 0, starSize[i] * r, starPaint);
            canvas.restore();
        }

        // окошко с ответом
        basis(0f, 0f, angle);
        if (main) pendingTextAlpha = 0f;
        if (vn[2] > 0f) {
            float k = WINDOW_K + (1f - WINDOW_K) * windowGrow;
            float wr = r * k;
            surfaceMatrix(surface, r * (float) Math.sqrt(Math.max(0f, 1 - k * k)));
            int wa = (int) (255 * windowAlpha * alphaMul);
            if (wa > 0) {
                canvas.save();
                canvas.concat(surface);
                // «жидкость» внутри окошка медленно переливается
                float lx = (float) Math.cos(time * 0.7f) * wr * 0.3f;
                float ly = (float) Math.sin(time * 0.9f) * wr * 0.3f;
                windowPaint.setShader(new RadialGradient(lx, ly, wr * 1.1f,
                        new int[]{Color.rgb(26, 38, 72), Color.rgb(3, 5, 12)}, null, Shader.TileMode.CLAMP));
                windowPaint.setAlpha(wa);
                canvas.drawCircle(0, 0, wr, windowPaint);
                canvas.restore();
            }
            if (main) {
                textMatrix.set(surface);
                pendingTextAlpha = answerAlpha * Math.min(1f, vn[2] * 1.5f);
                pendingTextScale = k;
            }
        }
    }

    private void drawText(Canvas canvas, float r) {
        int alpha = (int) (255 * pendingTextAlpha);
        if (alpha <= 0) return;
        // Текст свёрстан под полный радиус шара и масштабируется вместе с окошком
        StaticLayout layout = layoutFor(r);
        canvas.scale(pendingTextScale, pendingTextScale);
        textPaint.setAlpha(alpha);
        canvas.translate(-layout.getWidth() / 2f, -layout.getHeight() / 2f);
        layout.draw(canvas);
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

    private static float smoothstep(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3 - 2 * x);
    }

    /** Случайная ось вращения: в основном в плоскости экрана, чтобы окошко уходило за край. */
    private void pickRandomAxis() {
        double dir = random.nextDouble() * Math.PI * 2;
        double depth = (random.nextDouble() - 0.5) * 0.8;
        double x = Math.cos(dir), y = Math.sin(dir), z = depth;
        double len = Math.sqrt(x * x + y * y + z * z);
        axisX = (float) (x / len);
        axisY = (float) (y / len);
        axisZ = (float) (z / len);
    }

    // ---------- анимация ответа ----------

    /** Крутит шар 3 секунды, затем раскрывает новый ответ. */
    public void showAnswer(final String newAnswer) {
        animating = true;
        pickRandomAxis();
        final float startZoom = zoom;
        final float startGrow = windowGrow;
        final float startWindowAlpha = windowAlpha;
        final float startReveal = reveal;
        final float startHighlight = highlight;
        final float startAlpha = answerAlpha;
        final float fullTurn = (float) (TURNS * 2 * Math.PI);
        final float seconds = SPIN_DURATION_MS / 1000f;

        ValueAnimator spin = ValueAnimator.ofFloat(0f, 1f);
        spin.setDuration(SPIN_DURATION_MS);
        spin.setInterpolator(new LinearInterpolator());
        spin.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float t = (float) a.getAnimatedValue();
                float f = spinCurve(t);
                spinAngle = f * fullTurn;
                spinVelocity = (spinCurve(t + 0.002f) - spinCurve(t - 0.002f)) / 0.004f * fullTurn / seconds;
                answerAlpha = Math.max(0f, startAlpha * (1 - smoothstep(t * 6)));
                // в начале вращения всё возвращается к обычному виду
                float k = smoothstep(t * 5);
                zoom = startZoom + (1f - startZoom) * k;
                windowGrow = startGrow * (1f - k);
                windowAlpha = startWindowAlpha + (1f - startWindowAlpha) * k;
                reveal = startReveal * (1f - k);
                highlight = startHighlight + (1f - startHighlight) * k;
                bounce = 1f + 0.035f * (float) Math.sin(t * Math.PI * 6) * (1 - f);
            }
        });
        spin.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                spinAngle = 0f;
                spinVelocity = 0f;
                bounce = 1f;
                zoom = 1f;
                windowGrow = 0f;
                windowAlpha = 1f;
                answer = newAnswer;
                if (listener != null) listener.onSpinFinished();
            }
        });

        // Окошко разъезжается до размеров шара, круг растворяется, остаётся фраза
        ValueAnimator revealAnim = ValueAnimator.ofFloat(0f, 1f);
        revealAnim.setDuration(REVEAL_DURATION_MS);
        revealAnim.setInterpolator(new LinearInterpolator());
        final DecelerateInterpolator ease = new DecelerateInterpolator(2f);
        final OvershootInterpolator overshoot = new OvershootInterpolator(1.5f);
        revealAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float u = (float) a.getAnimatedValue();
                windowGrow = ease.getInterpolation(Math.min(1f, u / 0.5f));
                answerAlpha = smoothstep(u / 0.3f);
                windowAlpha = 1f - smoothstep((u - 0.4f) / 0.6f);
                reveal = smoothstep(u / 0.6f);
                highlight = 1f - smoothstep(u / 0.5f); // белый блик гаснет, пока слово раскрывается
                zoom = 1f + (ZOOM_SCALE - 1f) * overshoot.getInterpolation(Math.min(1f, u / 0.6f));
            }
        });

        AnimatorSet set = new AnimatorSet();
        set.playSequentially(spin, revealAnim);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animating = false;
            }
        });
        set.start();
    }
}
