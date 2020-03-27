package ibot.bot.actions.arcs;

import java.awt.Color;

import rlbot.flat.QuickChatSelection;
import ibot.bot.actions.Action;
import ibot.bot.bots.ABot;
import ibot.bot.controls.Handling;
import ibot.bot.input.Bundle;
import ibot.bot.input.Pencil;
import ibot.bot.physics.DrivePhysics;
import ibot.bot.utils.Constants;
import ibot.bot.utils.MathsUtils;
import ibot.bot.utils.Pair;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.ControlsOutput;
import ibot.output.Output;
import ibot.vectors.Vector2;

public class FollowArcs extends Action {

	private static final int N = 60, AIM_UU = 550, RENDER_STEP = 4, PRESSURE_RATE = 400;

	private CompositeArc compositeArc;
	private Vector2[] points;

	private Action action;

	private boolean boost = true;

	private double PRESSURE_UU = AIM_UU;
	private double lastTime;

	public FollowArcs(Bundle bundle, CompositeArc compArc){
		super(bundle);
		this.compositeArc = compArc;
		this.points = (compArc == null ? null : compArc.discretise(N));
		this.lastTime = this.getStartTime();
	}

	public ControlsOutput getOutput(){
		DataPacket packet = this.bundle.packet;
		Pencil pencil = this.bundle.pencil;
		ABot bot = this.bundle.bot;

		for(int i = 0; i < N - 1; i += RENDER_STEP){
			pencil.renderer.drawLine3d(pencil.altColour, this.points[i].withZ(Constants.CAR_HEIGHT),
					this.points[Math.min(N - 1, i + RENDER_STEP)].withZ(Constants.CAR_HEIGHT));
		}
		pencil.renderer.drawRectangle3d(Color.BLACK, this.points[0].withZ(Constants.CAR_HEIGHT), 8, 8, true);
		pencil.renderer.drawRectangle3d(Color.BLACK, this.points[N - 1].withZ(Constants.CAR_HEIGHT), 8, 8, true);

		if(this.action != null){
			if(!this.action.isFinished()){
				return action.getOutput();
			}else{
				this.action = null;
			}
		}

		Car car = packet.car;

		double carUu = findClosestUu(car.position.flatten());

		this.setFinished(carUu >= this.compositeArc.getLength() - 10 || !this.onPath(car, carUu));

		double progress = (carUu / this.compositeArc.getLength());

		// double targetVelocity =
		// Math.max(DrivePhysics.getSpeedFromRadius(Math.abs(this.compositeArc.getR1())),
		// DrivePhysics.getSpeedFromRadius(Math.abs(this.compositeArc.getR2())));

//		double targetVelocity = (progress < 0.5 ?
//				MathsUtils.lerp(DrivePhysics.getSpeedFromRadius(Math.abs(this.compositeArc.getR1())), Constants.MAX_CAR_VELOCITY, progress * 2)
//				: MathsUtils.lerp(Constants.MAX_CAR_VELOCITY, DrivePhysics.getSpeedFromRadius(Math.abs(this.compositeArc.getR1())), (progress - 0.5) * 2)
//				);

//		double targetVelocity = Constants.SUPERSONIC_VELOCITY;

		double targetVelocity;
		if(carUu < this.compositeArc.getL(0)){
			targetVelocity = DrivePhysics.getSpeedFromRadius(Math.abs(this.compositeArc.getR1()));
		}else if(carUu < this.compositeArc.getL(0) + this.compositeArc.getL(1)){
			targetVelocity = MathsUtils.lerp(Constants.SUPERSONIC_VELOCITY,
					DrivePhysics.getSpeedFromRadius(Math.abs(this.compositeArc.getR2())),
					(carUu - this.compositeArc.getL(0)) / this.compositeArc.getL(1));
		}else{
			targetVelocity = DrivePhysics.getSpeedFromRadius(Math.abs(this.compositeArc.getR2()));
//			targetVelocity = Constants.SUPERSONIC_VELOCITY;
		}

		// Quick-chat.
		if(packet.ball.latestTouch != null && packet.ball.latestTouch.elapsedSeconds == packet.ball.time){
			if(packet.ball.latestTouch.playerIndex == bot.getIndex()){
				bot.sendQuickChat(progress > 0.25 ? QuickChatSelection.Apologies_Whoops
						: QuickChatSelection.Custom_Toxic_404NoSkill);
			}else if(packet.ball.latestTouch.team == car.team){
				bot.sendQuickChat(QuickChatSelection.Information_AllYours, QuickChatSelection.Information_GoForIt);
			}
		}

		// Target.
		double dt = packet.time - this.lastTime;
		this.lastTime += dt;
		this.PRESSURE_UU = MathsUtils.clamp(this.PRESSURE_UU + PRESSURE_RATE * dt, carUu + AIM_UU,
				this.compositeArc.getLength() + AIM_UU);
		Vector2 target = this.aim(this.PRESSURE_UU);
		pencil.renderer.drawRectangle3d(pencil.colour, target.withZ(Constants.CAR_HEIGHT), 10, 10, true);

		boolean onStraightaway = (carUu >= this.compositeArc.getL(0) + this.compositeArc.getL(1)
				&& carUu <= this.compositeArc.getL(0) + this.compositeArc.getL(1) + this.compositeArc.getL(2));
		boolean canDodge = onStraightaway && carUu + DrivePhysics.estimateDodgeDistance(car) < this.compositeArc.getL(0)
				+ this.compositeArc.getL(1) + this.compositeArc.getL(2) + this.compositeArc.getL(3);

		Output output = Handling.driveVelocity(bundle, target.withZ(Constants.CAR_HEIGHT), canDodge, false,
				targetVelocity);
		if(output instanceof Action){
			this.action = (Action)output;
			return this.action.getOutput();
		}

		ControlsOutput controls = (ControlsOutput)output;
		return controls.withBoost(controls.holdBoost() && this.boost);
	}

