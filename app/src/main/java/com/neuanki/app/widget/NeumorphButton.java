package com.neuanki.app.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatButton;

import com.neuanki.app.R;

/** دکمهٔ نئومورف — در حالت فشرده فرورفته می‌شود */
public class NeumorphButton extends AppCompatButton {

    private float radius;
    private int bgCol = 0xFF23272B;
    private int lightCol = 0xFF3A4249;
    private int darkCol = 0xFF141719;

    private final RectF rect = new RectF();

    public NeumorphButton(Context context) {
        super(context);
        init(null);
    }

    public NeumorphButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public NeumorphButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        NeuPainter.enableSoftware(this);
        setBackground(null);
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.Neumorph);
            radius = a.getDimension(R.styleable.Neumorph_nm_radius, dp(16));
            bgCol = a.getColor(R.styleable.Neumorph_nm_bg, bgCol);
            lightCol = a.getColor(R.styleable.Neumorph_nm_light, lightCol);
            darkCol = a.getColor(R.styleable.Neumorph_nm_dark, darkCol);
            a.recycle();
        } else {
            radius = dp(16);
        }
        setStateListAnimator(null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float m = dp(1);
        rect.set(m, m, w - m, h - m);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (rect.width() > 0 && rect.height() > 0) {
            if (isPressed() || isSelected()) {
                NeuPainter.drawInset(canvas, rect, radius, bgCol, lightCol, darkCol, dp(5));
                setTextColorForState(true);
            } else {
                NeuPainter.drawRaised(canvas, rect, radius, bgCol, lightCol, darkCol, dp(8), dp(3));
                setTextColorForState(false);
            }
        }
        super.onDraw(canvas);
    }

    private ColorStateList baseText;

    private void setTextColorForState(boolean pressed) {
        if (baseText == null) baseText = getTextColors();
        // در حالت فشرده کمی تیره‌تر — ساده نگه می‌داریم
        setTextColor(pressed ? (baseText.getDefaultColor() & 0x00FFFFFF) | 0xB0000000
                : baseText.getDefaultColor());
    }

    @Override
    public void drawableStateChanged() {
        super.drawableStateChanged();
        if (rect.width() > 0) invalidate();
    }

    private float dp(int v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
