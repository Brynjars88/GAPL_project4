package GAPL_project3;

import java.io.IOException;
import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class Tester {

	public static void main(String[] args) throws IOException, MoveDefinitionException, TransitionDefinitionException{

		String prefix = System.getProperty("user.dir") + "/src/main/java/GAPL_project3/";
		StateMachine sm = utils.getGameSM(prefix + "tictactoeXwin.txt");

		MachineState init = sm.getInitialState();
		GameTree test = new GameTree(init, null, sm);

		List<List<Move>> jm = sm.getLegalJointMoves(init);
		System.out.println("Legal moves: "+jm);
		System.out.println("\nNext possible states:");
		for(MachineState x : sm.getNextStates(init)){
			System.out.println(x);
		}

		while(!jm.isEmpty())
		{
			test.addChild(jm.remove(0));
		}

		System.out.println(test.toString());

	}
}
