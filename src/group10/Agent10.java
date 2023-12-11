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
    private final String agentDetails = "Standard Negotiation Agent";
    private List<Bid> offerList;
    private OpponentModel opponentModel;

    private Bid previousBid;
    private Bid highestBid;
    private final double MAX_TARGET_UTILITY  = 0.95;
    private final double MIN_TARGET_UTILITY = 0.35;
    double max_utility;
    double min_utility;

    double targetUtility;
    double rate_of_concession = 0.1;
    double inital_proposal_value = 0.05;
    private Bid lowestBid;
    private BidHistory pastBids = new BidHistory();

    private List<ProposalAnalysis> receivedOffersList = new ArrayList<ProposalAnalysis>();

    @Override
    public void init(NegotiationInfo info) {
        try {
        super.init(info);


        offerList = new ArrayList<>();
        this.opponentModel = new OpponentModel(info.getUtilitySpace());

        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));

        AbstractUtilitySpace utilitySpace = info.getUtilitySpace();
        AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;

        try {
            if (hasPreferenceUncertainty()) {
//                System.out.println("Preference uncertainty is enabled.");
                highestBid = additiveUtilitySpace.getMaxUtilityBid();
                lowestBid = additiveUtilitySpace.getMinUtilityBid();
//                System.out.println("Lowest utility bid: " + lowestBid);
//                System.out.println("Highest utility bid: " + highestBid);
                max_utility = Math.max(utilitySpace.getUtility(highestBid), MAX_TARGET_UTILITY);
                min_utility = Math.max(utilitySpace.getUtility(lowestBid), MIN_TARGET_UTILITY);
            }
        } catch (Exception e) {
            max_utility = MAX_TARGET_UTILITY;
            min_utility = MIN_TARGET_UTILITY;
        }

        min_utility = Math.max(utilitySpace.getReservationValue(), min_utility);
//
//        System.out.println("max utility is: " + max_utility);
//        System.out.println("min utility is: " + min_utility);
//        System.out.println("Reserved value: " + utilitySpace.getReservationValue());


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
                return possibleAccept;

            } else {
                return possibleAccept;
            }
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
            offerList.add(offer.getBid());
            opponentModel.updateOpponentBid(offer.getBid(), getTimeLine().getTime());



            if (sender != null) {
                System.out.println("Received bid from: " + sender.getName());
            }

            // Print the information after updating the bid
            opponentModel.printLatestOpponentBid();
            opponentModel.printOpponentConcessionRate();
            opponentModel.printOpponentAverageUtility();
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

        for (ProposalAnalysis proposalAnalysis : receivedOffersList) {
            totalReceivedOffers = totalReceivedOffers + proposalAnalysis.getUtilityValue();
        }
        averageReceivedUtility = totalReceivedOffers / receivedOffersList.size();
        minimumUtilityLimit = ((utilitySpace.getUtility(highestBid) + averageReceivedUtility) / 2) * time;

        if (minimumUtilityLimit < 0.67) {
            minimumUtilityLimit = ((utilitySpace.getUtility(highestBid) + 0.8) / 2);
        }

//        System.out.println(minimumUtilityLimit);
//        System.out.println(pastBids.getAverageUtility());
//        System.out.println(pastBids.getBestBidDetails());
//        System.out.println(pastBids.getHistory());

        previousBid = generateRandomBidWithUtility(minimumUtilityLimit);
        return new Offer(this.getPartyId(), previousBid);

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
            return myRate * 1.1; // Be more conceding
        }
    }

    private ActionWithBid isAccepting() {
        Offer lastOffer = (getLastReceivedAction() instanceof Offer) ? (Offer) getLastReceivedAction() : null;
        if (lastOffer != null) {
            Bid receivedBid = lastOffer.getBid();
            double receivedBidUtility = getUtility(receivedBid);
            double targetUtility = calculateCurrentTargetUtility();

            // Accept if the utility of received bid is higher than the target utility
            if (receivedBidUtility >= targetUtility) {
                return new Accept(getPartyId(), receivedBid);
            }
        }
        return null;
    }

    private double calculateCurrentTargetUtility() {
        double time = getTimeLine().getTime();
        double opponentConcessionRate = opponentModel.calculateOpponentConcessionRate(); // Method from previous suggestion
        return calcTargetUtil(max_utility, min_utility, inital_proposal_value, rate_of_concession, time, opponentConcessionRate);
    }

    private ActionWithBid makeAnOffer() {
        System.out.println("Just made an offer!");

        double time = getTimeLine().getTime();

        // Process offers and update lists
        processOffers(time);

        // Decide on action based on the current time
        ActionWithBid action;
        if (time < 0.5) {
            action = makeEarlyGameOffer();
        } else {
            action = makeMidOrLateGameOffer();
        }

        // Ensure a non-null action is returned
        return action != null ? action : returnAction();
    }

    private void processOffers(double time) {
        for (Bid currentBid : offerList) {
            double offerUtility = this.utilitySpace.getUtility(currentBid);
            receivedOffersList.add(new ProposalAnalysis(offerUtility));
            BidDetails offerReceiveDetail = new BidDetails(currentBid, offerUtility, time);
            pastBids.add(offerReceiveDetail);
        }
        offerList.clear();
    }

    private ActionWithBid makeEarlyGameOffer() {
        double utilityThreshold = (utilitySpace.getUtility(highestBid) + 0.8) / 2;
        utilityThreshold = adjustUtilityThresholdBasedOnOpponent(utilityThreshold);
        previousBid = generateRandomBidWithUtility(utilityThreshold);
        return new Offer(this.getPartyId(), previousBid);
    }

    private ActionWithBid makeMidOrLateGameOffer() {
        List<ActionWithBid> possibleActions = generatePossibleActions();
        return chooseBestAction(possibleActions);
    }

    private List<ActionWithBid> generatePossibleActions() {
        List<ActionWithBid> possibleActions = new ArrayList<>();
        for (Bid currentBid : offerList) {
            if (previousBid != null && this.utilitySpace.getUtility(currentBid) > this.utilitySpace.getUtility(previousBid)) {
                possibleActions.add(new Accept(this.getPartyId(), currentBid));
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

    private double adjustUtilityThresholdBasedOnOpponent(double currentThreshold) {
        double opponentAverageUtility = opponentModel.calculateOpponentAverageUtility();
        double concessionRate = opponentModel.calculateOpponentConcessionRate();

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