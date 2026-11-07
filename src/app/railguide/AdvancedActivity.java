package app.railguide;
import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class AdvancedActivity extends Activity {
 private PresetStore store;private LinearLayout root;
 public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused&&Build.VERSION.SDK_INT>=30&&getWindow().getInsetsController()!=null)getWindow().getInsetsController().setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);}
 public void onCreate(Bundle state){setTheme(android.R.style.Theme_Material_Light_NoActionBar);super.onCreate(state);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);store=new PresetStore(this);render();}
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private void label(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextColor(0xff193c32);t.setTextSize(size);t.setPadding(0,dp(12),0,dp(12));root.addView(t);}
 private void toggle(String label,String key,boolean value){Switch s=new Switch(this);s.setText(label);s.setTextColor(0xff193c32);s.setTextSize(16);s.setSingleLine(false);s.setMinHeight(dp(56));s.setPadding(0,dp(8),0,dp(8));s.setChecked(store.settings().getBoolean(key,value));s.setOnCheckedChangeListener((v,checked)->store.settings().edit().putBoolean(key,checked).apply());root.addView(s,new LinearLayout.LayoutParams(-1,-2));}
 private void action(String title,Runnable run){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(0xff193c32);android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(0xffe7e8df);shape.setCornerRadius(dp(8));b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33245746),shape,null));b.setPadding(dp(16),dp(12),dp(16),dp(12));b.setMinHeight(dp(52));b.setOnClickListener(v->run.run());LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.bottomMargin=dp(8);root.addView(b,bp);}
 private void render(){
  ScrollView scroll=new ScrollView(this);if(Build.VERSION.SDK_INT>=29)scroll.setForceDarkAllowed(false);scroll.setFillViewport(true);scroll.setBackgroundColor(0xfff3f0e8);scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
  root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(24),dp(16),dp(24),dp(24));LinearLayout measure=new LinearLayout(this);measure.setOrientation(1);measure.setGravity(Gravity.CENTER_HORIZONTAL);scroll.addView(measure);measure.addView(root,new LinearLayout.LayoutParams(-1,-2));measure.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int width=Math.min(measure.getWidth(),dp(608));if(width>0&&root.getLayoutParams().width!=width){root.getLayoutParams().width=width;root.requestLayout();}});setContentView(scroll);scroll.requestApplyInsets();
  action("Back",this::finish);label("Advanced settings",26);
  toggle("Use a preset when table detection fails","fallback_enabled",false);
  PresetStore.Preset fallback=store.get(store.settings().getString("default_id",""));action("Default preset: "+(fallback==null?"None selected":fallback.name),()->chooseDefault());
  label("Fallback is off by default. It only applies when no table is detected and the saved screen proportions match. Loading a preset from the overlay pins it until you choose Auto or rotate the screen.",14);
  toggle("Show white cue-ball path","show_cue",true);toggle("Account for detected ball collisions","collisions",true);toggle("Bridge very short guide dropouts","history",true);
  label("Blue follows the visible cue-ball guide when available. Otherwise it is a no-spin estimate. History is limited to 350 ms and needs a visible contact marker, stable cue ball and a detected target. Lines indicate direction, not stopping distance.",14);
  label("Saved tables",22);List<PresetStore.Preset> all=store.list();if(all.isEmpty())label("In the overlay, choose Pockets to review the six markers, then Presets → Save reviewed table.",16);
  for(PresetStore.Preset p:all)action(p.name+" · manage",()->new AlertDialog.Builder(this).setTitle(p.name).setItems(new String[]{"Rename","Set as default","Delete"},(d,which)->{if(which==0){EditText input=new EditText(this);input.setText(p.name);new AlertDialog.Builder(this).setTitle("Preset name").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save",(x,y)->{String name=input.getText().toString().trim();if(!name.isEmpty()){p.name=name;store.save(p);render();}}).show();}else if(which==1){store.settings().edit().putString("default_id",p.id).apply();render();}else new AlertDialog.Builder(this).setMessage("Delete this preset?").setNegativeButton("Cancel",null).setPositiveButton("Delete",(x,y)->{store.delete(p.id);render();}).show();}).show());
 }
 private void chooseDefault(){List<PresetStore.Preset> list=store.list();String[] names=new String[list.size()+1];names[0]="None";for(int i=0;i<list.size();i++)names[i+1]=list.get(i).name;new AlertDialog.Builder(this).setTitle("Fallback table").setItems(names,(d,which)->{store.settings().edit().putString("default_id",which==0?"":list.get(which-1).id).apply();render();}).show();}
}
