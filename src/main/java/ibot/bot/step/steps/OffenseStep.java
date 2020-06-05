package ibot.bot.step.steps;

import java.util.OptionalDouble;

import ibot.bot.abort.BallTouchedAbort;
import ibot.bot.abort.NotMyPredictionAbort;
import ibot.bot.input.Bundle;
import ibot.bot.input.Info;
import ibot.bot.input.Pencil;
import ibot.bot.intercept.SeamIntercept;
import ibot.bot.stack.PopStack;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.bot.utils.maths.MathsUtils;
import ibot.bot.utils.rl.Constants;
import ibot.bot.utils.rl.Mode;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.Output;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class OffenseStep extends Step {

	private final DriveStep drive;

	public boolean canPop = true;
	protected boolean addOffset = true;

	public OffenseStep(Bundle bundle){
		super(bundle);
		this.drive = new DriveStep(bundle);
	}

	@Override
	public Output getOutput(){
		DataPacket packet = this.bundle.packet;
		Pencil pencil = this.bundle.pencil;
		Info info = this.bundle.info;
		Car car = packet.car;

		if(!info.commit && this.canPop /* && packet.time - this.getStartTime() > 0.2 */){
			return new PopStack();
		}

		Vector3 target;
		OptionalDouble targetTime = OptionalDouble.empty();
		Vector3 localInterceptBall = MathsUtils.local(car, info.groundIntercept.position);
		Vector3 dodgeTarget = DriveStrikeStep.getDodgeTarget(info.groundIntercept);
		boolean seam = info.groundIntercept instanceof SeamIntercept;

		this.drive.routing = true;

		if(seam || info.groundIntercept.plane.differentNormal(Vector3.Z)){
			// Wall intercept.
////			if(car.hasWheelContact){
////			if(!info.groundIntercept.plane.differentNormal(car)){
//			if(info.groundIntercept.plane.differentNormal(Vector3.Z)){
//				return new DriveStrikeStep(this.bundle, info.groundIntercept, false).withAbortCondition(
//						new BallTouchedAbort(this.bundle, packet.ball.latestTouch, car.index),
//						new SliceOffPredictionAbort(this.bundle, info.groundIntercept));
//			}else
			if(seam){
				target = ((SeamIntercept)info.groundIntercept).seamPosition.setDistanceFrom(car.position, 1000);
			}else{
				target = info.groundIntercept.intersectPosition;
			}
//		}else if(car.onFlatGround && localInterceptBall.z < 120
//				&& BallPrediction.getTime(info.groundIntercept.time + 0.1).position.z < 120 + Constants.CAR_HEIGHT && info.possession > 0.6){
//			target = info.groundIntercept.intersectPosition;
//
//			Vector2 trace = MathsUtils.traceToWall(info.groundIntercept.intersectPosition.flatten(),
//					info.groundIntercept.getOffset().scale(-1).flatten(), info.arena.getWidth(), info.arena.getLength());
//			if(Math.abs(trace.x) > Constants.GOAL_WIDTH - Constants.BALL_RADIUS || trace.y * car.sign < 0){
//				double distance = target.distance(car.position);
//
//				Biarc biarc = new Biarc(car.position.flatten(), car.orientation.forward.flatten(), target.flatten(),
//						trace.minus(target.flatten()));
//				double arcDistance = biarc.getLength();
//				double ratio = (arcDistance / distance);
//				double index = ((ratio * (info.groundIntercept.time - car.time)) / BallPrediction.DT);
//				BallSlice slice = BallPrediction.get((int)MathsUtils.clamp(index, 0, BallPrediction.SLICE_COUNT - 1));
//				target = biarc.S(Math.min(biarc.getLength(), 500)).withZ(Constants.CAR_HEIGHT);
//				pencil.renderer.drawRectangle3d(Color.RED, target, 8, 8, true);
//				pencil.renderer.drawRectangle3d(Color.BLUE, slice.position, 8, 8, true);
//				pencil.renderer.drawRectangle3d(Color.GREEN, info.groundIntercept.position, 8, 8, true);
//				pencil.stackRenderString(MathsUtils.round(ratio) + "", Color.BLUE);
//			}
		}else if(localInterceptBall.z > 180 && info.bounce != null
//				&& info.possession > Math.max(0.225, info.teamPossessionCorrectSide)
				&& info.possession > 0.3){
			target = info.bounce.position;
			if(Math.abs(car.position.y) < Constants.PITCH_LENGTH_SOCCAR || Math.abs(target.x) < Constants.GOAL_WIDTH){
				target = target.plus(Vector3.Y.scaleToMagnitude(-car.sign * target.distance(car.position)
						* Math.min((info.bounce.time - info.time) / 2.5, 0.4)));
			}
			targetTime = OptionalDouble.of(info.bounce.time);
		}else{
			target = info.groundIntercept.position;
			if(info.arena.getMode() == Mode.DROPSHOT){
				Vector3 offset = Vector3.Y;
				target = target.plus(offset
						.scaleToMagnitude(-car.sign * target.distance(car.position) * (info.isKickoff ? 0.05 : 0.2)));
			}else if(info.arena.getMode() == Mode.SOCCAR && !info.isKickoff){
				target = info.groundIntercept.intersectPosition;
//				if((info.groundIntercept.position.y - car.position.y) * car.sign < 0){
//					Vector2 toIntercept = target.minus(car.position).flatten().normalised();
//					Vector2 trace = car.position.flatten()
//							.plus(toIntercept.scale((-car.sign * Constants.PITCH_LENGTH_SOCCAR) / toIntercept.y));
//					// if(Math.abs(trace.x) < Constants.GOAL_WIDTH + 250){
//					Vector3 xSkew = InterceptCalculator.X_SKEW.withZ(1);
//					if(xSkew.x * trace.x > 0){
//						xSkew = xSkew.withX(-xSkew.x);
//					}
//					target = info.groundIntercept.position.plus(info.groundIntercept.getOffset().multiply(xSkew));
//					// }
//				}

				double distance = MathsUtils.local(car, target).flatten().magnitude();
//				double addedOffset = (this.addOffset && info.possession > 0.2 ? Math.floor(distance / 2000) * 1250 : 0);
//				double addedOffset = 0;
				double addedOffset = (this.addOffset ? Math.min(distance - 1000, 0) * 0.625 : 0);
				if(addedOffset > MathsUtils.EPSILON){
					this.drive.routing = false;
					target = target.plus(info.groundIntercept.getOffset().scaleToMagnitude(addedOffset)).clamp();
				}else if(addedOffset < 1000 && car.hasWheelContact && Math.abs(info.carForwardComponent) > 0.975
						&& info.getTimeOnGround() > 0.1){
					double height = MathsUtils.local(car, info.groundIntercept.position).z;
					double doubleHeight = MathsUtils.local(car, info.doubleJumpIntercept.position).z;
					double radians = Vector2.Y.angle(localInterceptBall.flatten());
					if(radians < Math.toRadians(40)){
						Step driveStrike = null;
						if(info.possession < 0.2 && car.onFlatGround
								&& info.doubleJumpIntercept.time < info.groundIntercept.time && doubleHeight > 300){
							driveStrike = new DriveStrikeStep(this.bundle, info.doubleJumpIntercept, true);
						}else if(height > 150 && info.possession > -0.4){
							driveStrike = new DriveStrikeStep(this.bundle, info.groundIntercept, false);
						}
						if(driveStrike != null){
							driveStrike.withAbortCondition(
									new BallTouchedAbort(this.bundle, packet.ball.latestTouch, car.index),
//									new SliceOffPredictionAbort(this.bundle, info.groundIntercept));
									new NotMyPredictionAbort(this.bundle, info.groundIntercept));
							return driveStrike;
						}
					}
				}
			}else{
				target = info.groundIntercept.intersectPosition;
			}
			pencil.renderer.drawLine3d(pencil.altColour, info.groundIntercept.intersectPosition,
					info.groundIntercept.position);
			boolean challenge = (Math.abs(info.possession) < 0.18);
			boolean nail = (info.groundIntercept.getOffset().normalised()
					.dot(info.groundIntercept.position.minus(car.position).normalised()) < -0.6);
			pencil.renderer.drawRectangle3d(pencil.colour, info.groundIntercept.intersectPosition, 8, 8, true);
			if(car.hasWheelContact && info.getTimeOnGround() > 0.2 && (info.groundIntercept.time - info.time) < 0.4
					&& Math.abs(info.lastControls.getSteer()) < (info.goingInHomeGoal || challenge ? 0.8 : 0.4)){
				if(Math.abs(localInterceptBall.z) > 130 || challenge || !nail
						|| car.velocity.magnitude() < (car.onFlatGround ? 1100 : 1550)){
					if((info.groundIntercept.time - info.time) < 0.255){
						return new FastDodgeStep(this.bundle, dodgeTarget.minus(car.position));
					}
				}else{
					target = dodgeTarget;
				}
			}
		}

		this.drive.target = target;
		this.drive.withTargetTime(targetTime);
		return this.drive.getOutput();
	}

	@Override
	public int getPriority(){
		return Priority.OFFENSE;
	}

}
