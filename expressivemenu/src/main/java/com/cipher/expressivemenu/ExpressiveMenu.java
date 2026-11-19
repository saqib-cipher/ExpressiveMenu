/*
 * ExpressiveMenu  -  Cipher                                                         v3
 * Single-file, zero-dependency popup / dropdown menu for Android (pure Java, View system).
 *
 *  STYLES
 *    STYLE_THEME (default)  Solid segments. Every colour (surface, text, icon tint, selected pill,
 *                           checkbox...) is read from the APP THEME, so it follows light / dark and
 *                           Material You automatically.
 *    STYLE_BLUR             Same theme colours, but each segment is frosted glass (blurred screen behind it,
 *                           translucent tint on top). It adapts to the theme AND to what is behind the menu,
 *                           so text keeps its contrast on light and dark screens.
 *
 *  - Material 3 Expressive segmented list: big outer corners, small inner corners, spring shape morphing
 *  - Header with description is a real segment (different style), not loose text
 *  - Sub-menus: tap an item with children to expand inline, parent turns into the selected pill
 *  - Descriptions, trailing text, selected check, icons (+ selected icon), animated checkbox, custom views
 *  - Smart positioning, spring enter, quick exit
 *
 * Min SDK 21. Only android.* framework classes. No lambdas (works in Sketchware Pro).
 *
 * QUICK START
 *
 *   ExpressiveMenu.with(this)
 *       .setStyle(ExpressiveMenu.STYLE_BLUR)            // optional, default = STYLE_THEME
 *       .addHeader("Sort by", "Choose how files are ordered")
 *       .add(ExpressiveMenu.Item.action(1, "Name").description("A to Z")
 *               .icon(ExpressiveMenu.Icons.starCircle(false))
 *               .selectedIcon(ExpressiveMenu.Icons.starCircle(true)).selected(true))
 *       .add(ExpressiveMenu.Item.submenu(10, "More options").description("Date, size, type")
 *               .child(ExpressiveMenu.Item.action(11, "Date"))
 *               .child(ExpressiveMenu.Item.checkbox(13, "Folders first", true)))
 *       .addSeparator()
 *       .add(ExpressiveMenu.Item.checkbox(3, "Show hidden files", false))
 *       .setSingleChoice(true)
 *       .setOnItemClickListener(new ExpressiveMenu.OnItemClickListener() {
 *           public void onItemClick(ExpressiveMenu menu, ExpressiveMenu.Item item) { ... item.getId() ... }
 *       })
 *       .show(anchorView);
 */
