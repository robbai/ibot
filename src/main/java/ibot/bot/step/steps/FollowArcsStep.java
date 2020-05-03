package ibot.bot.step.steps;

import java.awt.Color;

import rlbot.flat.QuickChatSelection;
import ibot.bot.bots.ABot;
import ibot.bot.input.Bundle;
import ibot.bot.input.Pencil;
import ibot.bot.physics.DrivePhysics;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.bot.utils.CompositeArc;
import ibot.bot.utils.Constants;
import ibot.bot.utils.MathsUtils;
import ibot.bot.utils.Pair;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.Output;
import ibot.vectors.Vector2;

public class FollowArcsStep extends Step {

	private static final double TIME_REACT = 0.3, MIN_AIM_UU = 500 * TIME_REACT;
	private static final int N = 60, RENDER_STEP = 2, PRESSURE_RATE = 400;

	private CompositeArc compositeArc;
	private Vector2[] points;
	private double pressureUu, lastTime;
	private final DriveStep drive;
	private boolean boost = true;

	public FollowArcsStep(Bundle bundle, CompositeArc compositeArc){
		super(bundle);
		this.compositeArc = compositeArc;
		this.points = (compositeArc == null ? null : compositeArc.discretise(N));
		this.lastTime = this.getStartTime();
		this.drive = new DriveStep(bundle);
	}

	@Override
	public Output getOutput(){
		DataPacket packet = this.bundle.packet;
		Pencil pencil = this.bundle.pencil;
		ABot bot = this.bundle.bot;
		Car car = packet.car;

		for(int i = 0; i < N - 1; i += RENDER_STEP){
			pencil.renderer.drawLine3d(pencil.altColour, this.points[i].withZ(Constants.CAR_HEIGHT),
					this.points[Math.min(N - 1, i + RENDER_STEP)].withZ(Constants.CAR_HEIGHT));
		}
		pencil.renderer.drawRectangle3d(Color.BLACK, this.points[0].withZ(Constants.CAR_HEIGHT), 8, 8, true);
		pencil.renderer.drawRectangle3d(Color.BLACK, this.points[N - 1].withZ(Constants.CAR_HEIGHT), 8, 8, true);

		double carUu = findClosestUu(car.position.flatten());

		this.setFinished(carUu >= this.compositeArc.getLength() - 10 || !this.onPath(car, carUu));

		double progress = (carUu / this.compositeArc.getLength());

		double targetVelocity;
		if(carUu < this.compositeArc.getL(0)){
			targetVelocity = DrivePhysics.getSpeedFromRadius(Math.abs(this.compositeArc.getR1()));
		}else if(carUu < this.compositeArc.getL(0) + this.compositeArc.getL(1)){
			targetVelocity = MathsUtils.lerp(Constants.SUPERSONIC_VELOCITY,
					DrivePhysics.getSpeedFromRadius(Math.abs(this.compositeArc.getR2())),
					(carUu - this.compositeArc.getL(0)) / this.compositeArc.getL(1));
		}else{
			targetVelocity = DrivePhysics.getSpeedFromRadius(Math.abs(this.compositeArc.getR2()));
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
		double aimUu = Math.max(car.forwardVelocityAbs * TIME_REACT, MIN_AIM_UU);
		this.pressureUu = MathsUtils.clamp(this.pressureUu + PRESSURE_RATE * dt, carUu + aimUu,
				this.compositeArc.getLength() + aimUu);
		Vector2 target = this.aim(this.pressureUu);
		pencil.renderer.drawRectangle3d(pencil.colour, target.withZ(Constants.CAR_HEIGHT), 10, 10, true);

		// Drive.
		boolean onStraightaway = (carUu >= this.compositeArc.getL(0) + this.compositeArc.getL(1)
				&& carUu <= this.compositeArc.getL(0) + this.compositeArc.getL(1) + this.compositeArc.getL(2));
		boolean canDodge = onStraightaway && carUu
				+ DrivePhysics.estimateDodgeDistance(car) < this.compositeArc.getLength() - this.compositeArc.getL(4);
		this.drive.withTargetVelocity(targetVelocity);
		this.drive.target = target.withZ(Constants.CAR_HEIGHT);
		this.drive.dodge = canDodge;
		this.drive.dontBoost = !this.boost;
		return this.drive.getOutput();
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

	public Step withBoost(boolean boost){
		this.boost = boost;
		return this;
	}

	@Override
	public int getPriority(){
		return Priority.DRIVE;
	}

}
