package hamlet.examples;

import hamlet.EnsembleSummary;
import hamlet.EnsembleSummarySpec;
import hamlet.Model;
import hamlet.Moment;
import hamlet.Population;
import hamlet.Reaction;
import hamlet.State;
import hamlet.SubPopulation;
import hamlet.TauLeapingIntegrator;
import java.io.FileNotFoundException;
import java.io.PrintStream;

/**
 * Simple model of HIV infection with neutral RT error-driven mutation.
 *
 * @author Tim Vaughan
 */
public class NeutralHIVEvolution {

    public static void main(String[] argv) throws FileNotFoundException {

        // Sequence length:
        int L = 1000;
        
        // Maximum number of mutations to consider:
        int hTrunc = 20;

        /*
         * Assemble model:
         */

        Model model = new Model();

        // Define populations:

        // Uninfected cell:
        Population X = new Population("X");
        model.addPopulation(X);

        // Infected cell:
        Population Y = new Population("Y", hTrunc+1);
        model.addPopulation(Y);

        // Virion:
        Population V = new Population("V", hTrunc+1);
        model.addPopulation(V);

        // Define reactions:

        // 0 -> X
        Reaction cellBirth = new Reaction();
        cellBirth.setReactantSchema();
        cellBirth.setProductSchema(X);
        cellBirth.setRate(2.5e8);
        model.addReaction(cellBirth);

        // X + V -> Y (with mutation)
        Reaction infection = new Reaction();
        infection.setReactantSchema(X, V);
        infection.setProductSchema(Y);

        double mu = 2e-5*L; // Mutation probabability per infection event.
        double beta = 5e-13; // Total infection rate.

        for (int h = 0; h<=hTrunc; h++) {

            SubPopulation Vsub = new SubPopulation(V, h);

            int hpmin = h>1 ? h-1 : 0;
            int hpmax = h<hTrunc ? h+1 : hTrunc;

            for (int hp = hpmin; hp<=hpmax; hp++) {

                SubPopulation Ysub = new SubPopulation(Y, hp);

                // Transition rate to hp from a given sequence in h:
                double rate = mu*gcond(h, hp, L)/(3.0*L);

                // Mutation-free contribution:
                if (h==hp)
                    rate += (1-mu);

                // Incorporate base infection rate:
                rate *= beta;

                infection.addReactantSubSchema(null, Vsub);
                infection.addProductSubSchema(Ysub);
                infection.addSubRate(rate);
            }
        }

        model.addReaction(infection);

        // Y -> Y + V
        Reaction budding = new Reaction();
        budding.setReactantSchema(Y);
        budding.setProductSchema(Y, V);
        for (int h = 0; h<=hTrunc; h++) {
            SubPopulation Ysub = new SubPopulation(Y, h);
            SubPopulation Vsub = new SubPopulation(V, h);
            budding.addReactantSubSchema(Ysub);
            budding.addProductSubSchema(Ysub, Vsub);
        }
        budding.setRate(1e3);
        model.addReaction(budding);

        // X -> 0
        Reaction cellDeath = new Reaction();
        cellDeath.setReactantSchema(X);
        cellDeath.setProductSchema();
        cellDeath.setRate(1e-3);
        model.addReaction(cellDeath);

        // Y -> 0
        Reaction infectedDeath = new Reaction();
        infectedDeath.setReactantSchema(Y);
        infectedDeath.setProductSchema();

        for (int h = 0; h<=hTrunc; h++) {
            SubPopulation Ysub = new SubPopulation(Y, h);

            infectedDeath.addReactantSubSchema(Ysub);
            infectedDeath.addProductSubSchema();
        }
        infectedDeath.setRate(1.0);
        model.addReaction(infectedDeath);

        // V -> 0
        Reaction virionDeath = new Reaction();
        virionDeath.setReactantSchema(V);
        virionDeath.setProductSchema();

        for (int h = 0; h<=hTrunc; h++) {
            SubPopulation Vsub = new SubPopulation(V, h);

            virionDeath.addReactantSubSchema(Vsub);
            virionDeath.addProductSubSchema();
        }
        virionDeath.setRate(3.0);
        model.addReaction(virionDeath);

        /*
         * Define moments:
         */

        Moment mX = new Moment("X", X);
        Moment mY = new Moment("Y", Y);
        Moment mV = new Moment("V", V);

        for (int h = 0; h<=hTrunc; h++) {
            SubPopulation Ysub = new SubPopulation(Y, h);
            mY.addSubSchema(Ysub);

            SubPopulation Vsub = new SubPopulation(V, h);
            mV.addSubSchema(Vsub);
        }

        /*
         * Set initial state:
         */

        State initState = new State(model);
        initState.set(X, 6.1e9);

        initState.set(new SubPopulation(Y, 0), 2.5e8);
        initState.set(new SubPopulation(V, 0), 8.2e10);

        /*
         * Define simulation:
         */

        EnsembleSummarySpec spec = new EnsembleSummarySpec();

        spec.setModel(model);
        spec.setSimulationTime(365.0);
        spec.setIntegrator(new TauLeapingIntegrator(365.0/1e4));
        spec.setnSamples(1001);
        spec.setnTraj(10);
        spec.setSeed(53);
        spec.setInitState(initState);
        spec.addMoment(mX);
        spec.addMoment(mY);
        spec.addMoment(mV);

        // Report on ensemble progress:
        spec.setVerbosity(1);

        /*
         * Generate ensemble:
         */

        EnsembleSummary ensemble = new EnsembleSummary(spec);

        /*
         * Dump results to file (JSON):
         */

        ensemble.dump(new PrintStream("out.json"));
    }

    /**
     * Return the number of sequences s2 satisfying d(s2,0)=h2 and d(s2,s1)=1
     * where s1 is a particular sequence satisfying d(s1,0)=h1.
     *
     * @param h1
     * @param h2
     * @param L
     * @return
     */
    static int gcond(int h1, int h2, int L) {

        int result;

        switch (h2-h1) {
            case 1:
                result = 3*(L-h1);
                break;
            case 0:
                result = 2*h1;
                break;
            case -1:
                result = h1;
                break;
            default:
                result = 0;
        }

        return result;
    }
}