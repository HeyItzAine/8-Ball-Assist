package app.railguide;
import java.util.*;

public final class ShotPhysics {
 public static final class Paths {
  public final List<Geometry.Segment> primary=new ArrayList<>(),secondary=new ArrayList<>();
  public final Map<Geometry.Segment,Integer> colors=new IdentityHashMap<>();
  public final List<double[]> contacts=new ArrayList<>();
 }
 public static Paths predict(Geometry.Segment guide,Geometry.Box box,double radius,int rails,List<BallDetector.Ball> balls,double[][] pockets){
  Paths out=new Paths();if(guide==null||guide.length()<.01)return out;
  Set<Integer> excluded=new HashSet<>();for(int i=0;i<balls.size();i++)if(Math.hypot(balls.get(i).x-guide.x,balls.get(i).y-guide.y)<radius*1.7)excluded.add(i);
  int color=0xffeeeeee;double nearest=radius*1.7;for(BallDetector.Ball b:balls){double d=Math.hypot(b.x-guide.x,b.y-guide.y);if(d<nearest){nearest=d;color=b.color;}}
  cast(guide.x,guide.y,(guide.u-guide.x)/guide.length(),(guide.v-guide.y)/guide.length(),box,radius,rails,balls,pockets,excluded,0,color,out);return out;
 }
 private static void cast(double x,double y,double dx,double dy,Geometry.Box box,double r,int rails,List<BallDetector.Ball> balls,double[][] pockets,Set<Integer> excluded,int depth,int color,Paths out){
  if(depth>1||out.primary.size()+out.secondary.size()>24)return;
  Geometry.Box inner=new Geometry.Box(box.l+r,box.t+r,box.r-r,box.b-r);
  if(!inner.contains(x,y)){
   double clampedX=Math.max(inner.l,Math.min(inner.r,x)),clampedY=Math.max(inner.t,Math.min(inner.b,y));
   if(depth!=0||Math.hypot(clampedX-x,clampedY-y)>r*.6)return;
   x=clampedX;y=clampedY;
  }
  for(int bounce=0;bounce<=rails;bounce++){
   double tx=dx>1e-8?(box.r-r-x)/dx:dx< -1e-8?(box.l+r-x)/dx:Double.POSITIVE_INFINITY;
   double ty=dy>1e-8?(box.b-r-y)/dy:dy< -1e-8?(box.t+r-y)/dy:Double.POSITIVE_INFINITY;
   double distance=Math.min(tx,ty);if(!Double.isFinite(distance)||distance<0)return;
   int hit=-1;
   for(int i=0;i<balls.size();i++){if(excluded.contains(i))continue;BallDetector.Ball ball=balls.get(i);double ox=ball.x-x,oy=ball.y-y,along=ox*dx+oy*dy,across=ox*ox+oy*oy-along*along,sum=r+ball.r;
    if(along<=0||across>sum*sum)continue;double entry=Math.max(0,along-Math.sqrt(Math.max(0,sum*sum-across)));if(entry<distance){distance=entry;hit=i;}}
   double nx=x+dx*distance,ny=y+dy*distance;
   Geometry.Segment segment=new Geometry.Segment(x,y,nx,ny);(depth==0?out.primary:out.secondary).add(segment);out.colors.put(segment,color);
   if(hit>=0){
    BallDetector.Ball b=balls.get(hit);out.contacts.add(new double[]{nx,ny});
    // Dense clusters require simultaneous collision solving; stop rather than invent a chain.
    for(int j=0;j<balls.size();j++)if(j!=hit&&!excluded.contains(j)&&Math.hypot(b.x-balls.get(j).x,b.y-balls.get(j).y)<b.r+balls.get(j).r+2)return;
    double ax=b.x-nx,ay=b.y-ny,n=Math.hypot(ax,ay);if(n<.01)return;ax/=n;ay/=n;double normal=dx*ax+dy*ay,ux=dx-normal*ax,uy=dy-normal*ay,tangent=Math.hypot(ux,uy);
    Set<Integer> next=new HashSet<>(excluded);next.add(hit);
    if(normal>.08)cast(b.x,b.y,ax,ay,box,r,0,balls,pockets,next,depth+1,b.color,out);
    if(tangent>.08)cast(nx+ux*.05,ny+uy*.05,ux/tangent,uy/tangent,box,r,0,balls,pockets,next,depth+1,color,out);
    return;
   }
   if(pockets!=null)for(double[] pocket:pockets)if(Math.hypot(nx-pocket[0],ny-pocket[1])<r*2.4)return;
   if(pockets==null){double pr=r*1.8;if((Math.abs(nx-(box.l+r))<.01||Math.abs(nx-(box.r-r))<.01)&&(Math.abs(ny-(box.t+r))<pr||Math.abs(ny-(box.b-r))<pr))return;if((Math.abs(ny-(box.t+r))<.01||Math.abs(ny-(box.b-r))<.01)&&(Math.abs(nx-(box.l+r))<pr||Math.abs(nx-(box.r-r))<pr||Math.abs(nx-(box.l+box.r)/2)<pr))return;}
   if(Math.abs(tx-distance)<.001)dx=-dx;if(Math.abs(ty-distance)<.001)dy=-dy;x=nx;y=ny;
  }
 }
 public static Geometry.Segment tangent(Geometry.Segment approach,Geometry.Segment target,double cx,double cy){
  if(approach==null||target==null||approach.length()<.01||target.length()<.01)return null;
  double dx=(approach.u-approach.x)/approach.length(),dy=(approach.v-approach.y)/approach.length(),nx=(target.u-target.x)/target.length(),ny=(target.v-target.y)/target.length();
  double dot=dx*nx+dy*ny,tx=dx-dot*nx,ty=dy-dot*ny,n=Math.hypot(tx,ty);if(n<.06||dot<=0)return null;
  return new Geometry.Segment(cx,cy,cx+tx/n*50,cy+ty/n*50);
 }
}
