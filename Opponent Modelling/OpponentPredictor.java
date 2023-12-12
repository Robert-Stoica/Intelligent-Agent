import org.tensorflow.SavedModelBundle;
import org.tensorflow.Tensor;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.types.TFloat32;

public class OpponentPredictor {
    SavedModelBundle model;
    public OpponentPredictor(String filepath){
        this.model = SavedModelBundle.load(filepath, "serve");
    }

    public float getOpponentUtil(float[][] inputValues){
        try (TFloat32 input = TFloat32.tensorOf(Shape.of(1, 10, 3))){
            for (int i = 0; i < inputValues.length; i++){
                for (int j = 0; j < inputValues[i].length; j++){
                    input.setFloat(inputValues[i][j], 0, i, j);
                }
            }
            try(Tensor output = model.session().runner().feed("serving_default_lstm_input", input).fetch("StatefulPartitionedCall").run().get(0)) {
                TFloat32 resultData = (TFloat32) output;
                return resultData.getFloat();
            }
        }
    }

    public static void main(String[] args) {
        OpponentPredictor predictor = new OpponentPredictor("model_2023-12-11_1636");

        // Our utility of the bid
        float eval = 3.95f;
        // How similar the bid is to the opponents initial bid
        float similar = 0.5f;
        // The percentage distance through the negotiation
        float distance = 0.48258706467661694f;

        // Should be the last 10 bids but for the sake of simplicity I've copied
        float[][] inputValues = {
                {eval, similar, distance},
                {eval, similar, distance},
                {eval, similar, distance},
                {eval, similar, distance},
                {eval, similar, distance},
                {eval, similar, distance},
                {eval, similar, distance},
                {eval, similar, distance},
                {eval, similar, distance},
                {eval, similar, distance}
        };

        float predictedUtil = predictor.getOpponentUtil(inputValues);
        System.out.println(predictedUtil);
    }
}
