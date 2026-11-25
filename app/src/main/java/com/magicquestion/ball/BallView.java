package com.magicquestion.ball;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
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
import android.view.animation.AccelerateDecelerateInterpolator;

/** Рисует магический шар с окошком, в котором всплывает ответ. */
public class BallView extends View {

    private final Paint ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint windowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trianglePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glarePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path triangle = new Path();

    private String answer = "?";
    private float answerAlpha = 1f;   // прозрачность ответа 0..1
    private float answerScale = 1f;
    private boolean animating = false;

    public BallView(Context context) {
        super(context);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(Color.rgb(40, 40, 60));
        trianglePaint.setColor(Color.rgb(35, 50, 190));
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    public boolean isAnimating() {
        return animating;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float r = radius();
        float cx = w / 2f, cy = h / 2f;
        ballPaint.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.5f,
                new int[]{Color.rgb(90, 90, 110), Color.rgb(20, 20, 28), Color.BLACK},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        windowPaint.setShader(new RadialGradient(cx, cy, r * 0.45f,
                new int[]{Color.rgb(25, 25, 70), Color.rgb(5, 5, 20)},
                null, Shader.TileMode.CLAMP));
        glarePaint.setShader(new RadialGradient(cx - r * 0.4f, cy - r * 0.5f, r * 0.35f,
                new int[]{Color.argb(110, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                null, Shader.TileMode.CLAMP));
        ringPaint.setStrokeWidth(r * 0.035f);
    }

    private float radius() {
        return Math.min(getWidth(), getHeight()) * 0.42f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = radius();
        float wr = r * 0.45f; // радиус окошка

        canvas.drawCircle(cx, cy, r, ballPaint);
        canvas.drawCircle(cx - r * 0.4f, cy - r * 0.5f, r * 0.35f, glarePaint);
        canvas.drawCircle(cx, cy, wr, windowPaint);
        canvas.drawCircle(cx, cy, wr, ringPaint);

        int alpha = (int) (255 * answerAlpha);
        if (alpha <= 0) return;

        canvas.save();
        canvas.scale(answerScale, answerScale, cx, cy);

        // Синий треугольник-«кубик»
        float ts = wr * 0.92f;
        triangle.reset();
        triangle.moveTo(cx - ts, cy - ts * 0.55f);
        triangle.lineTo(cx + ts, cy - ts * 0.55f);
        triangle.lineTo(cx, cy + ts * 0.95f);
        triangle.close();
        trianglePaint.setAlpha(alpha);
        canvas.drawPath(triangle, trianglePaint);

        // Текст ответа, переносим по словам и подбираем размер
        textPaint.setAlpha(alpha);
        int textWidth = (int) (ts * 1.15f);
        float size = wr * 0.30f;
        StaticLayout layout;
        while (true) {
            textPaint.setTextSize(size);
            layout = makeLayout(answer.toUpperCase(), textWidth);
            if ((layout.getHeight() <= ts * 0.9f && fitsWords(textWidth)) || size < 8) break;
            size *= 0.9f;
        }
        canvas.translate(cx - textWidth / 2f, cy - ts * 0.2f - layout.getHeight() / 2f);
        layout.draw(canvas);
        canvas.restore();
    }

    private boolean fitsWords(int width) {
        for (String word : answer.toUpperCase().split(" ")) {
            if (textPaint.measureText(word) > width) return false;
        }
        return true;
    }

    @SuppressWarnings("deprecation")
    private StaticLayout makeLayout(String text, int width) {
        return new StaticLayout(text, textPaint, width, Layout.Alignment.ALIGN_CENTER,
                0.95f, 0f, false);
    }

    /** Встряхивает шар, прячет старый ответ и проявляет новый. */
    public void showAnswer(final String newAnswer) {
        animating = true;

        float d = getWidth() * 0.04f;
        ObjectAnimator shake = ObjectAnimator.ofFloat(this, "translationX",
                0, -d, d, -d, d, -d * 0.6f, d * 0.6f, 0);
        shake.setDuration(600);

        ValueAnimator fadeOut = ValueAnimator.ofFloat(answerAlpha, 0f);
        fadeOut.setDuration(250);
        fadeOut.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                answerAlpha = (float) a.getAnimatedValue();
                invalidate();
            }
        });
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                answer = newAnswer;
            }
        });

        ValueAnimator fadeIn = ValueAnimator.ofFloat(0f, 1f);
        fadeIn.setDuration(900);
        fadeIn.setStartDelay(350);
        fadeIn.setInterpolator(new AccelerateDecelerateInterpolator());
        fadeIn.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float v = (float) a.getAnimatedValue();
                answerAlpha = v;
                answerScale = 0.6f + 0.4f * v;
                invalidate();
            }
        });

        AnimatorSet set = new AnimatorSet();
        set.play(shake).with(fadeOut);
        set.play(fadeIn).after(fadeOut);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animating = false;
            }
        });
        set.start();
    }
}
