package group10;

import genius.core.Bid;
import genius.core.Domain;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class JohnnyBlack implements OmClass {

    private HashMap<Issue, List<Value>> discreteIssueValues;
    private ValueFrequencyTracker frequencyAnalyzer;
    private double baselineUtility;
    private int freqThreshold;

    public JohnnyBlack(Domain negotiationDomain, double baselineUtility, int freqThreshold) {
        frequencyAnalyzer = new ValueFrequencyTracker(negotiationDomain);
        this.baselineUtility = baselineUtility;
        this.freqThreshold = freqThreshold;
        discreteIssueValues = new HashMap<>();
        for (Issue issue : negotiationDomain.getIssues()) {
            IssueDiscrete discreteIssue = (IssueDiscrete) issue;
            List<Value> optionValues = new ArrayList<>(discreteIssue.getValues());
            discreteIssueValues.put(issue, optionValues);
        }
    }

    public void updateOpponentModel(Bid opponentBid) {
        frequencyAnalyzer.FreqRegistering(opponentBid);
    }

    public double calculateBidUtility(Bid bid) {
        if (frequencyAnalyzer.retriveOverallFreq() > freqThreshold) {
            Map<Issue, Double> issueWeights = discreteIssueValues.keySet().stream()
                    .collect(Collectors.toMap(Function.identity(), this::determineIssueWeight));

            double weightSum = issueWeights.values().stream().mapToDouble(w -> w).sum();

            Map<Issue, Double> weightsNorm = discreteIssueValues.keySet().stream()
                    .collect(Collectors.toMap(Function.identity(), i -> issueWeights.get(i) / weightSum));

            return bid.getIssues().stream()
                    .mapToDouble(issue -> weightsNorm.get(issue) * calUtilityVal(issue, bid.getValue(issue)))
                    .sum();
        } else {
            return baselineUtility;
        }
    }

    private double calUtilityVal(Issue issue, Value v) {
        int rank = frequencyAnalyzer.calRankValForPos(issue, v);
        int totalValues = discreteIssueValues.get(issue).size();
        return ((double) (totalValues - rank + 1) / (double) totalValues);
    }

    private double determineIssueWeight(Issue issue) {
        int freqSum = frequencyAnalyzer.retriveOverallFreq();
        if (freqSum == 0) return 1.0 / (double) discreteIssueValues.keySet().size();
        return discreteIssueValues.get(issue).stream()
                .mapToDouble(v -> Math.pow((double) frequencyAnalyzer.frequencyObtain(issue, v) / (double) freqSum, 2))
                .sum();
    }
}