package com.cipher.expressivemenu;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class ExpressiveMenu {

    /** Solid segments coloured from the app theme (default). */
    public static final int STYLE_THEME = 0;
    /** Frosted-glass segments; adapts to the app theme and to the screen behind the menu. */
    public static final int STYLE_BLUR = 1;

    /* =====================================================================================
     *  Public types
     * ===================================================================================== */

    public interface OnItemClickListener {
        void onItemClick(ExpressiveMenu menu, Item item);
    }

    public interface OnCheckedChangeListener {
        void onCheckedChanged(ExpressiveMenu menu, Item item, boolean checked);
    }

    public interface OnSubmenuToggleListener {
        void onSubmenuToggled(ExpressiveMenu menu, Item parent, boolean expanded);
    }

    /** One entry of the menu. Build with the static factories, configure with the chained setters. */
    public static final class Item {
        public static final int ACTION = 0, CHECKBOX = 1, CUSTOM = 2, HEADER = 3, SEPARATOR = 4;

        final int type;
        int id;
        CharSequence title, description, trailingText;
        int descriptionLines = 0;                 // 0 = use the menu default
        Drawable icon, selectedIcon, trailingIcon;
        boolean enabled = true, checked, selected, expanded, keepOpen, keepOpenSet;
        View customView;
        Object tag;
        Runnable onClick, onTrailingClick;
        List<Item> children;
        Item parent;
        View rowView;                             // set while the menu is built

        private Item(int type) { this.type = type; }

        public static Item action(int id, CharSequence title) {
            Item i = new Item(ACTION); i.id = id; i.title = title; return i;
        }
        /** An item that expands inline to reveal its children. Add them with child(). */
        public static Item submenu(int id, CharSequence title) {
            Item i = new Item(ACTION); i.id = id; i.title = title; i.children = new ArrayList<Item>(); return i;
        }
        public static Item checkbox(int id, CharSequence title, boolean checked) {
            Item i = new Item(CHECKBOX); i.id = id; i.title = title; i.checked = checked; return i;
        }
        /** Hosts any view inside a segment (it keeps the segment shape + background). */
        public static Item custom(int id, View view) {
            Item i = new Item(CUSTOM); i.id = id; i.customView = view; return i;
        }
        /** A non-clickable segment with its own tonal style: a label + optional description. */
        public static Item header(CharSequence title, CharSequence description) {
            Item i = new Item(HEADER); i.title = title; i.description = description; return i;
        }
        public static Item separator() { return new Item(SEPARATOR); }

        public Item icon(Drawable d) { icon = d; return this; }
        public Item icon(Context c, int resId) { icon = c.getDrawable(resId); return this; }
        public Item selectedIcon(Drawable d) { selectedIcon = d; return this; }
        /** Secondary line under the title. */
        public Item description(CharSequence s) { description = s; return this; }
        public Item description(CharSequence s, int maxLines) { description = s; descriptionLines = maxLines; return this; }
        public Item trailingText(CharSequence s) { trailingText = s; return this; }
        /** Round tonal button on the right. (Sub-menu parents get an automatic chevron.) */
        public Item trailingIcon(Drawable d, Runnable onClick) { trailingIcon = d; onTrailingClick = onClick; return this; }
        public Item enabled(boolean e) { enabled = e; return this; }
        public Item selected(boolean s) { selected = s; return this; }
        public Item checked(boolean c) { checked = c; return this; }
        public Item keepOpen(boolean k) { keepOpen = k; keepOpenSet = true; return this; }
        public Item tag(Object t) { tag = t; return this; }
        public Item onClick(Runnable r) { onClick = r; return this; }
        /** Sub-menu: add a child (action, checkbox, custom view or another sub-menu). */
        public Item child(Item c) {
            if (children == null) children = new ArrayList<Item>();
            c.parent = this; children.add(c); return this;
        }
        /** Sub-menu: initial state. */
        public Item expanded(boolean e) { expanded = e; return this; }

        public int getId() { return id; }
        public Object getTag() { return tag; }
        public CharSequence getTitle() { return title; }
        public boolean isChecked() { return checked; }
        public boolean isSelected() { return selected; }
        public boolean isExpanded() { return expanded; }
        public Item getParent() { return parent; }
    }

    /**
     * Colours of the menu. Theme.app(context) (the default) reads them from the app theme: Material attributes
     * (colorSurface, colorOnSurface, colorSecondaryContainer, ...) when the app defines them, otherwise the
     * framework ones, and derives anything missing, so it works with AppCompat and Material themes alike.
     * dark()/light() are fixed palettes if you want to override: setTheme(Theme.dark()).
     */
    public static final class Theme {
        public int panel, item, itemSelected, headerContainer;
        public int text, textSelected, supporting, headerText, icon, iconSelected;
        public int primary, onPrimary, outline, trailingButton, stateLayer;

        public static Theme dark() {
            Theme t = new Theme();
            t.panel = 0xFF141218; t.item = 0xFF211F26; t.itemSelected = 0xFF4A4458; t.headerContainer = 0xFF2B2930;
            t.text = 0xFFE6E0E9; t.textSelected = 0xFFE8DEF8; t.supporting = 0xFFCAC4D0; t.headerText = 0xFFD0BCFF;
            t.icon = 0xFFCAC4D0; t.iconSelected = 0xFFE8DEF8; t.primary = 0xFFD0BCFF; t.onPrimary = 0xFF381E72;
            t.outline = 0xFF938F99; t.trailingButton = 0xFF141218; t.stateLayer = 0xFFE6E0E9;
            return t;
        }
        public static Theme light() {
            Theme t = new Theme();
            t.panel = 0xFFFEF7FF; t.item = 0xFFF3EDF7; t.itemSelected = 0xFFE8DEF8; t.headerContainer = 0xFFECE6F0;
            t.text = 0xFF1D1B20; t.textSelected = 0xFF1D192B; t.supporting = 0xFF49454F; t.headerText = 0xFF6750A4;
            t.icon = 0xFF49454F; t.iconSelected = 0xFF1D192B; t.primary = 0xFF6750A4; t.onPrimary = 0xFFFFFFFF;
            t.outline = 0xFF79747E; t.trailingButton = 0xFFE6E0E9; t.stateLayer = 0xFF1D1B20;
            return t;
        }

        /** Colours resolved from the theme of `c` (pass an Activity / the anchor's context). */
        public static Theme app(Context c) {
            Integer s = pick(c, new String[]{"colorSurface"}, new int[]{android.R.attr.colorBackground});
            int surface = s != null ? opaque(s) : (isNight(c) ? 0xFF141218 : 0xFFFEF7FF);
            boolean dark = luma(surface) < 0.5f;
            Theme d = dark ? dark() : light();
            Theme t = new Theme();

            int onSurface = or(pick(c, new String[]{"colorOnSurface"}, new int[]{android.R.attr.textColorPrimary}), d.text);
            int primary = or(pick(c, new String[]{"colorPrimary"}, new int[]{android.R.attr.colorPrimary, android.R.attr.colorAccent}), d.primary);
            Integer onPrimary = pick(c, new String[]{"colorOnPrimary"}, null);
            Integer item = pick(c, new String[]{"colorSurfaceContainer"}, null);
            Integer high = pick(c, new String[]{"colorSurfaceContainerHigh"}, null);
            Integer highest = pick(c, new String[]{"colorSurfaceContainerHighest"}, null);
            Integer secContainer = pick(c, new String[]{"colorSecondaryContainer"}, null);
            Integer onSecContainer = pick(c, new String[]{"colorOnSecondaryContainer"}, null);
            Integer variant = pick(c, new String[]{"colorOnSurfaceVariant"}, new int[]{android.R.attr.textColorSecondary});
            Integer outline = pick(c, new String[]{"colorOutline"}, null);

            t.panel = surface;
            t.item = item != null ? opaque(item) : blend(surface, onSurface, dark ? 0.07f : 0.045f);
            t.headerContainer = high != null ? opaque(high) : blend(surface, onSurface, dark ? 0.12f : 0.085f);
            t.itemSelected = secContainer != null ? opaque(secContainer) : blend(surface, primary, dark ? 0.30f : 0.20f);
            t.text = onSurface;
            t.textSelected = onSecContainer != null ? onSecContainer : onSurface;
            t.supporting = variant != null ? variant : blend(onSurface, surface, 0.28f);
            t.icon = or(pick(c, new String[]{"colorOnSurfaceVariant"}, new int[]{android.R.attr.colorControlNormal}), t.supporting);
            t.iconSelected = t.textSelected;
            t.primary = primary;
            t.onPrimary = onPrimary != null ? onPrimary : (luma(primary) > 0.6f ? 0xFF000000 : 0xFFFFFFFF);
            t.headerText = primary;
            t.outline = outline != null ? outline : blend(onSurface, surface, 0.5f);
            t.trailingButton = dark ? surface : (highest != null ? opaque(highest) : blend(surface, onSurface, 0.11f));
            t.stateLayer = onSurface;
            return t;
        }

        private static Integer pick(Context c, String[] names, int[] frameworkAttrs) {
            if (names != null) {
                String pkg = c.getPackageName();
                for (String n : names) {
                    int id = c.getResources().getIdentifier(n, "attr", pkg);
                    if (id != 0) { Integer v = read(c, id); if (v != null) return v; }
                }
            }
            if (frameworkAttrs != null) for (int id : frameworkAttrs) { Integer v = read(c, id); if (v != null) return v; }
            return null;
        }

        private static Integer read(Context c, int attr) {
            TypedArray ta = null;
            try {
                ta = c.obtainStyledAttributes(new int[]{attr});
                if (ta.hasValue(0)) return ta.getColor(0, 0);
            } catch (Throwable ignored) {
            } finally {
                if (ta != null) ta.recycle();
            }
            return null;
        }

        private static int or(Integer v, int fallback) { return v != null ? v : fallback; }
        private static int opaque(int c) { return c | 0xFF000000; }
        private static int blend(int a, int b, float t) {
            return 0xFF000000
                    | (Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t) << 16)
                    | (Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t) << 8)
                    | Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        }
    }

    /** Ready-made icons so the library needs no resources (tinted by the menu). */
    public static final class Icons {
        public static Drawable chevronDown() {
            Path p = new Path(); p.moveTo(6f, 9.5f); p.lineTo(12f, 15.5f); p.lineTo(18f, 9.5f);
            return new PathIcon(p, true);
        }
        public static Drawable check() {
            Path p = new Path(); p.moveTo(5f, 12.5f); p.lineTo(10f, 17.5f); p.lineTo(19f, 7f);
            return new PathIcon(p, true);
        }
        /** The circled star from the spec. filled=false -> ring + star, filled=true -> solid disc, star cut out. */
        public static Drawable starCircle(boolean filled) {
            Path p = new Path();
            p.setFillType(Path.FillType.EVEN_ODD);
            p.addCircle(12f, 12f, 10f, Path.Direction.CW);
            if (!filled) p.addCircle(12f, 12f, 8.3f, Path.Direction.CW);
            for (int i = 0; i < 10; i++) {
                double a = -Math.PI / 2 + i * Math.PI / 5;
                float r = (i % 2 == 0) ? 5.4f : 2.35f;
                float x = 12f + (float) Math.cos(a) * r, y = 12.4f + (float) Math.sin(a) * r;
                if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
            }
            p.close();
            return new PathIcon(p, false);
        }
    }

    /* =====================================================================================
     *  Configuration
     * ===================================================================================== */

    private static final float OUTER = 24f, INNER = 4f, SELECTED_R = 20f, PRESSED_R = 28f;
    private static final TimeInterpolator SPRING = new Spring(8f, 10f);
    private static final TimeInterpolator EMPH_DECEL = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    private static final TimeInterpolator EMPH_ACCEL = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);

    private final Context ctx;
    private final float dp;
    private final List<Item> items = new ArrayList<Item>();
    private Theme customTheme, theme;

    private int style = STYLE_THEME;
    private boolean singleChoice, dismissOnClick = true, haptics = true, tintIcons = true;
    private boolean animations = true, submenuExclusive = false, showSelectedCheck = false;
    private int blurRadiusDp = 22, minWidthDp = 220, maxWidthDp = 320, itemHeightDp = 56, descriptionLines = 2;
    private int offXdp = 0, offYdp = 4, gravity = Gravity.START;
    private float itemOpacity = 0.46f, scrimDim = 0f;
    private Typeface typeface;

    private OnItemClickListener clickListener;
    private OnCheckedChangeListener checkedListener;
    private OnSubmenuToggleListener toggleListener;
    private PopupWindow.OnDismissListener dismissListener;

    // runtime state
    private MenuWindow window;
    private Root root;
    private PanelView panel;
    private ScrollView scroll;
    private LinearLayout list;
    private View anchor;
    private final List<ItemRow> rows = new ArrayList<ItemRow>();
    private ColorDrawable scrim;
    private ValueAnimator enterAnim;
    private Bitmap snap;
    private float snapSx, snapSy, itemOpEff;
    private final int[] snapLoc = new int[2];
    private final int[] rootLoc = new int[2];
    private boolean openDown = true, dirLocked, darkTheme;

    public ExpressiveMenu(Context context) {
        this.ctx = context;
        this.dp = context.getResources().getDisplayMetrics().density;
    }

    public static ExpressiveMenu with(Context context) { return new ExpressiveMenu(context); }

    /* ---- content ---- */
    public ExpressiveMenu add(Item item) { items.add(item); return this; }
    public ExpressiveMenu addAction(int id, CharSequence title, Drawable icon) { return add(Item.action(id, title).icon(icon)); }
    public ExpressiveMenu addCheckbox(int id, CharSequence title, boolean checked) { return add(Item.checkbox(id, title, checked)); }
    public ExpressiveMenu addHeader(CharSequence title, CharSequence description) { return add(Item.header(title, description)); }
    public ExpressiveMenu addSeparator() { return add(Item.separator()); }
    public ExpressiveMenu addCustom(int id, View v) { return add(Item.custom(id, v)); }

    public Item getItem(int id) { return findItem(items, id); }

    private static Item findItem(List<Item> l, int id) {
        for (Item i : l) {
            if (i.id == id && i.type != Item.HEADER && i.type != Item.SEPARATOR) return i;
            if (i.children != null) { Item r = findItem(i.children, id); if (r != null) return r; }
        }
        return null;
    }

    /* ---- look ---- */
    /** STYLE_THEME (default) or STYLE_BLUR. Both follow the app theme. */
    public ExpressiveMenu setStyle(int s) { style = s; return this; }
    /** Override the colours (default: read from the app theme). */
    public ExpressiveMenu setTheme(Theme t) { customTheme = t; return this; }
    /** STYLE_BLUR: blur strength in dp (default 22). */
    public ExpressiveMenu setBlurRadius(int dp) { blurRadiusDp = dp; return this; }
    /** STYLE_BLUR: minimum segment opacity, lower = more see-through (default 0.46). Contrast is auto-protected. */
    public ExpressiveMenu setItemOpacity(float opacity) {
        itemOpacity = clampFloat(opacity, 0f, 1f);
        itemOpEff = itemOpacity;
        for (ItemRow r : rows) r.retarget(false);
        return this;
    }
    /** Dim the screen behind the menu. */
    public ExpressiveMenu setBackgroundDim(boolean enabled) { scrimDim = enabled ? 0.16f : 0f; return this; }
    /** Set the background dim amount from 0 (no dim) to 1 (maximum dim). */
    public ExpressiveMenu setBackgroundDim(float amount) { scrimDim = clampFloat(amount, 0f, 1f); if (scrim != null) scrim.setAlpha((int)(255 * scrimDim)); return this; }
    public ExpressiveMenu setTintIcons(boolean t) { tintIcons = t; return this; }
    public ExpressiveMenu setTypeface(Typeface t) { typeface = t; return this; }
    public ExpressiveMenu setAnimationsEnabled(boolean a) { animations = a; return this; }
    public ExpressiveMenu setHapticsEnabled(boolean h) { haptics = h; return this; }
    public ExpressiveMenu setMenuWidth(int minDp, int maxDp) { minWidthDp = minDp; maxWidthDp = maxDp; return this; }
    public ExpressiveMenu setItemHeight(int dp) { itemHeightDp = dp; return this; }
    /** Max lines of every item description (default 2; per-item override via Item.description(text, lines)). */
    public ExpressiveMenu setDescriptionMaxLines(int lines) { descriptionLines = Math.max(1, lines); return this; }
    /** Animated check mark at the end of the selected item (nice with single choice). */
    public ExpressiveMenu setShowSelectedCheck(boolean s) { showSelectedCheck = s; return this; }

    /* ---- behaviour ---- */
    /** Clicking a leaf ACTION item selects it (tonal pill) and deselects the others. */
    public ExpressiveMenu setSingleChoice(boolean s) { singleChoice = s; return this; }
    public ExpressiveMenu setDismissOnItemClick(boolean d) { dismissOnClick = d; return this; }
    /** Opening a sub-menu closes its open siblings (accordion). */
    public ExpressiveMenu setSubmenuExclusive(boolean e) { submenuExclusive = e; return this; }
    /** Gravity.START / END / CENTER_HORIZONTAL relative to the anchor. */
    public ExpressiveMenu setGravity(int g) { gravity = g; return this; }
    public ExpressiveMenu setOffset(int xDp, int yDp) { offXdp = xDp; offYdp = yDp; return this; }
    public ExpressiveMenu setOnItemClickListener(OnItemClickListener l) { clickListener = l; return this; }
    public ExpressiveMenu setOnCheckedChangeListener(OnCheckedChangeListener l) { checkedListener = l; return this; }
    public ExpressiveMenu setOnSubmenuToggleListener(OnSubmenuToggleListener l) { toggleListener = l; return this; }
    public ExpressiveMenu setOnDismissListener(PopupWindow.OnDismissListener l) { dismissListener = l; return this; }

    /** Select a leaf ACTION item by id (animates if the menu is open). */
    public ExpressiveMenu setSelected(int id) {
        selectRecursive(items, id);
        for (ItemRow r : rows) if (r.item.type == Item.ACTION) { r.bindContent(true); r.retarget(true); }
        return this;
    }

    private void selectRecursive(List<Item> l, int id) {
        for (Item i : l) {
            if (i.type == Item.ACTION && !isExpandable(i)) i.selected = (i.id == id);
            if (i.children != null) selectRecursive(i.children, id);
        }
    }

    /** Expand / collapse a sub-menu by id (animates if the menu is open). */
    public ExpressiveMenu setExpanded(int id, boolean expanded) {
        Item it = getItem(id);
        if (it == null || !isExpandable(it)) return this;
        ItemRow r = rowOf(it);
        if (r != null && isShowing()) toggleSubmenu(r, expanded, true); else it.expanded = expanded;
        return this;
    }

    /* =====================================================================================
     *  Show / dismiss
     * ===================================================================================== */

    public boolean isShowing() { return window != null && window.isShowing(); }

    public ExpressiveMenu show(View anchorView) {
        if (isShowing() || items.isEmpty() || anchorView == null || !anchorView.isAttachedToWindow()) return this;
        anchor = anchorView;
        dirLocked = false;
        theme = customTheme != null ? customTheme : Theme.app(anchorView.getContext());
        darkTheme = luma(theme.panel) < 0.5f;
        captureBackdrop();
        buildViews();

        window = new MenuWindow(root);
        window.setClippingEnabled(false);
        window.setAnimationStyle(0);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setElevation(0f);
        window.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        window.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override public void onDismiss() {
                snap = null; window = null; root = null; panel = null; scroll = null; rows.clear();
                for (Item i : items) clearRows(i);
                if (dismissListener != null) dismissListener.onDismiss();
            }
        });
        try {
            window.showAtLocation(anchorView, Gravity.NO_GRAVITY, 0, 0);
        } catch (Throwable t) {
            window = null;
        }
        return this;
    }

    public void dismiss() {
        if (window != null) window.dismiss();
    }

    private void clearRows(Item i) {
        i.rowView = null;
        if (i.children != null) for (Item c : i.children) clearRows(c);
    }

    /* =====================================================================================
     *  View construction
     * ===================================================================================== */

    private boolean glassActive() { return style == STYLE_BLUR && snap != null; }

    private void buildViews() {
        rows.clear();
        itemOpEff = itemOpacity;
        root = new Root(ctx);
        scrim = new ColorDrawable(Color.BLACK);
        scrim.setAlpha(0);
        root.setBackground(scrim);
        root.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dismiss(); }
        });

        panel = new PanelView(ctx);
        panel.setClickable(true);
        panel.setMinimumWidth(dpi(minWidthDp));

        scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buildList(items, list, 0, false);

        if (animations) {
            panel.setAlpha(0f);
            for (ItemRow r : rows) {
                if (!isRowVisible(r)) continue;
                r.setAlpha(0f); r.setScaleX(0.965f); r.setScaleY(0.965f);
            }
        }
    }

    /** Adds rows for `src` into `target`. Sub-menu children go into a collapsible container after their parent. */
    private void buildList(List<Item> src, LinearLayout target, int depth, boolean parentLast) {
        int n = src.size();
        boolean nested = depth > 0;
        for (int i = 0; i < n; i++) {
            Item it = src.get(i);
            if (it.type == Item.SEPARATOR) {
                if (!nested) target.addView(new View(ctx), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(6)));
                continue;
            }
            boolean first = !nested && (i == 0 || src.get(i - 1).type == Item.SEPARATOR);
            boolean last = nested ? (parentLast && i == n - 1) : (i == n - 1 || src.get(i + 1).type == Item.SEPARATOR);
            ItemRow row = new ItemRow(it, first, last, depth);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = nested ? (i == n - 1 ? 0 : dpi(2)) : (last ? 0 : dpi(2));
            target.addView(row, lp);
            rows.add(row);
            it.rowView = row;
            if (isExpandable(it)) {
                LinearLayout sub = new LinearLayout(ctx);
                sub.setOrientation(LinearLayout.VERTICAL);
                sub.setVisibility(it.expanded ? View.VISIBLE : View.GONE);
                row.sub = sub;
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                slp.bottomMargin = lp.bottomMargin;                       // same gap to the next sibling as the parent row
                slp.topMargin = lp.bottomMargin > 0 ? 0 : dpi(2);         // gap parent -> first child
                target.addView(sub, slp);
                buildList(it.children, sub, depth + 1, last);
            }
        }
    }

    private static boolean isExpandable(Item i) { return i.children != null && !i.children.isEmpty(); }

    private ItemRow rowOf(Item i) { return i.rowView instanceof ItemRow ? (ItemRow) i.rowView : null; }

    private boolean isRowVisible(View v) {
        View c = v;
        while (c != null && c != list) {
            if (c.getVisibility() != View.VISIBLE) return false;
            ViewParent p = c.getParent();
            c = p instanceof View ? (View) p : null;
        }
        return true;
    }

    /** Segment colour: solid theme colour, or a translucent tint over the item's own blurred backdrop. */
    private int surfaceColor(Item it, boolean sel) {
        int base = it.type == Item.HEADER ? theme.headerContainer : (sel ? theme.itemSelected : theme.item);
        base |= 0xFF000000;
        if (!glassActive()) return base;
        return withAlpha(base, itemOpacity);
    }

    /* =====================================================================================
     *  A single list row (segment)
     * ===================================================================================== */

    private final class ItemRow extends LinearLayout implements SegmentDrawable.Mapper {
        final Item item;
        final boolean first, last, header;
        final int depth;
        final SegmentDrawable bg;
        ImageView iconView, chevron, checkView;
        TextView titleView, descView;
        ExpressiveCheckBox box;
        LinearLayout sub;
        ValueAnimator subAnim;
        boolean pressedState, lastSel;

        ItemRow(Item it, boolean first, boolean last, int depth) {
            super(ctx);
            this.item = it; this.first = first; this.last = last; this.depth = depth;
            this.header = it.type == Item.HEADER;
            bg = new SegmentDrawable(dp, glassActive() ? snap : null, this);
            bg.setOverlayColor(theme.stateLayer);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setBackground(bg);
            if (it.type == Item.CUSTOM) {
                View cv = it.customView;
                if (cv.getParent() instanceof ViewGroup) ((ViewGroup) cv.getParent()).removeView(cv);
                addView(cv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                setMinimumHeight(dpi(itemHeightDp));
            } else if (header) {
                setPadding(dpi(16), dpi(12), dpi(16), dpi(12));
                buildHeader();
            } else {
                setMinimumHeight(dpi(it.description != null ? itemHeightDp + 12 : itemHeightDp));
                setPadding(dpi(16 + depth * 14), dpi(10), dpi(12), dpi(10));
                buildContent();
            }
            lastSel = sel();
            bindContent(false);
            retarget(false);
            if (!header && it.enabled && (it.type != Item.CUSTOM || it.onClick != null)) {
                setClickable(true);
                setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { handleClick(ItemRow.this); }
                });
            }
        }

        /** Where this row sits inside the blurred snapshot (layout position, so the glass stays put while it animates). */
        @Override public void map(RectF out) {
            int x = 0, y = 0;
            View c = this;
            while (c != null && c != panel) {
                x += c.getLeft(); y += c.getTop();
                ViewParent p = c.getParent();
                if (!(p instanceof View)) break;
                View pv = (View) p;
                x -= pv.getScrollX(); y -= pv.getScrollY();
                c = pv;
            }
            float l = rootLoc[0] + (panel != null ? panel.getLeft() : 0) + x - snapLoc[0];
            float t = rootLoc[1] + (panel != null ? panel.getTop() : 0) + y - snapLoc[1];
            out.set(l * snapSx, t * snapSy, (l + getWidth()) * snapSx, (t + getHeight()) * snapSy);
        }

        /** Visual "selected" state: selected leaf, or an expanded sub-menu parent. */
        boolean sel() { return !header && (item.selected || (isExpandable(item) && item.expanded)); }

        private TextView text(float sp) {
            TextView tv = new TextView(ctx);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
            if (typeface != null) tv.setTypeface(typeface);
            return tv;
        }

        private void buildHeader() {
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(VERTICAL);
            titleView = text(12f);
            titleView.setText(item.title);
            titleView.setAllCaps(false);
            titleView.setLetterSpacing(0.05f);
            titleView.setSingleLine(true);
            titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            col.addView(titleView);
            if (item.description != null) {
                descView = text(13f);
                descView.setText(item.description);
                descView.setMaxLines(item.descriptionLines > 0 ? item.descriptionLines : descriptionLines);
                descView.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                dl.topMargin = dpi(2);
                col.addView(descView, dl);
            }
            addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        private void buildContent() {
            if (item.icon != null) {
                iconView = new ImageView(ctx);
                iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpi(24), dpi(24));
                lp.rightMargin = dpi(12);
                addView(iconView, lp);
            }
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(VERTICAL);
            titleView = text(16f);
            titleView.setText(item.title);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(titleView);
            if (item.description != null) {
                descView = text(13f);
                descView.setText(item.description);
                descView.setLineSpacing(0f, 1.08f);
                descView.setMaxLines(item.descriptionLines > 0 ? item.descriptionLines : descriptionLines);
                descView.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                dl.topMargin = dpi(2);
                col.addView(descView, dl);
            }
            addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (item.trailingText != null) {
                TextView tt = text(14f);
                tt.setText(item.trailingText);
                tt.setTextColor(theme.supporting);
                tt.setSingleLine(true);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = dpi(12);
                addView(tt, lp);
            }
            if (isExpandable(item)) {
                // Round chevron button, flips when expanded (the whole row toggles, the button is decoration).
                chevron = new ImageView(ctx);
                chevron.setPadding(dpi(6), dpi(6), dpi(6), dpi(6));
                chevron.setImageDrawable(item.trailingIcon != null ? item.trailingIcon : Icons.chevronDown());
                chevron.setBackground(circle(glassActive() ? 0.85f : 1f));
                chevron.setRotation(item.expanded ? 180f : 0f);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpi(36), dpi(36));
                lp.leftMargin = dpi(12);
                addView(chevron, lp);
            } else if (item.trailingIcon != null) {
                ImageView tb = new ImageView(ctx);
                tb.setPadding(dpi(6), dpi(6), dpi(6), dpi(6));
                tb.setImageDrawable(item.trailingIcon);
                tb.setColorFilter(theme.icon, PorterDuff.Mode.SRC_IN);
                GradientDrawable mask = new GradientDrawable();
                mask.setShape(GradientDrawable.OVAL);
                mask.setColor(Color.WHITE);
                tb.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(theme.stateLayer, 0.2f)), circle(glassActive() ? 0.85f : 1f), mask));
                if (item.onTrailingClick != null) {
                    tb.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) { item.onTrailingClick.run(); }
                    });
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpi(36), dpi(36));
                lp.leftMargin = dpi(12);
                addView(tb, lp);
            }
            if (item.type == Item.CHECKBOX) {
                box = new ExpressiveCheckBox(ctx, theme.outline, theme.primary, theme.onPrimary, item.checked);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpi(28), dpi(28));
                lp.leftMargin = dpi(12);
                addView(box, lp);
            } else if (showSelectedCheck && item.type == Item.ACTION && !isExpandable(item)) {
                checkView = new ImageView(ctx);
                checkView.setImageDrawable(Icons.check());
                checkView.setColorFilter(theme.iconSelected, PorterDuff.Mode.SRC_IN);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpi(22), dpi(22));
                lp.leftMargin = dpi(12);
                addView(checkView, lp);
            }
        }

        private GradientDrawable circle(float alpha) {
            GradientDrawable oval = new GradientDrawable();
            oval.setShape(GradientDrawable.OVAL);
            oval.setColor(withAlpha(theme.trailingButton | 0xFF000000, alpha));
            return oval;
        }

        /** Colours, icon swap, check mark, chevron, disabled look. */
        void bindContent(boolean animate) {
            if (item.type == Item.CUSTOM) return;
            boolean sel = sel();
            if (header) {
                titleView.setTextColor(theme.headerText);
                if (descView != null) descView.setTextColor(theme.supporting);
                return;
            }
            titleView.setTextColor(sel ? theme.textSelected : theme.text);
            if (descView != null) descView.setTextColor(sel ? withAlpha(theme.textSelected, 0.82f) : theme.supporting);
            if (iconView != null) {
                Drawable d = (sel && item.selectedIcon != null) ? item.selectedIcon : item.icon;
                if (iconView.getDrawable() != d) iconView.setImageDrawable(d);
                if (tintIcons) iconView.setColorFilter(sel ? theme.iconSelected : theme.icon, PorterDuff.Mode.SRC_IN);
                else iconView.clearColorFilter();
                if (animate && sel != lastSel) {
                    iconView.setScaleX(0.55f); iconView.setScaleY(0.55f);
                    iconView.animate().scaleX(1f).scaleY(1f).setDuration(340).setInterpolator(new OvershootInterpolator(2.4f)).start();
                }
            }
            if (chevron != null) chevron.setColorFilter(sel ? theme.iconSelected : theme.icon, PorterDuff.Mode.SRC_IN);
            if (checkView != null) {
                float a = sel ? 1f : 0f, s = sel ? 1f : 0.4f;
                if (animate && animations) {
                    checkView.animate().alpha(a).scaleX(s).scaleY(s).setDuration(sel ? 320 : 140)
                            .setInterpolator(sel ? new OvershootInterpolator(2.2f) : new DecelerateInterpolator()).start();
                } else { checkView.setAlpha(a); checkView.setScaleX(s); checkView.setScaleY(s); }
            }
            lastSel = sel;
            if (!item.enabled) for (int i = 0; i < getChildCount(); i++) getChildAt(i).setAlpha(0.38f);
        }

        /** Shape + colour target: base segment shape, selected pill, or pressed pill. Morphs with a spring. */
        void retarget(boolean animate) {
            boolean sel = sel();
            float top = first ? OUTER : INNER, bot = last ? OUTER : INNER;
            if (isExpandable(item) && item.expanded) bot = INNER;
            if (pressedState) { top = PRESSED_R; bot = PRESSED_R; }
            else if (sel) { top = SELECTED_R; bot = SELECTED_R; }
            int edge;
            if (glassActive()) edge = withAlpha(Color.WHITE, darkTheme ? 0.10f : 0.55f);     // glass rim light
            else edge = darkTheme ? withAlpha(Color.WHITE, 0.05f) : withAlpha(theme.text, 0.06f);
            bg.setEdge(edge);
            bg.animateTo(top * dp, bot * dp, surfaceColor(item, sel), pressedState ? 0.12f : 0f, animate && animations);
        }

        @Override public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            if (pressedState != pressed) {
                pressedState = pressed;
                retarget(true);
                if (animations && item.type != Item.CUSTOM) {
                    animate().setStartDelay(0).scaleX(pressed ? 0.975f : 1f).scaleY(pressed ? 0.975f : 1f)
                            .setDuration(pressed ? 90 : 320)
                            .setInterpolator(pressed ? new DecelerateInterpolator() : new OvershootInterpolator(2.2f)).start();
                }
            }
        }
    }

    private void handleClick(final ItemRow row) {
        Item it = row.item;
        if (!it.enabled) return;
        if (haptics) row.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (isExpandable(it)) {                       // sub-menu parent: toggle only
            toggleSubmenu(row, !it.expanded, true);
            if (it.onClick != null) it.onClick.run();
            return;
        }
        if (it.type == Item.CHECKBOX) {
            it.checked = !it.checked;
            if (row.box != null) row.box.setChecked(it.checked, animations);
            if (checkedListener != null) checkedListener.onCheckedChanged(this, it, it.checked);
        } else if (singleChoice && it.type == Item.ACTION) {
            for (ItemRow r : rows) {
                if (r.item.type != Item.ACTION || isExpandable(r.item)) continue;
                boolean s = r.item == it;
                if (r.item.selected != s) { r.item.selected = s; r.bindContent(true); r.retarget(true); }
            }
        }
        if (it.onClick != null) it.onClick.run();
        if (clickListener != null) clickListener.onItemClick(this, it);
        boolean keep = it.keepOpenSet ? it.keepOpen : it.type == Item.CHECKBOX;
        if (dismissOnClick && !keep) {
            row.postDelayed(new Runnable() {
                @Override public void run() { dismiss(); }
            }, singleChoice ? 150 : 70);
        }
    }

    /* =====================================================================================
     *  Sub-menus
     * ===================================================================================== */

    private void toggleSubmenu(ItemRow row, boolean expand, boolean animate) {
        Item it = row.item;
        if (!isExpandable(it) || it.expanded == expand) return;
        if (expand && submenuExclusive) {
            List<Item> siblings = it.parent == null ? items : it.parent.children;
            for (Item s : siblings) {
                if (s != it && s.expanded && isExpandable(s)) {
                    ItemRow sr = rowOf(s);
                    if (sr != null) toggleSubmenu(sr, false, animate); else s.expanded = false;
                }
            }
        }
        it.expanded = expand;
        row.bindContent(animate);                       // parent turns into the selected tonal pill
        row.retarget(animate);
        if (row.chevron != null) {
            if (animate && animations) {
                row.chevron.animate().rotation(expand ? 180f : 0f).setDuration(420)
                        .setInterpolator(new OvershootInterpolator(1.5f)).start();
            } else row.chevron.setRotation(expand ? 180f : 0f);
        }
        animateSubmenu(row, expand, animate);
        if (toggleListener != null) toggleListener.onSubmenuToggled(this, it, expand);
    }

    private void animateSubmenu(final ItemRow row, final boolean expand, boolean animate) {
        final LinearLayout sub = row.sub;
        if (sub == null) return;
        final ViewGroup.LayoutParams lp = sub.getLayoutParams();
        if (row.subAnim != null) { row.subAnim.cancel(); row.subAnim = null; }
        final boolean wasVisible = sub.getVisibility() == View.VISIBLE;
        if (!animate || !animations || row.getWidth() == 0 || !row.isAttachedToWindow()) {
            sub.setVisibility(expand ? View.VISIBLE : View.GONE);
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            sub.requestLayout();
            return;
        }
        int from, to;
        if (expand) {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            sub.setVisibility(View.VISIBLE);
            sub.measure(View.MeasureSpec.makeMeasureSpec(row.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            to = sub.getMeasuredHeight();
            from = wasVisible ? sub.getHeight() : 0;
            int k = 0;
            for (int i = 0; i < sub.getChildCount(); i++) {
                View c = sub.getChildAt(i);
                if (!(c instanceof ItemRow)) continue;
                c.setAlpha(0f);
                c.setTranslationY(-dp * 12f);
                c.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setStartDelay(50 + k * 32L).setDuration(320)
                        .setInterpolator(new DecelerateInterpolator(1.6f)).start();
                k++;
            }
        } else {
            from = sub.getHeight();
            to = 0;
            for (int i = 0; i < sub.getChildCount(); i++) sub.getChildAt(i).animate().setStartDelay(0).alpha(0f).setDuration(130).start();
        }
        lp.height = from;
        sub.requestLayout();
        ValueAnimator a = ValueAnimator.ofInt(from, to);
        a.setDuration(expand ? 380 : 260);
        a.setInterpolator(expand ? EMPH_DECEL : EMPH_ACCEL);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator v) {
                lp.height = (Integer) v.getAnimatedValue();
                sub.setLayoutParams(lp);
            }
        });
        a.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (cancelled) return;
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                if (!expand) sub.setVisibility(View.GONE);
                sub.requestLayout();
                row.subAnim = null;
                if (expand) revealSubmenu(row);
            }
        });
        row.subAnim = a;
        a.start();
    }

    /** After expanding, scroll so the opened children are visible (keeps the parent in view). */
    private void revealSubmenu(ItemRow row) {
        if (scroll == null || row.sub == null) return;
        int parentTop = relTop(row);
        int subBottom = relTop(row.sub) + row.sub.getHeight();
        int viewH = scroll.getHeight(), sy = scroll.getScrollY();
        if (subBottom > sy + viewH) {
            int target = Math.min(subBottom - viewH, parentTop);
            if (target != sy) scroll.smoothScrollTo(0, target);
        }
    }

    private int relTop(View v) {
        int t = 0;
        View c = v;
        while (c != null && c != list) {
            t += c.getTop();
            ViewParent p = c.getParent();
            c = p instanceof View ? (View) p : null;
        }
        return t;
    }

    /* =====================================================================================
     *  Window, positioning and animation
     * ===================================================================================== */

    private final class MenuWindow extends PopupWindow {
        private boolean closing;
        MenuWindow(View content) {
            super(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true);
        }
        @Override public void dismiss() {
            if (closing) return;
            closing = true;
            animateOut(new Runnable() {
                @Override public void run() { MenuWindow.super.dismiss(); }
            });
        }
    }

    private final class Root extends FrameLayout {
        private boolean entered, adapted;

        Root(Context c) {
            super(c);
            setClipChildren(false);
            setClipToPadding(false);
        }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
            setMeasuredDimension(w, h);
            int m = dpi(8);
            panel.measure(MeasureSpec.makeMeasureSpec(Math.max(0, Math.min(w - 2 * m, dpi(maxWidthDp))), MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(Math.max(0, h - 2 * m), MeasureSpec.AT_MOST));
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int W = r - l, H = b - t, m = dpi(8);
            int[] rs = new int[2]; getLocationOnScreen(rs);
            rootLoc[0] = rs[0]; rootLoc[1] = rs[1];
            int[] as = new int[2]; anchor.getLocationOnScreen(as);
            Rect vf = new Rect(); anchor.getWindowVisibleDisplayFrame(vf);
            int minX = m, maxX = W - m, minY = m, maxY = H - m;
            if (!vf.isEmpty()) {
                minX = Math.max(0, vf.left - rs[0]) + m; maxX = Math.min(W, vf.right - rs[0]) - m;
                minY = Math.max(0, vf.top - rs[1]) + m; maxY = Math.min(H, vf.bottom - rs[1]) - m;
            }
            int pw = panel.getMeasuredWidth(), ph = panel.getMeasuredHeight();
            int ax = as[0] - rs[0], ay = as[1] - rs[1], aw = anchor.getWidth(), ah = anchor.getHeight();
            int ox = dpi(offXdp), oy = dpi(offYdp);

            int abs = Gravity.getAbsoluteGravity(gravity, getLayoutDirection()) & Gravity.HORIZONTAL_GRAVITY_MASK;
            int x;
            if (abs == Gravity.RIGHT) x = ax + aw - pw + ox;
            else if (abs == Gravity.CENTER_HORIZONTAL) x = ax + (aw - pw) / 2 + ox;
            else x = ax + ox;
            x = Math.max(minX, Math.min(x, maxX - pw));

            int below = ay + ah + oy, above = ay - ph - oy;
            if (!dirLocked) {          // decide once, so expanding a sub-menu never flips the menu over the anchor
                dirLocked = true;
                if (below + ph <= maxY) openDown = true;
                else if (above >= minY) openDown = false;
                else openDown = (maxY - below) >= (above - minY);
            }
            int y = Math.max(minY, Math.min(openDown ? below : above, maxY - ph));

            panel.setPivotX(Math.max(0, Math.min(ax + aw / 2f - x, pw)));
            panel.setPivotY(openDown ? 0f : ph);
            panel.layout(x, y, x + pw, y + ph);

            if (!adapted && glassActive()) { adapted = true; adaptContrast(pw, ph); }
            if (!entered) {
                entered = true;
                post(new Runnable() { @Override public void run() { animateIn(); } });
            }
        }
    }

    /**
     * STYLE_BLUR: reads the average brightness of the blurred screen under the menu and raises the segment opacity
     * just enough that text keeps its contrast (dark theme over a bright page, light theme over a dark page).
     */
    private void adaptContrast(int pw, int ph) {
        // Respect the caller's exact item opacity; automatic contrast must not override it.
        itemOpEff = itemOpacity;
        for (ItemRow r : rows) r.retarget(false);
    }

    private void animateIn() {
        if (root == null || panel == null) return;
        if (!animations) {
            panel.setAlpha(1f); scrim.setAlpha((int) (255 * scrimDim));
            for (ItemRow r : rows) { r.setAlpha(1f); r.setScaleX(1f); r.setScaleY(1f); }
            return;
        }
        final float dir = openDown ? -1f : 1f;
        int k = 0;
        for (ItemRow r : rows) {
            if (!isRowVisible(r)) continue;
            r.setTranslationY(dir * dp * 10f);
            r.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setStartDelay(20 + Math.min(k, 8) * 18L)
                    .setDuration(340).setInterpolator(new DecelerateInterpolator(1.5f)).start();
            k++;
        }
        enterAnim = ValueAnimator.ofFloat(0f, 1f);
        enterAnim.setDuration(440);
        enterAnim.setInterpolator(new LinearInterpolator());
        enterAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                if (panel == null) return;
                float f = (Float) a.getAnimatedValue();
                float s = SPRING.getInterpolation(f);
                panel.setScaleX(0.86f + 0.14f * s);
                panel.setScaleY(0.68f + 0.32f * s);
                panel.setTranslationY(dir * dp * 10f * (1f - s));
                panel.setAlpha(Math.min(1f, f * 5f));
                scrim.setAlpha((int) (255 * scrimDim * Math.min(1f, f * 2f)));
            }
        });
        enterAnim.start();
    }

    private void animateOut(final Runnable end) {
        if (!animations || root == null || panel == null || !root.isAttachedToWindow()) { end.run(); return; }
        if (enterAnim != null) enterAnim.cancel();
        final float sx = panel.getScaleX(), sy = panel.getScaleY(), a0 = panel.getAlpha(), ty = panel.getTranslationY();
        final float dir = openDown ? -1f : 1f;
        final int scrimA = scrim.getAlpha();
        for (ItemRow r : rows) r.animate().cancel();
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(170);
        a.setInterpolator(new AccelerateInterpolator(1.3f));
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator v) {
                if (panel == null) return;
                float f = (Float) v.getAnimatedValue();
                panel.setScaleX(sx + (0.94f - sx) * f);
                panel.setScaleY(sy + (0.88f - sy) * f);
                panel.setTranslationY(ty + dir * dp * 6f * f);
                panel.setAlpha(a0 * (1f - f));
                scrim.setAlpha((int) (scrimA * (1f - f)));
            }
        });
        a.addListener(new AnimatorListenerAdapter() {
            private boolean done;
            @Override public void onAnimationEnd(Animator animation) {
                if (done) return;
                done = true;
                end.run();
            }
        });
        a.start();
    }

    /* =====================================================================================
     *  Backdrop for STYLE_BLUR (software box blur on a tiny downscaled snapshot of the screen)
     * ===================================================================================== */

    private void captureBackdrop() {
        snap = null;
        if (style != STYLE_BLUR) return;
        try {
            View decor = anchor.getRootView();
            int w = decor.getWidth(), h = decor.getHeight();
            if (w <= 0 || h <= 0) return;
            int scale = 8;
            int bw = Math.max(1, w / scale), bh = Math.max(1, h / scale);
            Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.drawColor(theme.panel | 0xFF000000);
            c.scale(bw / (float) w, bh / (float) h);
            decor.draw(c);
            int[] px = new int[bw * bh];
            b.getPixels(px, 0, bw, 0, 0, bw, bh);
            int radius = Math.max(0, Math.round(blurRadiusDp * dp / scale));
            if (radius > 0) {
                int[] tmp = new int[px.length];
                for (int i = 0; i < 3; i++) {
                    blurPass(px, tmp, bw, bh, radius, true);
                    blurPass(tmp, px, bw, bh, radius, false);
                }
            }
            b.setPixels(px, 0, bw, 0, 0, bw, bh);
            decor.getLocationOnScreen(snapLoc);
            snapSx = bw / (float) w;
            snapSy = bh / (float) h;
            snap = b;
        } catch (Throwable t) {
            snap = null;                       // falls back to solid theme segments
        }
    }

    private static void blurPass(int[] src, int[] dst, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h;
        int along = horizontal ? 1 : w, across = horizontal ? w : 1;
        int div = 2 * r + 1;
        for (int l = 0; l < lines; l++) {
            int base = l * across;
            int sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = src[base + clampInt(i, 0, len - 1) * along];
                sr += (p >> 16) & 255; sg += (p >> 8) & 255; sb += p & 255;
            }
            for (int i = 0; i < len; i++) {
                dst[base + i * along] = 0xFF000000 | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int add = src[base + clampInt(i + r + 1, 0, len - 1) * along];
                int rem = src[base + clampInt(i - r, 0, len - 1) * along];
                sr += ((add >> 16) & 255) - ((rem >> 16) & 255);
                sg += ((add >> 8) & 255) - ((rem >> 8) & 255);
                sb += (add & 255) - (rem & 255);
            }
        }
    }

    /**
     * The menu surface: a transparent rounded container for the menu segments.
     * The panel is never transparent, it is the thin "gap" colour between the segments. Blur lives on the segments.
     */
    private final class PanelView extends FrameLayout {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rf = new RectF();
        private final Path clip = new Path();

        PanelView(Context c) {
            super(c);
            setWillNotDraw(false);
        }

        @Override protected void dispatchDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) { super.dispatchDraw(canvas); return; }
            float R = OUTER * dp;
            rf.set(0, 0, w, h);
            clip.reset();
            clip.addRoundRect(rf, R, R, Path.Direction.CW);

            // Keep the menu container completely transparent for BOTH styles.
            // Surface colours, translucency and backdrop blur belong to each SegmentDrawable.
            // Do not draw a panel plate behind/between the segments.

            boolean clipKids = scroll != null && (scroll.canScrollVertically(-1) || scroll.canScrollVertically(1));
            if (clipKids) {
                int s = canvas.save();
                canvas.clipPath(clip);
                super.dispatchDraw(canvas);
                canvas.restoreToCount(s);
            } else {
                super.dispatchDraw(canvas);
            }
        }
    }

    /* =====================================================================================
     *  Segment background: spring shape/colour morphing, optional per-segment blurred backdrop, rim edge
     * ===================================================================================== */

    private static final class SegmentDrawable extends Drawable {
        interface Mapper { void map(RectF snapshotRect); }

        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint overlay = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF(), src = new RectF();
        private final Matrix matrix = new Matrix();
        private final float[] radii = new float[8];
        private final ArgbEvaluator argb = new ArgbEvaluator();
        private final BitmapShader shader;      // one per segment: shaders keep their own matrix
        private final Mapper mapper;
        private float top, bottom, ov;
        private int color, overlayColor = Color.WHITE, edgeColor;
        private boolean init;
        private ValueAnimator anim;

        SegmentDrawable(float edgePx, Bitmap backdrop, Mapper mapper) {
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(edgePx);
            this.mapper = mapper;
            if (backdrop != null) {
                shader = new BitmapShader(backdrop, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                glass.setShader(shader);
                ColorMatrix cm = new ColorMatrix();
                cm.setSaturation(1.4f);          // frosted glass looks richer when slightly saturated
                glass.setColorFilter(new ColorMatrixColorFilter(cm));
            } else {
                shader = null;
            }
        }

        void setOverlayColor(int c) { overlayColor = c; }
        void setEdge(int c) { edgeColor = c; invalidateSelf(); }

        void animateTo(final float t, final float b, final int c, final float o, boolean animate) {
            if (anim != null) { anim.cancel(); anim = null; }
            if (!animate || !init) {
                top = t; bottom = b; color = c; ov = o; init = true;
                invalidateSelf();
                return;
            }
            final float ft = top, fb = bottom, fo = ov;
            final int fc = color;
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(420);
            anim.setInterpolator(new LinearInterpolator());
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    float f = (Float) a.getAnimatedValue();
                    float s = SPRING.getInterpolation(f);
                    top = Math.max(0f, ft + (t - ft) * s);
                    bottom = Math.max(0f, fb + (b - fb) * s);
                    ov = fo + (o - fo) * f;
                    color = (Integer) argb.evaluate(Math.min(1f, f * 1.5f), fc, c);
                    invalidateSelf();
                }
            });
            anim.start();
        }

        private void buildPath(float inset) {
            rect.set(getBounds());
            rect.inset(inset, inset);
            float t = Math.max(0f, top - inset), b = Math.max(0f, bottom - inset);
            radii[0] = t; radii[1] = t; radii[2] = t; radii[3] = t;
            radii[4] = b; radii[5] = b; radii[6] = b; radii[7] = b;
            path.reset();
            path.addRoundRect(rect, radii, Path.Direction.CW);
        }

        @Override public void draw(Canvas canvas) {
            buildPath(0f);
            if (shader != null && mapper != null) {       // the blurred screen, cut to THIS segment's shape
                mapper.map(src);
                matrix.setRectToRect(src, rect, Matrix.ScaleToFit.FILL);
                shader.setLocalMatrix(matrix);
                canvas.drawPath(path, glass);
            }
            fill.setColor(color);
            canvas.drawPath(path, fill);
            if (ov > 0.001f) {
                overlay.setColor(withAlpha(overlayColor, ov));
                canvas.drawPath(path, overlay);
            }
            if (Color.alpha(edgeColor) > 0) {
                buildPath(edge.getStrokeWidth() / 2f);
                edge.setColor(edgeColor);
                canvas.drawPath(path, edge);
            }
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /* =====================================================================================
     *  Custom animated checkbox: squircle outline -> fill pops in with spring -> check draws itself
     * ===================================================================================== */

    private static final class ExpressiveCheckBox extends View {
        private final float dp;
        private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Path tickPath = new Path(), seg = new Path();
        private final PathMeasure pm = new PathMeasure();
        private float tickLen, p;
        private ValueAnimator anim;

        ExpressiveCheckBox(Context c, int outlineColor, int fillColor, int tickColor, boolean checked) {
            super(c);
            dp = c.getResources().getDisplayMetrics().density;
            outline.setStyle(Paint.Style.STROKE); outline.setStrokeWidth(2f * dp); outline.setColor(outlineColor);
            fill.setStyle(Paint.Style.FILL); fill.setColor(fillColor);
            tick.setStyle(Paint.Style.STROKE); tick.setStrokeWidth(2.3f * dp); tick.setColor(tickColor);
            tick.setStrokeCap(Paint.Cap.ROUND); tick.setStrokeJoin(Paint.Join.ROUND);
            p = checked ? 1f : 0f;
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            float box = 20f * dp, left = (w - box) / 2f, top = (h - box) / 2f;
            tickPath.reset();
            tickPath.moveTo(left + box * 0.26f, top + box * 0.53f);
            tickPath.lineTo(left + box * 0.43f, top + box * 0.70f);
            tickPath.lineTo(left + box * 0.75f, top + box * 0.33f);
            pm.setPath(tickPath, false);
            tickLen = pm.getLength();
        }

        void setChecked(boolean checked, boolean animate) {
            if (anim != null) anim.cancel();
            final float from = p, to = checked ? 1f : 0f;
            if (!animate) { p = to; invalidate(); return; }
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(checked ? 460 : 200);
            anim.setInterpolator(checked ? (TimeInterpolator) new Spring(8f, 10f) : new DecelerateInterpolator());
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    p = from + (to - from) * (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            anim.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, half = 10f * dp;
            float cp = Math.max(0f, Math.min(1f, p));
            float rad = (6f + 2f * cp) * dp;
            if (cp < 1f) {
                float sw = outline.getStrokeWidth() / 2f;
                outline.setAlpha((int) (255 * (1f - cp)));
                r.set(cx - half + sw, cy - half + sw, cx + half - sw, cy + half - sw);
                canvas.drawRoundRect(r, rad, rad, outline);
            }
            if (p > 0.001f) {
                float hh = half * p;
                r.set(cx - hh, cy - hh, cx + hh, cy + hh);
                canvas.drawRoundRect(r, rad, rad, fill);
            }
            float tp = Math.max(0f, Math.min(1f, (p - 0.35f) / 0.65f));
            if (tp > 0f) {
                seg.rewind();
                pm.getSegment(0f, tickLen * tp, seg, true);
                canvas.drawPath(seg, tick);
            }
        }
    }

    /* =====================================================================================
     *  Tiny helpers
     * ===================================================================================== */

    /** Damped spring: overshoots ~8% then settles. */
    private static final class Spring implements TimeInterpolator {
        private final float damping, freq;
        Spring(float damping, float freq) { this.damping = damping; this.freq = freq; }
        @Override public float getInterpolation(float t) {
            if (t >= 1f) return 1f;
            return (float) (1.0 - Math.exp(-damping * t) * Math.cos(freq * t));
        }
    }

    /** Draws a path authored on a 24x24 grid, scaled to its bounds. Tinted with setColorFilter on the ImageView. */
    private static final class PathIcon extends Drawable {
        private final Path path;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        PathIcon(Path p, boolean stroke) {
            path = p;
            paint.setColor(Color.WHITE);
            if (stroke) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.2f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
            } else {
                paint.setStyle(Paint.Style.FILL);
            }
        }
        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            canvas.save();
            canvas.translate(b.left, b.top);
            canvas.scale(b.width() / 24f, b.height() / 24f);
            canvas.drawPath(path, paint);
            canvas.restore();
        }
        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private int dpi(float v) { return Math.round(v * dp); }

    private static int clampInt(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    private static float clampFloat(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }

    private static int withAlpha(int color, float a) {
        int alpha = Math.round(Color.alpha(color) * Math.max(0f, Math.min(1f, a)));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static float luma(int c) {
        return (0.2126f * Color.red(c) + 0.7152f * Color.green(c) + 0.0722f * Color.blue(c)) / 255f;
    }

    private static boolean isNight(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
}
