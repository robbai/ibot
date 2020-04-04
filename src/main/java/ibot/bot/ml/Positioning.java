package ibot.bot.ml;

import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import ibot.Main;

public class Positioning {

	public static void main(String[] args) throws Exception{
		// Load the model.
		String simpleMlp = Main.class.getClassLoader().getResource("model.h5").getPath();
		if(simpleMlp.startsWith("/"))
			simpleMlp = simpleMlp.substring(1);
		System.out.println(simpleMlp);
		MultiLayerNetwork model = KerasModelImport.importKerasSequentialModelAndWeights(simpleMlp);

		// Make a random sample.
		int inputSize = 6 + 6 * 9;
		INDArray input = Nd4j.zeros(new int[] { 1, inputSize });
		for(int i = 0; i < inputSize; i++){
			input.putScalar(i, Math.random());
		}
		System.out.println("Input: " + input.toString());

		// Get the prediction.
		INDArray prediction = model.output(input);
		System.out.println("Prediction: " + prediction.toString());
	}

}
