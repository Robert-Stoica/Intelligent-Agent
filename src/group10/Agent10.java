package group10;

import com.sun.tools.rngom.digested.Main;
import genius.core.AgentID;
import genius.core.Bid;
import genius.core.BidHistory;
import genius.core.actions.*;
import genius.core.bidding.BidDetails;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.uncertainty.AdditiveUtilitySpaceFactory;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;


public class Agent10 extends AbstractNegotiationParty {

    private int rounds;
    private final String agentDetails = "Standard Negotiation Agent";
    private List<BidAndAgent> offerList;
    private OpponentModel opponentModel;

    private Bid previousBid;
    private Bid highestBid;
    private final double MAX_TARGET_UTILITY  = 0.95;
    private final double MIN_TARGET_UTILITY = 0.70;
    double max_utility;
    double min_utility;

    double targetUtility;
    double rate_of_concession = 0.92;
    double inital_proposal_value = 0.2;
    private Bid lowestBid;
    private BidHistory pastBids = new BidHistory();

    private static final double FREQ_MIN = 1.0;
    private static final double FREQ_INTERCEPT = 3.5;
    private static final double FREQ_DIVIDER = 50;
    private static final double FREQ_BIAS = 0.25;

    private List<ProposalAnalysis> receivedOffersList = new ArrayList<ProposalAnalysis>();

    @Override
    public void init(NegotiationInfo info) {
        try {
            super.init(info);

            rounds = 0;

            offerList = new ArrayList<>();
            this.opponentModel = new OpponentModel(info.getUtilitySpace());
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));

            AbstractUtilitySpace utilitySpace = info.getUtilitySpace();
            AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;

            try {
                if (hasPreferenceUncertainty()) {
                    highestBid = additiveUtilitySpace.getMaxUtilityBid();
                    lowestBid = additiveUtilitySpace.getMinUtilityBid();
                    max_utility = Math.max(utilitySpace.getUtility(highestBid), MAX_TARGET_UTILITY);
                    min_utility = Math.max(utilitySpace.getUtility(lowestBid), MIN_TARGET_UTILITY);
                }
            } catch (Exception e) {
                max_utility = MAX_TARGET_UTILITY;
                min_utility = MIN_TARGET_UTILITY;
            }

