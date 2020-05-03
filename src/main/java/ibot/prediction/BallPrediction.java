package ibot.prediction;

import rlbot.cppinterop.RLBotDll;
import rlbot.cppinterop.RLBotInterfaceException;
import ibot.vectors.Vector3;

public class BallPrediction {

	/*
	 * Constants.
	 */
	public static final int SLICE_COUNT = 360;
	public static final double DT = 1 / 60;

	/*
	 * Prediction.
	 */
	private static final BallSlice[] prediction = new BallSlice[SLICE_COUNT];
	private static boolean empty;

	public static void update(){
		try{
			rlbot.flat.BallPrediction ballPrediction = RLBotDll.getBallPrediction();
			empty = false;

			for(int i = 0; i < SLICE_COUNT; i++){
				rlbot.flat.PredictionSlice slice = ballPrediction.slices(i);
				Vector3 position = new Vector3(slice.physics().location());
				Vector3 velocity = new Vector3(slice.physics().velocity());
				double time = slice.gameSeconds();
				prediction[i] = new BallSlice(position, velocity, time);
			}
		}catch(RLBotInterfaceException e){
			empty = true;
			prediction[0] = new BallSlice(new Vector3(), new Vector3(), 0);
		}
	}

	public static BallSlice get(int i){
		return prediction[empty ? 0 : i];
	}

	public static boolean isEmpty(){
		return empty;
	}

}
