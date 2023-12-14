package group10;

import genius.core.Bid;
import genius.core.Domain;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;

import java.util.*;
import java.util.stream.IntStream;

public class ValueFrequencyTracker {

    private HashMap<Issue, HashMap<Value, Integer>> issueValueFrequencies;
    private int FreqSum = 0;

    public ValueFrequencyTracker(Domain negotiationSpace) {
        issueValueFrequencies = new HashMap<Issue, HashMap<Value, Integer>>();
        for (Issue issue : negotiationSpace.getIssues()) {
            IssueDiscrete discreteIssue = (IssueDiscrete) issue;
            HashMap<Value, Integer> valueFreqMap = new HashMap<Value, Integer>();
            for (Value value : discreteIssue.getValues()) {
                valueFreqMap.put(value, 0);
            }
            issueValueFrequencies.put(issue, valueFreqMap);
        }
    }

    public void FreqRegistering(Bid bid) {
        bid.getIssues().forEach(issue -> {
            Value value = bid.getValue(issue);
            issueValueFrequencies.get(issue).merge(value, 1, Integer::sum);
        });
        FreqSum++;
    }


    public int frequencyObtain(Issue i, Value v) {
        return issueValueFrequencies.get(i).get(v);
    }

    public int retriveOverallFreq() {
        return issueValueFrequencies.get(issueValueFrequencies.keySet().iterator().next())
                .values()
                .stream()
                .mapToInt(integer -> integer)
                .sum();
    }

    public int calRankValForPos(Issue issue, Value value) {
        List<Map.Entry<Value, Integer>> freqSorted = new ArrayList<Map.Entry<Value, Integer>>(issueValueFrequencies.get(issue).entrySet());
        freqSorted.sort(Collections.reverseOrder(frequencyComparator));
        return IntStream.range(0, freqSorted.size())
                .filter(i -> freqSorted.get(i).getKey().equals(value))
                .findFirst().getAsInt() + 1;
    }

    private static final Comparator<Map.Entry<Value, Integer>> frequencyComparator = new Comparator<Map.Entry<Value, Integer>>() {
        @Override
        public int compare(Map.Entry<Value, Integer> e1, Map.Entry<Value, Integer> e2) {
            Integer v1 = e1.getValue();
            Integer v2 = e2.getValue();
            return v1.compareTo(v2);
        }
    };

}
