package app.railguide;
import java.util.*;

public final class Geometry {
 public static final class Box {
  public final double l,t,r,b;
  public Box(double l,double t,double r,double b){this.l=l;this.t=t;this.r=r;this.b=b;}
  public boolean contains(double x,double y){return x>=l&&x<=r&&y>=t&&y<=b;}
 }
 public static final class Segment {
  public final double x,y,u,v;
  public Segment(double x,double y,double u,double v){this.x=x;this.y=y;this.u=u;this.v=v;}
  public double length(){return Math.hypot(u-x,v-y);}
  public Segment reverse(){return new Segment(u,v,x,y);}
 }
 public static List<Segment> trace(Segment guide,Box table,double radius,int bounces){
  ArrayList<Segment> out=new ArrayList<>();
  Box box=new Box(table.l+radius,table.t+radius,table.r-radius,table.b-radius);
  double x=guide.x,y=guide.y,dx=guide.u-x,dy=guide.v-y,n=Math.hypot(dx,dy);
  if(n<1e-6||box.r<=box.l||box.b<=box.t||!box.contains(x,y))return out;
  dx/=n;dy/=n;
  for(int i=0;i<=bounces;i++){
   double tx=dx>1e-9?(box.r-x)/dx:dx< -1e-9?(box.l-x)/dx:Double.POSITIVE_INFINITY;
   double ty=dy>1e-9?(box.b-y)/dy:dy< -1e-9?(box.t-y)/dy:Double.POSITIVE_INFINITY;
   double d=Math.min(tx,ty); if(!Double.isFinite(d)||d< -1e-6)break;
   double nx=x+dx*d,ny=y+dy*d;out.add(new Segment(x,y,nx,ny));
   boolean hitX=Math.abs(tx-d)<1e-5,hitY=Math.abs(ty-d)<1e-5;
   double pocket=radius*1.8;
   if((hitX&&(Math.abs(ny-box.t)<pocket||Math.abs(ny-box.b)<pocket)) || (hitY&&(Math.abs(nx-box.l)<pocket||Math.abs(nx-box.r)<pocket||Math.abs(nx-(box.l+box.r)/2)<pocket)))break;
   if(hitX)dx=-dx;if(hitY)dy=-dy;x=nx;y=ny;
  }
  return out;
 }
}
