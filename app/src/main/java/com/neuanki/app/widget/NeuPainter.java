package com.neuanki.app.widget;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/** نقاشی سطوح نئومورف: برجسته (دو سایه) و فرورفته (سایه داخلی) */
public final class NeuPainter {

    private NeuPainter() {}

    /** سطح برجسته با سایهٔ روشن بالا-چپ و سایهٔ تیره پایین-راست */
    public static void drawRaised(Canvas canvas, RectF rect, float radius,
                                  int bg, int light, int dark, float shadowR, float offset) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(bg);
        p.setShadowLayer(shadowR, -offset, -offset, light);
        canvas.drawRoundRect(rect, radius, radius, p);

        Paint p2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        p2.setColor(bg);
        p2.setShadowLayer(shadowR, offset, offset, dark);
        canvas.drawRoundRect(rect, radius, radius, p2);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(bg);
        canvas.drawRoundRect(rect, radius, radius, fill);
    }

    /** سطح فرورفته با سایهٔ داخلی تیره بالا-چپ و روشن پایین-راست */
    public static void drawInset(Canvas canvas, RectF rect, float radius,
                                 int bg, int light, int dark, float depth) {
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(bg);
        canvas.drawRoundRect(rect, radius, radius, fill);

        Path path = new Path();
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);

        int darkA = (0x5A << 24) | (dark & 0x00FFFFFF);
        int lightA = (0x4A << 24) | (light & 0x00FFFFFF);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(depth * 2f);

        canvas.save();
        canvas.clipPath(path);

        stroke.setShader(new LinearGradient(0, rect.top, 0, rect.top + depth,
                darkA, darkA & 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawPath(path, stroke);

        stroke.setShader(new LinearGradient(rect.left, 0, rect.left + depth, 0,
                darkA, darkA & 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawPath(path, stroke);

        stroke.setShader(new LinearGradient(0, rect.bottom, 0, rect.bottom - depth,
                lightA, lightA & 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawPath(path, stroke);

        stroke.setShader(new LinearGradient(rect.right, 0, rect.right - depth, 0,
                lightA, lightA & 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawPath(path, stroke);

        canvas.restore();
    }

    /** تنظیم لایهٔ نرم برای پشتیبانی از shadowLayer */
    public static void enableSoftware(android.view.View v) {
        v.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null);
    }
}
