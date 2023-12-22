package group10;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.BidHistory;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.ActionWithBid;
import genius.core.actions.Offer;
import genius.core.bidding.BidDetails;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
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
import java.util.stream.Collectors;


public class AgentB extends AbstractNegotiationParty {

    private final String agentDetails = "Standard Negotiation Agent";
    private final double MAX_TARGET_UTILITY  = 0.95;
    private final double MIN_TARGET_UTILITY = 0.45;
    private final double randomFactor = 0.05; // Maximum percentage of randomness
    double max_utility;
    double min_utility;
    OmClass omClass;
    double targetUtility;
    Offer lastoffer;
    double rate_of_concession = 0.92;
    double inital_proposal_value = 0.2;
    private int rounds = 0;
    private List<BidAndAgent> offerList;
    private OpponentModel opponentModel;
    private Bid previousBid;
    private double previousConcessionRate = 0.0; // Initialize with 0 or an appropriate value
    private final double utilityFloor = 0.80; // Minimum threshold for max_utility
    private final double timeBasedFactor = 0.5; // Factor to control adjustment based on time
    private final BidHistory pastBids = new BidHistory();

    private static final int NUM_BIDS_TO_GENERATE = 100;
    private final double smallerAdjustmentFactor = 0.02; // Lesser adjustment if opponents concede less
    private final double largerAdjustmentFactor = 0.05; // Larger adjustment if opponents concede more
    private final double opponentConcessionThreshold = 0.1; // Threshold to determine significant concession

    private double lastUpdateTime = 0.0;
    private final double updateInterval = 0.25; // 25% of the negotiation time
    private final double significantChangeThreshold = 0.1; // Threshold for significant change in concession rate


    private List<ProposalAnalysis> receivedOffersList;

