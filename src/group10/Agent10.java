package group10;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.BidHistory;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.ActionWithBid;
import genius.core.actions.Offer;
import genius.core.bidding.BidDetails;
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

    private final String agentDetails = "Costume Negotiation Agent";
    private final double MAX_TARGET_UTILITY = 0.95;
    private final double MIN_TARGET_UTILITY = 0.70;
    private final BidHistory pastBids = new BidHistory();
    private final List<ProposalAnalysis> receivedOffersList = new ArrayList<ProposalAnalysis>();
    double max_utility;
    double min_utility;
    OmClass omClass;
    double targetUtility;
    double rate_of_concession = 0.92;
    double inital_proposal_value = 0.2;
    private int rounds;
    private List<BidAndAgent> offerList;
    private OpponentModel opponentModel;
    private Bid previousBid;
    private Bid highestBid;
    private Bid lowestBid;

    // Initialize agent with negotiation information
    @Override
    public void init(NegotiationInfo info) {
        try {
            super.init(info);

            rounds = 0;

            offerList = new ArrayList<>();
            // Initialize the opponent modeling class
            this.opponentModel = new OpponentModel(info.getUtilitySpace());

            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));

            AbstractUtilitySpace utilitySpace = info.getUtilitySpace();
            AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;

            // Determine the highest and lowest bids based on utility space
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
            omClass = new JohnnyBlack(getDomain(), MIN_TARGET_UTILITY, 10);

        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public Action chooseAction(List<Class<? extends Action>> list) {
        // Decide action: either accept a bid or make a new offer
        try {
            ActionWithBid possibleAccept = isAccepting();
            if (possibleAccept == null) {
                possibleAccept = makeAnOffer();
                System.out.println(possibleAccept);

            }
            // Clear lists for the new negotiation round
            offerList.clear();
            receivedOffersList.clear();
            return possibleAccept;

        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    @Override
    public void receiveMessage(AgentID sender, Action act) {
        try {
            super.receiveMessage(sender, act);
            // Process received offers and update opponent model
            if (act instanceof Offer offer) {
                offerList.add(new BidAndAgent(offer.getBid(), sender));
                opponentModel.updateOpponentBid(offer.getBid(), getTimeLine().getTime(), sender);
                omClass.updateOpponentModel(offer.getBid());
                if (sender != null) {
                    System.out.println("Received bid from: " + sender.getName());
                }

                // Adjust strategy based on opponent's most frequent bid
                adjustStrategyBasedOnMostFrequentBid(sender);
               //opponentModel.updateFrequencies(offer.getBid(), sender, getFrequencyWeight());

            }
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Bid generateRandomBidWithUtility(double utilityThreshold) {
        // Generate a bid above a certain utility threshold
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
        while (valueMetric >= utilityThreshold);
        return arbitraryBid;
    }

    private ActionWithBid returnAction() {
        double time = getTimeLine().getTime();

        double totalReceivedOffers = 0;
        double averageReceivedUtility;
        double minimumUtilityLimit;

        System.out.println("------------------------------------");

        // Calculate the minimum utility limit for bid acceptance
        for (ProposalAnalysis proposalAnalysis : receivedOffersList) {
            System.out.println(proposalAnalysis.getUtilityValue());
            totalReceivedOffers = totalReceivedOffers + proposalAnalysis.getUtilityValue();
        }
        averageReceivedUtility = totalReceivedOffers / receivedOffersList.size();
        minimumUtilityLimit = ((utilitySpace.getUtility(highestBid) + averageReceivedUtility) / 2) * time;
        //omClass.calculateBidUtility(highestBid) Johnny Black

        if (minimumUtilityLimit < 0.7) {
            minimumUtilityLimit = ((utilitySpace.getUtility(highestBid) + 0.8) / 2);
        }
        //omClass.calculateBidUtility(highestBid)
        System.out.println(minimumUtilityLimit);


        previousBid = generateRandomBidWithUtility(minimumUtilityLimit);
        return new Offer(this.getPartyId(), previousBid);

    }


    private double calcTargetUtil(double max_utility, double min_utility, double inital_proposal_value, double rate_of_concession, double time, double opponentConcessionRate) {
        // Update rate of concession based on opponent's concession rate
        rate_of_concession = adjustRateOfConcession(rate_of_concession, opponentConcessionRate);
        double timeFunc = inital_proposal_value + ((1 - inital_proposal_value) * Math.pow(time, 1 / rate_of_concession));
        return min_utility + (1 - timeFunc) * (max_utility - min_utility);
    }

    // Adjust concession rate based on opponent's behavior
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
        List<Double> listOfBids = new ArrayList<>();


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

        // Select the best acceptable bid based on utility difference
        for (int i = 0; i < listAccept.size(); i++) {
            double currentDiff = listOfBids.get(i);
            if (currentDiff > bestDiff) {
                bestDecision = listAccept.get(i);
            }
        }

        return bestDecision;
    }

    // Calculate current target utility based on opponent's concession rate
    private double calculateCurrentTargetUtility(AgentID agentID) {
        double time = getTimeLine().getTime();
        double opponentConcessionRate = opponentModel.calculateOpponentConcessionRate(agentID);
        return calcTargetUtil(max_utility, min_utility, inital_proposal_value, rate_of_concession, time, opponentConcessionRate);
    }


    // Adjust negotiation strategy based on the utility of the most frequent bid
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

    // Modify the rate of concession within bounds
    public void adjustRateOfConcession(double adjustment) {
        this.rate_of_concession = Math.max(0, Math.min(1, this.rate_of_concession + adjustment));
    }

    // Decide on an offer based on the current time in the negotiation
    private ActionWithBid makeAnOffer() {
        System.out.println("Just made an offer!");

        double time = getTimeLine().getTime();

        // Process offers and update lists
        processOffers(time);

        // Decide on action based on the current time
        ActionWithBid action;

        if (time > 0.1) {
            rounds++;

            if (time < 0.45) {
                action = makeEarlyGameOffer();
            } else {
                action = makeMidOrLateGameOffer();
            }
        } else {
            action = makeEarlyGameOffer();
        }

        // Ensure a non-null action is returned
        return action != null ? action : returnAction();
    }

    // Process received offers and update offer lists
    private void processOffers(double time) {
        for (BidAndAgent currentBid : offerList) {
            double offerUtility = this.utilitySpace.getUtility(currentBid.bid());
            receivedOffersList.add(new ProposalAnalysis(offerUtility));
            BidDetails offerReceiveDetail = new BidDetails(currentBid.bid(), offerUtility, time);
            pastBids.add(offerReceiveDetail);
        }
    }

    // Make an offer based on the stage of the negotiation (early/mid/late game)
    private ActionWithBid makeEarlyGameOffer() {

        double utilityThreshold = (utilitySpace.getUtility(highestBid) + 0.8) / 2;

        for (BidAndAgent bidAndAgent : offerList) {
            AgentID sender = bidAndAgent.agentID();
            utilityThreshold = adjustUtilityThresholdBasedOnOpponent(utilityThreshold, sender);
        }

        previousBid = generateRandomBidWithUtility(utilityThreshold);
        return new Offer(this.getPartyId(), previousBid);
    }

    private ActionWithBid makeMidOrLateGameOffer() {
        List<ActionWithBid> possibleActions = generatePossibleActions();
        return chooseBestAction(possibleActions);
    }

    // Generate a list of possible actions to take
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


    // Choose the best action based on utility
    private ActionWithBid chooseBestAction(List<ActionWithBid> actions) {
        return actions.stream()
                .max(Comparator.comparing(action -> this.utilitySpace.getUtility(action.getBid())))
                .orElse(null);
    }

    // Adjust utility threshold based on opponent's behavior
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
        Map<IssueDiscrete, Map<ValueDiscrete, Integer>> rankingOfValues = new HashMap<>();
        Map<IssueDiscrete, Map<ValueDiscrete, Integer>> countsOfValues = new HashMap<>();

        // Calculate value ranks and counts
        List<Bid> orderOfBids = userModel.getBidRanking().getBidOrder();
        for (int issue = 0; issue < orderOfBids.size(); issue++) {
            Bid b = orderOfBids.get(issue);
            for (IssueDiscrete i : issues) {
                ValueDiscrete value = (ValueDiscrete) b.getValue(i.getNumber());
                rankingOfValues.computeIfAbsent(i, k -> new HashMap<>());
                countsOfValues.computeIfAbsent(i, k -> new HashMap<>());

                rankingOfValues.get(i).put(value, rankingOfValues.get(i).getOrDefault(value, 0) + issue);
                countsOfValues.get(i).put(value, countsOfValues.get(i).getOrDefault(value, 0) + 1);
            }
        }

        // Calculate and set utilities based on linear preferences
        for (IssueDiscrete issue : issues) {
            Map<ValueDiscrete, Integer> ranks = rankingOfValues.get(issue);
            Map<ValueDiscrete, Integer> counts = countsOfValues.get(issue);

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
        Map<IssueDiscrete, Double> weighOfIssue = new HashMap<>();
        double totalImportance = 0.0;
        for (IssueDiscrete issue : issues) {
            List<Double> utilities = new ArrayList<>();
            for (ValueDiscrete value : issue.getValues()) {
                utilities.add(factory.getUtility(issue, value));
            }

            double variance = calculateVariance(utilities);
            weighOfIssue.put(issue, variance);
            totalImportance += variance;
        }

        // Assign weights based on calculated importance
        for (IssueDiscrete issue : issues) {
            double weight = weighOfIssue.get(issue) / totalImportance;
            factory.setWeight(issue, weight);
        }

        factory.scaleAllValuesFrom0To1();
        // factory.normalizeWeights(); // might not be needed as weights are already normalized

        return factory.getUtilitySpace();
    }

    // Calculate variance of a set of values
    private double calculateVariance(List<Double> values) {
        double mean = values.stream().mapToDouble(val -> val).average().orElse(0.0);
        return values.stream().mapToDouble(val -> (val - mean) * (val - mean)).average().orElse(0.0);
    }

    @Override
    public String getDescription() {
        return agentDetails;
    }

}