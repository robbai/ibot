package ibot.bot.utils;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Random;

import ibot.boost.BoostManager;
import ibot.boost.BoostPad;
import ibot.bot.actions.Action;
import ibot.bot.intercept.AerialType;
import ibot.bot.intercept.CarSlice;
import ibot.bot.intercept.CarTrajectory;
import ibot.bot.intercept.Intercept;
import ibot.bot.intercept.InterceptCalculator;
import ibot.input.Car;
import ibot.input.CarOrientation;
import ibot.input.DataPacket;
import ibot.output.ControlsOutput;
import ibot.prediction.BallPrediction;
import ibot.prediction.Slice;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;
import rlbot.Bot;
import rlbot.cppinterop.RLBotDll;
import rlbot.cppinterop.RLBotInterfaceException;
import rlbot.flat.GameTickPacket;
import rlbot.flat.QuickChatSelection;
import rlbot.manager.BotLoopRenderer;

public abstract class DataBot implements Bot {

	protected static boolean ranGc;

	protected final int playerIndex;

	private final Random random;

	private final ArrayList<RenderString> renderStack;

	public DataBot(int playerIndex){
		super();
		this.playerIndex = playerIndex;
		this.random = new Random();
		this.renderStack = new ArrayList<RenderString>();
	}

	@Override
	public int getIndex(){
		return this.playerIndex;
	}

	protected abstract ControlsOutput processInput(DataPacket dataPacket);

	@Override
	public ControlsOutput processInput(GameTickPacket rawPacket){
		if(!rawPacket.gameInfo().isRoundActive()){
			if(!ranGc){
				System.gc();
				ranGc = true;
			}
		}else{
			ranGc = false;
		}

		// Just return immediately if something looks wrong with the data.
		//    	System.out.println(rawPacket.gameInfo().isKickoffPause() + ", " + rawPacket.gameInfo().isMatchEnded() + ", " + rawPacket.gameInfo().isRoundActive());
		if(rawPacket.playersLength() <= playerIndex){
			return new ControlsOutput();
		}

		// Update the boost manager.
		BoostManager.loadGameTickPacket(rawPacket);

		// Update the ball prediction.
		BallPrediction.update();

		// Create the packet.
		DataPacket dataPacket = new DataPacket(rawPacket, playerIndex);

		//    	if(dataPacket.hasMatchEnded){
		//        	Car car = dataPacket.car;
		//        	double time = (dataPacket.secondsElapsed % (Math.PI * 2));
		//        	return new ControlsOutput().withJump(car.hasWheelContact && (time % 0.8) < 0.1).withOrient(AirControl.getRollPitchYaw(car, new Vector3(Math.sin(time), Math.cos(time), 1))).withBoost(car.velocity.z < 0);
		//    	}

		this.updateData(dataPacket);

		this.renderStack.clear();

		// Get our output.
		ControlsOutput controlsOutput = processInput(dataPacket);
		//        if(!dataPacket.isRoundActive) controlsOutput.withJump(false);
		this.lastControls = new ControlsOutput(controlsOutput)
				.withBoost(controlsOutput.holdBoost() && this.car.boost >= 1)
				.withThrottle(controlsOutput.holdBoost() && this.car.boost >= 1 ? 1 : controlsOutput.getThrottle());

		for(int i = 0; i < this.renderStack.size(); i++){
			renderer.drawString2d(this.renderStack.get(i).string, this.renderStack.get(i).colour, new Point(20 + 300 * this.playerIndex, 30 * (i + 1)), 2, 2);
		}

		return controlsOutput;
	}

	public void stackRenderString(String string, Color colour){
		this.renderStack.add(new RenderString(string, colour));
	}

	@Override
	public void retire(){
		System.out.println("Retiring sample bot " + playerIndex);
	}

	/*
	 * Data
	 */
	protected Action action;
	public ControlsOutput lastControls = new ControlsOutput();
	public Car car, backTeammate;
	public Vector3 ballPosition, ballVelocity, carPosition, carVelocity, enemyGoal, homeGoal;
	public CarTrajectory[] trajectories;
	public CarSlice[][] trajectoryResults;
	public CarOrientation carOrientation;
	public BotLoopRenderer renderer;
	public Intercept aerialDodge, aerialDouble, groundIntercept, bounce, wallIntercept, earliestTeammateIntercept, earliestEnemyIntercept;
	public boolean isKickoff, commit, pickupBoost, furthestBack, lastMan, goingInHomeGoal, goingInEnemyGoal;
	private boolean lastWheelContact, lastIsKickoff, hasMatchEnded;
	public Car enemyCar;
	public Color colour, altColour;
	public double gravity, time, carSpeed, sign, lastWheelContactTime, timeToHitGround, teamPossession, carForwardComponent;
	public Intercept[] groundIntercepts;
	public int team;
	public Mode mode;
	public BoostPad nearestBoost;

