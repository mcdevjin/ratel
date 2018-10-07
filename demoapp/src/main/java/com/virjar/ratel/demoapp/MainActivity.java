package com.virjar.ratel.demoapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        super.onResume();
        TextView textView = findViewById(R.id.indexTextView);
        textView.setText(text());
    }

    private String text() {
        return "原始文本";
    }

}
