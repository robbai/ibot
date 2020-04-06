package ibot.bot.bots;

import java.awt.Color;

import ibot.bot.abort.SliceOffPredictionAbort;
import ibot.bot.actions.Action;
import ibot.bot.actions.DriveStrike;
import ibot.bot.controls.Handling;
import ibot.bot.input.Info;
import ibot.bot.input.Pencil;
import ibot.bot.ml.Positioning;
import ibot.bot.utils.MathsUtils;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.ControlsOutput;
import ibot.output.Output;
import ibot.vectors.Vector3;

public class TestBot extends ABot {

	private Positioning positioning;

	public TestBot(int index, int team){
		super(index, team);

		if(positioning == null){
			positioning = new Positioning("model.h5");
		}
	}

	@Override
	protected ControlsOutput processInput(){
		DataPacket packet = this.bundle.packet;
		Pencil pencil = this.bundle.pencil;
		Info info = this.bundle.info;
		Car car = packet.car;

		if(this.action != null){
			if(!this.action.isFinished()){
				pencil.stackRenderString("Action: " + this.action.getClass().getSimpleName(), pencil.altColour);
				return action.getOutput();
			}
			this.action = null;
		}

		Vector3 target = null;
		double targetTime = 1;

		// Striking.
		if(info.possession > info.teamPossession
				&& (info.groundIntercept.position.distance(info.homeGoal) < 2500 || info.possession > -0.1)
				&& (info.teamPossession < 0 || (info.groundIntercept.position.y - car.position.y) * car.sign > 0)){
//		if(info.commit){
//			if(info.aerialDodge != null && info.possession > -0.1){
//				Vector3 local = MathsUtils.local(car, info.aerialDodge.intersectPosition);
//				if(Vector2.Y.angle(local.flatten()) < Math.toRadians(40)){
//					this.action = new Aerial(this.bundle, info.aerialDodge, AerialType.DODGE_STRIKE).withAbortCondition(
//							new SliceOffPredictionAbort(this.bundle, info.aerialDodge));
//					return this.action.getOutput();
//				}
//			}

			target = this.bundle.info.groundIntercept.position;
			targetTime = 0;
			if(Math.abs(info.lastControls.getSteer()) < 0.1){
				Vector3 dodgeTarget = DriveStrike.getDodgeTarget(info.groundIntercept);
				double doubleHeight = MathsUtils.local(car, info.doubleJumpIntercept.position).z;
				if(info.possession < 0.1 && car.onFlatGround
						&& info.doubleJumpIntercept.time < info.groundIntercept.time && doubleHeight > 280){
					this.action = new DriveStrike(this.bundle, info.doubleJumpIntercept.withIntersectPosition(
							DriveStrike.getDodgeTarget(info.doubleJumpIntercept)), info.enemyGoal, true);
				}else if(!packet.isKickoffPause){
					this.action = new DriveStrike(this.bundle, info.groundIntercept.withIntersectPosition(dodgeTarget),
							info.enemyGoal, false);
				}
				if(this.action != null){
					this.action.withAbortCondition(
							new SliceOffPredictionAbort(this.bundle, ((DriveStrike)this.action).intercept));
					return this.action.getOutput();
				}
			}
		}

//		if(!car.onFlatGround && car.hasWheelContact){
//			this.action = new Jump(this.bundle, 0.1);
//			return this.action.getOutput();
//		}

		// Positioning.
		if(target == null){
			positioning.update(packet);
			target = positioning.getPrediction(this.index);

//			if(car.team == 0){
//				target.withY(Math.min(info.possession < 0 ? info.earliestEnemyIntercept.position.y : info.groundIntercept.position.y, target.y));
//			}else{
//				target.withY(Math.max(info.possession < 0 ? info.earliestEnemyIntercept.position.y : info.groundIntercept.position.y, target.y));
//			}

			Color colour = (this.index % 2 == 0 ? pencil.altColour : pencil.colour);
			pencil.renderer.drawLine3d(colour, car.position, target);

//			if(car.boost < 70){
//				Vector2 boostTarget = Info.findNearestBoost(target.flatten(), BoostManager.getAllBoosts()).getLocation();
//				if(boostTarget.minus(car.position.flatten()).angle(target.minus(car.position).flatten()) < Math.toRadians(90)){
//					targetTime = 0.01;
//					target = boostTarget.withZ(Constants.CAR_HEIGHT);
//				}
//			}

//			double t = 1;
//			Vector3 a = target.minus(car.velocity.scale(t)).scale(2 / Math.pow(t, 2));
//			CompositeArc compositeArc = CompositeArc.create(car, target.flatten(), car.position.plus(a).flatten(), 0, 0);
//			this.action = new FollowArcs(this.bundle, compositeArc).withAbortCondition(new CommitAbort(this.bundle, 0));
//			return this.action.getOutput();
		}

		Output output = Handling.driveTime(this.bundle, target, true, targetTime != 0, packet.time + targetTime);
		if(output.isAction()){
			this.action = (Action)output;
			return this.action.getOutput();
		}
		ControlsOutput controls = (ControlsOutput)output;
//		controls.withThrottle(Math.max(0.25, controls.getThrottle())).withHandbrake(false);
		return controls;
	}

}
