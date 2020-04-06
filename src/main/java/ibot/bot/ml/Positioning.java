package ibot.bot.ml;

import java.util.ArrayList;
import java.util.Comparator;

import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import ibot.Main;
import ibot.boost.BoostManager;
import ibot.boost.BoostPad;
import ibot.bot.utils.Constants;
import ibot.bot.utils.MathsUtils;
import ibot.input.Ball;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.vectors.Vector3;

public class Positioning {

	private static final int CARS = 6, BALL_SIZE = 6, CAR_SIZE = 18, INPUT_SIZE = (BALL_SIZE + CAR_SIZE * CARS),
			OUTPUT_SIZE = (CARS * 3);

	private static final Comparator<float[]> COMPARE_TIME = (float[] car1,
			float[] car2) -> (-1 / car1[CAR_SIZE - 1] > -1 / car2[CAR_SIZE - 1] ? 1 : -1);

	private static final double POSITION_SCALE = Constants.PITCH_WIDTH_SOCCAR + 900,
			BALL_VELOCITY_SCALE = Constants.MAX_BALL_VELOCITY, CAR_VELOCITY_SCALE = Constants.MAX_CAR_VELOCITY;

	private MultiLayerNetwork model;

	private ArrayList<Integer> blueIndexes = new ArrayList<Integer>(CARS / 2),
			orangeIndexes = new ArrayList<Integer>(CARS / 2);

	private INDArray input;

	private Vector3[] prediction;

	private double lastTime = 0;

	public Positioning(String modelFileName){
		// Load the model.
		modelFileName = Main.class.getClassLoader().getResource(modelFileName).getPath();
		if(modelFileName.startsWith("/"))
			modelFileName = modelFileName.substring(1);
		try{
			this.model = KerasModelImport.importKerasSequentialModelAndWeights(modelFileName);
		}catch(Exception e){
			e.printStackTrace();
			System.exit(1); // Tragic!
		}

		this.input = Nd4j.zeros(new int[] { 1, INPUT_SIZE });

		this.prediction = new Vector3[CARS];
	}

	public void update(DataPacket packet){
		if(this.lastTime != 0 && packet.time - this.lastTime < Constants.DT){
			return;
		}
		this.lastTime = packet.time;

		// Make the input.
		formatInput(packet);
//		System.out.println(this.input.toString());

		// Get the prediction.
		INDArray prediction = this.model.output(this.input);
//		System.out.println(prediction.toString());
		this.formatPrediction(prediction);
	}

	private void formatInput(DataPacket packet){
		// Get the ball.
		float[] ball = getBallInputs(packet.ball);

		// Place the cars.
		ArrayList<float[]> cars = new ArrayList<float[]>(CARS);
		ArrayList<float[]> blues = new ArrayList<float[]>(CARS / 2);
		ArrayList<float[]> oranges = new ArrayList<float[]>(CARS / 2);
		this.blueIndexes.clear();
		this.orangeIndexes.clear();
		for(Car car : packet.cars){
			float[] inputs = getCarInputs(car, packet.ball);
			cars.add(inputs);
			if(car.team == 0){
				blues.add(inputs);
				this.blueIndexes.add(car.index);
			}else{
				oranges.add(inputs);
				this.orangeIndexes.add(car.index);
			}
		}

		// Sort the cars.
		this.blueIndexes.sort((i, j) -> COMPARE_TIME.compare(cars.get(i), cars.get(j)));
		this.orangeIndexes.sort((i, j) -> COMPARE_TIME.compare(cars.get(i), cars.get(j)));
//		System.out.println("First blue: " + (cars.get(this.blueIndexes.get(0))[CAR_SIZE - 1]));
//		System.out.println("Second blue: " + (cars.get(this.blueIndexes.get(1))[CAR_SIZE - 1]));
//		System.out.println("Third blue: " + (cars.get(this.blueIndexes.get(2))[CAR_SIZE - 1]));

		for(int i = 0; i < BALL_SIZE; i++){
			this.input.putScalar(i, ball[i]);
		}
		for(int i = 0; i < blues.size(); i++){
			for(int j = 0; j < CAR_SIZE; j++){
				this.input.putScalar(BALL_SIZE + i * CAR_SIZE + j, cars.get(this.blueIndexes.get(i))[j]);
			}
		}
		for(int i = 0; i < oranges.size(); i++){
			for(int j = 0; j < CAR_SIZE; j++){
				this.input.putScalar(BALL_SIZE + i * (CAR_SIZE + CARS) + j, cars.get(this.orangeIndexes.get(i))[j]);
			}
		}
	}

