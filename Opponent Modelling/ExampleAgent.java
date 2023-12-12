import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.DeadlineType;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;

/**
 * ExampleAgent returns the bid that maximizes its own utility for half of the negotiation session.
 * In the second half, it offers a random bid. It only accepts the bid on the table in this phase,
 * if the utility of the bid is higher than Example Agent's last bid.
 */
public class ExampleAgent extends AbstractNegotiationParty {
    private final String description = "Example Agent";

    private Bid lastReceivedOffer; // offer on the table
    private Bid myLastOffer;

    @Override
    public void init(NegotiationInfo info) {
        super.init(info);
        if (!hasPreferenceUncertainty()) {
            System.out.println("There is no preference uncertainty. Try this agent with a negotiation scenario that has preference uncertainty enabled.");
            return;
        }
    }

    /**
     * When this function is called, it is expected that the Party chooses one of the actions from the possible
     * action list and returns an instance of the chosen action.
     *
     * @param list
     * @return
     */
    @Override
    public Action chooseAction(List<Class<? extends Action>> list) {

    }
    // need to decide strategy

    /**
     * This method is called to inform the party that another NegotiationParty chose an Action.
     * @param sender
     * @param act
     */
    @Override
    public void receiveMessage(AgentID sender, Action act) {
        super.receiveMessage(sender, act);

        if (act instanceof Offer) { // sender is making an offer
            Offer offer = (Offer) act;

            // storing last received offer
            lastReceivedOffer = offer.getBid();
        }
    }

    /**
     * A human-readable description for this party.
     * @return
     */
    @Override
    public String getDescription() {
        return description;
    }
    /**
     * returns list of bids in descending order with respect to utility
     */

    public List<Bid> GCA(){
        for (Bid b:userModel.getBidRanking().getBidOrder()
        ) {opponentModelValues.add(new OpponentModelValues(b, (double) 0));}

        List<Bid> bs = new ArrayList<Bid>();
        for (int k = 1; k < 200; k++) {
            Bid xk = argmax(bs);
            List<Bid> newbs = new ArrayList<>(bs);
            newbs.add(xk);
            if (EU(incr(newbs)) < EU(incr(bs))){
                k -= 1;
            }
            bs.add(xk);
        }
        return incr(bs);
    }
    public List<Bid> incr(List<Bid> xs){
        xs.sort((x1, x2) -> (int) ((utilitySpace.getUtility(x1) * 1000) - (utilitySpace.getUtility(x2) * 1000)));
        return xs;
    }

    public double EU(List<Bid> pi){
        double eu = 0;
        int k = pi.size();

        for (int i = 0; i < k-1; i++) {
            double a = getOpponentProb(pi.get(i)) * utilitySpace.getUtility(pi.get(i));
            double b = 1;
            for (int j = 0; j < i-1; j++) {
                double temp = (1 - getOpponentProb(pi.get(j))) - getCost(k);
                b *= temp;
            }
            eu += (a * b);
        }
        return eu;
    }
    public int getCost (int k){
        int totalRounds = getDeadlines().getValue();
        int pastRounds = (int) getTimeLine().getCurrentTime();
        if (k < (totalRounds-pastRounds)){
            return 0;
        } else {return 999;}
    }

    public Double getOpponentProb(Bid b){
        return 0.0;
    }
    // need to actually estimate probability

    public Bid argmax(List<Bid> bs) {
        double maxEU = 0;
        Bid maxBid = null;
        for (Bid b : userModel.getBidRanking().getBidOrder().subList(0,199)) {
            if (!(bs.contains(b))) {
                List<Bid> newbs = new ArrayList<>(bs);
                newbs.add(b);
                double newEU = EU(newbs);
                if (newEU > maxEU) {
                    maxEU = newEU;
                    maxBid = b;
                }
            }
        }
        return maxBid;
    }
    // currently only looking at top 200 Bids not all of them as computationally expensive
}
