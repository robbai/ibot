package ibot.bot.abort;

import ibot.bot.utils.Constants;
import ibot.bot.utils.DataBot;
import ibot.input.DataPacket;
import ibot.input.Touch;

public class BallTouchedAbort extends AbortCondition {

	private float initialTouchSeconds;
	private int[] indexExceptions;

	public BallTouchedAbort(DataBot bot, Touch latestTouch, int... indexExceptions){
		super(bot);
		this.initialTouchSeconds = getSeconds(latestTouch);
		this.indexExceptions = (indexExceptions == null ? new int[] {} : indexExceptions);
	}

	public BallTouchedAbort(DataBot bot, Touch latestTouch){
		this(bot, latestTouch, null);
	}

	@Override
	public boolean shouldAbort(DataPacket packet){
		Touch latestTouch = packet.ball.latestTouch;

		// Check the exceptions.
		if(latestTouch != null){
			for(int index : this.indexExceptions){
				if(latestTouch.playerIndex == index){
					this.initialTouchSeconds = getSeconds(latestTouch);
					return false;
				}
			}
		}

		return getSeconds(latestTouch) > this.initialTouchSeconds + Constants.DT;
	}

	private float getSeconds(Touch touch){
		return (touch == null ? 0 : touch.elapsedSeconds);
	}

}
