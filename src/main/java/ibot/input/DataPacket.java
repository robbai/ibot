package ibot.input;

import rlbot.flat.GameTickPacket;

public class DataPacket {

	public final Car car, robbie;

	public final Car[] cars, teammates, enemies;

	public final Ball ball;
	public final int team;

	/** The index of your player */
	public final int playerIndex;

	public final double time, gravity;

	public final boolean isKickoffPause, isRoundActive, hasMatchEnded;

	public DataPacket(GameTickPacket rawPacket, int playerIndex){
		this.gravity = rawPacket.gameInfo().worldGravityZ();
		this.time = rawPacket.gameInfo().secondsElapsed();
		this.isKickoffPause = rawPacket.gameInfo().isKickoffPause();
		this.isRoundActive = rawPacket.gameInfo().isRoundActive();
		this.hasMatchEnded = rawPacket.gameInfo().isMatchEnded();

		this.ball = new Ball(rawPacket.ball(), this.time);

		this.team = rawPacket.players(playerIndex).team();

		int teammateCount = 0, enemyCount = 0;
		cars = new Car[rawPacket.playersLength()];
		for(int i = 0; i < rawPacket.playersLength(); i++){
			cars[i] = new Car(rawPacket.players(i), this.time, i);
			if(cars[i].team != this.team){
				enemyCount++;
			}else if(i != playerIndex){
				teammateCount++;
			}
		}
		this.teammates = new Car[teammateCount];
		this.enemies = new Car[enemyCount];
		teammateCount = 0;
		enemyCount = 0;
		for(int i = 0; i < cars.length; i++){
			if(cars[i].team != this.team){
				this.enemies[enemyCount] = cars[i];
				enemyCount++;
			}else if(i != playerIndex){
				this.teammates[teammateCount] = cars[i];
				teammateCount++;
			}
		}

		this.playerIndex = playerIndex;
		this.car = this.cars[playerIndex];
		this.robbie = (this.cars.length == 2 ? this.cars[1 - playerIndex] : null);
	}

}
