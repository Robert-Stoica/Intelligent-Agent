package group10;

import java.util.List;

import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.uncertainty.AdditiveUtilitySpaceFactory;
import genius.core.utility.AbstractUtilitySpace;

public class UncertaintyAgentExample extends AbstractNegotiationParty
{

    @Override
    public Action chooseAction(List<Class<? extends Action>> possibleActions)
    {

        // Sample code that accepts offers that appear in the top 10% of offers in the user model
        if (getLastReceivedAction() instanceof Offer && hasPreferenceUncertainty())
        {
            Bid receivedBid = ((Offer) getLastReceivedAction()).getBid();
            List<Bid> bidOrder = userModel.getBidRanking().getBidOrder();

            System.out.println("Bid order: " + bidOrder);
            System.out.println("Low: " + userModel.getBidRanking().getLowUtility());
            System.out.println("High: " + userModel.getBidRanking().getHighUtility());

            System.out.println("Received bid (which I will elicit against cost " + user.getElicitationCost() + "): " + receivedBid);
            user.elicitRank(receivedBid, userModel);
            System.out.println("Bother is now: " + user.getTotalBother());

            System.out.println("Updated bid order: " + bidOrder);
            System.out.println("Updated low: " + userModel.getBidRanking().getLowUtility());
            System.out.println("Updated high: " + userModel.getBidRanking().getHighUtility());

            // If the rank of the received bid is known
            if (bidOrder.contains(receivedBid))
            {
                double percentile = (bidOrder.size() - bidOrder.indexOf(receivedBid)) / (double) bidOrder.size();
                if (percentile < 0.1)
                    return new Accept(getPartyId(), receivedBid);
            }
            // If it is not, then elicit the new user model augmented with the received bid
            // provided the Total user bother doesn't exceed 0.1
            else if(user.getTotalBother()<0.1)
            {
                userModel = user.elicitRank(receivedBid,userModel);
                double percentile = (bidOrder.size() - bidOrder.indexOf(receivedBid)) / (double) bidOrder.size();
                if (percentile < 0.1)
                    return new Accept(getPartyId(), receivedBid);
            }
        }
        // Otherwise just offer a Random bid
        return new Offer(getPartyId(), generateRandomBid());
    }

    /**
     * Specific functionality, such as the estimate of the utility space in the
     * face of preference uncertainty, can be specified by overriding the
     * default behavior.
     *
     * This example estimator sets all weights uniformly
     */
    @Override
    public AbstractUtilitySpace estimateUtilitySpace()
    {
        AdditiveUtilitySpaceFactory additiveUtilitySpaceFactory = new AdditiveUtilitySpaceFactory(getDomain());
        List<IssueDiscrete> issues = additiveUtilitySpaceFactory.getIssues();
        for (IssueDiscrete i : issues)
        {
            additiveUtilitySpaceFactory.setWeight(i, 1.0 / issues.size());
            for (ValueDiscrete v : i.getValues())
            {
                int valueScore = 0;
                for (Bid b : userModel.getBidRanking().getBidOrder())
                    if (b.containsValue(i, v))
                        valueScore ++;
                additiveUtilitySpaceFactory.setUtility(i, v, valueScore);
            }
        }

        // Normalize the attribute functions, since we gave them integer scores
        additiveUtilitySpaceFactory.scaleAllValuesFrom0To1();

        // Normalizing the weights might be needed if the above code is changed; uncomment when needed.
        // additiveUtilitySpaceFactory.normalizeWeights();

        // The factory is done with setting all parameters, now return the estimated utility space
        return additiveUtilitySpaceFactory.getUtilitySpace();
    }

    @Override
    public String getDescription()
    {
        return "Example agent that can deal with uncertain preferences";
    }

    public String getName(){
        return "Uncertain.Agent";
    }

}