    @Override
    public void init(NegotiationInfo info) {
        try {
            super.init(info);

            rounds = 0;

            AbstractUtilitySpace utilitySpace = info.getUtilitySpace();
            AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;

            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
            offerList = new ArrayList<>();
            receivedOffersList = new ArrayList<>();
            opponentModel = new OpponentModel(utilitySpace);

            calculateDynamicUtilities(utilitySpace);

            System.out.println("max util bid : " + max_utility);
            omClass = new JohnnyBlack(getDomain(), MIN_TARGET_UTILITY, 10);

        }catch (Throwable e){
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void calculateDynamicUtilities(AbstractUtilitySpace utilitySpace) {
        try {
            Bid highestBid = utilitySpace.getMaxUtilityBid();
            Bid lowestBid = utilitySpace.getMinUtilityBid();
            max_utility = Math.max(utilitySpace.getUtility(highestBid), MAX_TARGET_UTILITY);
            min_utility = Math.max(utilitySpace.getUtility(lowestBid), MIN_TARGET_UTILITY);
        } catch (Exception e) {
            max_utility = MAX_TARGET_UTILITY;
            min_utility = MIN_TARGET_UTILITY;
        }
        min_utility = Math.max(utilitySpace.getReservationValue(), min_utility);

    }

    @Override
    public Action chooseAction(List<Class<? extends Action>> list) {
        try {
            rounds++;

            ActionWithBid possibleAccept = isAccepting();
            if (possibleAccept == null) {
                possibleAccept = makeAnOffer();
                System.out.println(possibleAccept);

                if (rounds % 5 == 0) {
                    System.out.println("Max utility: " + max_utility);

                }

            }


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
                lastoffer = offer;

                if (hasPreferenceUncertainty()){
                    opponentModel.updateOpponentBid(offer.getBid(), getTimeLine().getTime(), sender);
                    omClass.updateOpponentModel(offer.getBid());
                    adjustStrategyBasedOnMostFrequentBid(sender);
                }

                double time = getTimeLine().getTime();
                boolean isSignificantTimePassed = (time - lastUpdateTime) > updateInterval;
                boolean isSignificantChangeInConcession = hasSignificantChangeInConcessionRate();

                if (isSignificantTimePassed || isSignificantChangeInConcession) {
                    updateUtilities();
                    lastUpdateTime = time;
                }

            }
        }catch (Throwable e){
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    public void updateUtilities() {
        double time = getTimeLine().getTime();
        double aggregatedConcessionRate = calculateAggregatedOpponentConcessionRate();

        adjustMaxUtilityBasedOnOpponentBehavior(aggregatedConcessionRate, time);
    }

    private void adjustMaxUtilityBasedOnOpponentBehavior(double aggregatedConcessionRate, double time) {
        double adjustmentFactor = (aggregatedConcessionRate > opponentConcessionThreshold) ? largerAdjustmentFactor : smallerAdjustmentFactor;

        // Make adjustment more sensitive to the remaining time
        double timeAdjustment = (1 - time) * timeBasedFactor;

        // Introduce randomness in the adjustment
        double randomAdjustment = 1 + (Math.random() * 2 - 1) * randomFactor; // Random value between 1-randomFactor and 1+randomFactor

        // Adjust max_utility more gradually
        max_utility -= adjustmentFactor * timeAdjustment * randomAdjustment;

        // Ensure max_utility does not fall below the utility floor or MIN_TARGET_UTILITY
        max_utility = Math.max(max_utility, Math.max(utilityFloor, MIN_TARGET_UTILITY));
    }


    private boolean hasSignificantChangeInConcessionRate() {
        double currentConcessionRate = calculateAggregatedOpponentConcessionRate();
        double changeInConcession = Math.abs(currentConcessionRate - previousConcessionRate);

        // Update previousConcessionRate for the next comparison
        previousConcessionRate = currentConcessionRate;

        return changeInConcession > significantChangeThreshold;
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

        double utilityFromOffers = calculateUtilityFromOffers();
        double minimumUtilityLimit = calculateDynamicMinimumUtility(time, utilityFromOffers);

        // Adding randomness
        minimumUtilityLimit *= (1 - (Math.random() * 0.05)); // 5% randomness

        previousBid = generateRandomBidWithUtility(minimumUtilityLimit);
        return new Offer(this.getPartyId(), previousBid);
    }

    private double calculateUtilityFromOffers() {
        if (offerList.isEmpty()) {
            return 0.7; // Default utility value in case of no offers
        }

        // Consider more recent offers more significantly
        int recentOffersCount = Math.min(offerList.size(), 10);
        double totalUtility = 0;
        for (int i = offerList.size() - 1; i >= offerList.size() - recentOffersCount; i--) {
            totalUtility += utilitySpace.getUtility(offerList.get(i).bid());
        }
        return totalUtility / recentOffersCount;
    }

    private double calculateDynamicMinimumUtility(double time, double utilityFromOffers) {
        double maxUtilityOfBid = max_utility;
        double dynamicUtilityThreshold;

        if (time < 0.7) {
            // Early to Mid-Negotiation: Less conceding strategy
            dynamicUtilityThreshold = maxUtilityOfBid - ((1 - time) * (maxUtilityOfBid - utilityFromOffers));
        } else {
            // Late Negotiation: Gradually become more flexible
            double scale = (1.0 - time) * 1.2; // Less conceding compared to previous version
            dynamicUtilityThreshold = maxUtilityOfBid - scale * (maxUtilityOfBid - 0.7);
        }

        return Math.max(dynamicUtilityThreshold, 0.45); // Ensure minimum utility limit
    }



    private double calcTargetUtil(double max_utility, double min_utility, double initial_proposal_value, double rate_of_concession, double time, double opponentConcessionRate) {
        rate_of_concession = adjustRateOfConcession(rate_of_concession, opponentConcessionRate);
        double timeFunc = initial_proposal_value + ((1 - initial_proposal_value) * Math.pow(time, 1 / rate_of_concession));
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
            double targetUtility = calculateCurrentTargetUtility();

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

        if (timeline.getTime() < 0.90){
            return bestDecision;
        }else{
            return listAccept.get(listAccept.size() - 1);
        }


    }

    private double calculateCurrentTargetUtility() {
        double time = getTimeLine().getTime();
        double aggregatedConcessionRate = calculateAggregatedOpponentConcessionRate();
        return calcTargetUtil(max_utility, min_utility, inital_proposal_value, rate_of_concession, time, aggregatedConcessionRate);
    }

    private double calculateAggregatedOpponentConcessionRate() {
        Set<AgentID> opponentIDs = getOpponentIDs();
        double totalConcessionRate = 0;

        for (AgentID agentID : opponentIDs) {
            totalConcessionRate += opponentModel.calculateOpponentConcessionRate(agentID);
        }

        return opponentIDs.isEmpty() ? 0 : totalConcessionRate / opponentIDs.size();
    }

    private Set<AgentID> getOpponentIDs() {
        Set<AgentID> opponentIDs = new HashSet<>();
        for (BidAndAgent bidAndAgent : offerList) {
            opponentIDs.add(bidAndAgent.agentID());
        }
        return opponentIDs;
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

        double utilityThreshold = (max_utility + 0.8) / 2;

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
        List<Bid> generatedBids = generateRandomBids();
        List<BidDetails> evaluatedBids = evaluateBids(generatedBids);

        // Select the top bids based on utility
        List<BidDetails> topBids = selectTopBids(evaluatedBids);

        // Convert the top bids to actions
        return convertBidsToActions(topBids);
    }

    private List<Bid> generateRandomBids() {
        List<Bid> bids = new ArrayList<>();
        for (int i = 0; i < AgentB.NUM_BIDS_TO_GENERATE; i++) {
            bids.add(generateRandomBid());
        }
        return bids;
    }

    private List<BidDetails> evaluateBids(List<Bid> bids) {
        return bids.stream()
                .map(bid -> new BidDetails(bid, utilitySpace.getUtility(bid)))
                .collect(Collectors.toList());
    }

    private List<BidDetails> selectTopBids(List<BidDetails> evaluatedBids) {
        // Sort the bids by utility, in descending order
        evaluatedBids.sort((bid1, bid2) -> Double.compare(bid2.getMyUndiscountedUtil(), bid1.getMyUndiscountedUtil()));

        // Select the top N bids, N can be a fixed number or a percentage of NUM_BIDS_TO_GENERATE
        return evaluatedBids.subList(0, Math.min(evaluatedBids.size(), 10)); // Example: select top 10 bids
    }

    private List<ActionWithBid> convertBidsToActions(List<BidDetails> topBids) {
        List<ActionWithBid> actions = new ArrayList<>();
        for (BidDetails bidDetails : topBids) {
            actions.add(new Offer(getPartyId(), bidDetails.getBid()));
        }
        return actions;
    }

    private ActionWithBid chooseBestAction(List<ActionWithBid> actions) {
        BidDetails bestOpponentBid = getBestOpponentBid();
        return actions.stream()
                .max(Comparator.comparing(action -> evaluateActionUtility(action, bestOpponentBid)))
                .orElse(null);
    }

    private BidDetails getBestOpponentBid() {
        // Assuming offerList contains the offers made by opponents
        return offerList.stream()
                .map(bidAndAgent -> new BidDetails(bidAndAgent.bid(), utilitySpace.getUtility(bidAndAgent.bid())))
                .max(Comparator.comparing(BidDetails::getMyUndiscountedUtil))
                .orElse(null);
    }

    private double evaluateActionUtility(ActionWithBid action, BidDetails bestOpponentBid) {
        double ourUtility = utilitySpace.getUtility(action.getBid());
        double time = getTimeLine().getTime();
        double targetUtility = calculateCurrentTargetUtility();

        if (bestOpponentBid != null) {
            double opponentUtility = bestOpponentBid.getMyUndiscountedUtil();
            if (opponentUtility >= targetUtility) {
                return opponentUtility + getAcceptanceBonus(time);
            }

            if (isOpponentConceding() && time < 0.9) {
                return ourUtility * getConcessionFactor(time);
            }
        }
        return ourUtility;
    }

    private double getAcceptanceBonus(double time) {
        return 0.05 * (1 - time); // Example implementation
    }

    private boolean isOpponentConceding() {
        // Example implementation, adjust based on your strategy
        double currentConcessionRate = calculateAggregatedOpponentConcessionRate();
        return currentConcessionRate > opponentConcessionThreshold;
    }

    private double getConcessionFactor(double time) {
        return 1 - 0.1 * time; // Example implementation
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


    protected Bid generateRandomBid() {
        try {
            HashMap<Integer, Value> values = new HashMap<>(); // Stores the values for each issue
            List<Issue> issues = utilitySpace.getDomain().getIssues();

            for (Issue issue : issues) {
                switch (issue.getType()) {
                    case DISCRETE:
                        IssueDiscrete discreteIssue = (IssueDiscrete) issue;
                        int optionIndex = (int) (Math.random() * discreteIssue.getNumberOfValues());
                        values.put(issue.getNumber(), discreteIssue.getValue(optionIndex));
                        break;
                    // Add cases for other issue types if needed
                }
            }

            return new Bid(utilitySpace.getDomain(), values);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // Return null if unable to generate a bid
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