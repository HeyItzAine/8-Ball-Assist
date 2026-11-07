package app.railguide;
import java.util.*;

public final class BallDetector {
 public static final class Ball {
  public final double x,y,r,confidence;
  public final int color;
  public Ball(double x,double y,double r,double confidence){this(x,y,r,confidence,0xffeeeeee);}
  public Ball(double x,double y,double r,double confidence,int color){this.x=x;this.y=y;this.r=r;this.confidence=confidence;this.color=color;}
 }
 private static double difference(int a,int b){return Math.abs(((a>>16)&255)-((b>>16)&255))+Math.abs(((a>>8)&255)-((b>>8)&255))+Math.abs((a&255)-(b&255));}
 private static int pixel(int[] p,int w,int h,double x,double y){return p[Math.max(0,Math.min(h-1,(int)Math.round(y)))*w+Math.max(0,Math.min(w-1,(int)Math.round(x)))];}
 public static List<Ball> detect(int[] p,int w,int h,Geometry.Box table,double radius){
  ArrayList<Ball> candidates=new ArrayList<>(),balls=new ArrayList<>();if(table==null)return balls;
  double[] dx=new double[24],dy=new double[24];for(int k=0;k<24;k++){dx[k]=Math.cos(k*Math.PI/12);dy[k]=Math.sin(k*Math.PI/12);}
  int step=Math.max(2,(int)(radius/4));
  for(int y=(int)(table.t+radius*.7);y<table.b-radius*.7;y+=step)for(int x=(int)(table.l+radius*.7);x<table.r-radius*.7;x+=step){
   int edges=0,opposites=0;double score=0;boolean[] votes=new boolean[24];
   for(int k=0;k<24;k++){
    int inside=pixel(p,w,h,x+dx[k]*radius*.68,y+dy[k]*radius*.68),rim=pixel(p,w,h,x+dx[k]*radius*.98,y+dy[k]*radius*.98),outside=pixel(p,w,h,x+dx[k]*radius*1.35,y+dy[k]*radius*1.35);
    double contrast=difference(inside,outside),edge=difference(rim,outside);
    if(contrast>55&&edge>30){votes[k]=true;edges++;score+=Math.min(250,contrast);}
   }
   for(int k=0;k<12;k++)if(votes[k]&&votes[k+12])opposites++;
   if(edges>=17&&opposites>=7)candidates.add(new Ball(x,y,radius,edges+score/600,color(p,w,h,x,y,radius)));
  }
  candidates.sort((a,b)->Double.compare(b.confidence,a.confidence));
  for(Ball b:candidates){boolean duplicate=false;for(Ball old:balls)if(Math.hypot(b.x-old.x,b.y-old.y)<radius*1.5){duplicate=true;break;}if(!duplicate)balls.add(b);if(balls.size()==24)break;}
  return balls;
 }
 private static int color(int[] p,int w,int h,double x,double y,double r){
  int best=0xffeeeeee;double score=0;int dark=0;
  for(int yy=-(int)(r*.7);yy<=r*.7;yy++)for(int xx=-(int)(r*.7);xx<=r*.7;xx++)if(xx*xx+yy*yy<r*r*.49){
   int c=pixel(p,w,h,x+xx,y+yy),red=(c>>16)&255,g=(c>>8)&255,b=c&255,max=Math.max(red,Math.max(g,b)),min=Math.min(red,Math.min(g,b));
   if(max<65)dark++;double s=(max-min)*(max>85?1:0);if(s>score){score=s;best=c;}
  }
  return score<55&&dark>r*r*.18?0xff252525:best;
 }
}
