package group10;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.bidding.BidDetails;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.ValueDiscrete;
import genius.core.utility.AbstractUtilitySpace;

import java.util.*;
import java.util.stream.Collectors;

public class OpponentModel{
    private Map<AgentID, List<BidDetails>> opponentBids;
    private AbstractUtilitySpace utilitySpace;
    private Map<Bid, Integer> bidFrequency;
    private Map<AgentID, Double> totalConcession;
    private Map<AgentID, Integer>  concessionCount;
    private ArrayList<IssueDiscrete> setOfIssues;
    private HashMap<IssueDiscrete, HashMap<ValueDiscrete, HashMap<AgentID, Double>>> profileOfOpponent;
    private HashMap<IssueDiscrete, HashMap<ValueDiscrete, HashMap<AgentID, Double>>> profileOfValues;
    private HashMap<IssueDiscrete, HashMap<AgentID, Double>> profileOfIssues;

    public OpponentModel(AbstractUtilitySpace utilitySpace) {
        this.opponentBids = new HashMap<>();
        this.utilitySpace = utilitySpace;
        this.bidFrequency = new HashMap<>();
        this.totalConcession = new HashMap<>();
        this.concessionCount = new HashMap<>();

        this.setOfIssues = new ArrayList<>();
        this.profileOfOpponent = new HashMap<>();
        this.profileOfValues = new HashMap<>();
        this.profileOfIssues = new HashMap<>();

        for (Issue issue : utilitySpace.getDomain().getIssues()) {
            if (issue instanceof IssueDiscrete issueDiscrete) {
                setOfIssues.add(issueDiscrete);
                HashMap<ValueDiscrete, HashMap<AgentID, Double>> opponentMap = new HashMap<>();
                HashMap<ValueDiscrete, HashMap<AgentID, Double>> valueMap = new HashMap<>();
                for (ValueDiscrete value : issueDiscrete.getValues()) {
                    opponentMap.put(value, new HashMap<>());
                    valueMap.put(value, new HashMap<>());
                }
                profileOfOpponent.put(issueDiscrete, opponentMap);
                profileOfValues.put(issueDiscrete, valueMap);
                profileOfIssues.put(issueDiscrete, new HashMap<>());
            }
        }
    }


    public void FrequenciesOptimization(Bid opponentBid, AgentID agentID, double weightFreq) {
        for (IssueDiscrete issue : setOfIssues) {
            ValueDiscrete value = (ValueDiscrete) opponentBid.getValue(issue.getNumber());
            HashMap<ValueDiscrete, HashMap<AgentID, Double>> valueMap = profileOfOpponent.get(issue);
            HashMap<AgentID, Double> agentMap = valueMap.get(value);
            Double currentFrequency = agentMap.getOrDefault(agentID, 0.0);
            Double newFrequency = currentFrequency + weightFreq;
            agentMap.put(agentID, newFrequency);
        }
    }


    public double calculateOpponentConcessionRate(AgentID agentID) {
        return (concessionCount.get(agentID) != null) ? totalConcession.get(agentID) / concessionCount.get(agentID) : 0.0;
    }

    public double calculateOpponentAverageUtility(AgentID agentID) {
        List<BidDetails> bids = opponentBids.get(agentID);

        if (bids == null || bids.isEmpty()) {
            return 0.0;
        }

        return bids.stream()
                .mapToDouble(bid -> utilitySpace.getUtility(bid.getBid()))
                .average()
                .orElse(0.0);
    }

    public BidDetails getMostFrequentBid(AgentID agentID) {
        List<BidDetails> bidDetailsForAgent = opponentBids.get(agentID);

        if (bidDetailsForAgent == null || bidDetailsForAgent.isEmpty()){
            return null;
        }

        return bidDetailsForAgent.stream()
                .filter(bidDetail -> bidFrequency.containsKey(bidDetail.getBid()))
                .collect(Collectors.groupingBy(BidDetails::getBid, Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> findBidDetailsForBid(bidDetailsForAgent, entry.getKey()))
                .orElse(null);
    }

    private BidDetails findBidDetailsForBid(List<BidDetails> bidDetailsList, Bid bid) {
        return bidDetailsList.stream()
                .filter(bidDetail -> bidDetail.getBid().equals(bid))
                .findFirst()
                .orElse(null);
    }


    public void updateOpponentBid(Bid opponentBid, double time, AgentID agentID) {
        var agentArray = opponentBids.computeIfAbsent(agentID,(a) -> new ArrayList<>());
        double utility = this.utilitySpace.getUtility(opponentBid);
        agentArray.add(new BidDetails(opponentBid, utility, time));
        bidFrequency.put(opponentBid, bidFrequency.getOrDefault(opponentBid, 0) + 1);

        if (agentArray.size() > 1) {
            BidDetails previousBid = agentArray.get(agentArray.size() - 2);
            double concession = utilitySpace.getUtility(previousBid.getBid()) - utility;
            totalConcession.computeIfAbsent(agentID, (a) -> 0.0);
            totalConcession.compute(agentID, (a, previousConcesion) -> previousConcesion+concession);
            concessionCount.computeIfAbsent(agentID, (a) -> 0);
            concessionCount.compute(agentID, (a, previousCount) -> previousCount++);
        }

    }

}
