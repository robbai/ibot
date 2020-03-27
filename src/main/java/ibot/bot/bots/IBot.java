package ibot.bot.bots;

import java.awt.Color;
import java.util.OptionalDouble;

import ibot.boost.BoostManager;
import ibot.bot.abort.BallTouchedAbort;
import ibot.bot.abort.SliceOffPredictionAbort;
import ibot.bot.actions.Action;
import ibot.bot.actions.Aerial;
import ibot.bot.actions.DriveStrike;
import ibot.bot.actions.FastDodge;
import ibot.bot.actions.Jump;
import ibot.bot.controls.Handling;
import ibot.bot.input.Info;
import ibot.bot.input.Pencil;
import ibot.bot.intercept.AerialType;
import ibot.bot.intercept.Intercept;
import ibot.bot.intercept.InterceptCalculator;
import ibot.bot.utils.Constants;
import ibot.bot.utils.MathsUtils;
import ibot.bot.utils.Mode;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.ControlsOutput;
import ibot.output.Output;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class IBot extends ABot {

	private Action action;

	public IBot(int index, int team){
		super(index, team);
	}

	@Override
	protected ControlsOutput processInput(){
		DataPacket packet = this.bundle.packet;
		Pencil pencil = this.bundle.pencil;
		Info info = this.bundle.info;
		Car car = packet.car;

		if(this.action != null){
			if(!(this.action instanceof DriveStrike) || !car.hasWheelContact){
				if(!this.action.isFinished()){
					pencil.stackRenderString(this.action.getClass().getSimpleName(), pencil.altColour);
					return action.getOutput();
				}else{
					this.action = null;
				}
			}
		}

		if(info.mode != Mode.SOCCAR || !packet.isKickoffPause){
			if(info.aerialDodge != null)
				pencil.renderer.drawLine3d(Color.BLACK, info.carPosition, info.aerialDodge.position);
			if(info.aerialDouble != null)
				pencil.renderer.drawLine3d(Color.WHITE, info.carPosition, info.aerialDouble.position);
		}

		if(info.commit && (!info.isKickoff || (car.forwardVelocity > 1300 && info.mode != Mode.SOCCAR))){
			boolean doubleJump;
			if(info.isKickoff || !info.car.hasWheelContact){
				// We use the double-jump intercept for starting mid-air too.
				doubleJump = true;
			}else if(info.aerialDouble != null){
				Vector3 localDouble = MathsUtils.local(info.car, info.aerialDouble.position);
				doubleJump = (localDouble.z > 550
						&& localDouble.normalised().z > MathsUtils.lerp(0.6, 0.3,
								Math.pow(info.carSpeed / Constants.MAX_CAR_VELOCITY, 2))
						|| (info.aerialDouble.position.z > Constants.GOAL_HEIGHT - 50
								&& Math.abs(info.aerialDouble.position.x) < Constants.GOAL_WIDTH + 200
								&& Math.abs(info.aerialDouble.position.y) < -Constants.PITCH_LENGTH_SOCCAR + 1100));
			}else{
				doubleJump = (info.aerialDodge == null);
			}
			// if(!info.kickoff) doubleJump = (info.aerialDodge == null || new
			// Spherical(MathsUtils.local(car,
			// info.aerialDodge.ballPosition)).getElevation() > Math.toRadians(55));
			// if(!info.kickoff) doubleJump = (info.aerialDodge == null);
			AerialType type = (doubleJump ? AerialType.DOUBLE_JUMP : AerialType.DODGE_STRIKE);
			Intercept aerialIntercept = (doubleJump ? info.aerialDouble : info.aerialDodge);
			if(aerialIntercept != null){
				boolean betterThanDriveStrike = true;
				if(this.action != null && this.action instanceof DriveStrike){
					DriveStrike driveStrike = (DriveStrike)this.action;
					betterThanDriveStrike = (driveStrike.intercept.time > aerialIntercept.time);
				}

				Vector3 localIntercept = MathsUtils.local(info.car, aerialIntercept.position);
				double radians = Vector2.Y.correctionAngle(localIntercept.flatten());
				boolean theirSide = (aerialIntercept.position.y * info.sign >= 0);
				if(betterThanDriveStrike && (Math.abs(info.possession) < 0.15 || (Math
						.abs(radians) < Math.toRadians(doubleJump ? 35 : 45) * (info.goingInHomeGoal ? 1.5 : 1)
						&& (info.groundIntercept == null
								|| localIntercept.z > (localIntercept.magnitude() < 700 ? 90 : (theirSide ? 180 : 230))
								|| (info.carPosition.z > Math.max(500, aerialIntercept.position.z)
										&& info.mode == Mode.DROPSHOT))))){
					// if(true){
					// if(localIntercept.z > (doubleJump ? 190 : 110)){
					this.action = new Aerial(this.bundle, aerialIntercept, type).withAbortCondition(
							new BallTouchedAbort(this.bundle, packet.ball.latestTouch, this.index),
							new SliceOffPredictionAbort(this.bundle, aerialIntercept));
					return this.action.getOutput();
				}
			}
		}

		if(this.action != null){
			if(!this.action.isFinished()){
				pencil.stackRenderString(this.action.getClass().getSimpleName(), pencil.altColour);
				return action.getOutput();
			}else{
				this.action = null;
			}
		}

		// Drive.
		boolean wall = !info.car.onFlatGround;
		this.action = null;
		pencil.stackRenderString("Drive", pencil.colour);
		Vector3 target;
		OptionalDouble targetTime = OptionalDouble.empty();
		boolean dontBoost = false;
		if(info.commit){
			Vector3 localInterceptBall = MathsUtils.local(info.car, info.groundIntercept.position);
			Vector3 dodgeTarget = DriveStrike.getDodgeTarget(info.groundIntercept);
			if(info.wallIntercept != null){
				target = info.wallIntercept.intersectPosition;
				// if(target.y * info.sign > 0 && Math.abs(info.ballPosition.x) >
				// Math.abs(target.x)){
				// target = target.withX(Math.copySign(target.x, info.ballPosition.x * 5));
				// }
				pencil.renderer.drawLine3d(Color.GREEN, info.carPosition, info.wallIntercept.position);
				wall = true;
			}else if(localInterceptBall.z > 180 && info.bounce != null && info.bounce.position.y * info.sign < 0){
				target = info.bounce.position;
				if(Math.abs(car.position.y) < Constants.PITCH_LENGTH_SOCCAR
						|| Math.abs(target.x) < Constants.GOAL_WIDTH){
					target = target.plus(Vector3.Y.scaleToMagnitude(-info.sign * target.distance(car.position)
							* Math.min((info.bounce.time - info.time) / 2.5, 0.4)));
				}
				targetTime = OptionalDouble.of(info.bounce.time);
			}else{
				target = info.groundIntercept.position;
				if(info.mode == Mode.DROPSHOT){
					Vector3 offset = Vector3.Y;
					target = target.plus(offset.scaleToMagnitude(
							-info.sign * target.distance(car.position) * (info.isKickoff ? 0.05 : 0.2)));
				}else if(info.mode == Mode.SOCCAR && !info.isKickoff){
					target = info.groundIntercept.intersectPosition;
					if((info.groundIntercept.position.y - info.car.position.y) * info.sign < 0){
						Vector2 toIntercept = target.minus(info.car.position).flatten().normalised();
						Vector2 trace = info.car.position.flatten()
								.plus(toIntercept.scale((-info.sign * Constants.PITCH_LENGTH_SOCCAR) / toIntercept.y));
						// if(Math.abs(trace.x) < Constants.GOAL_WIDTH + 250){
						Vector3 xSkew = InterceptCalculator.X_SKEW.withZ(1);
						if(xSkew.x * trace.x > 0){
							xSkew = xSkew.withX(-xSkew.x);
						}
						target = info.groundIntercept.position.plus(info.groundIntercept.getOffset().multiply(xSkew));
						// }
					}

					double distance = MathsUtils.local(info.car, target).flatten().magnitude();
					double addedOffset = 1500 * Math.floor(distance / 3000);
					if(addedOffset > 0.001){
						target = target.plus(target.minus(info.groundIntercept.position).scaleToMagnitude(addedOffset))
								.clamp();
					}else if(MathsUtils.local(info.car, info.groundIntercept.position).z > 160
							&& info.car.hasWheelContact){
						double radians = Vector2.Y.correctionAngle(localInterceptBall.flatten());
						radians = MathsUtils.shorterAngle(radians);
						if(Math.abs(info.carForwardComponent) > 0.975 /* && Math.abs(radians) < Math.toRadians(50) */){
							this.action = new DriveStrike(this.bundle,
									info.groundIntercept.withIntersectPosition(dodgeTarget), info.enemyGoal)
											.withAbortCondition(
													new BallTouchedAbort(this.bundle, packet.ball.latestTouch,
															this.index),
													new SliceOffPredictionAbort(this.bundle, info.groundIntercept));
							return this.action.getOutput();
						}
					}
				}else{
					target = info.groundIntercept.intersectPosition;
				}
				pencil.renderer.drawLine3d(pencil.altColour, info.groundIntercept.intersectPosition,
						info.groundIntercept.position);
				boolean challenge = (Math.abs(info.possession) < 0.2);
				pencil.renderer.drawRectangle3d(pencil.colour, info.groundIntercept.intersectPosition, 8, 8, true);
				if(info.car.hasWheelContact && info.getTimeOnGround() > 0.2
						&& (info.groundIntercept.time - info.time) < 0.3
						&& Math.abs(info.lastControls.getSteer()) < (info.goingInHomeGoal || challenge ? 0.4 : 0.2)){
					// boolean opponentBlocking = false;
					// for(Car car : packet.enemies){
					// if(info.groundIntercept.position.minus(info.car.position).angle(car.position.minus(info.car.position))
					// < Math.toRadians(30)){
					// opponentBlocking = true;
					// break;
					// }
					// }
					if(Math.abs(localInterceptBall.z) > 105 || challenge
							|| info.carSpeed < (info.car.onFlatGround ? 1100 : 1550)){
						if((info.groundIntercept.time - info.time) < 0.255){
							this.action = new FastDodge(this.bundle, dodgeTarget.minus(info.carPosition));
							return this.action.getOutput();
						}
					}else{
						target = dodgeTarget;
					}
				}
			}
		}else{
			boolean high = (info.car.position.z > 150);

			// Intercept enemyIntersect = (info.furthestBack ? null :
			// info.enemyIntersect());
			// Intercept enemyIntersect = (!info.furthestBack && info.car.isSupersonic &&
			// info.groundIntercept.time - info.time > 1.1 ? info.enemyIntersect() : null);
			// Intercept enemyIntersect = info.enemyIntersect();
			// if(enemyIntersect != null && (enemyIntersect.position.y -
			// info.car.position.y) * info.sign > 0){
			// enemyIntersect = null;
			// }

			info.pickupBoost = false;
			if(info.carSpeed < 1300 && high && info.car.hasWheelContact && info.car.velocity.z < 450){
				this.action = new Jump(this.bundle, 30D / 120);
				return action.getOutput();

				// }else if(enemyIntersect != null){
				// target = enemyIntersect.position;
			}else if((info.car.boost < 40 || info.isKickoff) && info.mode != Mode.DROPSHOT
					&& info.nearestBoost != null){
				target = info.nearestBoost.getLocation().withZ(Constants.CAR_HEIGHT);

				info.pickupBoost = true;

				if(info.nearestBoost.isFullBoost() || info.mode == Mode.HOOPS){
					// if((info.car.onSuperFlatGround && info.carForwardComponent > 0.975 &&
					// !info.lastControls.holdHandbrake()) || info.isKickoff){
					// Vector2 endTarget = (info.teamPossession >= -0.001 || info.isKickoff ?
					// target.flatten().withY(0) : info.homeGoal.flatten()/*.multiply(new
					// Vector2(-1, 1))*/);
					//
					// // Vector2 endTarget;
					// // Vector2 direction = target.minus(info.carPosition).flatten().normalised();
					// // if(Math.abs(target.y) < 1000){
					// // endTarget = (direction.y * info.sign > 0 ?
					// info.groundIntercept.intersectPosition.flatten() :
					// info.homeGoal.flatten().multiply(new Vector2(-1, 1)));
					// // }else{
					// // if(Math.abs(direction.x) > Math.abs(direction.y)){
					// // endTarget = target.withY(0).flatten();
					// // }else{
					// // endTarget = target.withX(0).flatten();
					// // }
					// // }
					//
					// CompositeArc compositeArc = CompositeArc.create(info.car, target.flatten(),
					// endTarget, 1000, 200, 300);
					//
					// // CompositeArc compositeArc = null;
					// // for(int i = 0; i < 2; i++){
					// // Vector2 endTarget = target.flatten();
					// // if(i < 2){
					// // endTarget = endTarget.withX(0);
					// // }
					// // if(i > 0){
					// // endTarget = endTarget.withY(0);
					// // }
					// // CompositeArc arcs = CompositeArc.create(info.car, target.flatten(),
					// endTarget, 1200, 150, 300);
					// // if(compositeArc == null || arcs.getLength() < compositeArc.getLength()){
					// // compositeArc = arcs;
					// // }
					// // }
					//
					// this.action = new FollowArcs(this, compositeArc).withAbortCondition(new
					// BoostYoinkedAbort(this, info.nearestBoost));
					// if(!info.isKickoff){
					// this.action.withAbortCondition(new CommitAbort(this, 0));
					// }
					// return action.getOutput(packet);
					// }

					if(info.nearestBoost.isFullBoost()){
						target = target.scale(1 - (35 / target.magnitude()));
					}else{
						dontBoost = true;
					}
					Vector2 offset = target.minus(car.position).flatten().rotate(Math.PI / 2);
					if(offset.dot(target.flatten()) < 0)
						offset = offset.scale(-1);
					target = target.plus(offset.scale(info.nearestBoost.isFullBoost() && !info.isKickoff ? 0.12 : 0.1));
				}
				// }else if(high || info.carVel < 1500 || info.enemyCar == null){
				// target = packet.ball.position.withX(info.groundIntercept.interceptPosition.x
				// * (info.mode == Mode.SOCCAR ? 0.4 : 0.75))
				// .withY(Math.copySign(packet.ball.position.y * MathsUtils.lerp(0.9, 0.45,
				// car.boost / 100), -car.sign));
				// }
			}else{
				// dontBoost = info.teamPossession > 0;
				dontBoost = !info.lastMan;

				final double MIN_DEPTH = 0.3;
				final double MAX_DEPTH = 0.8;
				// double depthLerp = MathsUtils.clamp((info.teamPossession * 0.6) + 0.35, 0,
				// 1);
				double depthLerp = info.car.boost / 100;
				pencil.stackRenderString("Depth: " + MathsUtils.round(depthLerp), pencil.colour);

				// target = info.homeGoal.lerp(info.earliestEnemyIntercept.position,
				// MathsUtils.lerp(MIN_DEPTH, MAX_DEPTH,
				// depthLerp)).withZ(Constants.CAR_HEIGHT);

				double goalDistance = info.earliestEnemyIntercept.position.distance(info.homeGoal);
				Vector2 direction = info.earliestEnemyIntercept.getOffset().flatten().scaleToMagnitude(-1);
				// direction =
				// direction.plus(info.earliestEnemyIntercept.car.velocity.flatten().scale(0.3 /
				// Constants.SUPERSONIC_VELOCITY));
				target = info.earliestEnemyIntercept.position.plus(direction
						.scaleToMagnitude(goalDistance * (1 - MathsUtils.lerp(MIN_DEPTH, MAX_DEPTH, depthLerp))));
				if(Math.abs(target.x) > Constants.PITCH_WIDTH_SOCCAR - 400){
					target = target.withX(Math.copySign(Constants.PITCH_WIDTH_SOCCAR - 400, target.x));
				}
				if(Math.abs(target.y) > Constants.PITCH_LENGTH_SOCCAR - 300){
					target = target.withY(Math.copySign(Constants.PITCH_LENGTH_SOCCAR - 400, target.y));
				}

				// if(info.furthestBack || info.possession > -0.2){
				if(true){
					if(info.teamPossession * info.earliestEnemyIntercept.position.y * info.sign > 0
							|| info.furthestBack){
						target = target.withX(target.x * 0.6);
					}

					if(info.car.boost < 45 && target.y * info.sign < -1000 && goalDistance > 3500){
						target = Info.findNearestBoost(target.plus(info.car.velocity.scale(0.5)).flatten(),
								BoostManager.getSmallBoosts()).getLocation().withZ(Constants.CAR_HEIGHT);
					}
				}else{
					double distance = info.homeGoal.distance(info.car.position);
					final double closingDistance = 1000;
					double nose = Math.max(0, info.car.orientation.forward.y * info.sign);

					double x = MathsUtils.clamp((closingDistance - distance) / closingDistance, -3.5, 1);
					double y = (Constants.PITCH_LENGTH_SOCCAR - 300 - nose * 1200) / Math.abs(info.car.position.y);
					target = info.homeGoal.multiply(new Vector3(x, y, 1));
				}
				// else{
				// Vector2 fromGoal = info.backTeammate.position.minus(info.homeGoal).flatten();
				// fromGoal = fromGoal.multiply(new Vector2(-0.35, 0.45));
				// target = info.homeGoal.plus(fromGoal);
				// }

				pencil.renderer.drawLine3d(Color.BLACK, info.carPosition, target);
				pencil.renderer.drawLine3d(pencil.altColour, info.earliestEnemyIntercept.position, target);

				// double distance = target.distance(info.car.position);
				// //// // boolean correctSide = (info.car.position.y -
				// info.groundIntercept.intersectPosition.y) * info.sign < 0;
				// if(distance > 3000 && info.carForwardComponent > 0.95){
				// Vector2 endTarget = info.earliestEnemyIntercept.position.flatten();
				// CompositeArc compositeArc = CompositeArc.create(info.car, target.flatten(),
				// endTarget, 1300, 200,
				// 300);
				// this.action = new FollowArcs(this, compositeArc).withBoost(false)
				// .withAbortCondition(new CommitAbort(this, 0.1));
				// return action.getOutput(packet);
				// }
			}
		}
		Output output = Handling.driveTime(this.bundle, target, (!info.isKickoff || info.mode == Mode.SOCCAR && !wall),
				info.mode == Mode.DROPSHOT || dontBoost, targetTime);
		if(output.isAction()){
			this.action = (Action)output;
			return this.action.getOutput();
		}
		ControlsOutput controls = (ControlsOutput)output;
		if(info.isKickoff && info.car.hasWheelContact && info.commit){
			controls.withBoost(info.mode != Mode.HOOPS || info.car.boost > 22);
		}else if(controls.holdBoost()){
			controls.withBoost(!dontBoost);
		}
		return controls;
	}

}
