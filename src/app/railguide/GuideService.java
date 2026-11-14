package app.railguide;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import java.util.*;

public class GuideService extends Service {
 private final Handler main=new Handler(Looper.getMainLooper());
 private HandlerThread worker; private Handler background;
 private int[] framePixels;
 private MediaProjection projection;private VirtualDisplay display;private ImageReader reader;
 private WindowManager windows;private GuideView overlay;private LinearLayout toolbar;private TextView status;
 private WindowManager.LayoutParams overlayParams,toolbarParams;
 private final Detector detector=new Detector();
 private final TemporalTracker history=new TemporalTracker();
 private final AimFilter aimFilter=new AimFilter();
 private android.content.SharedPreferences.OnSharedPreferenceChangeListener settingsListener;
 private boolean expanded=false;private Button moreControl;private long lastAccepted;
 private final Runnable staleCheck=new Runnable(){public void run(){if(closing)return;if(!paused&&overlay!=null&&lastAccepted>0&&SystemClock.elapsedRealtime()-lastAccepted>750){clearPaths();aimFilter.reset();status.setText("Waiting for a fresh frame");}main.postDelayed(this,200);}};
 private PresetStore presets;private volatile int profile=0,revision=0;private int workerRevision=-1;private String historySource="";
 private volatile double[][] savedPockets;private boolean showPockets=false;private int draggedPocket=-1;
 private String tableSource="Auto";private AlertDialog presetDialog;private Button pauseControl;private double effectiveRadiusScale=.0075;
 private boolean menuWasPaused,reviewWasPaused;

