package ibot.bot.abort;

import ibot.bot.input.Bundle;
import ibot.bot.utils.rl.Constants;
import ibot.input.Touch;

public class BallTouchedAbort extends AbortCondition {

	private double initialTouchSeconds;
	private int[] indexExceptions;

	public BallTouchedAbort(Bundle bundle, Touch latestTouch, int... indexExceptions){
		super(bundle);
		this.initialTouchSeconds = getSeconds(latestTouch);
		this.indexExceptions = (indexExceptions == null ? new int[] {} : indexExceptions);
	}

	public BallTouchedAbort(Bundle bundle, Touch latestTouch){
		this(bundle, latestTouch, null);
	}

	public BallTouchedAbort(Bundle bundle){
		this(bundle, bundle.packet.ball.latestTouch, null);
	}

	@Override
	public boolean shouldAbort(){
		Touch latestTouch = this.bundle.packet.ball.latestTouch;

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

	private double getSeconds(Touch touch){
		return (touch == null ? 0 : touch.time);
	}

}
