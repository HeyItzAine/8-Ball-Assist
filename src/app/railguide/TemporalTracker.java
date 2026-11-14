package app.railguide;

public final class TemporalTracker {
 private Detector.Result last;private long observed;private int confirmations;
 public void reset(){last=null;observed=0;confirmations=0;}
 public Detector.Result update(Detector.Result current,long now){
  if(current.cueOnly||current.estimated){reset();return current;}
  if(current.line!=null){
   if(last!=null&&now>=observed&&now-observed<=350&&AimFilter.angle(last.line,current.line)<Math.toRadians(1.2)&&Math.hypot(last.line.x-current.line.x,last.line.y-current.line.y)<current.frameWidth*current.ballRadiusRatio*.35)confirmations++;else confirmations=1;
   last=current;observed=now;return current;
  }
  if(last==null||confirmations<2||now<observed||now-observed>350||current.table==null||current.approach==null||!Double.isFinite(current.contactX)||last.approach==null)return current;
  double r=current.ballRadiusRatio*current.frameWidth;
  if(Math.hypot(current.approach.x-last.approach.x,current.approach.y-last.approach.y)>r*.6||Math.hypot(current.contactX-last.contactX,current.contactY-last.contactY)>r*.75)return current;
  double a=Math.atan2(current.approach.v-current.approach.y,current.approach.u-current.approach.x),b=Math.atan2(last.approach.v-last.approach.y,last.approach.u-last.approach.x);
  if(Math.abs(Math.atan2(Math.sin(a-b),Math.cos(a-b)))>.08)return current;
  boolean targetStillPresent=false;for(BallDetector.Ball ball:current.balls)if(Math.hypot(ball.x-last.line.x,ball.y-last.line.y)<r*.7)targetStillPresent=true;
  if(!targetStillPresent)return current;
  double dx=last.line.x-current.contactX,dy=last.line.y-current.contactY,n=Math.hypot(dx,dy);if(n<r*1.5||n>r*2.6)return current;
  Geometry.Segment predicted=new Geometry.Segment(last.line.x,last.line.y,last.line.x+dx/n*last.line.length(),last.line.y+dy/n*last.line.length());
  Detector.Result result=current.withLine(predicted,"Estimated from recent contact · low confidence");result.estimated=true;result.cueLine=ShotPhysics.tangent(current.approach,predicted,current.contactX,current.contactY);return result;
 }
}
