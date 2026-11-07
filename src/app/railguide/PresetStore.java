package app.railguide;
import android.content.*;
import org.json.*;
import java.util.*;

public final class PresetStore {
 public static final class Preset {
  public String id,name;public Geometry.Box box;public double[][] pockets;public double aspect,radiusScale;public int profile;
  public boolean fits(int w,int h){return w>0&&h>0&&Math.abs(w/(double)h-aspect)/aspect<.025;}
 }
 private final SharedPreferences prefs;
 public PresetStore(Context c){prefs=c.getSharedPreferences("table_presets",Context.MODE_PRIVATE);}
 public SharedPreferences settings(){return prefs;}
 public List<Preset> list(){ArrayList<Preset> list=new ArrayList<>();try{JSONArray array=new JSONArray(prefs.getString("presets","[]"));for(int i=0;i<array.length();i++){JSONObject o=array.getJSONObject(i);Preset p=new Preset();p.id=o.getString("id");p.name=o.getString("name");p.box=new Geometry.Box(o.getDouble("l"),o.getDouble("t"),o.getDouble("r"),o.getDouble("b"));p.aspect=o.getDouble("aspect");p.profile=o.optInt("profile",0);p.radiusScale=o.optDouble("radius",.0075);JSONArray points=o.getJSONArray("pockets");p.pockets=new double[6][2];for(int k=0;k<6;k++){p.pockets[k][0]=points.getJSONArray(k).getDouble(0);p.pockets[k][1]=points.getJSONArray(k).getDouble(1);}list.add(p);}}catch(JSONException ignored){}return list;}
 public Preset get(String id){for(Preset p:list())if(p.id.equals(id))return p;return null;}
 public Preset fallback(){return prefs.getBoolean("fallback_enabled",false)?get(prefs.getString("default_id","")):null;}
 public void save(Preset preset){List<Preset> all=list();if(preset.id==null)preset.id=UUID.randomUUID().toString();all.removeIf(p->p.id.equals(preset.id));all.add(preset);write(all);}
 public void delete(String id){List<Preset> all=list();all.removeIf(p->p.id.equals(id));write(all);if(id.equals(prefs.getString("default_id","")))prefs.edit().remove("default_id").putBoolean("fallback_enabled",false).apply();}
 private void write(List<Preset> all){JSONArray array=new JSONArray();try{for(Preset p:all){JSONObject o=new JSONObject();o.put("id",p.id);o.put("name",p.name);o.put("l",p.box.l);o.put("t",p.box.t);o.put("r",p.box.r);o.put("b",p.box.b);o.put("aspect",p.aspect);o.put("radius",p.radiusScale);o.put("profile",p.profile);JSONArray points=new JSONArray();for(double[] q:p.pockets){JSONArray point=new JSONArray();point.put(q[0]);point.put(q[1]);points.put(point);}o.put("pockets",points);array.put(o);}prefs.edit().putString("presets",array.toString()).apply();}catch(JSONException e){throw new IllegalArgumentException("Invalid preset",e);}}
 public static double[][] pockets(Geometry.Box b){return new double[][]{{b.l,b.t},{(b.l+b.r)/2,b.t},{b.r,b.t},{b.r,b.b},{(b.l+b.r)/2,b.b},{b.l,b.b}};}
 public static double[][] scale(double[][] points,double x,double y){double[][] scaled=new double[points.length][2];for(int i=0;i<points.length;i++){scaled[i][0]=points[i][0]*x;scaled[i][1]=points[i][1]*y;}return scaled;}
}
