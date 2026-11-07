package app.railguide;
import java.util.*;

public final class Detector {
 private boolean contactSeen,whiteMode;
 private Geometry.Segment incoming;
 private double ringX=Double.NaN,ringY=Double.NaN;
 private int forcedProfile;
 private Geometry.Segment stickAim;private boolean geometryTarget;
 private List<BallDetector.Ball> frameBalls=Collections.emptyList();
 public static final class Result {
  public final Geometry.Box table; public final Geometry.Segment line; public final String status;
  public final double ballRadiusRatio;
  public Geometry.Segment cueLine,approach;
  public double contactX=Double.NaN,contactY=Double.NaN;
  public int frameWidth,profile;
  public boolean estimated,cueEstimated,cueOnly;
  public List<BallDetector.Ball> balls=Collections.emptyList();
  Result(Geometry.Box b,Geometry.Segment l,String s){this(b,l,s,.0075);}
  Result(Geometry.Box b,Geometry.Segment l,String s,double radius){table=b;line=l;status=s;ballRadiusRatio=radius;}
  public Result withLine(Geometry.Segment l,String status){Result r=new Result(table,l,status,ballRadiusRatio);r.approach=approach;r.cueLine=cueLine;r.contactX=contactX;r.contactY=contactY;r.frameWidth=frameWidth;r.profile=profile;r.balls=balls;r.cueEstimated=cueEstimated;return r;}
 }
 private boolean yellow(int c){int r=(c>>16)&255,g=(c>>8)&255,b=c&255;return r>170&&g>135&&b<135&&r>b*1.5&&g>b*1.5&&g>r*.65;}
 private boolean white(int c){int r=(c>>16)&255,g=(c>>8)&255,b=c&255;return r>205&&g>205&&b>205&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<35;}
 public Result detect(int[] pixels,int w,int h,Geometry.Box manual){
  return detect(pixels,w,h,manual,0);
 }
 public Result detect(int[] pixels,int w,int h,Geometry.Box manual,int profile){
  forcedProfile=profile;ringX=ringY=Double.NaN;incoming=null;stickAim=null;geometryTarget=false;whiteMode=false;frameBalls=Collections.emptyList();
  Result raw=detectRaw(pixels,w,h,manual);
  Result result=new Result(raw.table,raw.line,raw.status,whiteMode?.009:.0075);result.frameWidth=w;result.profile=whiteMode?2:1;
  if(result.table==null)return result;
  if(incoming==null){Geometry.Segment candidate=findLine(pixels,w,h,result.table,false);if(candidate!=null){double a=whiteMass(pixels,w,h,candidate.x,candidate.y),b=whiteMass(pixels,w,h,candidate.u,candidate.v);if(Math.abs(a-b)>.1)incoming=a>b?candidate:candidate.reverse();}}
  result.estimated=geometryTarget;result.approach=incoming;result.contactX=ringX;result.contactY=ringY;
  result.balls=new ArrayList<>();
  for(BallDetector.Ball ball:frameBalls){
   boolean stick=false;
   if(incoming!=null){double len=incoming.length(),ux=(incoming.u-incoming.x)/len,uy=(incoming.v-incoming.y)/len,ox=ball.x-incoming.x,oy=ball.y-incoming.y;
    if(ox*ux+oy*uy< -ball.r*2&&Math.abs(ox*uy-oy*ux)<ball.r){int warm=0;for(int sign:new int[]{-1,1}){boolean found=false;for(int offset=-(int)ball.r;offset<=ball.r;offset++){int xx=(int)(ball.x+sign*ux*ball.r*1.8-uy*offset),yy=(int)(ball.y+sign*uy*ball.r*1.8+ux*offset);if(xx>=0&&yy>=0&&xx<w&&yy<h){int c=pixels[yy*w+xx],red=(c>>16)&255,green=(c>>8)&255,blue=c&255;if(red>80&&red>green*1.1&&green>blue*1.25)found=true;}}if(found)warm++;}stick=warm==2;}
   }
   if(!stick)result.balls.add(ball);
  }
  if(Double.isFinite(ringX)){final double rr=w*result.ballRadiusRatio;ArrayList<BallDetector.Ball> cleaned=new ArrayList<>();for(BallDetector.Ball ball:result.balls)if(Math.hypot(ball.x-ringX,ball.y-ringY)>rr*(result.line==null?1.45:1.85)||(result.line!=null&&Math.hypot(ball.x-result.line.x,ball.y-result.line.y)<rr*.65))cleaned.add(ball);result.balls=cleaned;}
  if(result.line!=null&&incoming!=null&&Double.isFinite(ringX)){
   Geometry.Segment tangent=ShotPhysics.tangent(incoming,result.line,ringX,ringY);
   if(tangent!=null){result.cueLine=visibleCue(pixels,w,h,tangent,w*result.ballRadiusRatio);if(result.cueLine==null){result.cueLine=tangent;result.cueEstimated=true;}}
  }
  if(result.line==null&&whiteMode){
   Geometry.Segment aim=incoming!=null?incoming:stickAim;
   boolean adjacent=false;double rr=w*result.ballRadiusRatio;
   if(aim!=null&&incoming!=null)for(BallDetector.Ball ball:result.balls)if(Math.hypot(ball.x-aim.u,ball.y-aim.v)<rr*3&&Math.hypot(ball.x-ringX,ball.y-ringY)>rr*1.65)adjacent=true;
   if(aim!=null&&!adjacent){if(incoming!=null){BallDetector.Ball nearest=null;double distance=rr*2;for(BallDetector.Ball ball:result.balls){double d=Math.hypot(ball.x-aim.x,ball.y-aim.y);if(d<distance){distance=d;nearest=ball;}}if(nearest!=null)aim=new Geometry.Segment(nearest.x,nearest.y,nearest.x+aim.u-aim.x,nearest.y+aim.v-aim.y);}result.cueOnly=true;result.cueLine=aim;result.cueEstimated=incoming==null;}
  }
  return result;
 }
 private Geometry.Segment visibleCue(int[] pixels,int w,int h,Geometry.Segment tangent,double r){
  double angle=Math.atan2(tangent.v-tangent.y,tangent.u-tangent.x),best=0;Geometry.Segment winner=null;
  for(int offset=-24;offset<=24;offset++){double a=angle+offset*Math.PI/180,dx=Math.cos(a),dy=Math.sin(a);int hit=0,gap=0;double end=0;
   for(double t=r*.9;t<r*5;t++){boolean white=atWhite(pixels,w,h,tangent.x+dx*t,tangent.y+dy*t);if(white){hit++;gap=0;end=t;}else gap++;if(gap>3)break;}
   if(hit>r&&hit>best){best=hit;winner=new Geometry.Segment(tangent.x,tangent.y,tangent.x+dx*end,tangent.y+dy*end);}
  }return winner;
 }
 private Result detectRaw(int[] pixels,int w,int h,Geometry.Box manual){
  Geometry.Box box=manual==null?findTable(pixels,w,h):manual;
  if(box==null)return new Result(null,null,"Table not found · tap Table");
  int blue=0,cyan=0;
  for(int y=(int)box.t;y<box.b;y+=12)for(int x=(int)box.l;x<box.r;x+=12){int c=pixels[y*w+x],g=(c>>8)&255,b=c&255;if(b>g*1.2)blue++;else cyan++;}
  whiteMode=forcedProfile==2||(forcedProfile==0&&blue>cyan);incoming=null;
  frameBalls=BallDetector.detect(pixels,w,h,box,w*(whiteMode?.009:.0075));
  if(whiteMode){
   incoming=findLine(pixels,w,h,box,false);
   if(incoming!=null){double a=whiteMass(pixels,w,h,incoming.x,incoming.y),b=whiteMass(pixels,w,h,incoming.u,incoming.v);if(Math.abs(a-b)<.1)incoming=null;else if(b>a)incoming=incoming.reverse();}
  }
  Geometry.Segment contact=contactGuide(pixels,w,h,box);
  if(contact!=null)return new Result(box,contact,whiteMode?"Tracking · white target guide":"Tracking · contact ring + yellow guide",whiteMode?.009:.0075);
  if(whiteMode||contactSeen)return new Result(box,null,whiteMode?"Waiting for white target guide":"Contact found · waiting for yellow guide");
  Geometry.Segment white=findLine(pixels,w,h,box,false);
  if(white==null)return new Result(box,null,"Waiting for white aiming line");
  double startMass=whiteMass(pixels,w,h,white.x,white.y),endMass=whiteMass(pixels,w,h,white.u,white.v);
  if(Math.abs(startMass-endMass)<.10)return new Result(box,null,"Cue-ball endpoint uncertain");
  double targetX=startMass>endMass?white.u:white.x,targetY=startMass>endMass?white.v:white.y;
  double reach=w*.105;
  Geometry.Box search=new Geometry.Box(Math.max(box.l,targetX-reach),Math.max(box.t,targetY-reach),Math.min(box.r,targetX+reach),Math.min(box.b,targetY+reach));
  Geometry.Segment yellow=findLine(pixels,w,h,search,true);
  if(yellow==null)return new Result(box,null,"Searching for yellow guide");
  double a=Math.min(Math.hypot(yellow.x-white.x,yellow.y-white.y),Math.hypot(yellow.x-white.u,yellow.y-white.v));
  double b=Math.min(Math.hypot(yellow.u-white.x,yellow.v-white.y),Math.hypot(yellow.u-white.u,yellow.v-white.v));
  if(Math.min(a,b)>w*.055||Math.abs(a-b)<w*.008)return new Result(box,null,"Direction uncertain");
  return new Result(box,a<b?yellow:yellow.reverse(),"Tracking · ideal rail reflections");
 }
 private double whiteMass(int[] p,int w,int h,double px,double py){
  int radius=Math.max(4,(int)(w*.005));double best=0;
  for(int oy=-radius;oy<=radius;oy+=2)for(int ox=-radius;ox<=radius;ox+=2){int count=0,total=0;
   for(int y=-radius;y<=radius;y++)for(int x=-radius;x<=radius;x++)if(x*x+y*y<=radius*radius){int xx=(int)px+ox+x,yy=(int)py+oy+y;if(xx>=0&&xx<w&&yy>=0&&yy<h){total++;if(white(p[yy*w+xx]))count++;}}
   best=Math.max(best,count/(double)Math.max(1,total));
  }return best;
 }
 private boolean atWhite(int[] p,int w,int h,double x,double y){int ix=(int)Math.round(x),iy=(int)Math.round(y);return ix>=0&&ix<w&&iy>=0&&iy<h&&white(p[iy*w+ix]);}
 private boolean atYellow(int[] p,int w,int h,double x,double y){int ix=(int)Math.round(x),iy=(int)Math.round(y);return ix>=0&&ix<w&&iy>=0&&iy<h&&yellow(p[iy*w+ix]);}
 private boolean atGuide(int[] p,int w,int h,double x,double y){return whiteMode?atWhite(p,w,h,x,y):atYellow(p,w,h,x,y);}
 private boolean atDark(int[] p,int w,int h,double x,double y){int ix=(int)Math.round(x),iy=(int)Math.round(y);if(ix<0||ix>=w||iy<0||iy>=h)return false;int c=p[iy*w+ix],r=(c>>16)&255,g=(c>>8)&255,b=c&255;return r<170&&g<170&&b<170&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<65;}
 private Geometry.Segment contactGuide(int[] p,int w,int h,Geometry.Box box){
  double radius=w*(whiteMode?.0095:.0075),best=0;Geometry.Segment winner=null;
  double[] cos=new double[20],sin=new double[20];for(int k=0;k<20;k++){cos[k]=Math.cos(k*Math.PI/10);sin[k]=Math.sin(k*Math.PI/10);}
  ArrayList<double[]> rings=new ArrayList<>();
  for(int y=(int)box.t;y<box.b;y++)for(int x=(int)box.l;x<box.r;x++){
   if(whiteMode&&incoming!=null&&Math.hypot(x-incoming.u,y-incoming.v)>radius*2)continue;
   int cardinal=0;for(int k=0;k<20;k+=5)if(atWhite(p,w,h,x+cos[k]*radius*.8,y+sin[k]*radius*.8))cardinal++;
   if(cardinal<2)continue;
   int outer=0,dark=0;
   for(int k=0;k<20;k++){double dx=cos[k],dy=sin[k];if(atWhite(p,w,h,x+dx*radius*.7,y+dy*radius*.7)||atWhite(p,w,h,x+dx*radius*.81,y+dy*radius*.81)||atWhite(p,w,h,x+dx*radius*.9,y+dy*radius*.9))outer++;}
   for(int k=0;k<20;k++){double dx=cos[k],dy=sin[k];if(atDark(p,w,h,x+dx*radius*.5,y+dy*radius*.5)||atDark(p,w,h,x+dx*radius*.6,y+dy*radius*.6)||atDark(p,w,h,x+dx*radius*.68,y+dy*radius*.68))dark++;}
   if(outer>=(whiteMode?12:15)&&dark>=(whiteMode?8:10))rings.add(new double[]{x,y,outer+dark});
  }
  rings.sort((a,b)->Double.compare(b[2],a[2]));
  contactSeen=!rings.isEmpty();
  if(contactSeen){ringX=rings.get(0)[0];ringY=rings.get(0)[1];}
  for(int n=0;n<Math.min(12,rings.size());n++){
   double[] ring=rings.get(n);double cx=ring[0],cy=ring[1];
   for(int angle=0;angle<720;angle++){
    double a=angle*Math.PI/360,dx=Math.cos(a),dy=Math.sin(a);
    if(whiteMode&&incoming!=null&&(dx*(incoming.u-incoming.x)+dy*(incoming.v-incoming.y))<=0)continue;
    if(whiteMode){boolean target=false;for(BallDetector.Ball ball:frameBalls)if(Math.hypot(ball.x-(cx+dx*radius*2),ball.y-(cy+dy*radius*2))<radius*.7)target=true;if(!target)continue;}
    int hits=0,gaps=0;double end=0,first=-1;
    for(double t=radius*1.5;t<radius*12;t+=1){double x=cx+dx*t,y=cy+dy*t;if(!box.contains(x,y))break;
     boolean hit=atGuide(p,w,h,x,y)||atGuide(p,w,h,x-dy*.8,y+dx*.8)||atGuide(p,w,h,x+dy*.8,y-dx*.8);
     if(hit){if(first<0)first=t;hits++;gaps=0;end=t;}else gaps++;
     if(gaps>4&&first>=0)break;if(first<0&&t>radius*2.8)break;
    }
    double length=end-first;
    if(first<0||length<radius*2.2||hits/Math.max(1,length)<.8)continue;
    double score=hits+ring[2]*.5;
    if(score>best){best=score;ringX=cx;ringY=cy;winner=new Geometry.Segment(cx+dx*radius*2,cy+dy*radius*2,cx+dx*end,cy+dy*end);}
   }
  }
  if(winner==null&&whiteMode){
   // A short object-ball guide can be recovered from the contact normal, but only
   // with a real adjacent ball and visible outgoing white pixels beyond its rim.
   for(int n=0;n<Math.min(12,rings.size());n++)for(BallDetector.Ball ball:frameBalls){
    double cx=rings.get(n)[0],cy=rings.get(n)[1],dx=ball.x-cx,dy=ball.y-cy,d=Math.hypot(dx,dy);
    if(d<radius*1.65||d>radius*2.4)continue;dx/=d;dy/=d;
    if(incoming!=null&&dx*(incoming.u-incoming.x)+dy*(incoming.v-incoming.y)<=0)continue;
    int hits=0;for(double t=radius*.7;t<radius*1.6;t++)if(atWhite(p,w,h,ball.x+dx*t,ball.y+dy*t)||atWhite(p,w,h,ball.x+dx*t-dy*2,ball.y+dy*t+dx*2)||atWhite(p,w,h,ball.x+dx*t+dy*2,ball.y+dy*t-dx*2))hits++;
    if(hits>=Math.max(2,radius*.18)){ringX=cx;ringY=cy;return new Geometry.Segment(ball.x,ball.y,ball.x+dx*radius*3,ball.y+dy*radius*3);}
   }
   if(incoming!=null&&!rings.isEmpty()){
    double cx=rings.get(0)[0],cy=rings.get(0)[1];BallDetector.Ball target=null;int count=0;
    for(BallDetector.Ball ball:frameBalls){double d=Math.hypot(ball.x-cx,ball.y-cy);if(d>radius*1.65&&d<radius*2.4&&Math.hypot(ball.x-incoming.x,ball.y-incoming.y)>radius*2&&(ball.x-cx)*(incoming.u-incoming.x)+(ball.y-cy)*(incoming.v-incoming.y)>0){target=ball;count++;}}
    if(count==1){ringX=cx;ringY=cy;geometryTarget=true;return new Geometry.Segment(target.x,target.y,target.x+(target.x-cx)*3,target.y+(target.y-cy)*3);}
   }
   if(incoming==null)stickAim=findStickAim(p,w,h,rings,radius);
  }
  if(winner==null)return null;
  double length=winner.length(),dx=(winner.u-winner.x)/length,dy=(winner.v-winner.y)/length;
  ArrayList<double[]> samples=new ArrayList<>();double mx=0,my=0;
  for(int y=(int)Math.min(winner.y,winner.v)-4;y<=Math.max(winner.y,winner.v)+4;y++)for(int x=(int)Math.min(winner.x,winner.u)-4;x<=Math.max(winner.x,winner.u)+4;x++){
   double along=(x-winner.x)*dx+(y-winner.y)*dy,across=(x-winner.x)*dy-(y-winner.y)*dx;
   if(along>radius*.65&&along<length-radius*.3&&Math.abs(across)<2.8&&atGuide(p,w,h,x,y)){samples.add(new double[]{x,y});mx+=x;my+=y;}
  }
  if(samples.size()<12)return winner;mx/=samples.size();my/=samples.size();double xx=0,xy=0,yy=0;
  for(double[] q:samples){double x=q[0]-mx,y=q[1]-my;xx+=x*x;xy+=x*y;yy+=y*y;}
  double angle=.5*Math.atan2(2*xy,xx-yy),ux=Math.cos(angle),uy=Math.sin(angle);if(ux*dx+uy*dy<0){ux=-ux;uy=-uy;}
  double a=(winner.x-mx)*ux+(winner.y-my)*uy,b=(winner.u-mx)*ux+(winner.v-my)*uy;
  return new Geometry.Segment(mx+a*ux,my+a*uy,mx+b*ux,my+b*uy);
 }
 private Geometry.Segment findStickAim(int[] p,int w,int h,List<double[]> rings,double radius){
  double best=0;Geometry.Segment aim=null;
  for(int n=0;n<Math.min(24,rings.size());n++){
   double x=rings.get(n)[0],y=rings.get(n)[1];
   if(whiteMass(p,w,h,x,y)<.22)continue;
   for(int angle=0;angle<360;angle++){
    double dx=Math.cos(angle*Math.PI/180),dy=Math.sin(angle*Math.PI/180);int hits=0,near=0,total=0;double far=0;
    for(double t=radius*2;t<w*.42;t+=3){double xx=x+dx*t,yy=y+dy*t;if(xx<0||yy<0||xx>=w||yy>=h)break;total++;boolean hit=false;
     for(int k=-2;k<=2;k++){int ix=(int)(xx-dy*k),iy=(int)(yy+dx*k);if(ix<0||iy<0||ix>=w||iy>=h)continue;int c=p[iy*w+ix],red=(c>>16)&255,green=(c>>8)&255,blue=c&255;if(red>95&&red>green*1.12&&green>blue*1.3&&blue<130)hit=true;}
     if(hit){hits++;far=t;if(t<radius*7)near++;}
    }
    if(near>=5&&far>w*.15&&hits>total*.42&&hits>best){best=hits;aim=new Geometry.Segment(x,y,x-dx*100,y-dy*100);}
   }
  }return aim;
 }
 private Geometry.Box findTable(int[] p,int w,int h){
  int[] cols=new int[w],rows=new int[h];
  for(int y=h/20;y<h;y++)for(int x=w/10;x<w*9/10;x++){
   int c=p[y*w+x],r=(c>>16)&255,g=(c>>8)&255,b=c&255;
   if((g>110&&b>110&&g>r*1.18&&b>r*1.18)||(b>100&&g>65&&b>g*1.2&&b>r*1.5)){cols[x]++;rows[y]++;}
  }
  int l=w,r=0,t=h,b=0;
  for(int x=0;x<w;x++)if(cols[x]>h*.35){l=Math.min(l,x);r=x;}
  for(int y=0;y<h;y++)if(rows[y]>w*.40){t=Math.min(t,y);b=y;}
  if(r-l<w*.4||b-t<h*.35)return null;
  // Cyan cushions surround the cloth; locate the inner edges with a conservative inset.
  double inset=w*.009;
  int center=p[(h/2)*w+w/2];boolean blueFelt=(center&255)>((center>>8)&255)*1.2;
  double verticalInset=blueFelt?1:inset;
  return new Geometry.Box(l+inset,t+verticalInset,r-inset,b-verticalInset);
 }
 private Geometry.Segment findLine(int[] p,int w,int h,Geometry.Box box,boolean isYellow){
  boolean[] mask=new boolean[w*h];ArrayList<int[]> points=new ArrayList<>();
  int left=Math.max(6,(int)box.l+3),right=Math.min(w-6,(int)box.r-3),top=Math.max(6,(int)box.t+3),bottom=Math.min(h-6,(int)box.b-3);
  for(int y=top;y<bottom;y++)for(int x=left;x<right;x++)mask[y*w+x]=isYellow?yellow(p[y*w+x]):white(p[y*w+x]);
  for(int y=top;y<bottom;y++)for(int x=left;x<right;x++)if(mask[y*w+x]){
   int count=0;for(int yy=-4;yy<=4;yy+=2)for(int xx=-4;xx<=4;xx+=2)if(mask[(y+yy)*w+x+xx])count++;
   if(count<=14)points.add(new int[]{x,y});
  }
  int n=points.size();if(n<18||n>30000)return null;
  Random random=new Random(19);double best=0;Geometry.Segment winner=null;
  double minLength=w*(isYellow?.016:.055),maxLength=w*(isYellow?.23:.85);
  for(int trial=0;trial<900;trial++){
   int[] a=points.get(random.nextInt(n)),b=points.get(random.nextInt(n));
   double dx=b[0]-a[0],dy=b[1]-a[1],len=Math.hypot(dx,dy);if(len<minLength*.7)continue;dx/=len;dy/=len;
   int offset=w+h;int[] bins=new int[offset*2+1];
   for(int[] q:points){double px=q[0]-a[0],py=q[1]-a[1];if(Math.abs(px*dy-py*dx)<=2.2){int bin=(int)Math.round(px*dx+py*dy)+offset;if(bin>=0&&bin<bins.length)bins[bin]++;}}
   int start=-1,last=-1,support=0;
   for(int k=0;k<bins.length+5;k++){
    if(k<bins.length&&bins[k]>0){if(start<0){start=k;support=0;}last=k;support++;}
    if(start>=0&&k-last>4){double length=last-start,density=support/Math.max(1.,length);double score=length*density*density;
     if(length>=minLength&&length<=maxLength&&density>.72&&score>best){best=score;winner=new Geometry.Segment(a[0]+(start-offset)*dx,a[1]+(start-offset)*dy,a[0]+(last-offset)*dx,a[1]+(last-offset)*dy);}
     start=-1;
    }
   }
  }
  if(winner==null)return null;
  // Refit the central portion so round ball edges do not steer the guide angle.
  double ux=(winner.u-winner.x)/winner.length(),uy=(winner.v-winner.y)/winner.length(),length=winner.length();
  double mx=0,my=0;int count=0;ArrayList<int[]> inliers=new ArrayList<>();
  for(int[] q:points){double x=q[0]-winner.x,y=q[1]-winner.y,along=x*ux+y*uy;
   if(along>length*.12&&along<length*.88&&Math.abs(x*uy-y*ux)<3){inliers.add(q);mx+=q[0];my+=q[1];count++;}}
  if(count<12)return winner;mx/=count;my/=count;
  double xx=0,yy=0,xy=0;for(int[] q:inliers){double x=q[0]-mx,y=q[1]-my;xx+=x*x;yy+=y*y;xy+=x*y;}
  double angle=.5*Math.atan2(2*xy,xx-yy),dx=Math.cos(angle),dy=Math.sin(angle);if(dx*ux+dy*uy<0){dx=-dx;dy=-dy;}
  double a=(winner.x-mx)*dx+(winner.y-my)*dy,b=(winner.u-mx)*dx+(winner.v-my)*dy;
  return new Geometry.Segment(mx+a*dx,my+a*dy,mx+b*dx,my+b*dy);
 }
}
