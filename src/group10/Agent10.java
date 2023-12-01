package group10;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.BidHistory;
import genius.core.bidding.BidDetails;

import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;

import java.util.ArrayList;

import java.util.List;


public class Agent10 extends AbstractNegotiationParty {
    private final String agentDetails = "My example Agent";

    private Bid currentProposal;
    private Bid previousBid;

    private Bid highestBid;
    private Bid lowestBid;

    private BidHistory pastBids = new BidHistory();

    private List<ProposalAnalysis> receivedOffersList = new ArrayList<ProposalAnalysis>();

    @Override
    public void init(NegotiationInfo info) {
        super.init(info);

        AbstractUtilitySpace utilitySpace = info.getUtilitySpace();
        AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;

        try {
            highestBid = additiveUtilitySpace.getMaxUtilityBid();
            lowestBid = additiveUtilitySpace.getMinUtilityBid();


        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("max:" + utilitySpace.getUtility(highestBid));
        System.out.println("min:" + utilitySpace.getUtility(lowestBid));
        System.out.println("max:" + highestBid + "| min:" + lowestBid);

    }

    /**
     *
     * @param list
     * @return
     */
    @Override
    public Action chooseAction(List<Class<? extends Action>> list) {

        double time = getTimeLine().getTime();

        if (currentProposal != null) {

            double offerUtility = this.utilitySpace.getUtility(currentProposal);

            receivedOffersList.add(new ProposalAnalysis(offerUtility));

            BidDetails offerReceiveDetail = new BidDetails(currentProposal, 0, time);
            pastBids.add(offerReceiveDetail);

        }

        if (time < 0.5) {

            double utilityThreshold = ((utilitySpace.getUtility(highestBid) + 0.8) / 2);
            previousBid = generateRandomBidWithUtility(utilityThreshold);
            return new Offer(this.getPartyId(), previousBid);
        } else {

            if (currentProposal != null
                    && previousBid != null
                    && this.utilitySpace.getUtility(currentProposal) > this.utilitySpace.getUtility(previousBid)) {

                return new Accept(this.getPartyId(), currentProposal);
            } else {

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

                System.out.println(minimumUtilityLimit);
                System.out.println(pastBids.getAverageUtility());
                System.out.println(pastBids.getBestBidDetails());
                System.out.println(pastBids.getHistory());

                previousBid = generateRandomBidWithUtility(minimumUtilityLimit);
                return new Offer(this.getPartyId(), previousBid);
            }

        }
    }

    /**
     * @param sender
     * @param act
     */
    @Override
    public void receiveMessage(AgentID sender, Action act) {
        super.receiveMessage(sender, act);

        if (act instanceof Offer offer) {

            currentProposal = offer.getBid();
        }

    }

    /**
     * @return
     */
    @Override
    public String getDescription() {
        return agentDetails;
    }

    private Bid getMaxUtilityBid() {
        try {
            return this.utilitySpace.getMaxUtilityBid();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Bid generateRandomBidWithUtility(double utilityThreshold) {
        Bid arbitraryBid;
        double valueMetric;
        do {
            arbitraryBid = generateRandomBid();
            try {
                valueMetric = utilitySpace.getUtility(arbitraryBid);
            } catch (Exception e)
            {
                valueMetric = 0.0;
            }
        }
        while (valueMetric < utilityThreshold);
        return arbitraryBid;
    }

}