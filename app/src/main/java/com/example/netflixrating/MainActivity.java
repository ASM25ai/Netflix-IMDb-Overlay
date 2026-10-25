package com.example.netflixrating;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(36, 42, 36, 30);
        TextView title = new TextView(this); title.setText("Netflix IMDb Overlay"); title.setTextSize(24); root.addView(title);
        TextView info = new TextView(this);
        info.setText("Shows IMDb ratings over Netflix. Automatic detection works only when Netflix exposes a readable title. A floating search button is always available.\n\nGet a personal API key at omdbapi.com/apikey.aspx (free tier may have limits). Ratings are provided by OMDb, not Netflix or IMDb directly.");
        info.setPadding(0,20,0,20); root.addView(info);
        EditText key = new EditText(this); key.setHint("OMDb API key"); key.setSingleLine(true); key.setInputType(InputType.TYPE_CLASS_TEXT); key.setText(getPreferences(0).getString("api_key", "")); root.addView(key);
        Button save = new Button(this); save.setText("Save API key"); root.addView(save);
        save.setOnClickListener(v -> { getPreferences(0).edit().putString("api_key",key.getText().toString().trim()).apply(); Toast.makeText(this,"Saved",Toast.LENGTH_SHORT).show(); });
        Button overlay = new Button(this); overlay.setText("1. Allow display over other apps"); root.addView(overlay);
        overlay.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()))));
        Button access = new Button(this); access.setText("2. Enable Netflix title detection"); root.addView(access);
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        Button show = new Button(this); show.setText("3. Show floating rating button"); root.addView(show);
        show.setOnClickListener(v -> { if(!Settings.canDrawOverlays(this)) { Toast.makeText(this,"Grant overlay permission first",Toast.LENGTH_LONG).show(); return; } RatingAccessibilityService s = RatingAccessibilityService.instance; if(s != null) s.showOverlay(); else Toast.makeText(this,"Enable accessibility service first",Toast.LENGTH_LONG).show(); });
        Button netflix = new Button(this); netflix.setText("Open Netflix"); root.addView(netflix);
        netflix.setOnClickListener(v -> { Intent i=getPackageManager().getLaunchIntentForPackage("com.netflix.mediaclient"); if(i!=null)startActivity(i); else Toast.makeText(this,"Netflix not installed",Toast.LENGTH_SHORT).show(); });
        TextView privacy = new TextView(this); privacy.setText("Privacy: only Netflix accessibility events are monitored. The selected title is sent to OMDb for a rating lookup. No analytics or account login. To stop, disable the accessibility service."); privacy.setTextSize(12); privacy.setPadding(0,24,0,0); root.addView(privacy);
        ScrollView sc = new ScrollView(this); sc.addView(root); setContentView(sc);
    }
}
