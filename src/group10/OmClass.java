package group10;

import genius.core.Bid;

public interface OmClass {
    double calculateBidUtility(Bid bid);
    void updateOpponentModel(Bid bid);
}
