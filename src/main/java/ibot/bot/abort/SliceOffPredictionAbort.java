package ibot.bot.abort;

import ibot.bot.utils.DataBot;
import ibot.input.DataPacket;
import ibot.prediction.BallPrediction;
import ibot.prediction.Slice;

public class SliceOffPredictionAbort extends AbortCondition {

	private Slice slice;

	public SliceOffPredictionAbort(DataBot bot, Slice slice){
		super(bot);
		this.slice = slice;
	}

	@Override
	public boolean shouldAbort(DataPacket packet){
		Slice timed = this.getClosestToTime();
		boolean condition = Math.abs(timed.time - this.slice.time) > 0.2
				|| timed.position.distance(this.slice.position) > 80;
//		if(condition) System.out.println(this.bot.getIndex() + ": yeah @ " + (int)(packet.secondsElapsed / 60) + ":" + (int)(packet.secondsElapsed % 60));
		return condition;
	}

	private Slice getClosestToTime(){
		int low = 0;
		int high = BallPrediction.SLICE_COUNT - 1;
		while(low < high){
			int mid = Math.floorDiv(low + high, 2);
			if(BallPrediction.get(mid).time < this.slice.time){
				low = mid + 1;
			}else{
				high = mid;
			}
		}
		return BallPrediction.get(low);
	}

}
