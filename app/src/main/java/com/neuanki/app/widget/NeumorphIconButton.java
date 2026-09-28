package com.neuanki.app.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageView;

import com.neuanki.app.R;

/** دکمهٔ آیکونی نئومورف (دایره‌ای/گرد) */
public class NeumorphIconButton extends AppCompatImageView {

    private float radius;
    private int bgCol = 0xFF23272B;
    private int lightCol = 0xFF3A4249;
    private int darkCol = 0xFF141719;

    private final RectF rect = new RectF();

    public NeumorphIconButton(Context context) {
        super(context);
        init(null);
    }

    public NeumorphIconButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public NeumorphIconButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        NeuPainter.enableSoftware(this);
        setBackground(null);
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.Neumorph);
            radius = a.getDimension(R.styleable.Neumorph_nm_radius, -1);
            bgCol = a.getColor(R.styleable.Neumorph_nm_bg, bgCol);
            lightCol = a.getColor(R.styleable.Neumorph_nm_light, lightCol);
            darkCol = a.getColor(R.styleable.Neumorph_nm_dark, darkCol);
            a.recycle();
        }
        setColorFilter(0xFF98A2AC);
        setScaleType(ScaleType.FIT_CENTER);
        int pad = (int) dp(10);
        setPadding(pad, pad, pad, pad);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float m = dp(1);
        rect.set(m, m, w - m, h - m);
        if (radius <= 0) radius = Math.min(w, h) / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (rect.width() > 0 && rect.height() > 0) {
            if (isPressed()) {
                NeuPainter.drawInset(canvas, rect, radius, bgCol, lightCol, darkCol, dp(5));
            } else {
                NeuPainter.drawRaised(canvas, rect, radius, bgCol, lightCol, darkCol, dp(8), dp(3));
            }
        }
        super.onDraw(canvas);
    }

    @Override
    public void drawableStateChanged() {
        super.drawableStateChanged();
        if (rect.width() > 0) invalidate();
    }

    /** رنگ آیکون سفید برای FAB زمردی */
    public void setIconTint(int color) {
        setColorFilter(color);
    }

    private float dp(int v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