	private float[] getBallInputs(Ball ball){
		float[] ballInputs = new float[BALL_SIZE];
		ballInputs[0] = (float)(-ball.position.x / POSITION_SCALE);
		ballInputs[1] = (float)(ball.position.y / POSITION_SCALE);
		ballInputs[2] = (float)(ball.position.z / POSITION_SCALE);
		ballInputs[3] = (float)(-ball.velocity.x / BALL_VELOCITY_SCALE);
		ballInputs[4] = (float)(ball.velocity.y / BALL_VELOCITY_SCALE);
		ballInputs[5] = (float)(ball.velocity.z / BALL_VELOCITY_SCALE);
		return ballInputs;
	}

	private float[] getCarInputs(Car car, Ball ball){
		float[] carInputs = new float[CAR_SIZE];
		carInputs[0] = (float)(-car.position.x / POSITION_SCALE);
		carInputs[1] = (float)(car.position.y / POSITION_SCALE);
		carInputs[2] = (float)(car.position.z / POSITION_SCALE);
		carInputs[3] = (float)(-car.velocity.x / CAR_VELOCITY_SCALE);
		carInputs[4] = (float)(car.velocity.y / CAR_VELOCITY_SCALE);
		carInputs[5] = (float)(car.velocity.z / CAR_VELOCITY_SCALE);
		carInputs[6] = -car.orientation.forward.x;
		carInputs[7] = car.orientation.forward.y;
		carInputs[8] = car.orientation.forward.z;
		carInputs[9] = (float)(car.boost / 100);
		BoostPad pad = BoostManager.closestActive(car.position.flatten(), false);
		if(pad == null){
			carInputs[10] = 0;
			carInputs[11] = 0;
			carInputs[12] = -1;
		}else{
			carInputs[10] = (float)(-pad.getLocation().x / POSITION_SCALE);
			carInputs[11] = (float)(pad.getLocation().y / POSITION_SCALE);
			carInputs[12] = 1;
		}
		pad = BoostManager.closestActive(car.position.flatten(), true);
		if(pad == null){
			carInputs[13] = 0;
			carInputs[14] = 0;
			carInputs[15] = -1;
		}else{
			carInputs[13] = (float)(-pad.getLocation().x / POSITION_SCALE);
			carInputs[14] = (float)(pad.getLocation().y / POSITION_SCALE);
			carInputs[15] = 1;
		}
		double ballDistance = car.position.distance(ball.position);
		carInputs[16] = (float)(ballDistance / POSITION_SCALE);
		double velocityTowards = car.velocity.dot(ball.position.minus(car.position));
		velocityTowards = Math.copySign(Math.max(1, Math.abs(velocityTowards)), velocityTowards);
		float time = (float)MathsUtils.clamp((ballDistance / velocityTowards) / 10, -1, 1);
		time *= (float)(POSITION_SCALE / CAR_VELOCITY_SCALE);
		carInputs[17] = time;
		return carInputs;
	}

	private void formatPrediction(INDArray prediction){
		for(int i = 0; i < OUTPUT_SIZE / 3; i++){
			this.prediction[i] = new Vector3(-prediction.getDouble(i * 3), prediction.getDouble(i * 3 + 1),
					prediction.getDouble(i * 3 + 2)).scale(POSITION_SCALE);
		}
	}

	public Vector3 getPrediction(int index){
		for(int i = 0; i < this.blueIndexes.size(); i++){
			if(this.blueIndexes.get(i) == index){
				return this.prediction[i];
			}
		}
		for(int i = 0; i < this.orangeIndexes.size(); i++){
			if(this.orangeIndexes.get(i) == index){
				return this.prediction[this.blueIndexes.size() + i];
			}
		}
		return null;
	}

}
