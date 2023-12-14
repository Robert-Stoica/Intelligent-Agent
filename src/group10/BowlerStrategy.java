package group10;

public class BowlerStrategy {

    private final double max_utility;
    private final double min_utility;
    private final double inital_proposal_value;
    private double rate_of_concession;

    public BowlerStrategy(double max_utility, double min_utility, double inital_proposal_value, double rate_of_concession) {
        this.max_utility = max_utility;
        this.min_utility = min_utility;
        this.inital_proposal_value = inital_proposal_value;
        this.rate_of_concession = rate_of_concession;
    }

    public double calculateCurrentTargetUtility(double time) {
        double func_time = inital_proposal_value + ((1 - inital_proposal_value)) * Math.pow(time, 1/rate_of_concession);
        return  min_utility + (1 - func_time) * (max_utility - min_utility);
    }

    public void optimizeConcessionRate(double rate_of_concession){
        this.rate_of_concession = rate_of_concession;
    }

}
