package io.github.kensuke1984.kibrary;

import java.util.Random;

/**
 * Jokes.
 * @author otsuru
 * @since 2023/5/13 Imported from C version of spcsac.
 */
class Queens {

    private static int MAX = 18;

    static void noJokeNoScience() {
        // get random integer in [0:MAX-1]
        int mode = new Random().nextInt(MAX);
        if (mode == 1) {
            System.err.println("NO CAFFEINE, NO SCIENCE!");
        } else if (mode == 2) {
            System.err.println("A girl at a loss asked a policeman in New York,");
            System.err.println("  \"how can I get to Carnegie Hall?\"");
            System.err.println("  \"Practice,\" answered the policeman.");
        } else if (mode == 3) {
            System.err.println("People don't like NIH.");
            System.err.println("......................");
            System.err.println("Not Invented Here.");
        } else if (mode == 4) {
            System.err.println("We have almost completely established earthquake prediction method.");
            System.err.println("  We can predict 100% of earthquakes which took place two seconds ago.");
            System.err.println("  The only thing we have to do is now pure and simple:");
            System.err.println("  We have to advance the timing to predict four more seconds.");
        } else if (mode == 5) {
            System.err.println("Brazil is the country of the future:");
            System.err.println("       and forever it will be.");
        } else if (mode == 6) {
            System.err.println("\"What is your opinion about the shortage of meats?\"\n");
            System.err.println("   \"What do you mean by \'meats\'?,\" replied a North-Korean,");
            System.err.println("   \"I can't figure out what \'your opinion\' means,\" wondered a Chinese,");
            System.err.println("   \"I've never heard of the word \'shortage\' before!\" screamed an American.");
        } else if (mode == 7) {
            System.err.println("From IBM Fortran Compiler Manual(1977)");
            System.err.println("Error Code 103: Correct error and resubmit your problem.");
        } else if (mode == 8) {
            System.err.println("Vice President Richard B. Cheney:");
            System.err.println("He calls the shots.\n");
            System.err.println("\"The message is, if Dick Cheney is willing to shoot an innocent American");
            System.err.println("   citizen at point-blank range, imagine what he'll do to you,\"");
            System.err.println("Mr. Bush said.");
        } else if (mode == 9) {
            System.err.println("Trust me but verify!");
        } else if (mode == 10) {
            System.err.println("Poacher poached.");
        } else if (mode == 11) {
            System.err.println("There's something we hadn't better know.");
        } else if (mode == 12) {
            System.err.println("A man felt around a lamp stand for his car key one night.");
            System.err.println("His friend came up and asked:");
            System.err.println("   \"What are you doing? \"");
            System.err.println("   \"I've lost my car key and I'm looking for it,\" answered the man.");
            System.err.println("   \"You've lost it around here?");
            System.err.println("   \"No. Up there.\"");
            System.err.println("   \"Then how come you so eager about searching here? \"");
            System.err.println("   \"Look. Here it's brighter and easier to search.\"");
        } else if (mode == 13) {
            System.err.println("And so..... what's the xxxxing result?");
        } else if (mode == 14) {
            System.err.println("Surely You're Joking, Mr. Geller!");
        } else if (mode == 15) {
            System.err.println("There are three kinds of people in the world:");
            System.err.println("   those who can count and those who cannot.");
        } else if (mode == 16) {
            System.err.println("No doubt Paul Dirac was a great physicist.");
            System.err.println("But of course he could make a mistake.");
            System.err.println("In a class of physics his student asked Dr. Dirac,");
            System.err.println("    \"Sir, I think that you had a mistake in writing,\"");
            System.err.println("    \"That \'c\' squared must be \'c\' itself, I think.\"");
            System.err.println("Prof. Dirac glanced at the blackboad and replied,");
            System.err.println("    \"Well, what is your definition of \'c\'?");
            System.err.println("    \"Velocity of light, sir,\" replied the student immediately.");
            System.err.println("    \"Oh, I'm sorry but my definition of the velocity of light is.....\"");
            System.err.println("    \"\'c\' squared.\"");
            System.err.println("He continued the class for two more hours.");
            System.err.println("");
            System.err.println(" with his \'original\' definition of the velocity of light.");
        } else if (mode == 17) {
            System.err.println("We will rebuilt the detector, there's no question.");
            System.err.println("                                   --- Yoji Totsuka");
        } else {
            System.err.println("You have finally found me! ");
            System.err.println("Thank you very much for working with Kibrary.");
            System.err.println("Kibrary was developed in the laboratory of");
            System.err.println("Dr. R.J.Geller and Dr. K.Kawai in the Univ. of Tokyo.");
            System.err.println("You can see our site here:");
            System.err.println("https://utglobalseismology.org");
        }
    }
}
