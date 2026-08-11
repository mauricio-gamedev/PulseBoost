package io.github.astromg01.pulseboost;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

final class UiKit {
    static final int BACKGROUND = Color.rgb(8, 10, 16);
    static final int SURFACE = Color.rgb(17, 21, 32);
    static final int SURFACE_ALT = Color.rgb(24, 29, 43);
    static final int SURFACE_LIGHT = Color.rgb(31, 37, 54);
    static final int TEXT = Color.rgb(246, 247, 251);
    static final int MUTED = Color.rgb(158, 167, 188);
    static final int PURPLE = Color.rgb(138, 108, 255);
    static final int CYAN = Color.rgb(83, 228, 194);
    static final int YELLOW = Color.rgb(255, 200, 87);
    static final int RED = Color.rgb(255, 102, 122);
    static final int GREEN_DARK = Color.rgb(20, 68, 59);
    static final int PURPLE_DARK = Color.rgb(47, 39, 83);

    private UiKit() {
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    static LinearLayout horizontal(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    static LinearLayout card(Context context) {
        LinearLayout card = vertical(context);
        card.setPadding(dp(context, 17), dp(context, 16), dp(context, 17), dp(context, 16));
        card.setBackground(rounded(SURFACE, dp(context, 20), Color.TRANSPARENT, 0));
        return card;
    }

    static TextView text(
            Context context,
            CharSequence value,
            float sizeSp,
            int color,
            boolean medium) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(0, 1.08f);
        view.setTypeface(Typeface.create(
                medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return view;
    }

    static TextView button(
            Context context,
            CharSequence value,
            int backgroundColor,
            int textColor) {
        TextView button = text(context, value, 13, textColor, true);
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        button.setLetterSpacing(0.035f);
        button.setPadding(dp(context, 18), dp(context, 14), dp(context, 18), dp(context, 14));
        button.setMinHeight(dp(context, 48));
        button.setClickable(true);
        button.setFocusable(true);
        button.setBackground(ripple(backgroundColor, dp(context, 15)));
        return button;
    }

    static TextView pill(
            Context context,
            CharSequence value,
            int backgroundColor,
            int textColor) {
        TextView pill = text(context, value, 11, textColor, true);
        pill.setGravity(Gravity.CENTER);
        pill.setLetterSpacing(0.05f);
        pill.setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6));
        pill.setBackground(rounded(backgroundColor, dp(context, 99), Color.TRANSPARENT, 0));
        return pill;
    }

    static TextView sectionTitle(Context context, String title) {
        TextView view = text(context, title.toUpperCase(Locale.getDefault()), 12, MUTED, true);
        view.setLetterSpacing(0.12f);
        return view;
    }

    static View spacer(Context context, int heightDp) {
        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, heightDp)));
        return spacer;
    }

    static GradientDrawable rounded(int color, int radiusPx, int strokeColor, int strokeWidthPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        if (strokeWidthPx > 0) {
            drawable.setStroke(strokeWidthPx, strokeColor);
        }
        return drawable;
    }

    static RippleDrawable ripple(int color, int radiusPx) {
        GradientDrawable content = rounded(color, radiusPx, Color.TRANSPARENT, 0);
        return new RippleDrawable(
                ColorStateList.valueOf(Color.argb(55, 255, 255, 255)), content, null);
    }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams weight(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    static void setMargins(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) raw;
            params.setMargins(left, top, right, bottom);
            view.setLayoutParams(params);
        }
    }
}
