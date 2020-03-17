package ibot.prediction;

import ibot.vectors.Vector3;
import rlbot.cppinterop.RLBotDll;
import rlbot.cppinterop.RLBotInterfaceException;

public class BallPrediction {

	/*
	 * Constants.
	 */
	public static final int SLICE_COUNT = 360;
	public static final double DT = 1 / 60;
	
	/*
	 * Prediction.
	 */
	private static final Slice[] prediction = new Slice[SLICE_COUNT];
	private static boolean empty;
	
	public static void update(){
		try{
			rlbot.flat.BallPrediction ballPrediction = RLBotDll.getBallPrediction();
			empty = false;
			
			for(int i = 0; i < SLICE_COUNT; i++){
				rlbot.flat.PredictionSlice slice = ballPrediction.slices(i);
				Vector3 position = new Vector3(slice.physics().location());
				double time = slice.gameSeconds();
				prediction[i] = new Slice(position, time);
			}
		}catch(RLBotInterfaceException e){
			empty = true;
			prediction[0] = new Slice(new Vector3(), 0);
		}
	}
	
	public static Slice get(int i){
		return prediction[empty ? 0 : i];
	}

	public static boolean isEmpty(){
		return empty;
	}

}
