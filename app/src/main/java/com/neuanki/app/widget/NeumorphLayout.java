package com.neuanki.app.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.neuanki.app.R;

/** کانتینر نئومورف (برجسته یا فرورفته) */
public class NeumorphLayout extends FrameLayout {

    private float radius;
    private boolean inset;
    private int bgCol = 0xFF23272B;
    private int lightCol = 0xFF3A4249;
    private int darkCol = 0xFF141719;

    private final RectF rect = new RectF();

    public NeumorphLayout(Context context) {
        super(context);
        init(null);
    }

    public NeumorphLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public NeumorphLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        NeuPainter.enableSoftware(this);
        setWillNotDraw(false);
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.Neumorph);
            radius = a.getDimension(R.styleable.Neumorph_nm_radius, dp(20));
            inset = a.getBoolean(R.styleable.Neumorph_nm_inset, false);
            bgCol = a.getColor(R.styleable.Neumorph_nm_bg, bgCol);
            lightCol = a.getColor(R.styleable.Neumorph_nm_light, lightCol);
            darkCol = a.getColor(R.styleable.Neumorph_nm_dark, darkCol);
            a.recycle();
        } else {
            radius = dp(20);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float m = dp(1);
        rect.set(m, m, w - m, h - m);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (rect.width() <= 0 || rect.height() <= 0) return;
        if (inset) {
            NeuPainter.drawInset(canvas, rect, radius, bgCol, lightCol, darkCol, dp(7));
        } else {
            NeuPainter.drawRaised(canvas, rect, radius, bgCol, lightCol, darkCol, dp(10), dp(4));
        }
        super.onDraw(canvas);
    }

    private float dp(int v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