            min_utility = Math.max(utilitySpace.getReservationValue(), min_utility);


        }catch (Throwable e){
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public Action chooseAction(List<Class<? extends Action>> list) {
        try {
            ActionWithBid possibleAccept = isAccepting();
            if (possibleAccept == null) {
                possibleAccept = makeAnOffer();
                System.out.println(possibleAccept);

            }
            offerList.clear();
            receivedOffersList.clear();
            return possibleAccept;

        }catch (Throwable e){
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    @Override
    public void receiveMessage(AgentID sender, Action act) {
        try {
            super.receiveMessage(sender, act);

            if (act instanceof Offer offer) {
                offerList.add(new BidAndAgent(offer.getBid(), sender));
                opponentModel.updateOpponentBid(offer.getBid(), getTimeLine().getTime(), sender);

                if (sender != null) {
                    System.out.println("Received bid from: " + sender.getName());
                }

                // Adjust strategy based on opponent's most frequent bid
                adjustStrategyBasedOnMostFrequentBid(sender);
//                opponentModel.updateFrequencies(offer.getBid(), sender, getFrequencyWeight());

            }
        }catch (Throwable e){
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Bid generateRandomBidWithUtility(double utilityThreshold) {
        Bid arbitraryBid;
        double valueMetric;
        do {
            arbitraryBid = generateRandomBid();
            try {
                valueMetric = utilitySpace.getUtility(arbitraryBid);
            } catch (Exception e) {
                valueMetric = 0.0;
            }
        }
        while (valueMetric < utilityThreshold);
        return arbitraryBid;
    }

    private ActionWithBid returnAction() {
        double time = getTimeLine().getTime();

        double totalReceivedOffers = 0;
        double averageReceivedUtility;
        double minimumUtilityLimit;

        System.out.println("------------------------------------");


        for (ProposalAnalysis proposalAnalysis : receivedOffersList) {
            System.out.println(proposalAnalysis.getUtilityValue());
            totalReceivedOffers = totalReceivedOffers + proposalAnalysis.getUtilityValue();
        }
        averageReceivedUtility = totalReceivedOffers / receivedOffersList.size();
        minimumUtilityLimit = ((utilitySpace.getUtility(highestBid) + averageReceivedUtility) / 2) * time;

        if (minimumUtilityLimit < 0.7) {
            minimumUtilityLimit = ((utilitySpace.getUtility(highestBid) + 0.8) / 2);
        }

        System.out.println(minimumUtilityLimit);


        previousBid = generateRandomBidWithUtility(minimumUtilityLimit);
        return new Offer(this.getPartyId(), previousBid);

    }

    private double getFrequencyWeight() {
        return Math.max(FREQ_INTERCEPT - Math.log((double)rounds / FREQ_DIVIDER + FREQ_BIAS), FREQ_MIN);
    }

    private double calcTargetUtil(double max_utility, double min_utility, double inital_proposal_value, double rate_of_concession, double time, double opponentConcessionRate) {
        rate_of_concession = adjustRateOfConcession(rate_of_concession, opponentConcessionRate);
        double timeFunc = inital_proposal_value + ((1 - inital_proposal_value) * Math.pow(time, 1 / rate_of_concession));
        return min_utility + (1 - timeFunc) * (max_utility - min_utility);
    }

    private double adjustRateOfConcession(double myRate, double opponentRate) {
        // Example: Be more conceding if opponent is conceding less and vice versa
        if (opponentRate < myRate) {
            return myRate * 0.9; // Be less conceding
        } else {
            return myRate * 1.01; // Be more conceding
        }
    }

    private ActionWithBid isAccepting() {

        List<Accept> listAccept = new ArrayList<>();
        List<Double> listOfBids =  new ArrayList<>();


        for (BidAndAgent bidAndAgent : offerList) {
            Bid bid = bidAndAgent.bid();
            AgentID sender = bidAndAgent.agentID();
            double receivedBidUtility = utilitySpace.getUtility(bid);
            double targetUtility = calculateCurrentTargetUtility(sender);

            // Accept if the utility of the bid is higher than the target utility
            if (receivedBidUtility >= targetUtility) {
                double utilityDifference = Math.abs(receivedBidUtility - targetUtility);
                listOfBids.add(utilityDifference);
                listAccept.add(new Accept(getPartyId(), bid));
            }

        }

        double bestDiff = -1;
        Accept bestDecision = null;

        for (int i = 0; i < listAccept.size(); i++){
            double currentDiff = listOfBids.get(i);
            if (currentDiff > bestDiff) {
                bestDecision = listAccept.get(i);
            }
        }

        return bestDecision;
    }

    private double calculateCurrentTargetUtility(AgentID agentID) {
        double time = getTimeLine().getTime();
        double opponentConcessionRate = opponentModel.calculateOpponentConcessionRate(agentID); // Method from previous suggestion
        return calcTargetUtil(max_utility, min_utility, inital_proposal_value, rate_of_concession, time, opponentConcessionRate);
    }

    private void adjustStrategyBasedOnMostFrequentBid(AgentID agentID) {
        BidDetails mostFrequentBid = opponentModel.getMostFrequentBid(agentID);
        if (mostFrequentBid != null) {
            double ourUtility = utilitySpace.getUtility(mostFrequentBid.getBid());

            if (ourUtility > 0.8) {
                // If the utility is high, be less willing to concede
                adjustRateOfConcession(-0.05);
                System.out.println("Reducing concession rate due to high utility of most frequent opponent bid.");
            } else if (ourUtility < 0.5) {
                // If the utility is low, be more willing to concede
                adjustRateOfConcession(0.05);
                System.out.println("Increasing concession rate due to low utility of most frequent opponent bid.");
            }
        }
    }

    public void adjustRateOfConcession(double adjustment) {
        this.rate_of_concession = Math.max(0, Math.min(1, this.rate_of_concession + adjustment));
    }

    private ActionWithBid makeAnOffer() {
        System.out.println("Just made an offer!");

        double time = getTimeLine().getTime();

        // Process offers and update lists
        processOffers(time);

        // Decide on action based on the current time
        ActionWithBid action;

        if (time > 0.1){
            rounds ++;

            if (time < 0.45) {
                action = makeEarlyGameOffer();
            } else {
                action = makeMidOrLateGameOffer();
            }
        } else{
            action = makeEarlyGameOffer();
        }

        // Ensure a non-null action is returned
        return action != null ? action : returnAction();
    }

    private void processOffers(double time) {
        for (BidAndAgent currentBid : offerList) {
            double offerUtility = this.utilitySpace.getUtility(currentBid.bid());
            receivedOffersList.add(new ProposalAnalysis(offerUtility));
            BidDetails offerReceiveDetail = new BidDetails(currentBid.bid(), offerUtility, time);
            pastBids.add(offerReceiveDetail);
        }
    }

    private ActionWithBid makeEarlyGameOffer() {

        double utilityThreshold = (utilitySpace.getUtility(highestBid) + 0.8) / 2;

        for (BidAndAgent bidAndAgent : offerList) {
            AgentID sender = bidAndAgent.agentID();
            utilityThreshold = adjustUtilityThresholdBasedOnOpponent(utilityThreshold,sender);
        }

        previousBid = generateRandomBidWithUtility(utilityThreshold);
        return new Offer(this.getPartyId(), previousBid);
    }

    private ActionWithBid makeMidOrLateGameOffer() {
        List<ActionWithBid> possibleActions = generatePossibleActions();
        return chooseBestAction(possibleActions);
    }

    private List<ActionWithBid> generatePossibleActions() {
        List<ActionWithBid> possibleActions = new ArrayList<>();
        for (BidAndAgent currentBid : offerList) {
            if (previousBid != null && this.utilitySpace.getUtility(currentBid.bid()) > this.utilitySpace.getUtility(previousBid)) {
                possibleActions.add(new Accept(this.getPartyId(), currentBid.bid()));
            } else {
            possibleActions.add(returnAction());
            }
        }
        return possibleActions;
    }


    private ActionWithBid chooseBestAction(List<ActionWithBid> actions) {
        return actions.stream()
                .max(Comparator.comparing(action -> this.utilitySpace.getUtility(action.getBid())))
                .orElse(null);
    }

    private double adjustUtilityThresholdBasedOnOpponent(double currentThreshold, AgentID agentID) {
        double opponentAverageUtility = opponentModel.calculateOpponentAverageUtility(agentID);
        double concessionRate = opponentModel.calculateOpponentConcessionRate(agentID);

        if (concessionRate > 0) { // Opponent is conceding
            return Math.max(currentThreshold, opponentAverageUtility); // Be more aggressive
        } else {
            return (currentThreshold + opponentAverageUtility) / 2; // Be more conservative
        }
    }


    @Override
    public AbstractUtilitySpace estimateUtilitySpace() {
        AdditiveUtilitySpaceFactory factory = new AdditiveUtilitySpaceFactory(userModel.getDomain());
        List<IssueDiscrete> issues = factory.getIssues();

        // Maps to store the sum of ranks and counts for each value
        Map<IssueDiscrete, Map<ValueDiscrete, Integer>> valueRanks = new HashMap<>();
        Map<IssueDiscrete, Map<ValueDiscrete, Integer>> valueCounts = new HashMap<>();

        // Calculate value ranks and counts
        List<Bid> bidOrder = userModel.getBidRanking().getBidOrder();
        for (int i = 0; i < bidOrder.size(); i++) {
            Bid b = bidOrder.get(i);
            for (IssueDiscrete issue : issues) {
                ValueDiscrete value = (ValueDiscrete) b.getValue(issue.getNumber());
                valueRanks.computeIfAbsent(issue, k -> new HashMap<>());
                valueCounts.computeIfAbsent(issue, k -> new HashMap<>());

                valueRanks.get(issue).put(value, valueRanks.get(issue).getOrDefault(value, 0) + i);
                valueCounts.get(issue).put(value, valueCounts.get(issue).getOrDefault(value, 0) + 1);
            }
        }

        // Calculate and set utilities based on linear preferences
        for (IssueDiscrete issue : issues) {
            Map<ValueDiscrete, Integer> ranks = valueRanks.get(issue);
            Map<ValueDiscrete, Integer> counts = valueCounts.get(issue);

            // Sort values based on average rank
            List<ValueDiscrete> sortedValues = new ArrayList<>(issue.getValues());
            sortedValues.sort(Comparator.comparingDouble(v -> ranks.getOrDefault(v, 0) / (double) counts.getOrDefault(v, 1)));

            // Assign linear utilities
            int numValues = sortedValues.size();
            for (int i = 0; i < numValues; i++) {
                ValueDiscrete value = sortedValues.get(i);
                double utility = i / (double) (numValues - 1); // Linear scale
                factory.setUtility(issue, value, utility);
            }


        }

        // Calculate issue importance based on value utility variance
        Map<IssueDiscrete, Double> issueImportance = new HashMap<>();
        double totalImportance = 0.0;
        for (IssueDiscrete issue : issues) {
            List<Double> utilities = new ArrayList<>();
            for (ValueDiscrete value : issue.getValues()) {
                utilities.add(factory.getUtility(issue, value));
            }

            double variance = calculateVariance(utilities);
            issueImportance.put(issue, variance);
            totalImportance += variance;
        }

        // Assign weights based on calculated importance
        for (IssueDiscrete issue : issues) {
            double weight = issueImportance.get(issue) / totalImportance;
            factory.setWeight(issue, weight);
        }

        factory.scaleAllValuesFrom0To1();
        // factory.normalizeWeights(); // might not be needed as weights are already normalized

        return factory.getUtilitySpace();
    }

    private double calculateVariance(List<Double> values) {
        double mean = values.stream().mapToDouble(val -> val).average().orElse(0.0);
        return values.stream().mapToDouble(val -> (val - mean) * (val - mean)).average().orElse(0.0);
    }


    @Override
    public String getDescription() {
        return agentDetails;
    }

}