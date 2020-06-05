package ibot.bot.input;

import java.util.ArrayList;
import java.util.OptionalDouble;

import rlbot.cppinterop.RLBotDll;
import rlbot.cppinterop.RLBotInterfaceException;
import rlbot.flat.QuickChatSelection;
import ibot.boost.BoostManager;
import ibot.boost.BoostPad;
import ibot.bot.bots.ABot;
import ibot.bot.input.arena.Arena;
import ibot.bot.input.arena.SoccarArena;
import ibot.bot.intercept.AerialType;
import ibot.bot.intercept.Intercept;
import ibot.bot.intercept.InterceptCalculator;
import ibot.bot.utils.maths.MathsUtils;
import ibot.bot.utils.rl.Constants;
import ibot.bot.utils.rl.Mode;
import ibot.input.Ball;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.Controls;
import ibot.prediction.BallPrediction;
import ibot.prediction.Slice;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class Info {

	public final ABot bot;

	public Info(ABot bot){
		super();
		this.bot = bot;
	}

	/*
	 * Data
	 */
	private Car car;
	private Ball ball;
	public Controls lastControls = new Controls();
	public Car backTeammate;
	public Vector3 enemyGoal, homeGoal;
	public Intercept aerialDodge, aerialDouble, groundIntercept, doubleJumpIntercept, bounce, earliestTeammateIntercept,
			earliestEnemyIntercept, earliestTeammateInterceptCorrectSide;
	public boolean isKickoff, commit, furthestBack, lastMan, goingInHomeGoal, goingInEnemyGoal, slowestTeammate;
	private boolean lastWheelContact, lastIsKickoff, hasMatchEnded;
	public double deltaTime, time, lastWheelContactTime, timeToHitGround, possession, teamPossession,
			teamPossessionCorrectSide, carForwardComponent;
	public Intercept[] groundIntercepts;
	public Mode mode;
	public BoostPad nearestBoost;
	public OptionalDouble goalTime;
	public Arena arena;
	public double[] interceptValues = {};

	public void update(DataPacket packet){
		this.deltaTime = (packet.time - this.time);
		this.time = packet.time;
		this.car = packet.car;
		this.ball = packet.ball;

		Mode lastMode = this.mode;
		try{
			if(this.mode == null){
				this.mode = getMode(RLBotDll.getMatchSettings().gameMode());
			}
		}catch(RLBotInterfaceException e){
			this.mode = null;
			e.printStackTrace();
		}

		// Arena.
		if(this.mode != lastMode){
			switch(this.mode){
				default: // TODO
					this.arena = new SoccarArena();
					break;
			}
		}
		this.arena.update(packet);

		if(this.car.hasWheelContact && !this.lastWheelContact){
			this.lastWheelContactTime = this.time;
		}
		this.lastWheelContact = this.car.hasWheelContact;

		this.timeToHitGround = estimateTimeToHitGround(this.car, this.arena.getGravityAcc());

		this.carForwardComponent = this.car.velocity.normalised().dot(this.car.orientation.forward);

		// Kickoff reset.
		this.isKickoff = packet.isKickoffPause;
		if(this.isKickoff && !this.lastIsKickoff){
			this.bot.clearSteps();
			this.commit = true;
		}
		this.lastIsKickoff = this.isKickoff;

		this.nearestBoost = findNearestBoost(this.car, BoostManager.getFullBoosts());
		if(this.nearestBoost == null){
			this.nearestBoost = findNearestBoost(this.car, BoostManager.getSmallBoosts());
		}

		// Intercept.
		this.aerialDodge = InterceptCalculator.aerialCalculate(this, this.car, AerialType.DODGE_STRIKE, this.mode,
				this.isKickoff, false);
		this.aerialDouble = InterceptCalculator.aerialCalculate(this, this.car, AerialType.DOUBLE_JUMP, this.mode,
				this.isKickoff, false);
		this.doubleJumpIntercept = InterceptCalculator.groundCalculate(packet, this, this.car, true);
		this.groundIntercepts = new Intercept[packet.cars.length];
		this.earliestTeammateIntercept = null;
		this.earliestEnemyIntercept = null;
		this.earliestTeammateInterceptCorrectSide = null;
		for(int i = 0; i < packet.cars.length; i++){
			Intercept intercept = InterceptCalculator.groundCalculate(packet, this, packet.cars[i], false);
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
				boolean correctSide = packet.cars[i].correctSide(intercept.position);
				if(correctSide && (this.earliestTeammateInterceptCorrectSide == null
						|| intercept.time < this.earliestTeammateInterceptCorrectSide.time)){
					this.earliestTeammateInterceptCorrectSide = intercept;
				}
			}
		}
		this.teamPossession = Info.determinePossession(this.earliestTeammateIntercept, this.earliestEnemyIntercept);
		this.teamPossessionCorrectSide = Info.determinePossession(this.earliestTeammateInterceptCorrectSide,
				this.earliestEnemyIntercept);
		this.possession = Info.determinePossession(this.groundIntercept, this.earliestEnemyIntercept);
		this.groundIntercept = this.groundIntercepts[this.bot.index];
		this.bounce = findBounce(this.arena, this.groundIntercept == null ? 0 : this.groundIntercept.time);
		this.goingInHomeGoal = goingInGoal(-this.car.sign);
		this.goingInEnemyGoal = goingInGoal(this.car.sign);
		if(this.goingInHomeGoal || this.goingInEnemyGoal){
			this.goalTime = getGoalTime();
		}else{
			this.goalTime = OptionalDouble.empty();
		}
		this.slowestTeammate = true;
		if(this.groundIntercept != null){
			for(Car teammate : packet.teammates){
				if(this.groundIntercepts[teammate.index] == null
						|| this.groundIntercepts[teammate.index].time > this.groundIntercept.time){
					this.slowestTeammate = false;
					break;
				}
			}
		}

		// Goals.
		this.enemyGoal = InterceptCalculator.chooseGoal(this.arena, this.car, this.groundIntercept.position)
				.withZ(Constants.BALL_RADIUS);
		if(this.earliestEnemyIntercept != null){
			this.homeGoal = InterceptCalculator
					.chooseGoal(this.arena, this.earliestEnemyIntercept.car, this.earliestEnemyIntercept.position)
					.withZ(Constants.BALL_RADIUS);
		}else{
			this.homeGoal = this.enemyGoal.withY(-this.enemyGoal.y);
		}

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
			if(Math.abs(c.position.x - this.ball.position.x) > Constants.PITCH_WIDTH_SOCCAR * 1.2)
				continue;
			if(c.correctSide(packet.ball.position)){
				this.lastMan = false;
				break;
			}
		}

		// Commit.
		if(!this.car.onFlatGround){
			this.commit |= this.groundIntercept.position.z > 300 || this.lastMan;
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
		}else if(this.earliestEnemyIntercept == null){
			this.commit = true;
		}else{
			boolean lastCommit = this.commit;
			this.commit = true;
			double carInterceptValue = interceptValue(this.groundIntercept, this.car, this.earliestEnemyIntercept.time,
					this.enemyGoal);
			if(this.interceptValues.length != packet.cars.length)
				this.interceptValues = new double[packet.cars.length];
			this.interceptValues[this.car.index] = carInterceptValue;
			double ourBonus = 0;
			ourBonus += (lastCommit ? 0.2 : -0.1);
//			if(this.groundIntercept.position.y * this.car.sign < 0
//					&& (this.groundIntercept.position.y - this.car.position.y) * this.car.sign > 0
//					&& this.teamPossession < 0.3){
//				ourBonus += (this.car.onFlatGround && (!this.furthestBack || (this.lastMan && this.possession > -0.75))
//						? 1.5
//						: 0.8);
//			}
			for(Car c : packet.teammates){
				if(c.isDemolished)
					continue;
				double interceptValue = interceptValue(this.groundIntercepts[c.index], c,
						this.earliestEnemyIntercept.time,
						InterceptCalculator.chooseGoal(this.arena, c, this.groundIntercepts[c.index].position)
								.withZ(Constants.BALL_RADIUS));
				this.interceptValues[c.index] = carInterceptValue;
				if(interceptValue > carInterceptValue + ourBonus){
					this.commit = false;
					break;
				}
			}
			if(this.commit && !lastCommit){
				this.bot.sendQuickChat(QuickChatSelection.Information_IGotIt, QuickChatSelection.Information_Incoming);
			}
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
	}

	private static double determinePossession(Intercept us, Intercept them){
		final double MAX_POSSESSION = 10;
		if(them == null)
			return MAX_POSSESSION;
		if(us == null)
			return -MAX_POSSESSION;
		return MathsUtils.clampMagnitude(them.time - us.time, MAX_POSSESSION);
	}

	public void postUpdate(DataPacket packet, Controls controls){
//		this.lastControls = new Controls(controls);
		this.lastControls = new Controls(controls).withBoost(controls.holdBoost() && packet.car.boost >= 1)
				.withThrottle(controls.holdBoost() && packet.car.boost >= 1 ? 1 : controls.getThrottle());
	}

	private static boolean goingInGoal(double sign){
		if(BallPrediction.isEmpty())
			return false;
		for(int i = 0; i < BallPrediction.SLICE_COUNT; i += 20){
			if(BallPrediction.get(i).position.y * sign > Constants.PITCH_LENGTH_SOCCAR + 2 * Constants.BALL_RADIUS){
				return true;
			}
		}
		return false;
	}

	private static OptionalDouble getGoalTime(){
		if(BallPrediction.isEmpty())
			return OptionalDouble.empty();
		for(int i = 0; i < BallPrediction.SLICE_COUNT; i++){
			if(Math.abs(BallPrediction.get(i).position.y) > Constants.PITCH_LENGTH_SOCCAR + 2 * Constants.BALL_RADIUS){
				return OptionalDouble.of(BallPrediction.get(i).time);
			}
		}
		return OptionalDouble.empty();
	}

	private static double interceptValue(Intercept intercept, Car car, double enemyEarliestIntercept, Vector3 goal){
		return car.velocity.dot(intercept.position.minus(car.position).normalised()) / 1500
				+ Math.min(3 / (intercept.time - car.time), 1.2)
				+ MathsUtils.clamp(enemyEarliestIntercept - intercept.time + 0.1, 0, 1) / 1.6
				+ Math.cos(car.position.minus(goal).flatten().angle(intercept.position.minus(goal).flatten())) * 1.8
						* Math.max(0.5, Math.abs(intercept.position.y / Constants.PITCH_LENGTH_SOCCAR));
//		boolean losing = (enemyEarliestIntercept < intercept.time);
//		double angle = Vector2.Y.angle(intercept.intersectPosition.minus(car.position).flatten());
//		if(losing)
//			return enemyEarliestIntercept - intercept.time;
//		return (Math.cos(angle) + 1) * car.velocity.magnitude();
//		double possession = (enemyEarliestIntercept - intercept.time);
//		double angle = car.position.minus(intercept.position).flatten().angle(intercept.getOffset().flatten());
//		return possession >= 0 ? -angle : -angle - Math.PI;
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

	private static Intercept findBounce(Arena arena, double minTime){
		for(int i = 0; i < BallPrediction.SLICE_COUNT; i++){
			Slice slice = BallPrediction.get(i);

			if(slice.time < minTime)
				continue;

			if(slice.position.z < Constants.BALL_RADIUS + 15){
				return new Intercept(slice.position.withZ(0), null, slice.position, arena.getFloor(), slice.time);
			}
		}
		return null;
	}

	/*
	 * https://github.com/RLBot/RLBot/blob/5b073de36344e9cb9daea9815d7ed0d259086e4b/
	 * src/main/flatbuffers/rlbot.fbs#L558-L564
	 */
	private Mode getMode(byte gameMode){
		System.out.println(this.bot.printPrefix() + "Received mode: " + gameMode);
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
		System.err.println(this.bot.printPrefix() + "Unknown mode: " + gameMode + ", defaulting to " + defaultMode);
		return defaultMode;
	}

	protected static BoostPad findNearestBoost(Car car, ArrayList<BoostPad> boosts){
		Vector2 carPosition = car.position.flatten();
		// carPosition = carPosition.lerp(new Vector2(0, Constants.PITCH_LENGTH_SOCCAR *
		// -car.sign), 0.3);

		BoostPad shortestBoost = null;
		double shortestDistance = 0;
		for(BoostPad boost : boosts){
			double distance = boost.getPosition().distance(carPosition);
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
			double distance = boost.getPosition().distance(position);
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
			return boost != null && boost.getTimeLeft() >= 0
					&& (boost.isActive() || distance / Constants.SUPERSONIC_VELOCITY > boost.getTimeLeft())
					&& (!car.correctSide(boost.getPosition()) || distance < 3000);
		}
		return boost != null && (boost.isActive() || distance / Constants.SUPERSONIC_VELOCITY > boost.getTimeLeft());
	}

	public static boolean isBoostValid(BoostPad boost, Car car){
		if(boost == null)
			return false;
		return isBoostValid(boost, car, boost.getPosition().distance(car.position.flatten()));
	}

	public double getTimeOnGround(){
		if(this.car.hasWheelContact){
			return this.time - this.lastWheelContactTime;
		}
		return 0;
	}

}