	protected void updateData(DataPacket packet){
		this.time = packet.time;

		this.car = packet.car;
		this.carPosition = this.car.position;
		this.carVelocity = this.car.velocity;
		this.carSpeed = this.car.velocity.magnitude();
		this.carOrientation = this.car.orientation;
		this.sign = this.car.sign;
		this.team = this.car.team;

		this.ballPosition = packet.ball.position;
		this.ballVelocity = packet.ball.velocity;

		this.renderer = BotLoopRenderer.forBotLoop(this);

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
			this.action = null;
			this.commit = true;
		}
		this.lastIsKickoff = this.isKickoff;

		if(!this.pickupBoost || !isBoostValid(this.nearestBoost, this.car)){
			this.nearestBoost = findNearestBoost(this.car, BoostManager.getFullBoosts());
			if(this.nearestBoost == null){
				this.nearestBoost = findNearestBoost(this.car, BoostManager.getSmallBoosts());
			}
		}
		if(this.nearestBoost == null) this.pickupBoost = false;

		// Goals.
		double ballForwards = (this.ballPosition.y * this.sign + Constants.PITCH_LENGTH_SOCCAR) / (2 * Constants.PITCH_LENGTH_SOCCAR);
		this.enemyGoal = new Vector3((this.mode != Mode.SOCCAR ? 0 : ballForwards * Math.copySign(Math.min(Constants.GOAL_WIDTH - 350, Math.abs(ballPosition.x)), ballPosition.x)),
				(this.mode == Mode.HOOPS ? Constants.PITCH_LENGTH_HOOPS - 700 : Constants.PITCH_LENGTH_SOCCAR) * this.car.sign, 0);
		this.homeGoal = new Vector3(this.enemyGoal.x, this.enemyGoal.y * -1, this.enemyGoal.z);
//		if(this.ballPosition.y * this.sign > Constants.PITCH_WIDTH_SOCCAR - 2000 && Math.abs(this.ballPosition.x) > Constants.GOAL_WIDTH - Constants.BALL_RADIUS * 2){
//			this.enemyGoal = this.enemyGoal.withX(-this.enemyGoal.x);
//		}

		// Intercept.
		this.aerialDodge = InterceptCalculator.aerialCalculate(this, car, gravity, car.boost, AerialType.DODGE_STRIKE, this.mode, this.isKickoff, true);
		this.aerialDouble = InterceptCalculator.aerialCalculate(this, car, gravity, car.boost, AerialType.DOUBLE_JUMP, this.mode, this.isKickoff, false);
		this.groundIntercepts = new Intercept[packet.cars.length];
		this.earliestTeammateIntercept = null;
		this.earliestEnemyIntercept = null;
		for(int i = 0; i < packet.cars.length; i++){
			Intercept intercept = InterceptCalculator.groundCalculate(this, packet.cars[i], this.mode);
			this.groundIntercepts[i] = intercept;
			if(intercept == null) continue;
			if(packet.cars[i].team != this.car.team){
				if(this.earliestEnemyIntercept == null || intercept.time < this.earliestEnemyIntercept.time){
					this.earliestEnemyIntercept = intercept;
				}
			}else if(i != this.playerIndex){
				if(this.earliestTeammateIntercept == null || intercept.time < this.earliestTeammateIntercept.time){
					this.earliestTeammateIntercept = intercept;
				}
			}
		}
		this.teamPossession = MathsUtils.clamp((this.earliestEnemyIntercept == null ? 10 : this.earliestEnemyIntercept.time) - (this.earliestTeammateIntercept == null ? 10 : this.earliestTeammateIntercept.time), -10, 10);
		this.groundIntercept = this.groundIntercepts[this.playerIndex];
		this.wallIntercept = (this.groundIntercept == null ? null : InterceptCalculator.wallCalculate(this, this.car, this.groundIntercept.time));
		this.bounce = findBounce(this.groundIntercept == null ? 0 : this.groundIntercept.time);
		this.goingInHomeGoal = goingInGoal(-this.sign);
		this.goingInEnemyGoal = goingInGoal(-this.sign);

