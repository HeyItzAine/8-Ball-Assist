package app.railguide;
import android.app.*;
import android.content.*;
import android.media.projection.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
 private final int bg=0xfff3f0e8,panel=0xffe7e8df,ink=0xff193c32,muted=0xff58635b,accent=0xff245746;
 private Switch controlsToggle;private TextView permissionState;private Button permissionButton,startButton;private boolean requesting;
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(8));return d;}
 private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(dp(3),1);return v;}
 private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v;}
 private void gap(LinearLayout v,int h){v.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
 private LinearLayout card(LinearLayout parent){LinearLayout v=column();v.setPadding(dp(20),dp(20),dp(20),dp(20));v.setBackground(shape(panel));parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;}
 private Button button(String label,boolean primary){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(16);b.setTypeface(null,1);b.setTextColor(primary?0xfffffcf4:ink);b.setMinHeight(dp(52));b.setPadding(dp(18),dp(12),dp(18),dp(12));b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3344aaaa),shape(primary?accent:panel),null));b.setStateListAnimator(null);return b;}
 private void addButton(LinearLayout p,Button b){p.addView(b,new LinearLayout.LayoutParams(-1,-2));}
 public void onCreate(Bundle state){
  super.onCreate(state);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  FrameLayout safe=new FrameLayout(this);safe.setBackgroundColor(bg);if(Build.VERSION.SDK_INT>=29)safe.setForceDarkAllowed(false);
  safe.setOnApplyWindowInsetsListener((v,insets)->{int l=insets.getSystemWindowInsetLeft(),t=insets.getSystemWindowInsetTop(),r=insets.getSystemWindowInsetRight(),b=insets.getSystemWindowInsetBottom();if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){android.view.DisplayCutout c=insets.getDisplayCutout();l=Math.max(l,c.getSafeInsetLeft());t=Math.max(t,c.getSafeInsetTop());r=Math.max(r,c.getSafeInsetRight());b=Math.max(b,c.getSafeInsetBottom());}v.setPadding(l,t,r,b);return insets.consumeSystemWindowInsets();});
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);safe.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout frame=column();frame.setGravity(Gravity.CENTER_HORIZONTAL);frame.setPadding(dp(24),dp(24),dp(24),dp(24));scroll.addView(frame,new ScrollView.LayoutParams(-1,-1));
  LinearLayout root=column();frame.addView(root,new LinearLayout.LayoutParams(-1,-2));
  frame.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int target=Math.min(frame.getWidth()-frame.getPaddingLeft()-frame.getPaddingRight(),dp(560));if(target>0&&root.getLayoutParams().width!=target){root.getLayoutParams().width=target;root.requestLayout();}});
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header);
  TextView name=text("8 Ball Assist",30,ink);name.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
  ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.launcher_legacy);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);header.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
  gap(root,24);root.addView(new TablePreview(this),new LinearLayout.LayoutParams(-1,-2));gap(root,8);
  root.addView(text("Example trajectory · not a live view",12,muted));gap(root,24);
  TextView purpose=text("Automatic line tracking",22,ink);purpose.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));root.addView(purpose);gap(root,8);
  root.addView(text("Extend the target ball’s guide across the table while you aim.",16,muted));gap(root,24);
  divider(root);gap(root,16);permissionState=text("Screen overlay permission needed",16,ink);permissionState.setTypeface(null,1);root.addView(permissionState);gap(root,4);root.addView(text("Allows the guide to appear over your game.",14,muted));gap(root,12);
  permissionButton=button("Allow screen overlay",false);permissionButton.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);permissionButton.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));addButton(root,permissionButton);
  gap(root,16);controlsToggle=new Switch(this);controlsToggle.setText("Show floating controls");controlsToggle.setTextColor(ink);controlsToggle.setTextSize(16);controlsToggle.setMinHeight(dp(48));controlsToggle.setChecked(new PresetStore(this).settings().getBoolean("show_controls",false));controlsToggle.setOnCheckedChangeListener((v,checked)->new PresetStore(this).settings().edit().putBoolean("show_controls",checked).apply());root.addView(controlsToggle,new LinearLayout.LayoutParams(-1,-2));root.addView(text("Off by default. Paths stay visible. Stop tracking here or from the notification.",14,muted));
  gap(root,24);startButton=button("Start overlay",true);startButton.setOnClickListener(v->startCapture());addButton(root,startButton);gap(root,8);root.addView(text("Choose Entire screen, then open your game.",14,muted));gap(root,24);divider(root);
  Button help=button("How to use",false);help.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18245746),shape(bg),null));help.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);addButton(root,help);
  TextView details=text("Open your game in landscape. Paths match the ball colors. The white solid path follows the cue ball; dashed paths follow object balls.\n\nIf the table isn’t detected, tap Table in the floating toolbar and mark the two inner cushion corners.\n\nFlip reverses direction. Rails sets the number of bounces. R− and R+ adjust ball radius. Pause hides the path.\n\nMove the toolbar by dragging its status text. Tap × to stop.",16,muted);details.setPadding(0,dp(8),0,dp(16));details.setVisibility(View.GONE);root.addView(details);
  help.setOnClickListener(v->{boolean open=details.getVisibility()!=View.VISIBLE;details.setVisibility(open?View.VISIBLE:View.GONE);help.setText(open?"Hide instructions":"How to use");if(Build.VERSION.SDK_INT>=30)help.setStateDescription(open?"Expanded":"Collapsed");});
  gap(root,8);Button advanced=button("Presets & advanced settings",false);advanced.setOnClickListener(v->startActivity(new Intent(this,AdvancedActivity.class)));addButton(root,advanced);
  gap(root,8);Button stop=button("Stop overlay",false);stop.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);stop.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18245746),shape(bg),null));stop.setOnClickListener(v->{stopService(new Intent(this,GuideService.class));Toast.makeText(this,"Overlay stopped",Toast.LENGTH_SHORT).show();});addButton(root,stop);
  gap(root,16);divider(root);gap(root,16);root.addView(text("Processed on your phone. Nothing recorded.",12,muted));gap(root,8);root.addView(text("Collision paths are approximate. Spin and stopping distances are not modeled.",12,muted));
  setContentView(safe);safe.requestApplyInsets();
 }
 protected void onResume(){super.onResume();if(permissionState==null)return;if(controlsToggle!=null)controlsToggle.setChecked(new PresetStore(this).settings().getBoolean("show_controls",false));boolean granted=Settings.canDrawOverlays(this);permissionState.setText(granted?"Screen overlay allowed":"Screen overlay permission needed");permissionState.setTextColor(granted?accent:ink);permissionButton.setText(granted?"Manage permission":"Allow screen overlay");startButton.setEnabled(granted&&!requesting);startButton.setAlpha(granted?1f:.45f);}
 public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused&&Build.VERSION.SDK_INT>=30&&getWindow().getInsetsController()!=null)getWindow().getInsetsController().setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);}
 public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==13){requesting=false;startCapture();}}
 private void startCapture(){if(requesting)return;if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED&&!new PresetStore(this).settings().getBoolean("asked_notifications",false)){new PresetStore(this).settings().edit().putBoolean("asked_notifications",true).apply();requesting=true;requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},13);return;}if(!Settings.canDrawOverlays(this)){Toast.makeText(this,"Allow overlay access first",Toast.LENGTH_LONG).show();return;}stopService(new Intent(this,GuideService.class));MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);Intent capture=Build.VERSION.SDK_INT>=34?m.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()):m.createScreenCaptureIntent();requesting=true;startButton.setText("Opening screen sharing…");startButton.setEnabled(false);startActivityForResult(capture,12);}
 protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==12){requesting=false;startButton.setText("Start overlay");startButton.setEnabled(Settings.canDrawOverlays(this));if(result==RESULT_OK&&data!=null){startForegroundService(new Intent(this,GuideService.class).putExtra("result",result).putExtra("capture",data));moveTaskToBack(true);}}}
 private void divider(LinearLayout parent){View line=new View(this);line.setBackgroundColor(0xffc5cbbf);parent.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}
 private class TablePreview extends View {
  private final android.graphics.Paint p=new android.graphics.Paint(3);
  TablePreview(Context c){super(c);setContentDescription("Example pool table with a ball path reflecting off two cushions. Illustrative only.");}
  protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec);setMeasuredDimension(width,Math.round(width*.625f));}
  protected void onDraw(android.graphics.Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();c.save();c.scale(w/320f,h/200f);
   p.setStyle(android.graphics.Paint.Style.FILL);p.setColor(0xff163a30);c.drawRoundRect(0,0,320,200,12,12,p);p.setColor(0xff306854);c.drawRoundRect(14,14,306,186,6,6,p);p.setColor(0xff245746);c.drawRect(23,23,297,177,p);
   p.setColor(0xffb4c9ac);for(int x=72;x<300;x+=44){c.drawCircle(x,18,1.4f,p);c.drawCircle(x,182,1.4f,p);}for(int y=62;y<160;y+=38){c.drawCircle(18,y,1.4f,p);c.drawCircle(302,y,1.4f,p);}
   p.setColor(0xff0b201a);float[][] holes={{20,20},{160,18},{300,20},{20,180},{160,182},{300,180}};for(float[] q:holes)c.drawCircle(q[0],q[1],7,p);
   p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0xffebc872);p.setPathEffect(new android.graphics.DashPathEffect(new float[]{6,5},0));android.graphics.Path path=new android.graphics.Path();path.moveTo(116,124);path.lineTo(210,29);path.lineTo(291,111);path.lineTo(231,171);c.drawPath(path,p);p.setPathEffect(null);
   p.setStrokeWidth(1);p.setColor(0xffb4c9ac);c.drawLine(56,146,101,132,p);p.setStyle(android.graphics.Paint.Style.FILL);p.setColor(0xfffffcf4);c.drawCircle(56,146,7,p);p.setColor(0xffebc872);c.drawCircle(116,124,7,p);p.setColor(0xff163a30);c.drawCircle(116,124,3,p);p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0xfffffcf4);c.drawCircle(102,132,7,p);c.restore();
  }
 }
}
