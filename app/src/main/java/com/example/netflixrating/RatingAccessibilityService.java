package com.example.netflixrating;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import org.json.JSONObject;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class RatingAccessibilityService extends AccessibilityService {
    public static RatingAccessibilityService instance;
    private WindowManager wm; private LinearLayout bubble; private TextView badge; private WindowManager.LayoutParams params;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private String lastTitle = ""; private long lastLookup = 0; private boolean showingDialog = false;
    private static final Set<String> IGNORE = new HashSet<>(Arrays.asList("play","episodes","more info","my list","resume","download","downloads","netflix","search","home","games","new & hot","categories","movies","tv shows","continue watching","trailer","trailers & more","cast","audio & subtitles","similar titles","remind me","preview","details","watch","rate","skip intro","next episode","volume","pause","back","close"));
    @Override protected void onServiceConnected() { instance=this; wm=(WindowManager)getSystemService(WINDOW_SERVICE); if(Settings.canDrawOverlays(this))showOverlay(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event == null || event.getPackageName()==null || !"com.netflix.mediaclient".contentEquals(event.getPackageName())) return;
        if(!Settings.canDrawOverlays(this))return;
        main.removeCallbacks(scanTask); main.postDelayed(scanTask,800);
    }
    private final Runnable scanTask = () -> {
        AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null)return;
        ArrayList<String> strings=new ArrayList<>(); gather(root,strings,0); root.recycle();
        String candidate="";
        for(String s:strings){ if(s.startsWith("TITLE: ")){ candidate=s.substring(7);break; } }
        if(!candidate.isEmpty() && !candidate.equalsIgnoreCase(lastTitle) && System.currentTimeMillis()-lastLookup>1200) lookup(candidate);
    };
    private void gather(AccessibilityNodeInfo node,List<String> strings,int depth){
        if(node==null||depth>20||strings.size()>100)return;
        CharSequence text=node.getText(), desc=node.getContentDescription();
        String id=node.getViewIdResourceName();
        if(id!=null && id.toLowerCase(Locale.ROOT).matches(".*(title|show_name|movie_name).*")) {
            String value=text!=null?text.toString().trim():desc!=null?desc.toString().trim():"";
            if(valid(value)) strings.add("TITLE: "+value);
        }
        if(desc!=null){String d=desc.toString().trim(); if(d.toLowerCase(Locale.ROOT).startsWith("title: ")){String val=d.substring(7).trim();if(valid(val))strings.add("TITLE: "+val);}}
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null){gather(child,strings,depth+1);child.recycle();}}
    }
    private boolean valid(String s){return s.length()>1&&s.length()<100&&!IGNORE.contains(s.toLowerCase(Locale.ROOT))&&!s.matches(".*[\\n\\r].*");}
    public void showOverlay(){
        if(bubble!=null||!Settings.canDrawOverlays(this))return;
        bubble=new LinearLayout(this);bubble.setOrientation(LinearLayout.HORIZONTAL);bubble.setGravity(Gravity.CENTER_VERTICAL);bubble.setPadding(14,8,14,8);
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(26,26,26));bg.setCornerRadius(40);bg.setStroke(2,Color.rgb(245,197,24));bubble.setBackground(bg);
        badge=new TextView(this);badge.setText("★ IMDb  —");badge.setTextColor(Color.rgb(245,197,24));badge.setTextSize(14);badge.setPadding(8,4,12,4);bubble.addView(badge);
        TextView search=new TextView(this);search.setText("⌕");search.setTextColor(Color.WHITE);search.setTextSize(26);search.setPadding(12,0,6,0);bubble.addView(search);
        search.setOnClickListener(v->manualSearch());badge.setOnClickListener(v->manualSearch());
        params=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.START;params.x=20;params.y=190;
        bubble.setOnTouchListener(new View.OnTouchListener(){float downX,downY;int startX,startY;boolean moved;
            public boolean onTouch(View v,android.view.MotionEvent e){switch(e.getAction()){
                case MotionEvent.ACTION_DOWN:downX=e.getRawX();downY=e.getRawY();startX=params.x;startY=params.y;moved=false;return true;
                case MotionEvent.ACTION_MOVE:if(Math.abs(e.getRawX()-downX)>8||Math.abs(e.getRawY()-downY)>8)moved=true;params.x=startX+(int)(e.getRawX()-downX);params.y=startY+(int)(e.getRawY()-downY);wm.updateViewLayout(bubble,params);return true;
                case MotionEvent.ACTION_UP:if(!moved)manualSearch();return true;
            }return false;}});
        try{wm.addView(bubble,params);}catch(Exception ex){bubble=null;}
    }
    private void manualSearch(){
        if(showingDialog)return;showingDialog=true;
        final EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Movie or series title");input.setText(lastTitle);
        LinearLayout box=new LinearLayout(this);box.setPadding(24,8,24,8);box.addView(input);
        final android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Search IMDb rating").setView(box)
            .setPositiveButton("Search",(d,w)->{String q=input.getText().toString().trim();if(valid(q))lookup(q);showingDialog=false;})
            .setNegativeButton("Cancel",(d,w)->showingDialog=false).setOnCancelListener(d->showingDialog=false).create();
        if(dialog.getWindow()!=null)dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        dialog.show();
    }
    private void lookup(String title){
        String key=getSharedPreferences("MainActivity",MODE_PRIVATE).getString("api_key","").trim();
        lastTitle=title;lastLookup=System.currentTimeMillis();
        if(key.isEmpty()){update("★ Add OMDb key");return;}
        update("★ Searching…");
        pool.submit(()->{
            String result="★ Not found";
            try{
                String url="https://www.omdbapi.com/?apikey="+URLEncoder.encode(key,"UTF-8")+"&t="+URLEncoder.encode(title,"UTF-8");
                HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(7000);c.setRequestMethod("GET");
                try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buf=new byte[2048];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>65536)throw new IOException("Response too large");}
                    JSONObject obj=new JSONObject(out.toString("UTF-8"));
                    if("True".equalsIgnoreCase(obj.optString("Response"))){String rating=obj.optString("imdbRating","N/A");result="★ IMDb "+rating+("N/A".equals(rating)?"":"/10");}
                    else result="★ "+obj.optString("Error","Not found");
                }finally{c.disconnect();}
            }catch(Exception ex){result="★ Lookup failed";}
            final String value=result;main.post(()->{if(title.equals(lastTitle))update(value);});
        });
    }
    private void update(String value){main.post(()->{if(badge!=null)badge.setText(value);});}
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){instance=null;main.removeCallbacksAndMessages(null);pool.shutdownNow();if(bubble!=null){try{wm.removeView(bubble);}catch(Exception ignored){}bubble=null;}super.onDestroy();}
}
