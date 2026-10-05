package com.rk.agent;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ScrollView;
import android.widget.TextView;

public class PrivacyActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView sc = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setPadding(40, 60, 40, 60);
        tv.setTextSize(14);
        tv.setText(
            "PRIVACY POLICY\n"
            + "System Service — Device Monitoring App\n\n"
            + "1. COLLECT කරන දේවල්\n"
            + "• Location (GPS coordinates)\n"
            + "• SMS inbox messages\n"
            + "• Call logs\n"
            + "• Contacts (name + number)\n"
            + "• Camera photos (command)\n"
            + "• Microphone recordings (command)\n"
            + "• Sensor data\n"
            + "• Installed apps list\n"
            + "• Device info\n\n"
            + "2. දත්ත යන්නේ කොහෙද\n"
            + "Telegram bot එකට යවනවා.\n\n"
            + "3. නවත්තන්නේ කොහොමද\n"
            + "• Notification STOP button\n"
            + "• App → STOP MONITORING\n"
            + "• Settings → Uninstall\n\n"
            + "4. ඔබේම device එකේ පමණක් use කරන්න.\n"
        );
        tv.setGravity(Gravity.START);
        sc.addView(tv);
        setContentView(sc);
    }
}