	public double findClosestUu(Vector2 car){
		double uu = 0;
		double closestDistance = -1;

		double s = 0;
		for(int i = 0; i < (this.points.length - 1); i++){
			Pair<Vector2, Vector2> lineSegment = new Pair<Vector2, Vector2>(this.points[i], this.points[i + 1]);
			double segmentLength = lineSegment.getOne().distance(lineSegment.getTwo());

			Pair<Double, Double> result = MathsUtils.closestPointToLineSegment(car, lineSegment);
			double dist = result.getOne(), t = result.getTwo();

			if(closestDistance == -1 || closestDistance > dist){
				closestDistance = dist;
				uu = s + segmentLength * t;
			}
			s += segmentLength;
		}

		return uu;
	}

	private double carDistance(Vector2 car){
		if(this.points.length == 1)
			return this.points[0].distance(car);

		double closestDistance = Double.MAX_VALUE;

		for(int i = 0; i < (this.points.length - 1); i++){
			Pair<Vector2, Vector2> lineSegment = new Pair<Vector2, Vector2>(this.points[i], this.points[i + 1]);
			Pair<Double, Double> result = MathsUtils.closestPointToLineSegment(car, lineSegment);

			closestDistance = Math.min(closestDistance, result.getOne());
		}

		return closestDistance;
	}

	private Vector2 aim(double targetUu){
		if(this.points.length == 1)
			return this.points[0];

		double totalUu = 0;
		for(int i = 0; i < (this.points.length - 1); i++){
			Vector2 a = this.points[i], b = this.points[i + 1];
			double segmentUu = a.distance(b);
			if(totalUu + segmentUu > targetUu){
				return a.lerp(b, (targetUu - totalUu) / segmentUu);
			}
			totalUu += segmentUu;
		}

		// We are at the end.
		Vector2 ultimate = this.points[N - 1];
		Vector2 penultimate = this.points[N - 2];
		return ultimate.plus(ultimate.minus(penultimate).scaleToMagnitude(targetUu - totalUu));
	}

	private boolean onPath(Car car, double carUu){
		Vector2 carPosition = car.position.flatten();

		if(this.points == null || carDistance(carPosition) > 500)
			return false;

//		Vector2 aim = this.aim(carUu + 100);
//		Vector2 local = MathsUtils.local(car, aim.withZ(Constants.CAR_HEIGHT)).flatten();
//		return MathsUtils.shorterAngle(Vector2.Y.angle(local)) < Math.toRadians(70);

		return true;
	}

	public Action withBoost(boolean boost){
		this.boost = boost;
		return this;
	}

}
