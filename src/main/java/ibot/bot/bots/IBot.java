package ibot.bot.bots;

import java.awt.Color;
import java.util.OptionalDouble;

import ibot.boost.BoostManager;
import ibot.bot.abort.BallTouchedAbort;
import ibot.bot.abort.CommitAbort;
import ibot.bot.abort.SliceOffPredictionAbort;
import ibot.bot.actions.Action;
import ibot.bot.actions.Aerial;
import ibot.bot.actions.DriveStrike;
import ibot.bot.actions.FastDodge;
import ibot.bot.actions.Jump;
import ibot.bot.actions.arcs.CompositeArc;
import ibot.bot.actions.arcs.FollowArcs;
import ibot.bot.controls.Handling;
import ibot.bot.intercept.AerialType;
import ibot.bot.intercept.Intercept;
import ibot.bot.intercept.InterceptCalculator;
import ibot.bot.utils.Constants;
import ibot.bot.utils.DataBot;
import ibot.bot.utils.MathsUtils;
import ibot.bot.utils.Mode;
import ibot.input.DataPacket;
import ibot.output.ControlsOutput;
import ibot.output.Output;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class IBot extends DataBot {

	public IBot(int playerIndex){
		super(playerIndex);
	}

	@Override
	protected ControlsOutput processInput(DataPacket packet){
		if(this.action != null){
			if(!(this.action instanceof DriveStrike) || !this.car.hasWheelContact){
				if(!this.action.isFinished(packet)){
					this.stackRenderString(this.action.getClass().getSimpleName(), this.altColour);
					return action.getOutput(packet);
				}else{
					this.action = null;
				}
			}
		}

		if(this.mode != Mode.SOCCAR || !this.isKickoff){
			if(this.aerialDodge != null)
				renderer.drawLine3d(Color.BLACK, this.carPosition, this.aerialDodge.position);
			if(this.aerialDouble != null)
				renderer.drawLine3d(Color.WHITE, this.carPosition, this.aerialDouble.position);
		}

		if(this.commit && (!this.isKickoff || (car.forwardVelocity > 1300 && this.mode != Mode.SOCCAR))){
			boolean doubleJump;
			if(this.isKickoff || !this.car.hasWheelContact){
				// We use the double-jump intercept for starting mid-air too.
				doubleJump = true;
			}else if(this.aerialDouble != null){
				Vector3 localDouble = MathsUtils.local(this.car, this.aerialDouble.position);
				doubleJump = (localDouble.z > 550
						&& localDouble.normalised().z > MathsUtils.lerp(0.6, 0.3,
								Math.pow(this.carSpeed / Constants.MAX_CAR_VELOCITY, 2))
						|| (this.aerialDouble.position.z > Constants.GOAL_HEIGHT - 50
								&& Math.abs(this.aerialDouble.position.x) < Constants.GOAL_WIDTH + 200
								&& Math.abs(this.aerialDouble.position.y) < -Constants.PITCH_LENGTH_SOCCAR + 1100));
			}else{
				doubleJump = (this.aerialDodge == null);
			}
			// if(!this.kickoff) doubleJump = (this.aerialDodge == null || new
			// Spherical(MathsUtils.local(car,
			// this.aerialDodge.ballPosition)).getElevation() > Math.toRadians(55));
			// if(!this.kickoff) doubleJump = (this.aerialDodge == null);
			AerialType type = (doubleJump ? AerialType.DOUBLE_JUMP : AerialType.DODGE_STRIKE);
			Intercept aerialIntercept = (doubleJump ? this.aerialDouble : this.aerialDodge);
			if(aerialIntercept != null){
				boolean betterThanDriveStrike = true;
				if(this.action != null && this.action instanceof DriveStrike){
					DriveStrike driveStrike = (DriveStrike)this.action;
					betterThanDriveStrike = (driveStrike.intercept.time > aerialIntercept.time);
				}

				Vector3 localIntercept = MathsUtils.local(this.car, aerialIntercept.position);
				double radians = Vector2.Y.correctionAngle(localIntercept.flatten());
				boolean theirSide = (aerialIntercept.position.y * this.sign >= 0);
				if(betterThanDriveStrike
						&& Math.abs(radians) < Math.toRadians(doubleJump ? 35 : 45) * (this.goingInHomeGoal ? 1.5 : 1)
						&& (this.groundIntercept == null
								|| localIntercept.z > (localIntercept.magnitude() < 700 ? 90 : (theirSide ? 180 : 230))
								|| (this.carPosition.z > Math.max(500, aerialIntercept.position.z)))){
					// if(true){
					// if(localIntercept.z > (doubleJump ? 190 : 110)){
					this.action = new Aerial(this, aerialIntercept, type).withAbortCondition(
							new BallTouchedAbort(this, packet.ball.latestTouch, this.playerIndex),
							new SliceOffPredictionAbort(this, aerialIntercept));
					return this.action.getOutput(packet);
				}
			}
		}

		if(this.action != null){
			if(!this.action.isFinished(packet)){
				this.stackRenderString(this.action.getClass().getSimpleName(), this.altColour);
				return action.getOutput(packet);
			}else{
				this.action = null;
			}
		}

		// Drive.
		boolean wall = !this.car.onFlatGround;
		this.action = null;
		this.stackRenderString("Drive", this.colour);
		Vector3 target;
		OptionalDouble targetTime = OptionalDouble.empty();
		boolean dontBoost = false;
		if(this.commit){
			Vector3 localInterceptBall = MathsUtils.local(this.car, this.groundIntercept.position);
			Vector3 dodgeTarget = DriveStrike.getDodgeTarget(this.groundIntercept);
			if(this.wallIntercept != null){
				target = this.wallIntercept.intersectPosition;
				// if(target.y * this.sign > 0 && Math.abs(this.ballPosition.x) >
				// Math.abs(target.x)){
				// target = target.withX(Math.copySign(target.x, this.ballPosition.x * 5));
				// }
				renderer.drawLine3d(Color.GREEN, this.carPosition, this.wallIntercept.position);
				wall = true;
			}else if(localInterceptBall.z > 180 && this.bounce != null && this.bounce.position.y * this.sign < 0){
				target = this.bounce.position;
				if(Math.abs(car.position.y) < Constants.PITCH_LENGTH_SOCCAR
						|| Math.abs(target.x) < Constants.GOAL_WIDTH){
					target = target.plus(Vector3.Y.scaleToMagnitude(-this.sign * target.distance(carPosition)
							* Math.min((this.bounce.time - this.time) / 2.5, 0.4)));
				}
				targetTime = OptionalDouble.of(this.bounce.time);
			}else{
				target = this.groundIntercept.position;
				if(this.mode == Mode.DROPSHOT){
					Vector3 offset = Vector3.Y;
					target = target.plus(offset.scaleToMagnitude(
							-this.sign * target.distance(carPosition) * (this.isKickoff ? 0.05 : 0.2)));
				}else if(this.mode == Mode.SOCCAR && !this.isKickoff){
					target = this.groundIntercept.intersectPosition;
					if((this.groundIntercept.position.y - this.car.position.y) * this.sign < 0){
						Vector2 toIntercept = target.minus(this.car.position).flatten().normalised();
						Vector2 trace = this.car.position.flatten()
								.plus(toIntercept.scale((-this.sign * Constants.PITCH_LENGTH_SOCCAR) / toIntercept.y));
//						if(Math.abs(trace.x) < Constants.GOAL_WIDTH + 250){
						Vector3 xSkew = InterceptCalculator.xSkew.withZ(1);
						if(xSkew.x * trace.x > 0){
							xSkew = xSkew.withX(-xSkew.x);
						}
						target = this.groundIntercept.position.plus(this.groundIntercept.getOffset().multiply(xSkew));
//						}
					}

					double distance = MathsUtils.local(this.car, target).flatten().magnitude();
					double addedOffset = 1500 * Math.floor(distance / 4000);
					if(addedOffset > 0.001){
						target = target.plus(target.minus(this.groundIntercept.position).scaleToMagnitude(addedOffset));
					}else if(MathsUtils.local(this.car, this.groundIntercept.position).z > 160
							&& this.car.hasWheelContact){
						double radians = Vector2.Y.correctionAngle(localInterceptBall.flatten());
						radians = MathsUtils.shorterAngle(radians);
						if(Math.abs(this.carForwardComponent) > 0.975 /* && Math.abs(radians) < Math.toRadians(50) */){
							this.action = new DriveStrike(this,
									this.groundIntercept/* .withIntersectPosition(dodgeTarget) */).withAbortCondition(
											new BallTouchedAbort(this, packet.ball.latestTouch, this.playerIndex),
											new SliceOffPredictionAbort(this, this.groundIntercept));
							return this.action.getOutput(packet);
						}
					}
				}else{
					target = this.groundIntercept.intersectPosition;
				}
				this.renderer.drawLine3d(this.altColour, this.groundIntercept.intersectPosition,
						this.groundIntercept.position);
				this.renderer.drawRectangle3d(this.colour, this.groundIntercept.intersectPosition, 8, 8, true);
				if(this.car.hasWheelContact && this.getTimeOnGround() > 0.2
						&& (this.groundIntercept.time - this.time) < 0.3
						&& Math.abs(this.lastControls.getSteer()) < (this.goingInHomeGoal ? 0.4 : 0.2)){
					// boolean opponentBlocking = false;
					// for(Car car : packet.enemies){
					// if(this.groundIntercept.position.minus(this.car.position).angle(car.position.minus(this.car.position))
					// < Math.toRadians(30)){
					// opponentBlocking = true;
					// break;
					// }
					// }
					if(Math.abs(localInterceptBall.z) > 105
							|| Math.abs(this.groundIntercept.time - this.earliestEnemyIntercept.time) < 0.15
							|| this.carSpeed < (this.car.onFlatGround ? 1100 : 1550)){
						if((this.groundIntercept.time - this.time) < 0.255){
							this.action = new FastDodge(this, dodgeTarget.minus(this.carPosition));
							return this.action.getOutput(packet);
						}
					}else{
						target = dodgeTarget;
					}
				}
			}
		}else{
			boolean high = (this.car.position.z > 150);
			// Intercept enemyIntersect = (this.furthestBack ? null :
			// this.enemyIntersect());
//			Intercept enemyIntersect = (!this.furthestBack && this.car.isSupersonic && this.groundIntercept.time - this.time > 1.1 ? this.enemyIntersect() : null);
			this.pickupBoost = false;
			if(carSpeed < 1300 && high && car.hasWheelContact && this.car.velocity.z < 450){
				this.action = new Jump(this, 30D / 120);
				return action.getOutput(packet);

//			}else if(enemyIntersect != null){
//				target = enemyIntersect.position;
			}else if((this.car.boost < 40 || this.isKickoff) && this.mode != Mode.DROPSHOT
					&& this.nearestBoost != null){
				target = this.nearestBoost.getLocation().withZ(Constants.CAR_HEIGHT);

				this.pickupBoost = true;

				if(this.nearestBoost.isFullBoost() || this.mode == Mode.HOOPS){
					// if((this.car.onSuperFlatGround && this.carForwardComponent > 0.975 &&
					// !this.lastControls.holdHandbrake()) || this.isKickoff){
					// Vector2 endTarget = (this.teamPossession >= -0.001 || this.isKickoff ?
					// target.flatten().withY(0) : this.homeGoal.flatten()/*.multiply(new
					// Vector2(-1, 1))*/);
					//
					// // Vector2 endTarget;
					// // Vector2 direction = target.minus(this.carPosition).flatten().normalised();
					// // if(Math.abs(target.y) < 1000){
					// // endTarget = (direction.y * this.sign > 0 ?
					// this.groundIntercept.intersectPosition.flatten() :
					// this.homeGoal.flatten().multiply(new Vector2(-1, 1)));
					// // }else{
					// // if(Math.abs(direction.x) > Math.abs(direction.y)){
					// // endTarget = target.withY(0).flatten();
					// // }else{
					// // endTarget = target.withX(0).flatten();
					// // }
					// // }
					//
					// CompositeArc compositeArc = CompositeArc.create(this.car, target.flatten(),
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
					// // CompositeArc arcs = CompositeArc.create(this.car, target.flatten(),
					// endTarget, 1200, 150, 300);
					// // if(compositeArc == null || arcs.getLength() < compositeArc.getLength()){
					// // compositeArc = arcs;
					// // }
					// // }
					//
					// this.action = new FollowArcs(this, compositeArc).withAbortCondition(new
					// BoostYoinkedAbort(this, this.nearestBoost));
					// if(!this.isKickoff){
					// this.action.withAbortCondition(new CommitAbort(this, 0));
					// }
					// return action.getOutput(packet);
					// }

					if(this.nearestBoost.isFullBoost()){
						target = target.scale(1 - (35 / target.magnitude()));
					}else{
						dontBoost = true;
					}
					Vector2 offset = target.minus(car.position).flatten().rotate(Math.PI / 2);
					if(offset.dot(target.flatten()) < 0)
						offset = offset.scale(-1);
					target = target.plus(offset.scale(this.nearestBoost.isFullBoost() && !this.isKickoff ? 0.12 : 0.1));
				}
				// }else if(high || this.carVel < 1500 || this.enemyCar == null){
				// target = packet.ball.position.withX(this.groundIntercept.interceptPosition.x
				// * (this.mode == Mode.SOCCAR ? 0.4 : 0.75))
				// .withY(Math.copySign(packet.ball.position.y * MathsUtils.lerp(0.9, 0.45,
				// car.boost / 100), -car.sign));
				// }
			}else{
				dontBoost = this.teamPossession > 0;

				final double MIN_DEPTH = 0.4;
				final double MAX_DEPTH = 0.85;
				double depthLerp = MathsUtils.clamp((this.teamPossession * 0.5) + 0.25, 0, 1);
				this.stackRenderString("Depth: " + MathsUtils.round(depthLerp), this.colour);

				// target = this.homeGoal.lerp(this.earliestEnemyIntercept.position,
				// MathsUtils.lerp(MIN_DEPTH, MAX_DEPTH,
				// depthLerp)).withZ(Constants.CAR_HEIGHT);

				double goalDistance = this.earliestEnemyIntercept.position.distance(this.homeGoal);
				Vector2 direction = this.earliestEnemyIntercept.getOffset().flatten().scaleToMagnitude(-1);
//				direction = direction.plus(this.earliestEnemyIntercept.car.velocity.flatten().scale(0.3 / Constants.SUPERSONIC_VELOCITY));
				target = this.earliestEnemyIntercept.position.plus(direction
						.scaleToMagnitude(goalDistance * (1 - MathsUtils.lerp(MIN_DEPTH, MAX_DEPTH, depthLerp))));
				if(Math.abs(target.x) > Constants.PITCH_WIDTH_SOCCAR - 400){
					target = target.withX(Math.copySign(Constants.PITCH_WIDTH_SOCCAR - 400, target.x));
				}
				if(Math.abs(target.y) > Constants.PITCH_LENGTH_SOCCAR - 300){
					target = target.withY(Math.copySign(Constants.PITCH_LENGTH_SOCCAR - 400, target.y));
				}

				if(!this.furthestBack/*
										 * || this.car.orientation.forward.y * this.sign > Math.sin(Math.toRadians(25))
										 * || goalDistance > 6000
										 */){
					if(this.teamPossession * this.earliestEnemyIntercept.position.y * this.sign > 0
							|| this.furthestBack){
						target = target.withX(target.x * 0.7);
					}

					if(this.car.boost < 40 && target.y * this.sign < -1500 && goalDistance > 4000){
						target = findNearestBoost(target.plus(this.car.velocity.scale(0.5)).flatten(),
								BoostManager.getSmallBoosts()).getLocation().withZ(Constants.CAR_HEIGHT);
					}
				}else{
					double distance = this.homeGoal.distance(this.car.position);
					final double closingDistance = 1000;
					double nose = Math.max(0, this.car.orientation.forward.y * this.sign);

					double x = MathsUtils.clamp((closingDistance - distance) / closingDistance, -2.5, 1);
					double y = MathsUtils.clamp(
							(Constants.PITCH_LENGTH_SOCCAR - 600 - nose * 900) / Math.abs(this.car.position.y),
							Math.max(0.5, 3000 / goalDistance), 0.9);
					target = this.homeGoal.multiply(new Vector3(x, y, 1));
				}
//				else{
//					Vector2 fromGoal = this.backTeammate.position.minus(this.homeGoal).flatten();
//					fromGoal = fromGoal.multiply(new Vector2(-0.35, 0.45));
//					target = this.homeGoal.plus(fromGoal);
//				}

				renderer.drawLine3d(Color.BLACK, this.carPosition, target);
				renderer.drawLine3d(this.altColour, this.earliestEnemyIntercept.position, target);

				double distance = target.distance(this.car.position);
//				////				//								boolean correctSide = (this.car.position.y - this.groundIntercept.intersectPosition.y) * this.sign < 0;
				if(distance > 3000 && this.carForwardComponent > 0.95){
					Vector2 endTarget = this.earliestEnemyIntercept.position.flatten();
					CompositeArc compositeArc = CompositeArc.create(this.car, target.flatten(), endTarget, 1300, 200,
							300);
					this.action = new FollowArcs(this, compositeArc).withBoost(false)
							.withAbortCondition(new CommitAbort(this, 0.1));
					return action.getOutput(packet);
				}
			}
		}
		Output output = Handling.driveTime(this, target, (!this.isKickoff || this.mode == Mode.SOCCAR && !wall),
				this.mode == Mode.DROPSHOT || dontBoost, targetTime);
		if(output.isAction()){
			this.action = (Action)output;
			return this.action.getOutput(packet);
		}
		ControlsOutput controls = (ControlsOutput)output;
		if(this.isKickoff && this.car.hasWheelContact && this.commit){
			controls.withBoost(this.mode != Mode.HOOPS || this.car.boost > 22);
		}else if(controls.holdBoost()){
			controls.withBoost(!dontBoost);
		}
		return controls;
	}

}