 private volatile boolean paused=false,closing=false;private volatile Geometry.Box manual;
 private int screenW,screenH,captureW,captureH,bounces=3,calibration=0;private double radiusRatio=.0075;private boolean flipped=false;
 private double firstX,firstY;private long lastFrame;private Detector.Result lastResult;
 public IBinder onBind(Intent intent){return null;}
 public int onStartCommand(Intent intent,int flags,int id){
  if(intent==null||"stop".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
  if("controls".equals(intent.getAction())){if(presets!=null)presets.settings().edit().putBoolean("show_controls",true).apply();else stopSelf();return START_NOT_STICKY;}
  if(projection!=null)return START_NOT_STICKY;
  NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("tracking","Live tracking",NotificationManager.IMPORTANCE_LOW));
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,GuideService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  PendingIntent controls=PendingIntent.getService(this,3,new Intent(this,GuideService.class).setAction("controls"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  Notification notification=new Notification.Builder(this,"tracking").setContentTitle("8 Ball Assist is tracking").setContentText("Tap to open settings or stop tracking").setContentIntent(open).setSmallIcon(android.R.drawable.ic_menu_compass).addAction(new Notification.Action.Builder(null,"Controls",controls).build()).addAction(new Notification.Action.Builder(null,"Stop",stop).build()).setOngoing(true).build();
  if(Build.VERSION.SDK_INT>=29)startForeground(1,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);else startForeground(1,notification);
  try{
   presets=new PresetStore(this);windows=(WindowManager)getSystemService(WINDOW_SERVICE); dimensions();
   worker=new HandlerThread("guide-detector");worker.start();background=new Handler(worker.getLooper());
   Intent data=intent.getParcelableExtra("capture");
   projection=getSystemService(MediaProjectionManager.class).getMediaProjection(intent.getIntExtra("result",Activity.RESULT_CANCELED),data);
   projection.registerCallback(new MediaProjection.Callback(){public void onStop(){stopSelf();}},main);
   addOverlay();createCapture();
   settingsListener=(prefs,key)->main.post(()->{if(!closing&&"show_controls".equals(key))syncToolbar();});
   presets.settings().registerOnSharedPreferenceChangeListener(settingsListener);main.postDelayed(staleCheck,200);
  }catch(Exception error){Toast.makeText(this,"Unable to start tracking: "+error.getMessage(),Toast.LENGTH_LONG).show();stopSelf();}
  return START_NOT_STICKY;
 }
 private void dimensions(){DisplayMetrics m=new DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(m);screenW=m.widthPixels;screenH=m.heightPixels;captureW=Math.min(1280,screenW);captureH=Math.round(screenH*(captureW/(float)screenW));}
 private void createCapture(){
  reader=ImageReader.newInstance(captureW,captureH,PixelFormat.RGBA_8888,2); reader.setOnImageAvailableListener(this::frame,background);
  int density=getResources().getDisplayMetrics().densityDpi;
  if(display==null)display=projection.createVirtualDisplay("8 Ball Assist",captureW,captureH,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,background);
  else {display.resize(captureW,captureH,density);display.setSurface(reader.getSurface());}
 }
 private void frame(ImageReader source){
  try(Image image=source.acquireLatestImage()){
   if(image==null||paused||closing)return;
   long now=SystemClock.elapsedRealtime();if(now-lastFrame<130)return;lastFrame=now;
   int w=image.getWidth(),h=image.getHeight();Image.Plane plane=image.getPlanes()[0];
   framePixels=FramePixels.read(plane.getBuffer(),w,h,plane.getRowStride(),plane.getPixelStride(),framePixels);
   int[] colors=framePixels;
   int frameRevision=revision;if(workerRevision!=frameRevision){history.reset();workerRevision=frameRevision;}
   Geometry.Box norm=manual,box=norm==null?null:new Geometry.Box(norm.l*w,norm.t*h,norm.r*w,norm.b*h);
   Detector.Result detected=detector.detect(colors,w,h,box,profile);String sourceName=norm==null?"Auto":"Loaded / calibrated";double[][] framePockets=savedPockets;double frameRadius=radiusRatio;
   if(detected.table==null&&norm==null){PresetStore.Preset fallback=presets.fallback();if(fallback!=null){if(fallback.fits(w,h)){Geometry.Box fb=fallback.box;detected=detector.detect(colors,w,h,new Geometry.Box(fb.l*w,fb.t*h,fb.r*w,fb.b*h),fallback.profile);sourceName="Fallback: "+fallback.name;framePockets=fallback.pockets;frameRadius=fallback.radiusScale;}else sourceName="Fallback skipped: screen shape differs";}}
   if(!sourceName.equals(historySource)){history.reset();historySource=sourceName;}
   if(presets.settings().getBoolean("history",true))detected=history.update(detected,now);else history.reset();
   final Detector.Result result=detected;final String sourceLabel=sourceName;final double[][] pocketsForFrame=framePockets;final double sizeForFrame=frameRadius;final long capturedAt=now;
   main.post(()->{if(!closing&&!paused&&calibration==0&&frameRevision==revision&&w==captureW&&h==captureH){if(SystemClock.elapsedRealtime()-capturedAt>650){clearPaths();aimFilter.reset();return;}lastAccepted=SystemClock.elapsedRealtime();tableSource=sourceLabel;overlay.pockets=pocketsForFrame==null?(result.table==null?null:PresetStore.pockets(result.table)):PresetStore.scale(pocketsForFrame,w,h);accept(result,sizeForFrame);}});

  }catch(IllegalStateException ignored){}catch(Exception error){main.post(()->{if(status!=null)status.setText("Capture interrupted · restart tracking");if(overlay!=null){overlay.path=Collections.emptyList();overlay.cuePath=Collections.emptyList();overlay.secondary=Collections.emptyList();overlay.invalidate();}});}
 }
 private void accept(Detector.Result result,double radiusScale){
  effectiveRadiusScale=radiusScale;lastResult=result;overlay.box=result.table;overlay.balls=result.balls;overlay.targetGuide=result.line;overlay.approachGuide=result.approach;overlay.cueGuide=result.cueLine;overlay.cuePath=Collections.emptyList();overlay.secondary=Collections.emptyList();Geometry.Segment line=result.cueOnly?result.cueLine:result.line;overlay.colors.clear();
  if(line==null){aimFilter.reset();overlay.path=Collections.emptyList();overlay.cuePath=Collections.emptyList();overlay.secondary=Collections.emptyList();status.setText(tableSource+" · "+result.status);overlay.invalidate();return;}
  line=aimFilter.update(result,SystemClock.elapsedRealtime());
  if(line==null){clearPaths();status.setText("Confirming aim...");return;}
  if(flipped)line=line.reverse();
  overlay.ballRadius=captureW*radiusScale*result.ballRadiusRatio/.0075;
  java.util.List<BallDetector.Ball> obstacles=new ArrayList<>();if(presets.settings().getBoolean("collisions",true))for(BallDetector.Ball ball:result.balls)obstacles.add(new BallDetector.Ball(ball.x,ball.y,overlay.ballRadius,ball.confidence,ball.color));
  java.util.List<BallDetector.Ball> targetObstacles=new ArrayList<>(obstacles);
  if(!result.cueOnly&&result.approach!=null)targetObstacles.removeIf(ball->Math.hypot(ball.x-result.approach.x,ball.y-result.approach.y)<overlay.ballRadius*1.5);
  ShotPhysics.Paths target=ShotPhysics.predict(line,result.table,overlay.ballRadius,result.estimated||result.cueEstimated?1:bounces,targetObstacles,overlay.pockets);
  overlay.path=result.cueOnly?Collections.emptyList():target.primary;overlay.cuePath=result.cueOnly?target.primary:Collections.emptyList();overlay.secondary=target.secondary;overlay.colors.putAll(target.colors);overlay.estimated=result.estimated;overlay.cueEstimated=result.cueEstimated;
  if(!result.cueOnly){int color=Color.WHITE;double nearest=overlay.ballRadius*1.7;for(BallDetector.Ball ball:result.balls){double distance=Math.hypot(ball.x-line.x,ball.y-line.y);if(distance<nearest){nearest=distance;color=ball.color;}}for(Geometry.Segment s:overlay.path)overlay.colors.put(s,color);}
  if(!result.cueOnly&&presets.settings().getBoolean("show_cue",true)&&result.cueLine!=null){
   ArrayList<BallDetector.Ball> cueObstacles=new ArrayList<>();for(BallDetector.Ball ball:obstacles)if(Math.hypot(ball.x-line.x,ball.y-line.y)>overlay.ballRadius*1.5&&(result.approach==null||Math.hypot(ball.x-result.approach.x,ball.y-result.approach.y)>overlay.ballRadius*1.5))cueObstacles.add(ball);
   ShotPhysics.Paths cue=ShotPhysics.predict(result.cueLine,result.table,overlay.ballRadius,result.cueEstimated||result.estimated?1:bounces,cueObstacles,overlay.pockets);overlay.cuePath=cue.primary;overlay.secondary.addAll(cue.secondary);overlay.colors.putAll(cue.colors);overlay.cueEstimated=result.cueEstimated||result.estimated;
  }
  if(!presets.settings().getBoolean("show_cue",true))overlay.cuePath=Collections.emptyList();
  status.setText(tableSource+" · "+(result.cueOnly?"Cue rail preview":"Ball-colored paths")+" · white = cue"+(result.cueEstimated||result.estimated?" · estimated":""));overlay.invalidate();
 }
 private void setPaused(boolean value){paused=value;if(pauseControl!=null)pauseControl.setText(value?"Resume":"Pause");}
 private void invalidateTracking(){aimFilter.reset();revision++;lastResult=null;if(overlay!=null){overlay.path=Collections.emptyList();overlay.cuePath=Collections.emptyList();overlay.secondary=Collections.emptyList();overlay.invalidate();}}
 private void reviewPockets(){
  if(showPockets){if(overlay.pockets!=null)savedPockets=PresetStore.scale(overlay.pockets,1.0/captureW,1.0/captureH);showPockets=false;setPaused(reviewWasPaused);unlock();invalidateTracking();return;}
  if(overlay.box==null){Toast.makeText(this,"Detect or calibrate a table first",Toast.LENGTH_LONG).show();return;}
  reviewWasPaused=paused;showPockets=true;setPaused(true);overlay.path=Collections.emptyList();overlay.cuePath=Collections.emptyList();overlay.secondary=Collections.emptyList();if(overlay.pockets==null)overlay.pockets=PresetStore.pockets(overlay.box);
  overlayParams.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;windows.updateViewLayout(overlay,overlayParams);status.setText("Review P1–P6 · drag to adjust · Presets to save · Pockets to finish");overlay.invalidate();
 }
 private void dialog(AlertDialog d){if(presetDialog!=null)presetDialog.dismiss();presetDialog=d;d.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);d.show();}
 private void presetMenu(){
  menuWasPaused=paused;setPaused(true);revision++;overlay.path=Collections.emptyList();overlay.cuePath=Collections.emptyList();overlay.secondary=Collections.emptyList();overlay.invalidate();
  List<PresetStore.Preset> list=presets.list();String[] names=new String[list.size()+2];names[0]="Save reviewed table";names[1]="Advanced settings";for(int i=0;i<list.size();i++)names[i+2]="Load: "+list.get(i).name;
  dialog(new AlertDialog.Builder(this).setTitle("Table presets").setItems(names,(d,index)->{if(index==0)savePreset();else if(index==1){if(showPockets){showPockets=false;unlock();}setPaused(true);invalidateTracking();startActivity(new Intent(this,AdvancedActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));status.setText("Paused · tap Resume after settings");}else loadPreset(list.get(index-2));}).setNegativeButton("Close",(d,i)->setPaused(menuWasPaused||showPockets)).setOnCancelListener(d->setPaused(menuWasPaused||showPockets)).create());
 }
 private void savePreset(){
  if(!showPockets||overlay.box==null||overlay.pockets==null){Toast.makeText(this,"Tap Pockets and review the six markers before saving",Toast.LENGTH_LONG).show();setPaused(menuWasPaused);return;}
  EditText name=new EditText(this);name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});name.setSingleLine(true);name.setHint("Table name");name.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
  AlertDialog d=new AlertDialog.Builder(this).setTitle("Save reviewed table").setMessage("Save only if the boundary and all six pocket markers match your table.").setView(name).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();dialog(d);d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String label=name.getText().toString().trim();if(label.isEmpty()){name.setError("Enter a table name");return;}for(PresetStore.Preset old:presets.list())if(old.name.equalsIgnoreCase(label)){name.setError("That name is already used");return;}
   PresetStore.Preset p=new PresetStore.Preset();Geometry.Box b=overlay.box;p.name=label;p.box=new Geometry.Box(b.l/captureW,b.t/captureH,b.r/captureW,b.b/captureH);p.pockets=PresetStore.scale(overlay.pockets,1.0/captureW,1.0/captureH);p.aspect=captureW/(double)captureH;p.profile=profile==0?(lastResult==null?0:lastResult.profile):profile;p.radiusScale=effectiveRadiusScale;presets.save(p);d.dismiss();status.setText("Saved "+label+" · fallback is controlled in Advanced settings");});
 }
 private void loadPreset(PresetStore.Preset p){
  if(!p.fits(captureW,captureH)){Toast.makeText(this,"Saved screen proportions differ. Rotate or recalibrate first.",Toast.LENGTH_LONG).show();return;}
  manual=p.box;savedPockets=PresetStore.scale(p.pockets,1,1);profile=p.profile;radiusRatio=p.radiusScale;showPockets=false;calibration=0;setPaused(false);unlock();invalidateTracking();status.setText("Loaded "+p.name);
 }
 private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
 private WindowManager.LayoutParams params(int w,int h,int flags){
  WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,flags,PixelFormat.TRANSLUCENT);
  p.gravity=Gravity.TOP|Gravity.LEFT;p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;return p;
 }
 private void clearPaths(){overlay.path=Collections.emptyList();overlay.cuePath=Collections.emptyList();overlay.secondary=Collections.emptyList();overlay.invalidate();}
 private void syncToolbar(){
  if(presets.settings().getBoolean("show_controls",false)){if(!toolbar.isAttachedToWindow())windows.addView(toolbar,toolbarParams);}
  else {
   if(presetDialog!=null){presetDialog.dismiss();presetDialog=null;}
   if(showPockets||calibration>0){showPockets=false;calibration=0;setPaused(false);unlock();invalidateTracking();}
   if(toolbar.isAttachedToWindow())windows.removeView(toolbar);
  }
 }
 private void toggleExpanded(){expanded=!expanded;toolbar.getChildAt(1).setVisibility(expanded?View.VISIBLE:View.GONE);moreControl.setText(expanded?"Less":"More");}
 private void addOverlay(){
  overlay=new GuideView();overlayParams=params(-1,-1,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
  overlayParams.alpha=.70f;windows.addView(overlay,overlayParams);
  toolbar=new LinearLayout(this);toolbar.setOrientation(1);toolbar.setPadding(dp(4),dp(2),dp(4),dp(2));toolbar.setBackgroundColor(Color.argb(115,18,25,39));
  status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(12);status.setMaxWidth(Math.min(dp(320),screenW-dp(32)));status.setSingleLine(true);status.setEllipsize(android.text.TextUtils.TruncateAt.END);status.setText("Searching for table · drag here to move");toolbar.addView(status);
  HorizontalScrollView menuScroll=new HorizontalScrollView(this);menuScroll.setHorizontalScrollBarEnabled(false);toolbar.addView(menuScroll,new LinearLayout.LayoutParams(Math.min(dp(320),screenW-dp(24)),-2));LinearLayout buttons=new LinearLayout(this);menuScroll.addView(buttons);
  pauseControl=button(buttons,"Pause",v->{if(showPockets){showPockets=false;unlock();}invalidateTracking();setPaused(!paused);((Button)v).setText(paused?"Resume":"Pause");if(paused){overlay.path=Collections.emptyList();overlay.cuePath=Collections.emptyList();overlay.secondary=Collections.emptyList();overlay.invalidate();status.setText("Paused");}});
  button(buttons,"Table",v->{showPockets=false;savedPockets=null;invalidateTracking();calibration=1;setPaused(true);overlay.path=Collections.emptyList();overlay.cuePath=Collections.emptyList();overlay.secondary=Collections.emptyList();status.setText("Tap TOP-LEFT inner cushion corner");overlayParams.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;windows.updateViewLayout(overlay,overlayParams);overlay.invalidate();});
  button(buttons,"Auto",v->{showPockets=false;savedPockets=null;profile=0;invalidateTracking();manual=null;calibration=0;setPaused(false);unlock();status.setText("Automatic cyan / blue table detection");});
  button(buttons,"Pockets",v->reviewPockets());
  button(buttons,"Presets",v->presetMenu());
  button(buttons,"Guide",v->{profile=(profile+1)%3;invalidateTracking();status.setText(profile==0?"Guide style: Auto":profile==1?"Guide style: Yellow":"Guide style: White");});
  button(buttons,"Flip",v->{invalidateTracking();flipped=!flipped;overlay.path=Collections.emptyList();overlay.cuePath=Collections.emptyList();overlay.secondary=Collections.emptyList();overlay.invalidate();});
  button(buttons,"3 rails",v->{bounces=bounces%5+1;((Button)v).setText(bounces+" rails");});
  button(buttons,"R−",v->{radiusRatio=Math.max(.003,radiusRatio-.0005);status.setText("Ball size: "+Math.round(radiusRatio/.0075*100)+"% of detected style");});
  button(buttons,"R+",v->{radiusRatio=Math.min(.02,radiusRatio+.0005);status.setText("Ball size: "+Math.round(radiusRatio/.0075*100)+"% of detected style");});
  button(buttons,"×",v->stopSelf());
  toolbarParams=params(-2,-2,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);toolbarParams.x=dp(12);toolbarParams.y=dp(10);
  // The expanded tools keep 48dp touch targets inside a narrower scrolling strip.
  LinearLayout compact=new LinearLayout(this);toolbar.addView(compact);
  buttons.removeView(pauseControl);compact.addView(pauseControl);
  moreControl=button(compact,"More",v->toggleExpanded());
  button(compact,"Hide",v->presets.settings().edit().putBoolean("show_controls",false).apply());
  menuScroll.setVisibility(View.GONE);syncToolbar();
  status.setOnTouchListener(new View.OnTouchListener(){float x,y;int ox,oy;public boolean onTouch(View v,android.view.MotionEvent e){if(e.getAction()==0){x=e.getRawX();y=e.getRawY();ox=toolbarParams.x;oy=toolbarParams.y;}if(e.getAction()==2){toolbarParams.x=Math.max(0,Math.min(screenW-toolbar.getWidth(),ox+(int)(e.getRawX()-x)));toolbarParams.y=Math.max(0,Math.min(screenH-toolbar.getHeight(),oy+(int)(e.getRawY()-y)));windows.updateViewLayout(toolbar,toolbarParams);}return true;}});
 }
 private Button button(LinearLayout row,String label,View.OnClickListener listener){Button b=new Button(this);b.setText(label);b.setTextSize(12);b.setAllCaps(false);b.setMinWidth(dp(48));b.setMinimumWidth(dp(48));b.setPadding(dp(8),0,dp(8),0);b.setTextColor(Color.WHITE);android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(Color.argb(70,18,25,39));bg.setCornerRadius(dp(6));b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x55ffffff),bg,null));row.addView(b,new LinearLayout.LayoutParams(-2,dp(48)));b.setOnClickListener(listener);return b;}
 private void unlock(){overlayParams.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;windows.updateViewLayout(overlay,overlayParams);overlay.invalidate();}
 public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);main.postDelayed(()->{
  if(closing||display==null)return;
  if(presetDialog!=null){presetDialog.dismiss();presetDialog=null;}
  setPaused(true);invalidateTracking();showPockets=false;savedPockets=null;overlay.pockets=null;overlay.box=null;manual=null;calibration=0;unlock();
  background.post(()->{if(closing)return;display.setSurface(null);reader.close();dimensions();createCapture();main.post(()->{if(closing)return;status.setMaxWidth(Math.min(dp(320),screenW-dp(32)));status.setSingleLine(true);status.setEllipsize(android.text.TextUtils.TruncateAt.END);toolbar.getChildAt(1).getLayoutParams().width=Math.min(dp(320),screenW-dp(24));toolbar.getChildAt(1).requestLayout();toolbarParams.x=dp(12);toolbarParams.y=dp(10);if(toolbar.isAttachedToWindow())windows.updateViewLayout(toolbar,toolbarParams);setPaused(false);});});
 },300);}
 public void onDestroy(){closing=true;if(presets!=null&&settingsListener!=null)presets.settings().unregisterOnSharedPreferenceChangeListener(settingsListener);if(presetDialog!=null)presetDialog.dismiss();main.removeCallbacksAndMessages(null);if(display!=null)display.release();if(reader!=null)reader.close();if(projection!=null)projection.stop();if(worker!=null)worker.quitSafely();if(windows!=null){if(overlay!=null&&overlay.isAttachedToWindow())windows.removeView(overlay);if(toolbar!=null&&toolbar.isAttachedToWindow())windows.removeView(toolbar);}stopForeground(true);super.onDestroy();}
 private class GuideView extends View {
  Geometry.Segment targetGuide,approachGuide,cueGuide;
  final Map<Geometry.Segment,Integer> colors=new IdentityHashMap<>();
  double ballRadius;boolean estimated,cueEstimated;double[][] pockets;java.util.List<BallDetector.Ball> balls=Collections.emptyList();
  java.util.List<Geometry.Segment> cuePath=Collections.emptyList(),secondary=Collections.emptyList();
  final Paint paint=new Paint(3);java.util.List<Geometry.Segment> path=Collections.emptyList();Geometry.Box box;
  GuideView(){super(GuideService.this);}
  protected void onDraw(Canvas canvas){
   super.onDraw(canvas);float sx=getWidth()/(float)captureW,sy=getHeight()/(float)captureH;
   canvas.save();
   // MediaProjection captures this overlay too. Keep the source evidence unobscured.
   Path protectedArea=new Path();Paint mask=new Paint();mask.setStyle(Paint.Style.STROKE);mask.setStrokeWidth(dp(12));mask.setStrokeCap(Paint.Cap.ROUND);
   for(Geometry.Segment guide:new Geometry.Segment[]{targetGuide,approachGuide,cueGuide})if(guide!=null){Path center=new Path(),outline=new Path();center.moveTo((float)guide.x*sx,(float)guide.y*sy);center.lineTo((float)guide.u*sx,(float)guide.v*sy);mask.getFillPath(center,outline);protectedArea.addPath(outline);}
   for(BallDetector.Ball ball:balls)protectedArea.addCircle((float)ball.x*sx,(float)ball.y*sy,(float)(ball.r*sx*1.35),Path.Direction.CW);
   if(targetGuide!=null)protectedArea.addCircle((float)targetGuide.x*sx,(float)targetGuide.y*sy,(float)(ballRadius*sx*1.4),Path.Direction.CW);
   if(lastResult!=null&&Double.isFinite(lastResult.contactX))protectedArea.addCircle((float)lastResult.contactX*sx,(float)lastResult.contactY*sy,(float)(ballRadius*sx*1.5),Path.Direction.CW);
   canvas.clipOutPath(protectedArea);
   drawPaths(canvas,path,sx,sy,false,false);
   drawPaths(canvas,cuePath,sx,sy,true,false);
   drawPaths(canvas,secondary,sx,sy,false,true);
   canvas.restore();
   if(showPockets&&box!=null){paint.setStyle(Paint.Style.STROKE);paint.setColor(Color.CYAN);paint.setStrokeWidth(dp(2));canvas.drawRect((float)box.l*sx,(float)box.t*sy,(float)box.r*sx,(float)box.b*sy,paint);for(BallDetector.Ball ball:balls)canvas.drawCircle((float)ball.x*sx,(float)ball.y*sy,(float)ball.r*sx,paint);if(pockets!=null)for(int i=0;i<pockets.length;i++){float x=(float)pockets[i][0]*sx,y=(float)pockets[i][1]*sy;canvas.drawCircle(x,y,dp(14),paint);paint.setTextSize(dp(16));canvas.drawText("P"+(i+1),x+dp(16),y,paint);}}
   if(calibration>0){paint.setColor(Color.CYAN);paint.setTextSize(dp(20));paint.setStyle(Paint.Style.FILL);canvas.drawText(calibration==1?"Tap top-left INNER rail corner":"Tap bottom-right INNER rail corner",dp(30),getHeight()/2f,paint);if(calibration==2){paint.setStyle(Paint.Style.STROKE);canvas.drawCircle((float)firstX*getWidth(),(float)firstY*getHeight(),dp(10),paint);}}
  }
  private void drawPaths(Canvas canvas,List<Geometry.Segment> segments,float sx,float sy,boolean cue,boolean secondaryPath){
   for(Geometry.Segment s:segments){
    // Keep the captured cue preview below the detector's white-guide threshold.
    int color=cue?Color.rgb(195,220,240):colors.getOrDefault(s,Color.WHITE);
    paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setAlpha(secondaryPath?180:235);
    paint.setPathEffect(cue?null:new DashPathEffect(new float[]{dp(secondaryPath?3:9),dp(secondaryPath?7:5)},0));
    paint.setStrokeWidth(dp(secondaryPath?3:4));paint.setColor(color==0xff252525?Color.WHITE:Color.BLACK);
    canvas.drawLine((float)s.x*sx,(float)s.y*sy,(float)s.u*sx,(float)s.v*sy,paint);
    paint.setStrokeWidth(dp(secondaryPath?1:2));paint.setColor(color);
    canvas.drawLine((float)s.x*sx,(float)s.y*sy,(float)s.u*sx,(float)s.v*sy,paint);
   }
   paint.setPathEffect(null);paint.setAlpha(255);
  }
  public boolean onTouchEvent(android.view.MotionEvent e){if(showPockets&&pockets!=null){float x=e.getX()*captureW/getWidth(),y=e.getY()*captureH/getHeight();if(e.getAction()==0){double nearest=dp(40)*captureW/getWidth();for(int i=0;i<pockets.length;i++){double d=Math.hypot(x-pockets[i][0],y-pockets[i][1]);if(d<nearest){nearest=d;draggedPocket=i;}}}if(draggedPocket>=0&&(e.getAction()==2||e.getAction()==1)){pockets[draggedPocket][0]=Math.max(0,Math.min(captureW,x));pockets[draggedPocket][1]=Math.max(0,Math.min(captureH,y));invalidate();}return true;}if(calibration==0)return false;if(e.getAction()==android.view.MotionEvent.ACTION_UP){double x=e.getX()/getWidth(),y=e.getY()/getHeight();if(calibration==1){firstX=x;firstY=y;calibration=2;status.setText("Tap BOTTOM-RIGHT inner cushion corner");}else if(x>firstX+.2&&y>firstY+.2){manual=new Geometry.Box(firstX,firstY,x,y);invalidateTracking();savedPockets=null;calibration=0;setPaused(false);unlock();status.setText("Table calibrated");}else status.setText("Corner must be below and right · try again");invalidate();}return true;}
 }
}
