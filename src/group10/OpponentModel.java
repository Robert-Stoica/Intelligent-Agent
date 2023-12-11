package group10;

import genius.core.Bid;
import genius.core.bidding.BidDetails;
import genius.core.uncertainty.UserModel;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;

import java.util.ArrayList;
import java.util.List;

public class OpponentModel {
    private List<BidDetails> opponentBids;
    private AbstractUtilitySpace utilitySpace;

    public OpponentModel(AbstractUtilitySpace utilitySpace) {
        this.opponentBids = new ArrayList<>();
        this.utilitySpace = utilitySpace;
    }

    public void updateOpponentBid(Bid bid, double time) {
        double utility = this.utilitySpace.getUtility(bid);
        opponentBids.add(new BidDetails(bid, utility, time));
    }

    public double calculateOpponentConcessionRate() {
        if (opponentBids.size() < 2) {
            return 0.0;
        }

        BidDetails latestBid = opponentBids.get(opponentBids.size() - 1);
        BidDetails previousBid = opponentBids.get(opponentBids.size() - 2);

        return utilitySpace.getUtility(previousBid.getBid()) - utilitySpace.getUtility(latestBid.getBid());
    }

    public double calculateOpponentAverageUtility() {
        if (opponentBids.isEmpty()) {
            return 0.0;
        }

        return opponentBids.stream()
                .mapToDouble(bid -> utilitySpace.getUtility(bid.getBid()))
                .average()
                .orElse(0.0);
    }


    public void printLatestOpponentBid() {
        if (!opponentBids.isEmpty()) {
            BidDetails latestBid = opponentBids.get(opponentBids.size() - 1);

            System.out.println("Latest Opponent Bid: " + latestBid.getBid() + ", Utility: " + utilitySpace.getUtility(latestBid.getBid()) + ", Time: " + latestBid.getTime());
        }
    }


    public void printOpponentConcessionRate() {
        double concessionRate = calculateOpponentConcessionRate();
        System.out.println("Opponent Concession Rate: " + concessionRate);
    }

    public void printOpponentAverageUtility() {
        double avgUtility = calculateOpponentAverageUtility();
        System.out.println("Opponent Average Utility: " + avgUtility);
    }
}
