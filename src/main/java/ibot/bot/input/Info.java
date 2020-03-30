package ibot.bot.input;

import java.util.ArrayList;

import rlbot.cppinterop.RLBotDll;
import rlbot.cppinterop.RLBotInterfaceException;
import rlbot.flat.QuickChatSelection;
import ibot.boost.BoostManager;
import ibot.boost.BoostPad;
import ibot.bot.bots.ABot;
import ibot.bot.intercept.AerialType;
import ibot.bot.intercept.CarSlice;
import ibot.bot.intercept.CarTrajectory;
import ibot.bot.intercept.Intercept;
import ibot.bot.intercept.InterceptCalculator;
import ibot.bot.utils.Constants;
import ibot.bot.utils.MathsUtils;
import ibot.bot.utils.Mode;
import ibot.input.Ball;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.ControlsOutput;
import ibot.prediction.BallPrediction;
import ibot.prediction.Slice;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class Info {

	private final ABot bot;

	public Info(ABot bot){
		super();
		this.bot = bot;
	}

	/*
	 * Data
	 */
	private Car car;
	private Ball ball;
	public ControlsOutput lastControls = new ControlsOutput();
	public Car enemyCar, backTeammate;
	public Vector3 enemyGoal, homeGoal;
	public CarTrajectory[] trajectories;
	public CarSlice[][] trajectoryResults;
	public Intercept aerialDodge, aerialDouble, groundIntercept, bounce, wallIntercept, earliestTeammateIntercept,
			earliestEnemyIntercept;
	public boolean isKickoff, commit, pickupBoost, furthestBack, lastMan, goingInHomeGoal, goingInEnemyGoal;
	private boolean lastWheelContact, lastIsKickoff, hasMatchEnded;
	public double gravity, time, lastWheelContactTime, timeToHitGround, possession, teamPossession, carForwardComponent;
	public Intercept[] groundIntercepts;
	public Mode mode;
	public BoostPad nearestBoost;

	public void update(DataPacket packet){
		this.time = packet.time;
		this.car = packet.car;
		this.ball = packet.ball;

		try{
			if(packet.hasMatchEnded){
				this.mode = null;
			}else if(this.mode == null){
				this.mode = getMode(RLBotDll.getMatchSettings().gameMode());
			}
		}catch(RLBotInterfaceException e){
			this.mode = null;
			e.printStackTrace();
		}

		if(this.car.hasWheelContact && !this.lastWheelContact){
			this.lastWheelContactTime = this.time;
		}
		this.lastWheelContact = this.car.hasWheelContact;

		this.timeToHitGround = estimateTimeToHitGround(this.car, this.gravity);

		this.carForwardComponent = this.car.velocity.normalised().dot(this.car.orientation.forward);

		this.gravity = packet.gravity;

		// Kickoff reset.
		this.isKickoff = packet.isKickoffPause;
		if(this.isKickoff != this.lastIsKickoff){
			this.bot.action = null;
			this.commit = true;
		}
		this.lastIsKickoff = this.isKickoff;

		if(!this.pickupBoost || !isBoostValid(this.nearestBoost, this.car)){
			this.nearestBoost = findNearestBoost(this.car, BoostManager.getFullBoosts());
			if(this.nearestBoost == null){
				this.nearestBoost = findNearestBoost(this.car, BoostManager.getSmallBoosts());
			}
		}
		if(this.nearestBoost == null)
			this.pickupBoost = false;

		// Goals.
		double ballForwards = (this.ball.position.y * this.car.sign + Constants.PITCH_LENGTH_SOCCAR)
				/ (2 * Constants.PITCH_LENGTH_SOCCAR);
		this.enemyGoal = new Vector3((this.mode != Mode.SOCCAR ? 0
				: ballForwards * Math.copySign(Math.min(Constants.GOAL_WIDTH - 350, Math.abs(this.ball.position.x)),
						this.ball.position.x)),
				(this.mode == Mode.HOOPS ? Constants.PITCH_LENGTH_HOOPS - 700 : Constants.PITCH_LENGTH_SOCCAR)
						* this.car.sign,
				0);
		this.homeGoal = new Vector3(this.enemyGoal.x, this.enemyGoal.y * -1, this.enemyGoal.z);
		// if(this.ball.position.y * this.car.sign > Constants.PITCH_WIDTH_SOCCAR - 2000
		// &&
		// Math.abs(this.ball.position.x) > Constants.GOAL_WIDTH - Constants.BALL_RADIUS
		// * 2){
		// this.enemyGoal = this.enemyGoal.withX(-this.enemyGoal.x);
		// }

		// Intercept.
		this.aerialDodge = InterceptCalculator.aerialCalculate(this, car, gravity, car.boost, AerialType.DODGE_STRIKE,
				this.mode, this.isKickoff, true);
		this.aerialDouble = InterceptCalculator.aerialCalculate(this, car, gravity, car.boost, AerialType.DOUBLE_JUMP,
				this.mode, this.isKickoff, false);
		this.groundIntercepts = new Intercept[packet.cars.length];
		this.earliestTeammateIntercept = null;
		this.earliestEnemyIntercept = null;
		for(int i = 0; i < packet.cars.length; i++){
			Vector2 goal = packet.cars[i].team == this.car.team ? this.enemyGoal.flatten() : this.homeGoal.flatten();
			Intercept intercept = InterceptCalculator.groundCalculate(packet.cars[i], packet.gravity, goal, this.mode);
			this.groundIntercepts[i] = intercept;

			if(intercept == null)
				continue;
			if(packet.cars[i].team != this.car.team){
				if(this.earliestEnemyIntercept == null || intercept.time < this.earliestEnemyIntercept.time){
					this.earliestEnemyIntercept = intercept;
				}
			}else if(i != this.bot.index){
				if(this.earliestTeammateIntercept == null || intercept.time < this.earliestTeammateIntercept.time){
					this.earliestTeammateIntercept = intercept;
				}
			}
		}
		this.teamPossession = MathsUtils
				.clamp((this.earliestEnemyIntercept == null ? 10 : this.earliestEnemyIntercept.time)
						- (this.earliestTeammateIntercept == null ? 10 : this.earliestTeammateIntercept.time), -10, 10);
		this.possession = MathsUtils.clamp((this.earliestEnemyIntercept == null ? 10 : this.earliestEnemyIntercept.time)
				- (this.groundIntercept == null ? 10 : this.groundIntercept.time), -10, 10);
		this.groundIntercept = this.groundIntercepts[this.bot.index];
		this.wallIntercept = (this.groundIntercept == null ? null
				: InterceptCalculator.wallCalculate(this, this.car, this.groundIntercept.time));
		this.bounce = findBounce(this.groundIntercept == null ? 0 : this.groundIntercept.time);
		this.goingInHomeGoal = goingInGoal(-this.car.sign);
		this.goingInEnemyGoal = goingInGoal(-this.car.sign);

		this.backTeammate = null;
		this.furthestBack = true;
		for(Car c : packet.teammates){
			if(c.isDemolished)
				continue;
			if(this.backTeammate == null
					|| this.backTeammate.position.y * this.car.sign < c.position.y * this.car.sign){
				this.backTeammate = c;
			}
			if(c.position.y * this.car.sign < this.car.position.y * this.car.sign){
				this.furthestBack = false;
			}
		}
		this.lastMan = true;
		for(Car c : packet.teammates){
			if(c.isDemolished)
				continue;
			if(Math.abs(c.position.x - this.ball.position.x) > Constants.PITCH_WIDTH_SOCCAR * 1.2){
				continue;
			}
			if((c.position.y - this.ball.position.y) * this.car.sign < 0){
				this.lastMan = false;
				break;
			}
		}

		// Commit.
		boolean lastPickupBoost = this.pickupBoost;
		if(this.isKickoff){
			if(this.mode == Mode.DROPSHOT){
				this.commit = true;
				this.pickupBoost = false;
			}else{
				this.commit = true;
				for(Car c : packet.teammates){
					if(this.groundIntercepts[c.index].time + 0.02 < this.groundIntercept.time){
						this.commit = false;
						break;
					}
				}
				if(!packet.isRoundActive && !this.commit){
					this.bot.sendQuickChat(QuickChatSelection.Information_GoForIt,
							QuickChatSelection.Information_AllYours);
				}
				this.pickupBoost = !this.commit;
			}
			// }else if((this.ball.position.y * this.car.sign < -3000 ||
			// this.goingInHomeGoal) &&
			// this.teamPossession < 0.4 && (!this.pickupBoost || this.lastMan)){
			// this.commit = this.commit || (this.ball.position.y - this.car.position.y) *
			// this.car.sign > 0 && !(this.furthestBack && this.lastMan);
			// this.pickupBoost &= !this.commit;
		}else if(!this.car.onSuperFlatGround){
			this.commit |= this.groundIntercept.position.z > 300 || this.lastMan;
			this.pickupBoost &= !this.commit;
			// }else if(this.lastMan && (!this.pickupBoost || this.teamPossession < 1.2) &&
			// packet.enemies.length > 0){
			// this.commit = this.groundIntercept.position.y * this.car.sign <
			// -MathsUtils.lerp(2000, 3000, 1 - Math.abs(this.car.position.x /
			// Constants.PITCH_WIDTH_SOCCAR)) || this.earliestEnemyIntercept.time -
			// this.groundIntercept.time > -0.25;
			// this.pickupBoost = false;
			// }else if(this.pickupBoost && this.ball.position.distance(this.homeGoal) >
			// 2000){
			// this.commit = false;
			// this.pickupBoost = !this.commit;
			// }else if(this.groundIntercept.position.y * this.car.sign < 0){
			// this.commit = !this.furthestBack || this.lastMan;
			// }else if(this.mode != Mode.DROPSHOT && (this.car.position.y -
			// this.groundIntercept.position.y) * this.car.sign > 0){
			// this.commit = false;
		}else{
			boolean lastCommit = this.commit;
			this.commit = true;
			double carInterceptValue = interceptValue(this.groundIntercept, this.car,
					(this.earliestEnemyIntercept == null ? 10 : this.earliestEnemyIntercept.time), this.enemyGoal,
					this.time);
			double ourBonus = (lastCommit ? 0.3 : -0.25);
			if(this.groundIntercept.position.y * this.car.sign < 0
					&& (this.groundIntercept.position.y - this.car.position.y) * this.car.sign > 0){
				ourBonus += (this.car.onFlatGround && (!this.furthestBack || (this.lastMan && this.possession > -0.75))
						? 2
						: 0.5);
			}
			for(Car c : packet.teammates){
				if(c.isDemolished)
					continue;
				if(interceptValue(this.groundIntercepts[c.index], c, this.earliestEnemyIntercept.time, this.enemyGoal,
						this.time) > carInterceptValue + ourBonus){
					this.commit = false;
					break;
				}
			}
			if(this.commit && !lastCommit){
				this.bot.sendQuickChat(QuickChatSelection.Information_IGotIt, QuickChatSelection.Information_Incoming);
			}
		}
		if(this.pickupBoost)
			this.commit = false; // TODO
		if(!this.commit && this.pickupBoost && !lastPickupBoost){
			this.bot.sendQuickChat(QuickChatSelection.Information_NeedBoost);
		}
		// this.commit &= !this.furthestBack || this.earliestEnemyIntercept.time -
		// this.groundIntercept.time > -0.8 || this.goingInHomeGoal ||
		// this.goingInEnemyGoal; // TODO
		// this.commit &= !this.furthestBack || ((packet.enemies.length == 0 ||
		// this.earliestEnemyIntercept.time - this.groundIntercept.time > -1.5) ||
		// this.groundIntercept.position.y * this.car.sign < 3000) ||
		// this.goingInHomeGoal;
		// // TODO

		if(this.hasMatchEnded != packet.hasMatchEnded){
			this.bot.sendQuickChat(QuickChatSelection.PostGame_Gg, QuickChatSelection.PostGame_EverybodyDance,
					QuickChatSelection.PostGame_NiceMoves, QuickChatSelection.PostGame_WhatAGame,
					QuickChatSelection.PostGame_OneMoreGame, QuickChatSelection.PostGame_Rematch,
					QuickChatSelection.PostGame_ThatWasFun, QuickChatSelection.PostGame_WellPlayed);
		}
		this.hasMatchEnded = packet.hasMatchEnded;

		this.enemyCar = getEnemyCar(packet.cars);

		this.determineTrajectories(packet.cars);
	}

	public void postUpdate(DataPacket packet, ControlsOutput controls){
		this.lastControls = new ControlsOutput(controls).withBoost(controls.holdBoost() && packet.car.boost >= 1)
				.withThrottle(controls.holdBoost() && packet.car.boost >= 1 ? 1 : controls.getThrottle());
	}

	private static boolean goingInGoal(double sign){
		if(BallPrediction.isEmpty())
			return false;
		for(int i = 0; i < BallPrediction.SLICE_COUNT; i += 20){
			if(BallPrediction.get(i).position.y * sign > Constants.PITCH_WIDTH_SOCCAR + 2 * Constants.BALL_RADIUS){
				return true;
			}
		}
		return false;
	}

	private static double interceptValue(Intercept intercept, Car car, double enemyEarliestIntercept, Vector3 goal,
			double secondsElapsed){
		return car.velocity.dot(intercept.position.minus(car.position).normalised()) / 1500
				+ Math.min(4 / (intercept.time - secondsElapsed), 1.5)
				+ MathsUtils.clamp(enemyEarliestIntercept - intercept.time + 0.1, 0, 1) / 1.4
				+ Math.cos(goal.minus(car.position).flatten().angle(intercept.position.minus(car.position).flatten()))
						* 1.7 * (Math.abs(intercept.position.y / Constants.PITCH_LENGTH_SOCCAR));
	}

	private static double estimateTimeToHitGround(Car car, double gravity){
		if(car.hasWheelContact)
			return 0;
		gravity *= -1;
		double distance = (car.position.z - Constants.CAR_HEIGHT);
		double initialVelocity = -car.velocity.z;
		double time = (-(Math.sqrt(2 * gravity * distance + Math.pow(initialVelocity, 2)) + initialVelocity) / gravity);
		if(time < 0)
			time = ((Math.sqrt(2 * gravity * distance + Math.pow(initialVelocity, 2)) - initialVelocity) / gravity);
		if(initialVelocity + gravity * time > Constants.MAX_CAR_VELOCITY){
			time = ((Constants.MAX_CAR_VELOCITY - initialVelocity) / gravity);
			return time + (distance - (2 * distance) / (initialVelocity + Constants.MAX_CAR_VELOCITY))
					/ Constants.MAX_CAR_VELOCITY;
		}
		return time;
	}

	private static Intercept findBounce(double minTime){
		for(int i = 0; i < BallPrediction.SLICE_COUNT; i++){
			Slice slice = BallPrediction.get(i);

			if(slice.time < minTime)
				continue;

			if(slice.position.z < Constants.BALL_RADIUS + 15){
				return new Intercept(slice.position.withZ(0), null, slice.position, slice.time);
			}
		}
		return null;
	}

	/*
	 * https://github.com/RLBot/RLBot/blob/5b073de36344e9cb9daea9815d7ed0d259086e4b/
	 * src/main/flatbuffers/rlbot.fbs#L558-L564
	 */
	private Mode getMode(byte gameMode){
		System.out.println("Received mode: " + gameMode);
		switch(gameMode){
			case 0:
				return Mode.SOCCAR;
			case 1:
				return Mode.HOOPS;
			case 2:
				return Mode.DROPSHOT;
			// case 3:
			// return Mode.SNOWDAY;
			// case 4:
			// return Mode.RUMBLE;
		}
		final Mode defaultMode = Mode.SOCCAR;
		System.err.println("Unknown mode: " + gameMode + ", defaulting to " + defaultMode);
		return defaultMode;
	}

	protected static BoostPad findNearestBoost(Car car, ArrayList<BoostPad> boosts){
		Vector2 carPosition = car.position.flatten();
		// carPosition = carPosition.lerp(new Vector2(0, Constants.PITCH_LENGTH_SOCCAR *
		// -car.sign), 0.3);

		BoostPad shortestBoost = null;
		double shortestDistance = 0;
		for(BoostPad boost : boosts){
			double distance = boost.getLocation().distance(carPosition);
			if(!isBoostValid(boost, car, distance))
				continue;
			if(shortestBoost == null || shortestDistance > distance){
				shortestBoost = boost;
				shortestDistance = distance;
			}
		}
		return shortestBoost;
	}

	public static BoostPad findNearestBoost(Vector2 position, ArrayList<BoostPad> boosts){
		BoostPad shortestBoost = null;
		double shortestDistance = 0;
		for(BoostPad boost : boosts){
			double distance = boost.getLocation().distance(position);
			if(!isBoostValid(boost, null, distance))
				continue;
			if(shortestBoost == null || shortestDistance > distance){
				shortestBoost = boost;
				shortestDistance = distance;
			}
		}
		return shortestBoost;
	}

	private static boolean isBoostValid(BoostPad boost, Car car, double distance){
		if(car != null){
			return boost != null && boost.isActive() && distance / boost.getTimeLeft() < Constants.SUPERSONIC_VELOCITY
					&& (boost.getLocation().y - car.position.y) * car.sign < 0;
		}
		return boost != null && boost.isActive() && distance / boost.getTimeLeft() < Constants.SUPERSONIC_VELOCITY;
	}

	private static boolean isBoostValid(BoostPad boost, Car car){
		if(boost == null)
			return false;
		return isBoostValid(boost, car, boost.getLocation().distance(car.position.flatten()));
	}

	private CarTrajectory[] determineTrajectories(Car[] cars){
		if(trajectories == null || trajectories.length != cars.length){
			trajectories = new CarTrajectory[cars.length];
			trajectoryResults = new CarSlice[cars.length][];
			for(int i = 0; i < cars.length; i++){
				Car car = cars[i];
				if(car == null)
					continue;
				trajectories[i] = new CarTrajectory(i, car);
			}
		}else{
			for(int i = 0; i < cars.length; i++){
				CarTrajectory trajectory = trajectories[i];
				if(trajectory == null)
					continue;
				Car car = cars[i];
				trajectoryResults[i] = trajectory.estimateTrajectory(car);
			}
		}
		return trajectories;
	}

	private Car getEnemyCar(Car[] cars){
		int best = -1;
		double lowestScore = 0;
		for(int i = 0; i < cars.length; i++){
			Car otherCar = cars[i];
			if(otherCar == null || otherCar.team == this.car.team || otherCar.isDemolished)
				continue;

			// double score = this.car.position.distance(car.position);
			// double score = this.ball.position.distance(car.position);
			// double score = (this.car.position.distance(car.position) +
			// this.ball.position.distance(car.position));

			double u = this.car.velocity.dot(otherCar.position.minus(this.car.position).normalised());
			double a = Constants.BOOST_GROUND_ACCELERATION;
			double score = ((-u + Math.sqrt(Math.pow(u, 2) + 2 * a * this.car.position.distance(otherCar.position)))
					/ a);
			if(score < 0)
				score = ((-u - Math.sqrt(Math.pow(u, 2) + 2 * a * this.car.position.distance(otherCar.position))) / a);

			if(best == -1 || score < lowestScore){
				lowestScore = score;
				best = i;
			}
		}
		return (best == -1 ? null : cars[best]);
	}

	public Intercept enemyIntersect(){
		if(this.enemyCar != null && this.trajectoryResults != null){
			int enemyIndex = enemyCar.index;
			CarSlice[] slices = this.trajectoryResults[enemyIndex];

			if(slices != null){
				for(int i = 0; i < slices.length - 1; i++){
//					renderer.drawLine3d(Color.GRAY, slices[i].position, slices[i + 1].position);

					if(Math.abs(slices[i + 1].position.x) > Constants.PITCH_WIDTH_SOCCAR
							|| Math.abs(slices[i + 1].position.y) > Constants.PITCH_LENGTH_SOCCAR
							|| !MathsUtils.between(slices[i + 1].position.z, 0, Constants.CEILING)){
						return null;
					}
				}

				Intercept intercept = InterceptCalculator.groundCalculate(slices, car);
				if(intercept != null)
					return intercept;
			}
		}
		return null;
	}

	public double getTimeOnGround(){
		if(this.car.hasWheelContact){
			return this.time - this.lastWheelContactTime;
		}
		return 0;
	}

}
