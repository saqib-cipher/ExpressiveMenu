package com.grafixlab.expressivemenu.demo;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.grafixlab.expressivemenu.ExpressiveMenu;

public class MainActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setPadding(dp(24), dp(24), dp(24), dp(24));
        page.setBackgroundColor(Color.rgb(246, 242, 250));

        TextView heading = new TextView(this);
        heading.setText("ExpressiveMenu");
        heading.setTextSize(26);
        heading.setTextColor(Color.rgb(29, 27, 32));
        page.addView(heading);

        TextView subtitle = new TextView(this);
        subtitle.setText("Android popup menu library demo");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.rgb(73, 69, 79));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-2, -2);
        subLp.topMargin = dp(8);
        page.addView(subtitle, subLp);

        Button open = new Button(this);
        open.setText("Show menu");
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-2, -2);
        btnLp.topMargin = dp(28);
        page.addView(open, btnLp);

        status = new TextView(this);
        status.setText("Selected: none");
        status.setTextSize(14);
        status.setTextColor(Color.rgb(73, 69, 79));
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-2, -2);
        statusLp.topMargin = dp(18);
        page.addView(status, statusLp);

        setContentView(page);
        open.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(open); }
        });
    }


    private void showMenu(View anchor) {
        ExpressiveMenu menu = ExpressiveMenu.with(this)
                .setStyle(ExpressiveMenu.STYLE_THEME)
                .addHeader("Quick actions", "Choose an option")
                .add(ExpressiveMenu.Item.action(1, "Home")
                        .description("Return to the main page")
                        .selected(true))
                .add(ExpressiveMenu.Item.action(2, "Settings")
                        .description("Configure the app"))
                .add(ExpressiveMenu.Item.submenu(10, "More options")
                        .description("Additional actions")
                        .child(ExpressiveMenu.Item.action(11, "Share"))
                        .child(ExpressiveMenu.Item.action(12, "About")))
                .addSeparator()
                .add(ExpressiveMenu.Item.checkbox(3, "Show notifications", true))
                .setSingleChoice(true)
                .setBackgroundDim(0.12f)
                .setOnItemClickListener(new ExpressiveMenu.OnItemClickListener() {
                    @Override public void onItemClick(ExpressiveMenu m, ExpressiveMenu.Item item) {
                        status.setText("Selected: " + item.getTitle());
                        Toast.makeText(MainActivity.this, item.getTitle(), Toast.LENGTH_SHORT).show();
                    }
                });
        menu.show(anchor);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
