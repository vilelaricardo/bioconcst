/**
 * Concurrent Benchmarks
 * 
 * Title:  Cigarette Smokers with Semaphore     
 * 
 * Description:  This benchmark implements the of cigarette-smokers program
 *		         using semaphores.
 * 
 * Paradigm:     Shared Memory               
 *               
 * Year:         2014
 * Company:      ICMC/USP - São Carlos
 *               University of São Paulo (USP)
 *               Institute of Mathematics and Computer Science (ICMC)
 *               
 * @author       George Gabriel Mendes Dourado
 * @version      1.0
 */
 
/* TEST 1
 * java Main 0 1
 *
 * OUTPUT: 
 * The smoker with match smoke the cigarette!
 */
 
/* TEST 2
 * java Main 0 2
 *
 * OUTPUT: 
 * The smoker with paper smoke the cigarette!
 */
 
/* TEST 3
 * java Main 1 2
 *
 * OUTPUT: 
 * The smoker with tobacco smoke the cigarette!
 */


//0 - Tabaco na mesa ----- 1 Papel na Mesa ----- 
 
import java.util.Random;
import java.util.concurrent.Semaphore;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        boolean isTobacco = false;
        boolean isPaper = false;
        boolean isMatch = false;
        Semaphore agentSemaphore = new Semaphore(0);
        Semaphore tobaccoSemaphore = new Semaphore(0);
        Semaphore paperSemaphore = new Semaphore(0);
        Semaphore matchSemaphore = new Semaphore(0);
        agentSemaphore.release();
        tobaccoSemaphore.release();
        paperSemaphore.release();
        matchSemaphore.release();
                                
        int choice1 = 2;
        int choice2 = 1;
        
          //  choice1 = Integer.parseInt(args[0]); // 0 ou 1 ou 2  --- não podem ser iguais
          //  choice2 = Integer.parseInt(args[1]); // 0 ou 1 ou 2 --- não podem ser iguais

            
 if((choice1>=0) && (choice1<=2)){  
      if((choice2>=0) && (choice2<=2)){
    	  if(choice1!=choice2){
      
        Controller controller = new Controller();
        controller.setController(choice1, choice2, isTobacco, isPaper, isMatch);
        controller.setAgentSemaphore(agentSemaphore); 
        controller.setTobaccoSemaphore(tobaccoSemaphore);
        controller.setPaperSemaphore(paperSemaphore);
        controller.setMatchSemaphore(matchSemaphore);
        controller.start();
        
        SharedObject status = new SharedObject();

        Smoker_With_Tobacco smokerTobacco = new Smoker_With_Tobacco();
        smokerTobacco.setSmoker_With_Tobacco(status);
        smokerTobacco.setAgentSemaphore(agentSemaphore); 
        smokerTobacco.setTobaccoSemaphore(tobaccoSemaphore);
        smokerTobacco.setPaperSemaphore(paperSemaphore);
        smokerTobacco.setMatchSemaphore(matchSemaphore);
        smokerTobacco.start();

        Smoker_With_Paper smokerPaper = new Smoker_With_Paper();
        smokerPaper.setSmoker_With_Paper(status);
        smokerPaper.setAgentSemaphore(agentSemaphore); 
        smokerPaper.setTobaccoSemaphore(tobaccoSemaphore);
        smokerPaper.setPaperSemaphore(paperSemaphore);
        smokerPaper.setMatchSemaphore(matchSemaphore);
        smokerPaper.start();

        Smoker_With_Match smokerMatch = new Smoker_With_Match();
        smokerMatch.setSmoker_With_Match(status);
        smokerMatch.setAgentSemaphore(agentSemaphore); 
        smokerMatch.setTobaccoSemaphore(tobaccoSemaphore);
        smokerMatch.setPaperSemaphore(paperSemaphore);
        smokerMatch.setMatchSemaphore(matchSemaphore);
        smokerMatch.start();
    } }}
}
}