		this.backTeammate = null;
		this.furthestBack = true;
		for(Car c : packet.teammates){
			if(c.isDemolished) continue;
			if(this.backTeammate == null || this.backTeammate.position.y * this.sign < c.position.y * this.sign){
				this.backTeammate = c;
			}
			if(c.position.y * this.sign < this.carPosition.y * this.sign){
				this.furthestBack = false;
			}
		}
		this.lastMan = true;
		for(Car c : packet.teammates){
			if(c.isDemolished) continue;
			if(Math.abs(c.position.x - this.ballPosition.x) > Constants.PITCH_WIDTH_SOCCAR * 1.2){
				continue;
			}
			if((c.position.y - this.ballPosition.y) * this.sign < 0){
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
					this.sendQuickChat(QuickChatSelection.Information_GoForIt, QuickChatSelection.Information_AllYours);
				}
				this.pickupBoost = !this.commit;
			}
//		}else if((this.ballPosition.y * this.sign < -3000 || this.goingInHomeGoal) && this.teamPossession < 0.4 && (!this.pickupBoost || this.lastMan)){
//			this.commit = this.commit || (this.ballPosition.y - this.carPosition.y) * this.sign > 0 && !(this.furthestBack && this.lastMan);
////			this.pickupBoost &= !this.commit;
//			this.pickupBoost = false;
		}else if(!this.car.onSuperFlatGround){
			this.commit = this.groundIntercept.position.z > 300 || this.lastMan;
			this.pickupBoost &= !this.commit;
//		}else if(this.lastMan && (!this.pickupBoost || this.teamPossession < 1.2) && packet.enemies.length > 0){
//			this.commit = this.groundIntercept.position.y * this.sign < -MathsUtils.lerp(2000, 3000, 1 - Math.abs(this.car.position.x / Constants.PITCH_WIDTH_SOCCAR)) || this.earliestEnemyIntercept.time - this.groundIntercept.time > -0.25;
//			this.pickupBoost = false;
		}else if(this.pickupBoost/* && this.ballPosition.distance(this.homeGoal) > 2000*/){
			//						this.commit = this.lastMan && Math.abs((-3000 - this.ballPosition.y * this.sign) / (this.ballVelocity.y * this.sign)) < 0.5;
			this.commit = false;
			this.pickupBoost = !this.commit;
			//		}else if(this.groundIntercept.ballPosition.y * this.sign < 0){
			//			this.commit = !furthestBack || lastMan;
			////			this.commit = furthestBack;
			//		}else if(this.mode != Mode.DROPSHOT && (this.carPosition.y - this.groundIntercept.ballPosition.y) * this.sign > 0){
			//			this.commit = false;
		}else{
			boolean lastCommit = this.commit;
			this.commit = true;
			double carInterceptValue = interceptValue(this.groundIntercept, this.car, (this.earliestEnemyIntercept == null ? 10 : this.earliestEnemyIntercept.time), this.enemyGoal, this.time);
			for(Car c : packet.teammates){
				if(c.isDemolished) continue;
				//				if(interceptValue(this.groundIntercepts[c.index], c, this.enemyEarliestIntercept, this.enemyGoal, this.secondsElapsed) > carInterceptValue + (this.groundIntercept.ballPosition.y * this.sign < 0 ? 1 : 0)){
				//				if(interceptValue(this.groundIntercepts[c.index], c, this.enemyEarliestIntercept, this.enemyGoal, this.secondsElapsed) > carInterceptValue + (this.pickupBoost ? -3 : 0)){
				if(interceptValue(this.groundIntercepts[c.index], c, this.earliestEnemyIntercept.time, this.enemyGoal, this.time) > carInterceptValue + (lastCommit ? 0.45 : -0.2)){
					this.commit = false;
					break;
				}
			}
			if(this.commit && !lastCommit){
				this.sendQuickChat(QuickChatSelection.Information_IGotIt, QuickChatSelection.Information_Incoming);
			}
		}
		if(this.pickupBoost) this.commit = false; // TODO
		if(!this.commit && this.pickupBoost && !lastPickupBoost){
			this.sendQuickChat(QuickChatSelection.Information_NeedBoost);
		}
