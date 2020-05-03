package ibot.bot.abort;

import ibot.bot.input.Bundle;
import ibot.bot.utils.Constants;
import ibot.input.Touch;
import ibot.prediction.Slice;

public class NotMyPredictionAbort extends SliceOffPredictionAbort {

	public NotMyPredictionAbort(Bundle bundle, Slice slice){
		super(bundle, slice);
	}

	public boolean shouldAbort(){
		Touch latestTouch = this.bundle.packet.ball.latestTouch;
		if(latestTouch != null && latestTouch.elapsedSeconds > this.getStartTime() + Constants.DT
				&& latestTouch.playerIndex == this.bundle.packet.car.index){
			return false;
		}
		return super.shouldAbort();
	}

}