//		this.commit &= !this.furthestBack || this.earliestEnemyIntercept.time - this.groundIntercept.time > -0.8 || this.goingInHomeGoal || this.goingInEnemyGoal; // TODO
//		this.commit &= !this.furthestBack || ((packet.enemies.length == 0 || this.earliestEnemyIntercept.time - this.groundIntercept.time > -1.5) || this.groundIntercept.position.y * this.sign < 3000) || this.goingInHomeGoal; // TODO

		if(this.hasMatchEnded != packet.hasMatchEnded){
			this.sendQuickChat(QuickChatSelection.PostGame_Gg, QuickChatSelection.PostGame_EverybodyDance, QuickChatSelection.PostGame_NiceMoves, QuickChatSelection.PostGame_WhatAGame,
					QuickChatSelection.PostGame_OneMoreGame, QuickChatSelection.PostGame_Rematch, QuickChatSelection.PostGame_ThatWasFun, QuickChatSelection.PostGame_WellPlayed);
		}
		this.hasMatchEnded = packet.hasMatchEnded;

		this.enemyCar = getEnemyCar(packet.cars);
		this.colour = (this.car.team == 0 ? Color.BLUE : Color.ORANGE);
		this.altColour = (this.car.team == 0 ? Color.CYAN : Color.RED);
		this.determineTrajectories(packet.cars);
	}

	private static boolean goingInGoal(double sign){
		if(BallPrediction.isEmpty()) return false;
		for(int i = 0; i < BallPrediction.SLICE_COUNT; i += 20){
			if(BallPrediction.get(i).position.y * sign > Constants.PITCH_WIDTH_SOCCAR + 2 * Constants.BALL_RADIUS){
				return true;
			}
		}
		return false;
	}

	public void sendQuickChat(boolean teamOnly, byte... quickChatSelection){
		try{
			RLBotDll.sendQuickChat(this.playerIndex, teamOnly, quickChatSelection[this.random.nextInt(quickChatSelection.length)]);
		}catch(Exception e){
			System.err.println("Error when trying to send quick-chat [" + quickChatSelection.toString() + "]");
		}
	}

	public void sendQuickChat(byte... quickChatSelection){
		this.sendQuickChat(false, quickChatSelection);
	}

	private static double interceptValue(Intercept intercept, Car car, double enemyEarliestIntercept, Vector3 goal, double secondsElapsed){
		//		if((car.position.y - intercept.interceptPosition.y) * car.sign > 0) return -100;
		return car.velocity.dot(intercept.position.minus(car.position).normalised()) / 1500 +
				Math.min(4 / (intercept.time - secondsElapsed), 1.7) +
				MathsUtils.clamp(enemyEarliestIntercept - intercept.time + 0.1, 0, 1) / 1.5 +
				Math.cos(goal.minus(car.position).flatten().angle(intercept.position.minus(car.position).flatten())) * 1.5 * (intercept.position.y * car.sign + Constants.PITCH_LENGTH_SOCCAR) / (2 * Constants.PITCH_LENGTH_SOCCAR);
	}

	private static double estimateTimeToHitGround(Car car, double gravity){
		if(car.hasWheelContact) return 0;
		gravity *= -1;
		double distance = (car.position.z - Constants.CAR_HEIGHT);
		double initialVelocity = -car.velocity.z;
		double time = (-(Math.sqrt(2 * gravity * distance + Math.pow(initialVelocity, 2)) + initialVelocity) / gravity);
		if(time < 0) time = ((Math.sqrt(2 * gravity * distance + Math.pow(initialVelocity, 2)) - initialVelocity) / gravity);
		if(initialVelocity + gravity * time > Constants.MAX_CAR_VELOCITY){
			time = ((Constants.MAX_CAR_VELOCITY - initialVelocity) / gravity);
			return time + (distance - (2 * distance) / (initialVelocity + Constants.MAX_CAR_VELOCITY)) / Constants.MAX_CAR_VELOCITY;
		}
		return time;
	}

	private static Intercept findBounce(double minTime){
		for(int i = 0; i < BallPrediction.SLICE_COUNT; i++){
			Slice slice = BallPrediction.get(i);

			if(slice.time < minTime) continue;

			if(slice.position.z < Constants.BALL_RADIUS + 15){
				return new Intercept(slice.position.withZ(0), null, slice.position, slice.time);
			}
		}
		return null;
	}

	/*
	 * https://github.com/RLBot/RLBot/blob/5b073de36344e9cb9daea9815d7ed0d259086e4b/src/main/flatbuffers/rlbot.fbs#L558-L564
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
				//			case 3:
				//				return Mode.SNOWDAY;
				//			case 4:
				//				return Mode.RUMBLE;
		}
		final Mode defaultMode = Mode.SOCCAR;
		System.err.println("Unknown mode: " + gameMode + ", defaulting to " + defaultMode);
		return defaultMode;
	}

	protected static BoostPad findNearestBoost(Car car, ArrayList<BoostPad> boosts){
		Vector2 carPosition = car.position.flatten();
//		carPosition = carPosition.lerp(new Vector2(0, Constants.PITCH_LENGTH_SOCCAR * -car.sign), 0.3);

		BoostPad shortestBoost = null;
		double shortestDistance = 0;
		for(BoostPad boost : boosts){
			double distance = boost.getLocation().distance(carPosition);
			if(!isBoostValid(boost, car, distance)) continue;
			if(shortestBoost == null || shortestDistance > distance){
				shortestBoost = boost;
				shortestDistance = distance;
			}
		}
		return shortestBoost;
	}

	protected static BoostPad findNearestBoost(Vector2 position, ArrayList<BoostPad> boosts){
		BoostPad shortestBoost = null;
		double shortestDistance = 0;
		for(BoostPad boost : boosts){
			double distance = boost.getLocation().distance(position);
			if(!isBoostValid(boost, null, distance)) continue;
			if(shortestBoost == null || shortestDistance > distance){
				shortestBoost = boost;
				shortestDistance = distance;
			}
		}
		return shortestBoost;
	}

	private static boolean isBoostValid(BoostPad boost, Car car, double distance){
		if(car != null){
			return boost != null && boost.isActive() && distance / boost.getTimeLeft() < Constants.SUPERSONIC_VELOCITY && (boost.getLocation().y - car.position.y) * car.sign < 0;
		}
		return boost != null && boost.isActive() && distance / boost.getTimeLeft() < Constants.SUPERSONIC_VELOCITY;
	}

	private static boolean isBoostValid(BoostPad boost, Car car){
		if(boost == null) return false;
		return isBoostValid(boost, car, boost.getLocation().distance(car.position.flatten()));
	}

	private CarTrajectory[] determineTrajectories(Car[] cars){
		if(trajectories == null || trajectories.length != cars.length) {
			trajectories = new CarTrajectory[cars.length];
			trajectoryResults = new CarSlice[cars.length][];
			for(int i = 0; i < cars.length; i++){
				Car car = cars[i];
				if(car == null) continue;
				trajectories[i] = new CarTrajectory(i, car);
			}
		}else{
			for(int i = 0; i < cars.length; i++){
				CarTrajectory trajectory = trajectories[i];
				if(trajectory == null) continue;
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
			Car car = cars[i];
			if(car == null || car.team == this.team || car.isDemolished) continue;

			//			double score = this.carPosition.distance(car.position);
			//			double score = this.ballPosition.distance(car.position);
			//			double score = (this.carPosition.distance(car.position) + this.ballPosition.distance(car.position));

			double u = carVelocity.dot(car.position.minus(this.carPosition).normalised());
			double a = Constants.BOOST_GROUND_ACCELERATION;
			double score = ((-u + Math.sqrt(Math.pow(u, 2) + 2 * a * this.carPosition.distance(car.position))) / a);
			if(score < 0) score = ((-u - Math.sqrt(Math.pow(u, 2) + 2 * a * this.carPosition.distance(car.position))) / a);

			if(best == -1 || score < lowestScore){
				lowestScore = score;
				best = i;
			}
		}
		return (best == -1 ? null : cars[best]);
	}

	public Intercept enemyIntersect(){
		if(enemyCar != null && this.trajectoryResults != null){
			int enemyIndex = enemyCar.index;
			CarSlice[] slices = this.trajectoryResults[enemyIndex];

			if(slices != null){
				for(int i = 0; i < slices.length - 1; i++) {
					renderer.drawLine3d(Color.GRAY, slices[i].position, slices[i + 1].position);

					if(Math.abs(slices[i + 1].position.x) > Constants.PITCH_WIDTH_SOCCAR
							|| Math.abs(slices[i + 1].position.y) > Constants.PITCH_LENGTH_SOCCAR
							|| !MathsUtils.between(slices[i + 1].position.z, 0, Constants.CEILING)){
						return null;
					}
				}

				Intercept intercept = InterceptCalculator.groundCalculate(slices, car);
				if(intercept != null) return intercept;
			}
		}
		return null;
	}

	public void renderGoals(){
		Vector3 offset1 = new Vector3(250, 0, 0), offset2 = new Vector3(0, 250, 0);

		renderer.drawLine3d(Color.GREEN, enemyGoal.plus(offset1), enemyGoal.minus(offset1));
		renderer.drawLine3d(Color.GREEN, enemyGoal.plus(offset2), enemyGoal.minus(offset2));
		//		renderer.drawLine3d(Color.RED, homeGoal.plus(offset1), homeGoal.minus(offset1));
		//		renderer.drawLine3d(Color.RED, homeGoal.plus(offset2), homeGoal.minus(offset2));
	}

	public double getTimeOnGround(){
		if(this.car.hasWheelContact){
			return this.time - this.lastWheelContactTime;
		}
		return 0;
	}

}
